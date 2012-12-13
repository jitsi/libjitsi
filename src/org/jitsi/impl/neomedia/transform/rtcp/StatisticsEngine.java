/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import java.util.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>TransformEngine</tt> which doesn't transform packets, just
 * listens for outgoing RTCP packets and logs and stores statistical data about
 * a specific <tt>MediaStream</tt>.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class StatisticsEngine
    implements TransformEngine,
               PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>StatisticsEngine</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(StatisticsEngine.class);

    /**
     * The RTP statistics prefix we use for every log.
     * Simplifies parsing and searching for statistics info in log files.
     */
    public static final String RTP_STAT_PREFIX = "rtpstat:";

    /**
     * Number of lost packets reported.
     */
    private long lost = 0;

    /**
     * The minimum inter arrival jitter value we have reported.
     */
    private long maxInterArrivalJitter = 0;

    /**
     * The stream created us.
     */
    private final MediaStreamImpl mediaStream;

    /**
     * The minimum inter arrival jitter value we have reported.
     */
    private long minInterArrivalJitter = -1;

    /**
     * Number of sender reports send.
     * Used only for logging and debug purposes.
     */
    private long numberOfSenderReports = 0;

    /**
     * Creates Statistic engine.
     * @param stream the stream creating us.
     */
    public StatisticsEngine(MediaStreamImpl stream)
    {
        this.mediaStream = stream;
    }

    /**
     * Close the transformer and underlying transform engine.
     * 
     * Nothing to do here. 
     */
    public void close()
    {
    }

    /**
     * Number of lost packets reported.
     * @return number of lost packets reported.
     */
    public long getLost()
    {
        return lost;
    }

    /**
     * The minimum inter arrival jitter value we have reported.
     * @return minimum inter arrival jitter value we have reported.
     */
    public long getMaxInterArrivalJitter()
    {
        return maxInterArrivalJitter;
    }

    /**
     * The maximum inter arrival jitter value we have reported.
     * @return maximum inter arrival jitter value we have reported.
     */
    public long getMinInterArrivalJitter()
    {
        return minInterArrivalJitter;
    }

    /**
     * Returns a reference to this class since it is performing RTP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>StatisticsEngine</tt>.
     */
    public PacketTransformer getRTCPTransformer()
    {
        return this;
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTP transformations.
     */
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    /**
     * Transfers RTCP sender report feedback as new information about the upload
     * stream for the MediaStreamStats.
     * Returns the packet as we are listening just for sending packages.
     *
     * @param pkt the packet without any change.
     * @return the packet without any change.
     */
    public RawPacket reverseTransform(RawPacket pkt)
    {
        try
        {
            int length = pkt.getLength();

            if(length != 0)
            {
                byte[] data = pkt.getBuffer();
                int offset = pkt.getOffset();
                RTCPHeader header = new RTCPHeader(data, offset, length);

                if(header.getPacketType() == RTCPPacket.SR)
                {
                    RTCPSenderReport senderReport
                        = new RTCPSenderReport(data, offset, length);
                    List<?> feedbackReports = senderReport.getFeedbackReports();

                    if(feedbackReports.size() != 0)
                    {
                        RTCPFeedback feedback
                            = (RTCPFeedback) feedbackReports.get(0);

                        mediaStream
                            .getMediaStreamStats()
                                .updateNewReceivedFeedback(feedback);
                    }
                }
            }
        }
        catch(Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
            {
                logger.error(
                        "Failed to analyze an incoming RTCP packet"
                            + " for the purposes of statistics.",
                        t);
            }
        }

        return pkt;
    }

    /**
     * Transfers RTCP sender report feedback as new information about the
     * download stream for the MediaStreamStats.
     * Finds the info needed for statistics in the packet and stores it.
     * Then returns the same packet as we are not modifying it.
     *
     * @param pkt the packet
     * @return the packet
     */
    public RawPacket transform(RawPacket pkt)
    {
        try
        {
            numberOfSenderReports++;

            byte[] data = pkt.getBuffer();
            int offset = pkt.getOffset();
            int length = pkt.getLength();
            RTCPHeader header = new RTCPHeader(data, offset, length);

            if (header.getPacketType() == RTCPPacket.SR)
            {
                RTCPSenderReport senderReport
                    = new RTCPSenderReport(data, offset, length);
                List<?> feedbackReports = senderReport.getFeedbackReports();

                if(feedbackReports.size() != 0)
                {
                    RTCPFeedback feedback
                        = (RTCPFeedback) feedbackReports.get(0);

                    mediaStream
                        .getMediaStreamStats()
                            .updateNewSentFeedback(feedback);

                    // The rest of this function is only used for logging
                    // purpose. Thus, it is useless to continue if the
                    // logger is not at least in INFO mode.
                    if(!logger.isInfoEnabled())
                        return pkt;

                    long jitter = feedback.getJitter();

                    if((jitter < getMinInterArrivalJitter())
                            || (getMinInterArrivalJitter() == -1))
                        minInterArrivalJitter = jitter;
                    if(getMaxInterArrivalJitter() < jitter)
                        maxInterArrivalJitter = jitter;

                    lost = feedback.getNumLost();

                    // As sender reports are sent on every 5 seconds, print
                    // every 4th packet, on every 20 seconds.
                    if(numberOfSenderReports % 4 != 1)
                        return pkt;

                    StringBuilder buff = new StringBuilder(RTP_STAT_PREFIX);
                    MediaType mediaType = mediaStream.getMediaType();
                    String mediaTypeStr
                        = (mediaType == null) ? "" : mediaType.toString();

                    buff.append("Sending a report for ")
                        .append(mediaTypeStr)
                        .append(" stream SSRC:")
                        .append(feedback.getSSRC())
                        .append(" [packet count:")
                        .append(senderReport.getSenderPacketCount())
                        .append(", bytes:")
                        .append(senderReport.getSenderByteCount())
                        .append(", interarrival jitter:")
                        .append(jitter)
                        .append(", lost packets:").append(feedback.getNumLost())
                        .append(", time since previous report:")
                        .append((int) (feedback.getDLSR() / 65.536))
                        .append("ms ]");
                    logger.info(buff);
                }
            }
        }
        catch(Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
            {
                logger.error(
                        "Failed to analyze an outgoing RTCP packet"
                            + " for the purposes of statistics.",
                        t);
            }
        }

        return pkt;
    }
}
