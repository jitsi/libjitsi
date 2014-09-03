/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.rtp.remotebitrateestimator.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 */
public class MediaStreamStatsImpl
    implements MediaStreamStats
{
    /**
     * Enumeration of the direction (DOWNLOAD or UPLOAD) used for the stats.
     */
    private enum StreamDirection
    {
        DOWNLOAD,
        UPLOAD
    }

    /**
     * The <tt>Logger</tt> used by the <tt>MediaStreamImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaStreamStatsImpl.class);

    /**
     * Computes an Exponentially Weighted Moving Average (EWMA). Thus, the most
     * recent history has a more preponderant importance in the average
     * computed.
     *
     * @param nbStepSinceLastUpdate The number of step which has not been
     * computed since last update. In our case the number of packets received
     * since the last computation.
     * @param lastValue The value computed during the last update.
     * @param newValue The value newly computed.
     *
     * @return The EWMA average computed.
     */
    private static double computeEWMA(
            long nbStepSinceLastUpdate,
            double lastValue,
            double newValue)
    {
        // For each new packet received the EWMA moves by a 0.1 coefficient.
        double EWMACoeff = 0.01 * nbStepSinceLastUpdate;
        // EWMA must be <= 1.
        if(EWMACoeff > 1)
            EWMACoeff = 1.0;
        return lastValue * (1.0 - EWMACoeff) + newValue * EWMACoeff;
    }

    /**
     * Computes the loss rate.
     *
     * @param nbLostAndRecv The number of lost and received packets.
     * @param nbLost The number of lost packets.
     *
     * @return The loss rate in percent.
     */
    private static double computePercentLoss(long nbLostAndRecv, long nbLost)
    {
        return
            (nbLostAndRecv == 0)
                ? 0
                : (((double) 100 * nbLost) / nbLostAndRecv);
    }

    /**
     * Computes the bandwidth usage in Kilo bits per seconds.
     *
     * @param nbByteRecv The number of Byte received.
     * @param callNbTimeMsSpent The time spent since the mediaStreamImpl is
     * connected to the endpoint.
     *
     * @return the bandwidth rate computed in Kilo bits per seconds.
     */
    private static double computeRateKiloBitPerSec(
            long nbByteRecv,
            long callNbTimeMsSpent)
    {
        return
            (nbByteRecv == 0)
                ? 0
                : ((nbByteRecv * 8.0 / 1000.0) / (callNbTimeMsSpent / 1000.0));
    }

    /**
     * Gets the <tt>JitterBufferControl</tt> of a <tt>ReceiveStream</tt>.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> to get the
     * <tt>JitterBufferControl</tt> of
     * @return the <tt>JitterBufferControl</tt> of <tt>receiveStream</tt>.
     */
    public static JitterBufferControl getJitterBufferControl(
            ReceiveStream receiveStream)
    {
        DataSource ds = receiveStream.getDataSource();

        if (ds instanceof PushBufferDataSource)
        {
            for (PushBufferStream pbs
                    : ((PushBufferDataSource) ds).getStreams())
            {
                JitterBufferControl pqc
                    = (JitterBufferControl)
                        pbs.getControl(JitterBufferControl.class.getName());

                if (pqc != null)
                    return pqc;
            }
        }
        return null;
    }

    /**
     * The last jitter received/sent in a RTCP feedback (in RTP timestamp
     * units).
     */
    private double[] jitterRTPTimestampUnits = {0, 0};

    /**
     * The source data stream to analyze in order to compute the stats.
     */
    private final MediaStreamImpl mediaStreamImpl;

    /**
     * The last number of received/sent Bytes.
     */
    private long[] nbByte = {0, 0};

    /**
     * The total number of discarded packets
     */
    private long nbDiscarded = 0;

    /**
     * The number of packets for which FEC data was decoded. This is only
     */
    private long nbFec = 0;

    /**
     * The last number of download/upload lost packets.
     */
    private long[] nbLost = {0, 0};

    /**
     * The last number of received/sent packets.
     */
    private long[] nbPackets = {0, 0};

    /**
     * The last percent of discarded packets
     */
    private double percentDiscarded = 0;

    /**
     * The last download/upload loss rate computed (in %).
     */
    private double[] percentLoss = {0, 0};

    /**
     * The last used bandwidth computed in download/upload (in Kbit/s).
     */
    private double[] rateKiloBitPerSec = {0, 0};

    /**
     * The <tt>RTCPReportListener</tt> which listens to {@link #rtcpReports}
     * about the sending and the receiving of RTCP sender/receiver reports and
     * updates this <tt>MediaStreamStats</tt> with their feedback reports.
     */
    private final RTCPReportListener rtcpReportListener
        = new RTCPReportAdapter()
        {
            /**
             * {@inheritDoc}
             *
             * Updates this <tt>MediaStreamStats</tt> with the received feedback
             * (report).
             */
            @Override
            public void rtcpReportReceived(RTCPReport report)
            {
                List<?> feedbackReports = report.getFeedbackReports();

                if (!feedbackReports.isEmpty())
                {
                    updateNewReceivedFeedback(
                            (RTCPFeedback) feedbackReports.get(0));
                }
            }

            /**
             * {@inheritDoc}
             *
             * Updates this <tt>MediaStreamStats</tt> with the sent feedback
             * (report).
             */
            @Override
            public void rtcpReportSent(RTCPReport report)
            {
                List<?> feedbackReports = report.getFeedbackReports();

                if (!feedbackReports.isEmpty())
                {
                    updateNewSentFeedback(
                            (RTCPFeedback) feedbackReports.get(0));
                }
            }
        };

    /**
     * The detailed statistics about the RTCP reports sent and received by the
     * associated local peer.
     */
    private final RTCPReports rtcpReports = new RTCPReports();

    /**
     * The RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1,
     * subsection "delay since last SR (DLSR): 32 bits").
     * -1 if the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    private long rttMs = -1;

    /**
     * The last time these stats have been updated.
     */
    private long updateTimeMs;

    /**
     * The last number of sent packets when the last feedback has been received.
     * This counter is used to compute the upload loss rate.
     */
    private long uploadFeedbackNbPackets = 0;

    /**
     * Creates a new instance of stats concerning a MediaStream.
     *
     * @param mediaStreamImpl The MediaStreamImpl used to compute the stats.
     */
    public MediaStreamStatsImpl(MediaStreamImpl mediaStreamImpl)
    {
        this.mediaStreamImpl = mediaStreamImpl;

        getRTCPReports().addRTCPReportListener(rtcpReportListener);

        updateTimeMs = System.currentTimeMillis();
    }

    /**
     * Computes the RTT with the data (LSR and DLSR) contained in the last
     * RTCP Sender Report (RTCP feedback). This RTT computation is based on
     * RFC3550, section 6.4.1, subsection "delay since last SR (DLSR): 32
     * bits".
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     *
     * @return The RTT in milliseconds, or -1 if the RTT is not computable.
     */
    private long computeRTTInMs(RTCPFeedback feedback)
    {
        long now = System.currentTimeMillis();
        long lsr = feedback.getLSR();
        long dlsr = feedback.getDLSR();
        int rtt = RecvSSRCInfo.getRoundTripDelay(now, lsr, dlsr);

        /*
         * If the RTT is greater than a minute, it may signal a bug in the
         * computation. Log such occurrences in order to debug them.
         */
        if ((rtt >= 65536) && logger.isInfoEnabled())
        {
            logger.info(
                    "Stream: " + mediaStreamImpl.getName()
                        + ", RTT computation may be wrong (" + rtt
                        + ">= 65536 milliseconds): now " + now + ", lsr " + lsr
                        + ", dlsr " + dlsr);
        }

        return rtt;
    }

    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getDownloadJitterMs()
    {
        return getJitterMs(StreamDirection.DOWNLOAD);
    }

    /**
     * Returns the number of lost packets for the receive streams.
     * @return  the number of lost packets for the receive streams.
     */
    private long getDownloadNbPacketLost()
    {
        long downloadLost = 0;
        for(ReceiveStream stream : mediaStreamImpl.getReceiveStreams())
        {
                downloadLost += stream.getSourceReceptionStats().getPDUlost();
        }
        return downloadLost;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) lost in download since
     * the beginning of the session.
     *
     * @return the number of packets lost for this stream.
     */
    private long getDownloadNbPDULost()
    {
        MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();
        int nbLost = 0;

        if (devSession != null)
        {
            for(ReceiveStream receiveStream : devSession.getReceiveStreams())
                nbLost += receiveStream.getSourceReceptionStats().getPDUlost();
        }
        return nbLost;
    }

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getDownloadPercentLoss()
    {
        return percentLoss[StreamDirection.DOWNLOAD.ordinal()];
    }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    public double getDownloadRateKiloBitPerSec()
    {
        return rateKiloBitPerSec[StreamDirection.DOWNLOAD.ordinal()];
    }

    /**
     * Returns the download video format if this stream downloads a video, or
     * null if not.
     *
     * @return the download video format if this stream downloads a video, or
     * null if not.
     */
    private VideoFormat getDownloadVideoFormat()
    {
        MediaDeviceSession deviceSession = mediaStreamImpl.getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession)
                    .getReceivedVideoFormat()
                : null;
    }

    /**
     * Returns the download video size if this stream downloads a video, or
     * null if not.
     *
     * @return the download video size if this stream downloads a video, or null
     * if not.
     */
    public Dimension getDownloadVideoSize()
    {
        VideoFormat format = getDownloadVideoFormat();

        return (format == null) ? null : format.getSize();
    }

    /**
     * Returns the MediaStream enconding.
     *
     * @return the encoding used by the stream.
     */
    public String getEncoding()
    {
        MediaFormat format = mediaStreamImpl.getFormat();

        return (format == null) ? null : format.getEncoding();
    }

    /**
     * Returns the MediaStream enconding rate (in Hz)..
     *
     * @return the encoding rate used by the stream.
     */
    public String getEncodingClockRate()
    {
        MediaFormat format = mediaStreamImpl.getFormat();

        return (format == null) ? null : format.getRealUsedClockRateString();
    }

    /**
     * Returns the set of <tt>PacketQueueControls</tt> found for all the
     * <tt>DataSource</tt>s of all the <tt>ReceiveStream</tt>s. The set contains
     * only non-null elements.
     *
     * @return the set of <tt>PacketQueueControls</tt> found for all the
     * <tt>DataSource</tt>s of all the <tt>ReceiveStream</tt>s. The set contains
     * only non-null elements.
     */
    private Set<JitterBufferControl> getJitterBufferControls()
    {
        Set<JitterBufferControl> set = new HashSet<JitterBufferControl>();

        if (mediaStreamImpl.isStarted())
        {
            MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();

            if (devSession != null)
            {
                for(ReceiveStream receiveStream
                        : devSession.getReceiveStreams())
                {
                    JitterBufferControl pqc
                        = getJitterBufferControl(receiveStream);

                    if(pqc != null)
                        set.add(pqc);
                }
            }
        }
        return set;
    }

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer.
     * Since there might be multiple <tt>ReceiveStreams</tt>, returns the
     * biggest delay found in any of them.
     *
     * @return the delay in milliseconds introduces by the jitter buffer
     */
    public int getJitterBufferDelayMs()
    {
        int delay = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
          if(pqc.getCurrentDelayMs() > delay)
              delay = pqc.getCurrentDelayMs();
        return delay;
    }

    /**
     * Returns the delay in number of packets introduced by the jitter buffer.
     * Since there might be multiple <tt>ReceiveStreams</tt>, returns the
     * biggest delay found in any of them.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    public int getJitterBufferDelayPackets()
    {
        int delay = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            if(pqc.getCurrentDelayPackets() > delay)
                delay = pqc.getCurrentDelayPackets();
        return delay;
    }

    /**
     * Returns the jitter average of this upload/download stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     *
     * @return the last jitter average computed (in ms).
     */
    private double getJitterMs(StreamDirection streamDirection)
    {
        MediaFormat format = mediaStreamImpl.getFormat();
        double clockRate;

        if (format == null)
        {
            MediaType mediaType = mediaStreamImpl.getMediaType();

            clockRate = MediaType.VIDEO.equals(mediaType) ? 90000 : -1;
        }
        else
            clockRate = format.getClockRate();

        if (clockRate <= 0)
            return -1;

        // RFC3550 says that concerning the RTP timestamp unit (cf. section 5.1
        // RTP Fixed Header Fields, subsection timestamp: 32 bits):
        // As an example, for fixed-rate audio the timestamp clock would likely
        // increment by one for each sampling period.
        //
        // Thus we take the jitter in RTP timestamp units, convert it to seconds
        // (/ clockRate) and finally converts it to milliseconds  (* 1000).
        return
            (jitterRTPTimestampUnits[streamDirection.ordinal()] / clockRate)
                * 1000.0;
    }


    /**
     * Returns the local IP address of the MediaStream.
     *
     * @return the local IP address of the stream.
     */
    public String getLocalIPAddress()
    {
        InetSocketAddress mediaStreamLocalDataAddress
            = mediaStreamImpl.getLocalDataAddress();

        return
            (mediaStreamLocalDataAddress == null)
                ? null
                : mediaStreamLocalDataAddress.getAddress().getHostAddress();
    }

    /**
     * Returns the local port of the MediaStream.
     *
     * @return the local port of the stream.
     */
    public int getLocalPort()
    {
        InetSocketAddress mediaStreamLocalDataAddress
            = mediaStreamImpl.getLocalDataAddress();

        return
            (mediaStreamLocalDataAddress == null)
                ? -1
                : mediaStreamLocalDataAddress.getPort();
    }

    /**
     * Returns the number of sent/received bytes since the beginning of the
     * session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the number of sent/received
     * bytes.
     *
     * @return the number of sent/received bytes for this stream.
     */
    private long getNbBytes(StreamDirection streamDirection)
    {
        StreamRTPManager rtpManager = mediaStreamImpl.queryRTPManager();
        long nbBytes = 0;

        if(rtpManager != null)
        {
            switch(streamDirection)
            {
            case DOWNLOAD:
                nbBytes = rtpManager.getGlobalReceptionStats().getBytesRecd();
                break;
            case UPLOAD:
                nbBytes
                    = rtpManager.getGlobalTransmissionStats().getBytesSent();
                break;
            }
        }
        return nbBytes;
    }

    /**
     * Returns the total number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session. It's the sum over
     * all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets.
     */
    public long getNbDiscarded()
    {
        int nbDiscarded = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            nbDiscarded =+ pqc.getDiscarded();
        return nbDiscarded;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session because it was full.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets because it was full.
     */
    public int getNbDiscardedFull()
    {
        int nbDiscardedFull = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            nbDiscardedFull =+ pqc.getDiscardedFull();
        return nbDiscardedFull;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session because they were late.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets because they were late.
     */
    public int getNbDiscardedLate()
    {
        int nbDiscardedLate = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            nbDiscardedLate =+ pqc.getDiscardedLate();
        return nbDiscardedLate;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session during resets.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets during resets.
     */
    public int getNbDiscardedReset()
    {
        int nbDiscardedReset = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            nbDiscardedReset =+ pqc.getDiscardedReset();
        return nbDiscardedReset;
    }

    /**
     * Returns the number of Protocol Data Units (PDU) discarded by the
     * FMJ packet queue since the beginning of the session due to shrinking.
     * It's the sum over all <tt>ReceiveStream</tt>s of the <tt>MediaStream</tt>
     *
     * @return the number of discarded packets due to shrinking.
     */
    public int getNbDiscardedShrink()
    {
        int nbDiscardedShrink = 0;
        for(JitterBufferControl pqc : getJitterBufferControls())
            nbDiscardedShrink =+ pqc.getDiscardedShrink();
        return nbDiscardedShrink;
    }

    /**
     * Returns the number of packets for which FEC data was decoded. Currently
     * this is cumulative over all <tt>ReceiveStream</tt>s.
     *
     * @return the number of packets for which FEC data was decoded. Currently
     * this is cumulative over all <tt>ReceiveStream</tt>s.
     *
     * @see org.jitsi.impl.neomedia.MediaStreamStatsImpl#updateNbFec()
     */
    public long getNbFec()
    {
        return nbFec;
    }

    /**
     * Returns the total number of packets that are send or receive for this
     * stream since the stream is created.
     * @return the total number of packets.
     */
    public long getNbPackets()
    {
        return getNbPDU(StreamDirection.DOWNLOAD)
            + getDownloadNbPacketLost()
            + uploadFeedbackNbPackets;
    }

    /**
     * Returns the number of lost packets for that stream.
     * @return the number of lost packets.
     */
    public long getNbPacketsLost()
    {
        return nbLost[StreamDirection.UPLOAD.ordinal()]
            + getDownloadNbPacketLost();
    }

    /**
     * Returns the number of Protocol Data Units (PDU) sent/received since the
     * beginning of the session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the number of sent/received
     * packets.
     *
     * @return the number of packets sent/received for this stream.
     */
    private long getNbPDU(StreamDirection streamDirection)
    {
        StreamRTPManager rtpManager = mediaStreamImpl.queryRTPManager();
        long nbPDU = 0;

        if(rtpManager != null)
        {
            switch(streamDirection)
            {
            case UPLOAD:
                nbPDU = rtpManager.getGlobalTransmissionStats().getRTPSent();
                break;

            case DOWNLOAD:
                GlobalReceptionStats globalReceptionStats
                    = rtpManager.getGlobalReceptionStats();

                nbPDU
                    = globalReceptionStats.getPacketsRecd()
                        - globalReceptionStats.getRTCPRecd();
                break;

            }
        }
        return nbPDU;
    }

    @Override
    public long getNbReceivedBytes()
    {
        AbstractRTPConnector connector = mediaStreamImpl.getRTPConnector();

        if(connector != null)
        {
            RTPConnectorInputStream<?> stream;

            try
            {
                stream = connector.getDataInputStream();
            }
            catch (IOException ex)
            {
                // We should not enter here because we are not creating stream.
                stream = null;
            }
            if(stream != null)
                return stream.getNumberOfReceivedBytes();
        }
        return 0;
    }


    @Override
    public long getNbSentBytes()
    {
        AbstractRTPConnector connector = mediaStreamImpl.getRTPConnector();
        if(connector == null)
        {
            return 0;
        }

        RTPConnectorOutputStream stream = null;
        try
        {
            stream = connector.getDataOutputStream(false);
        }
        catch (IOException e)
        {
            //We should not enter here because we are not creating output stream
        }

        if(stream == null)
        {
            return 0;
        }

        return stream.getNumberOfBytesSent();
    }

    /**
     * Returns the number of packets in the first <tt>JitterBufferControl</tt>
     * found via <tt>getJitterBufferControls</tt>.
     *
     * @return the number of packets in the first <tt>JitterBufferControl</tt>
     * found via <tt>getJitterBufferControls</tt>.
     */
    public int getPacketQueueCountPackets()
    {
        for(JitterBufferControl pqc : getJitterBufferControls())
            return pqc.getCurrentPacketCount();
        return 0;
    }

    /**
     * Returns the size of the first <tt>JitterBufferControl</tt> found via
     * <tt>getJitterBufferControls</tt>.
     *
     * @return the size of the first <tt>JitterBufferControl</tt> found via
     * <tt>getJitterBufferControls</tt>.
     */
    public int getPacketQueueSize()
    {
        for(JitterBufferControl pqc : getJitterBufferControls())
            return pqc.getCurrentSizePackets();
        return 0;
    }

    /**
     * Returns the percent of discarded packets
     *
     * @return the percent of discarded packets
     */
    public double getPercentDiscarded()
    {
        return percentDiscarded;
    }

    /**
     * Returns the remote IP address of the MediaStream.
     *
     * @return the remote IP address of the stream.
     */
    public String getRemoteIPAddress()
    {
        MediaStreamTarget mediaStreamTarget = mediaStreamImpl.getTarget();

        // Gets this stream IP address endpoint. Stops if the endpoint is
        // disconnected.
        return
            (mediaStreamTarget == null)
                ? null
                : mediaStreamTarget.getDataAddress().getAddress()
                        .getHostAddress();
    }

    /**
     * Returns the remote port of the MediaStream.
     *
     * @return the remote port of the stream.
     */
    public int getRemotePort()
    {
        MediaStreamTarget mediaStreamTarget = mediaStreamImpl.getTarget();

        // Gets this stream port endpoint. Stops if the endpoint is
        // disconnected.
        return
            (mediaStreamTarget == null)
                ? -1
                : mediaStreamTarget.getDataAddress().getPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPReports getRTCPReports()
    {
        return rtcpReports;
    }

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns -1 if the RTT
     * has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs()
    {
        return rttMs;
    }

    /**
     * Returns the jitter average of this upload stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getUploadJitterMs()
    {
        return getJitterMs(StreamDirection.UPLOAD);
    }

    /**
     * Returns the percent loss of the upload stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getUploadPercentLoss()
    {
        return percentLoss[StreamDirection.UPLOAD.ordinal()];
    }

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    public double getUploadRateKiloBitPerSec()
    {
        return rateKiloBitPerSec[StreamDirection.UPLOAD.ordinal()];
    }

    /**
     * Returns the upload video format if this stream uploads a video, or null
     * if not.
     *
     * @return the upload video format if this stream uploads a video, or null
     * if not.
     */
    private VideoFormat getUploadVideoFormat()
    {
        MediaDeviceSession deviceSession = mediaStreamImpl.getDeviceSession();

        return
            (deviceSession instanceof VideoMediaDeviceSession)
                ? ((VideoMediaDeviceSession) deviceSession)
                    .getSentVideoFormat()
                : null;
    }

    /**
     * Returns the upload video size if this stream uploads a video, or null if
     * not.
     *
     * @return the upload video size if this stream uploads a video, or null if
     * not.
     */
    public Dimension getUploadVideoSize()
    {
        VideoFormat format = getUploadVideoFormat();

        return (format == null) ? null : format.getSize();
    }

    public boolean isAdaptiveBufferEnabled()
    {
        for(JitterBufferControl pcq : getJitterBufferControls())
            if(pcq.isAdaptiveBufferEnabled())
                return true;
        return false;
    }

    /**
     * Sets a specific value on {@link #rttMs}. If there is an actual difference
     * between the old and the new values, notifies the (known)
     * <tt>CallStatsObserver</tt>s.
     *
     * @param rttMs the value to set on <tt>MediaStreamStatsImpl.rttMs</tt>
     */
    private void setRttMs(long rttMs)
    {
        if (this.rttMs != rttMs)
        {
            this.rttMs = rttMs;

            // Notify the CallStatsObservers.
            rttMs = getRttMs();
            if (rttMs >= 0)
            {
                // RemoteBitrateEstimator is a CallStatsObserver and
                // VideoMediaStream has a RemoteBitrateEstimator.
                MediaStream mediaStream = this.mediaStreamImpl;

                if (mediaStream instanceof VideoMediaStream)
                {
                    RemoteBitrateEstimator remoteBitrateEstimator
                        = ((VideoMediaStream) mediaStream)
                            .getRemoteBitrateEstimator();

                    if (remoteBitrateEstimator instanceof CallStatsObserver)
                    {
                        ((CallStatsObserver) remoteBitrateEstimator)
                            .onRttUpdate(rttMs);
                    }
                }
            }
        }
    }

    /**
     * Updates the jitter stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the jitter.
     */
    private void updateJitterRTPTimestampUnits(
            RTCPFeedback feedback,
            StreamDirection streamDirection)
    {
        // Updates the download jitter in RTP timestamp units. There is no need
        // to compute a jitter average, since (cf. RFC3550, section 6.4.1 SR:
        // Sender Report RTCP Packet, subsection interarrival jitter: 32 bits)
        // the value contained in the RTCP sender report packet contains a mean
        // deviation of the jitter.
        jitterRTPTimestampUnits[streamDirection.ordinal()]
            = feedback.getJitter();
    }

    /**
     * Updates the number of discarded packets.
     *
     * @param newNbDiscarded The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss
     * update.
     */
    private void updateNbDiscarded(
            long newNbDiscarded,
            long nbSteps)
    {
        double newPercentDiscarded
            = MediaStreamStatsImpl.computePercentLoss(nbSteps, newNbDiscarded);

        percentDiscarded
            = MediaStreamStatsImpl.computeEWMA(
                    nbSteps,
                    percentDiscarded,
                    newPercentDiscarded);
        // Saves the last update number download lost value.
        nbDiscarded += newNbDiscarded;
    }

    /**
     * Updates the <tt>nbFec</tt> field with the sum of FEC-decoded packets
     * over the different <tt>ReceiveStream</tt>s
     */
    private void updateNbFec()
    {
        MediaDeviceSession devSession = mediaStreamImpl.getDeviceSession();
        int nbFec = 0;

        if(devSession != null)
        {
            for(ReceiveStream receiveStream : devSession.getReceiveStreams())
            {
                for(FECDecoderControl fecDecoderControl
                        : devSession.getDecoderControls(
                                receiveStream,
                                FECDecoderControl.class))
                {
                    nbFec += fecDecoderControl.fecPacketsDecoded();
                }
            }
        }
        this.nbFec = nbFec;
    }

    /**
     * Updates the number of loss for a given stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function updates the stats.
     * @param newNbLost The last update of the number of lost.
     * @param nbSteps The number of elapsed steps since the last number of loss
     * update.
     */
    private void updateNbLoss(
            StreamDirection streamDirection,
            long newNbLost,
            long nbSteps)
    {
        int streamDirectionIndex = streamDirection.ordinal();
        double newPercentLoss
            = MediaStreamStatsImpl.computePercentLoss(nbSteps, newNbLost);

        percentLoss[streamDirectionIndex]
            = MediaStreamStatsImpl.computeEWMA(
                    nbSteps,
                    percentLoss[streamDirectionIndex],
                    newPercentLoss);
        // Saves the last update number download lost value.
        nbLost[streamDirectionIndex] += newNbLost;
    }

    /**
     * Updates this stream stats with the new feedback received.
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     */
    private void updateNewReceivedFeedback(RTCPFeedback feedback)
    {
        StreamDirection streamDirection = StreamDirection.UPLOAD;

        updateJitterRTPTimestampUnits(feedback, streamDirection);

        // Updates the loss rate with the RTCP sender report feedback, since
        // this is the only information source available for the upload stream.
        long uploadNewNbRecv = feedback.getXtndSeqNum();
        long newNbLost
            = feedback.getNumLost() - nbLost[streamDirection.ordinal()];
        long nbSteps = uploadNewNbRecv - uploadFeedbackNbPackets;

        updateNbLoss(streamDirection, newNbLost, nbSteps);

        // Updates the upload loss counters.
        uploadFeedbackNbPackets = uploadNewNbRecv;

        // Computes RTT.
        setRttMs(computeRTTInMs(feedback));
    }

    /**
     * Updates this stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     */
    private void updateNewSentFeedback(RTCPFeedback feedback)
    {
        updateJitterRTPTimestampUnits(feedback, StreamDirection.DOWNLOAD);

        // No need to update the download loss as we have a more accurate value
        // in the global reception stats, which are updated for each new packet
        // received.
    }

    /**
     * Computes and updates information for a specific stream.
     */
    public void updateStats()
    {
        // Gets the current time.
        long currentTimeMs = System.currentTimeMillis();

        // UPdates stats for the download stream.
        updateStreamDirectionStats(StreamDirection.DOWNLOAD, currentTimeMs);
        // UPdates stats for the upload stream.
        updateStreamDirectionStats(StreamDirection.UPLOAD, currentTimeMs);
        // Saves the last update values.
        updateTimeMs = currentTimeMs;
    }

    /**
     * Computes and updates information for a specific stream.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function updates the stats.
     * @param currentTimeMs The current time in ms.
     */
    private void updateStreamDirectionStats(
            StreamDirection streamDirection,
            long currentTimeMs)
    {
        int streamDirectionIndex = streamDirection.ordinal();

        // Gets the current number of packets correctly received since the
        // beginning of this stream.
        long newNbRecv = getNbPDU(streamDirection);
        // Gets the number of byte received/sent since the beginning of this
        // stream.
        long newNbByte = getNbBytes(streamDirection);

        // Computes the number of update steps which has not been done since
        // last update.
        long nbSteps = newNbRecv - nbPackets[streamDirectionIndex];
        // Even if the remote peer does not send any packets (i.e. is
        // microphone is muted), Jitsi must updates it stats. Thus, Jitsi
        // computes a number of steps equivalent as if Jitsi receives a packet
        // each 20ms (default value).
        if(nbSteps == 0)
            nbSteps = (currentTimeMs - updateTimeMs) / 20;

        // The upload percentLoss is only computed when a new RTCP feedback is
        // received. This is not the case for the download percentLoss which is
        // updated for each new RTP packet received.
        // Computes the loss rate for this stream.
        if(streamDirection == StreamDirection.DOWNLOAD)
        {
            // Gets the current number of losses in download since the beginning
            // of this stream.
            long newNbLost
                = getDownloadNbPDULost() - nbLost[streamDirectionIndex];

            updateNbLoss(streamDirection, newNbLost, nbSteps + newNbLost);

            long newNbDiscarded = getNbDiscarded() - nbDiscarded;
            updateNbDiscarded(newNbDiscarded, nbSteps + newNbDiscarded);
        }

        // Computes the bandwidth used by this stream.
        double newRateKiloBitPerSec
            = MediaStreamStatsImpl.computeRateKiloBitPerSec(
                    newNbByte - nbByte[streamDirectionIndex],
                    currentTimeMs - updateTimeMs);
        rateKiloBitPerSec[streamDirectionIndex]
            = MediaStreamStatsImpl.computeEWMA(
                    nbSteps,
                    rateKiloBitPerSec[streamDirectionIndex],
                    newRateKiloBitPerSec);

        // Saves the last update values.
        nbPackets[streamDirectionIndex] = newNbRecv;
        nbByte[streamDirectionIndex] = newNbByte;

        updateNbFec();
    }
}
