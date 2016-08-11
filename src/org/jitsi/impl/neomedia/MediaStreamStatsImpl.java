/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import javax.media.rtp.rtcp.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.remotebitrateestimator.*;
import org.jitsi.impl.neomedia.stats.*;
import org.jitsi.impl.neomedia.transform.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.service.neomedia.stats.*;
import org.jitsi.util.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * Note: please do not add more code here. New code should be added to
 * {@link MediaStreamStats2Impl} instead, where we can manage the complexity
 * and consistency better.
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
    public enum StreamDirection
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
     * Computes the bitrate in kbps.
     *
     * @param nbBytes The number of bytes received.
     * @param intervalMs The number of milliseconds during which
     * <tt>nbBytes</tt> bytes were sent or received.
     *
     * @return the bitraterate computed in kbps (1000 bits per second)
     */
    private static double computeRateKiloBitPerSec(
            long nbBytes,
            long intervalMs)
    {
        return intervalMs == 0
            ? 0
            : (nbBytes * 8.0) / intervalMs;
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
     * The number of packets lost, as reported by the remote side in the last
     * received RTCP RR.
     */
    private long nbPacketsLostUpload = 0;

    /**
     * The {@code RemoteClockEstimator} which tracks the remote (wall)clocks and
     * RTP timestamps of the RTP streams received by the associated
     * {@link #mediaStreamImpl}.
     */
    private final RemoteClockEstimator _remoteClockEstimator;

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
                MediaStreamStatsImpl.this.rtcpReportReceived(report);
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
     * The maximum inter arrival jitter value the other party has reported, in
     * RTP time units.
     */
    private long minRemoteInterArrivalJitter = -1;

    /**
     * The minimum inter arrival jitter value the other party has reported, in
     * RTP time units.
     */
    private long maxRemoteInterArrivalJitter = 0;

    /**
     * The sum of all RTP jitter values reported by the remote side, in RTP
     * time units.
     */
    private long remoteJitterSum = 0;

    /**
     * The number of remote RTP jitter reports received.
     */
    private int remoteJitterCount = 0;

    /**
     * The list of listeners to be notified when NACK packets are received.
     */
    private final List<NACKListener> nackListeners = new LinkedList<>();

    /**
     * The list of listeners to be notified when REMB packets are received.
     */
    private final List<REMBListener> rembListeners = new LinkedList<>();

    /**
     * Creates a new instance of stats concerning a MediaStream.
     *
     * @param mediaStreamImpl The MediaStreamImpl used to compute the stats.
     */
    public MediaStreamStatsImpl(MediaStreamImpl mediaStreamImpl)
    {
        this.mediaStreamImpl = mediaStreamImpl;

        _remoteClockEstimator
            = new RemoteClockEstimator(mediaStreamImpl.getMediaType());
        updateTimeMs = System.currentTimeMillis();

        getRTCPReports().addRTCPReportListener(rtcpReportListener);
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
    private int computeRTTInMs(RTCPFeedback feedback)
    {
        long now = System.currentTimeMillis();
        long lsr = feedback.getLSR();
        long dlsr = feedback.getDLSR();
        int rtt = -1;

        // The RTCPFeedback may represents a Sender Report without any report
        // blocks (and so without LSR and DLSR)
        if (lsr > 0 && dlsr > 0)
        {
            // If we perform RTCP termination, the NTP timestamps we include in
            // outgoing SRs are based on the actual sender's clock. We need to
            // take this into account in order to compute the correct RTT.
            now = maybeEstimateRemoteClock(feedback.getSSRC(), now);

            rtt = getRoundTripDelay(now, lsr, dlsr);

            // Values over 3s are suspicious and likely indicate a bug.
            if (rtt < 0 || rtt >= 3000)
            {
                logger.info(
                        "Stream: " + mediaStreamImpl.getName()
                                + ", RTT computation may be wrong (" + rtt
                                + "): now " + now + ", lsr " + lsr + ", dlsr "
                                + dlsr);

                rtt = -1;
            }
        }


        return rtt;
    }

    /**
     * Gets the round trip (delay) time between RTP interfaces, expressed in
     * milliseconds.
     *
     * @param timeMs the <tt>System</tt> time in milliseconds since the epoch.
     * @param lsr the LSR (last SR) time reported in SR (Sender Report) in NTP
     * Short Format (Q16.16).
     * @param dlsr the DLSR (delay since last SR) reported in SR in NTP Short
     * Format (Q16.16).
     * @return the round trip (delay) time between RTP interfaces, expressed in
     * milliseconds, or -1 if it can not be calculated.
     */
    public static int getRoundTripDelay(long timeMs, long lsr, long dlsr)
    {
        long ntpTime = TimeUtils.toNtpTime(timeMs);
        ntpTime = TimeUtils.toNtpShortFormat(ntpTime);

        long ntprtd = ntpTime - lsr - dlsr;

        long delayLong;
        if (ntprtd >= 0)
        {
            delayLong = TimeUtils.ntpShortToMs(ntprtd);
        }
        else
        {
            /*
             * Even if ntprtd is negative we compute delayLong
             * as it might round to zero.
             * ntpShortToMs expect positive numbers.
             */
            delayLong = -TimeUtils.ntpShortToMs(-ntprtd);
        }

        if (delayLong >= 0 && delayLong < Integer.MAX_VALUE)
        {
            return (int) delayLong;
        }

        return -1;
    }

    /**
     * Estimates the time on the clock of a remote RTP endpoint (identified by
     * <tt>ssrc</tt>) corresponding to the local time <tt>localTimeMs</tt>, if
     * RTCP termination is enabled and the translation can be performed.
     * Otherwise, does not perform any estimation/translation and return the
     * input time.
     *
     * @param ssrc the SSRC which identifies the remote RTP endpoint.
     * @param localTimeMs the local in milliseconds since the epoch.
     * @return an estimation of the time of the RTP endpoint with SSRC
     * <tt>ssrc</tt> at local time <tt>localTimeMs</tt>, in milliseconds since
     * the epoch.
     */
    private long maybeEstimateRemoteClock(long ssrc, long localTimeMs)
    {
        long remoteTimeMs = localTimeMs;

        RemoteClock remoteClock
            = RemoteClock.findRemoteClock(mediaStreamImpl, (int) ssrc);
        if (remoteClock != null)
        {
            Timestamp remoteTs = remoteClock.estimate(localTimeMs);
            if (remoteTs != null)
            {
                remoteTimeMs = remoteTs.getSystemTimeMs();
            }
        }

        return remoteTimeMs;
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
    public long getDownloadNbPacketLost()
    {
        long downloadLost = 0;
        for(ReceiveStream stream : mediaStreamImpl.getReceiveStreams())
        {
                downloadLost += stream.getSourceReceptionStats().getPDUlost();
        }
        return downloadLost;
    }

    /**
     * Returns the total number of sent packets lost.
     * @return  the total number of sent packets lost.
     */
    public long getUploadNbPacketLost()
    {
        return nbPacketsLostUpload;
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
        Set<JitterBufferControl> set = new HashSet<>();

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
        // RFC3550 says that concerning the RTP timestamp unit (cf. section 5.1
        // RTP Fixed Header Fields, subsection timestamp: 32 bits):
        // As an example, for fixed-rate audio the timestamp clock would likely
        // increment by one for each sampling period.
        //
        // Thus we take the jitter in RTP timestamp units, convert it to seconds
        // (/ clockRate) and finally converts it to milliseconds  (* 1000).
        return rtpTimeToMs(jitterRTPTimestampUnits[streamDirection.ordinal()]);
    }

    /**
     * Gets the RTP clock rate associated with the <tt>MediaStream</tt>.
     * @return the RTP clock rate associated with the <tt>MediaStream</tt>.
     */
    private double getRtpClockRate()
    {
        MediaFormat format = mediaStreamImpl.getFormat();
        double clockRate;

        if (format == null)
        {
            MediaType mediaType = mediaStreamImpl.getMediaType();

            clockRate = MediaType.VIDEO.equals(mediaType) ? 90000 : 48000;
        }
        else
            clockRate = format.getClockRate();

        return clockRate;
    }

    /**
     * Converts from RTP time units (using the assumed RTP clock rate of the
     * media stream) to milliseconds. Returns -1D if an appropriate RTP clock
     * rate cannot be found.
     * @param rtpTime the RTP time units to convert.
     * @return the milliseconds corresponding to <tt>rtpTime</tt> RTP units.
     */
    private double rtpTimeToMs(double rtpTime)
    {
        double rtpClockRate = getRtpClockRate();
        if (rtpClockRate <= 0)
            return -1D;
        return (rtpTime / rtpClockRate) * 1000;
    }

    /**
     * {@inheritDoc}
     */
    public double getMinDownloadJitterMs()
    {
        StatisticsEngine statisticsEngine
                = mediaStreamImpl.getStatisticsEngine();
        if (statisticsEngine != null)
        {
            return rtpTimeToMs(statisticsEngine.getMinInterArrivalJitter());
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public double getMaxDownloadJitterMs()
    {
        StatisticsEngine statisticsEngine
                = mediaStreamImpl.getStatisticsEngine();
        if (statisticsEngine != null)
        {
            return rtpTimeToMs(statisticsEngine.getMaxInterArrivalJitter());
        }

        return -1D;
    }

    /**
     * {@inheritDoc}
     */
    public double getMinUploadJitterMs()
    {
        return rtpTimeToMs(minRemoteInterArrivalJitter);
    }

    /**
     * {@inheritDoc}
     */
    public double getMaxUploadJitterMs()
    {
        return rtpTimeToMs(maxRemoteInterArrivalJitter);
    }

    /**
     * {@inheritDoc}
     */
    public double getAvgDownloadJitterMs()
    {
        StatisticsEngine statisticsEngine
                = mediaStreamImpl.getStatisticsEngine();
        if (statisticsEngine != null)
        {
            return rtpTimeToMs(statisticsEngine.getAvgInterArrivalJitter());
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public double getAvgUploadJitterMs()
    {
        int count = remoteJitterCount;
        if (count == 0)
            return -1;
        return rtpTimeToMs(((double) remoteJitterSum) / count);
    }

    /**
     * Notifies this instance that an RTCP report with the given value for
     * RTP jitter was received.
     * @param remoteJitter the jitter received, in RTP time units.
     */
    public void updateRemoteJitter(long remoteJitter)
    {
        if((remoteJitter < minRemoteInterArrivalJitter)
                || (minRemoteInterArrivalJitter == -1))
            minRemoteInterArrivalJitter = remoteJitter;

        if(maxRemoteInterArrivalJitter < remoteJitter)
            maxRemoteInterArrivalJitter = remoteJitter;

        remoteJitterSum += remoteJitter;
        remoteJitterCount++;
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
     * @return the number of sent/received bytes for this stream.
     */
    private long getNbBytes(StreamDirection streamDirection)
    {
        return getBasicStats(streamDirection).getBytes();
    }

    private BasicStreamStats getBasicStats(StreamDirection streamDirection)
    {
        MediaStreamStats2Impl extended = getExtended();

        return streamDirection == StreamDirection.DOWNLOAD
            ? extended.getReceiveStats() : extended.getSendStats();
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
     * {@inheritDoc}
     */
    public long getNbPacketsSent()
    {
        return getNbPDU(StreamDirection.UPLOAD);
    }

    /**
     * {@inheritDoc}
     */
    public long getNbPacketsReceived()
    {
        return getNbPDU(StreamDirection.DOWNLOAD);
    }

    /**
     * Returns the number of Protocol Data Units (PDU) sent/received since the
     * beginning of the session.
     *
     * @param streamDirection The stream direction (DOWNLOAD or UPLOAD) of the
     * stream from which this function retrieve the number of sent/received
     * packets.
     * @return the number of packets sent/received for this stream.
     */
    private long getNbPDU(StreamDirection streamDirection)
    {
        return getBasicStats(streamDirection).getPackets();
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
     * Gets the {@link RemoteClockEstimator} of this instance.
     *
     * @return the {@code RemoteClockEstimator} of this instance.
     */
    public RemoteClockEstimator getRemoteClockEstimator()
    {
        return _remoteClockEstimator;
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
                            .onRttUpdate(
                                    /* avgRttMs */ rttMs,
                                    /* maxRttMs*/ rttMs);
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

        MediaStreamStats2Impl extended = getExtended();
        extended.updateJitter(
            feedback.getSSRC(),
            streamDirection,
            rtpTimeToMs(feedback.getJitter()));
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

        nbPacketsLostUpload = feedback.getNumLost();

        long newNbLost
            = nbPacketsLostUpload - nbLost[streamDirection.ordinal()];
        long nbSteps = uploadNewNbRecv - uploadFeedbackNbPackets;

        updateNbLoss(streamDirection, newNbLost, nbSteps);

        // Updates the upload loss counters.
        uploadFeedbackNbPackets = uploadNewNbRecv;

        // Computes RTT.
        int rtt = computeRTTInMs(feedback);
        // If a new RTT could not be computed based on this feedback, keep the
        // old one.
        if (rtt >= 0)
        {
            setRttMs(rtt);

            MediaStreamStats2Impl extended = getExtended();
            extended.updateRtt(feedback.getSSRC(), rtt);
        }
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
            = computeRateKiloBitPerSec(newNbByte - nbByte[streamDirectionIndex],
                                       currentTimeMs - updateTimeMs);
        rateKiloBitPerSec[streamDirectionIndex]
            = computeEWMA(nbSteps,
                          rateKiloBitPerSec[streamDirectionIndex],
                          newRateKiloBitPerSec);

        // Saves the last update values.
        nbPackets[streamDirectionIndex] = newNbRecv;
        nbByte[streamDirectionIndex] = newNbByte;

        updateNbFec();
    }

    /**
     * Notifies this instance that an RTCP REMB packet was received.
     * @param remb the packet.
     */
    public void rembReceived(RTCPREMBPacket remb)
    {
        if (remb != null)
        {
            for (REMBListener listener : rembListeners)
            {
                listener.rembReceived(remb.getBitrate());
            }
        }
    }

    /**
     * Notifies this instance that an RTCP NACK packet was received.
     * @param nack the packet.
     */
    public void nackReceived(NACKPacket nack)
    {
        if (nack != null)
        {
            for (NACKListener listener : nackListeners)
            {
                listener.nackReceived(nack);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNackListener(NACKListener listener)
    {
        if (listener != null)
        {
            synchronized (nackListeners)
            {
                nackListeners.add(listener);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRembListener(REMBListener listener)
    {
        if (listener != null)
        {
            synchronized (rembListeners)
            {
                rembListeners.add(listener);
            }
        }
    }

    /**
     * Notifies this instance that a specific RTCP RR or SR report was received
     * by {@link #rtcpReports}.
     *
     * @param report the received RTCP RR or SR report
     */
    private void rtcpReportReceived(RTCPReport report)
    {
        try
        {
            if (report instanceof SenderReport)
            {
                // Estimate the remote (wall)clock.
                getRemoteClockEstimator().update((SenderReport) report);
            }
        }
        finally
        {
            // reception report blocks
            List<RTCPFeedback> feedbackReports = report.getFeedbackReports();

            if (!feedbackReports.isEmpty())
            {
                updateNewReceivedFeedback(feedbackReports.get(0));

                MediaStreamStats2Impl extended = getExtended();
                for (RTCPFeedback rtcpFeedback : feedbackReports)
                {
                    extended.rtcpReceiverReportReceived(
                            rtcpFeedback.getSSRC(),
                            rtcpFeedback.getFractionLost());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * This method is different from {@link #getUploadRateKiloBitPerSec()} in
     * that:
     * 1. It is not necessary for {@link #updateStats()} to be called
     * periodically by the user of libjitsi in order for it to return correct
     * values.
     * 2. The returned value is based on the average bitrate over a fixed
     * window, as opposed to an EWMA.
     * 3. The measurement is performed after the {@link MediaStream}'s
     * transformations, notably after simulcast layers are dropped (i.e. closer
     * to the network interface).
     *
     * The return value includes RTP payload and RTP headers, as well as RTCP.
     */
    @Override
    public long getSendingBitrate()
    {
        long sbr = -1;
        AbstractRTPConnector rtpConnector = mediaStreamImpl.getRTPConnector();

        if (rtpConnector != null)
        {
            try
            {
                RTPConnectorOutputStream rtpStream
                    = rtpConnector.getDataOutputStream(false);

                if (rtpStream != null)
                {
                    long now = System.currentTimeMillis();
                    sbr = rtpStream.getOutputBitrate(now);

                    RTPConnectorOutputStream rtcpStream
                        = rtpConnector.getControlOutputStream(false);
                    if (rtcpStream != null)
                    {
                        sbr += rtcpStream.getOutputBitrate(now);
                    }
                }
            }
            catch (IOException ioe)
            {
                logger.warn("Failed to get sending bitrate: ", ioe);
            }
        }

        return sbr;
    }

    private MediaStreamStats2Impl getExtended()
    {
        return mediaStreamImpl.getMediaStreamStats();
    }

}
