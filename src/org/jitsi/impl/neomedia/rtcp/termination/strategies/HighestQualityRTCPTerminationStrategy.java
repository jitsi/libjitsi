/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;

/**
 * <p>
 * For each media sender we can calculate a reverse map of all its
 * receivers and the feedback they report. From this the bridge can
 * calculate a reverse map map (not a typo) like this:
 * </p>
 *
 * <pre>
 * &lt;media sender, &lt;media receiver, feedback&gt;&gt;
 * </pre>
 *
 * <p>
 * For example, suppose we have a conference of 4 endpoints like in the
 * figure bellow :
 * </p>
 *
 * <pre>
 * +---+      +-------------+      +---+
 * | A |<---->|             |<---->| B |
 * +---+      |    Jitsi    |      +---+
 *            | Videobridge |
 * +---+      |             |      +---+
 * | C |<---->|             |<---->| D |
 * +---+      +-------------+      +---+
 *
 * Figure 1: Sample video conference
 *
 * </pre>
 *
 * <p>
 * The reverse map map would have the following form :
 * </p>
 *
 * <pre>
 * &lt;A, &lt;B, feedback of B&gt;&gt;
 * &lt;A, &lt;C, feedback of C&gt;&gt;
 * ...
 * &lt;B, &lt;A, feedback of A&gt;&gt;
 * &lt;B, &lt;C, feedback of C&gt;&gt;
 * ...
 * </pre>
 *
 * <p>
 * In other words, for each endpoint that sends video, the bridge
 * calculates the following picture :
 * </p>
 *
 * <pre>
 *             +-------------+-data->+---+
 *             |             |       | B | RANK#3
 *             |             |<-feed-+---+
 *             |             |
 * +---+-data->|    Jitsi    |-data->+---+
 * | A |       | Videobridge |       | D | RANK#1
 * +---+<-feed-|             |<-feed-+---+
 *             |             |
 *             |             |-data->+---+
 *             |             |       | C | RANK#2
 *             +-------------+<-feed-+---+
 *
 * Figure 2: Partial view of the conference. A sends media and receives
 * feedback. B, D, C receive media and send feedback.
 * </pre>
 *
 * <p>
 * This calculation is not instantaneous, so it takes place ONLY when we
 * the bridge decides to send RTCP feedback, and not, for example, when
 * we inspect/modify incoming RTCP packets.
 * </p>
 *
 * <p>
 * We do that by keeping a feedback cache that holds the most recent
 * <tt>RTCPReportBlock</tt>s and <tt>RTCPREMBPacket</tt>s grouped by media
 * receiver SSRC. So, at any given moment we have the last reported feedback for
 * all the media receivers in the conference, and from that we can
 * calculate the above reverse map map.
 * </p>
 *
 * <p>
 * What's most interesting, maybe, is the score logic :
 * </p>
 *
 * <pre> {@code
 * double score = feedback.remb.mantissa * Math.pow(2, feedback.remb.exp);
 * if (feedback.rr != null) {
 *   score = ((100 - feedback.rr.lost) / 100) * score;
 * }
 * } </pre>
 *
 * <p>
 * The score is basically the available bandwidth estimation after taking
 * into consideration the packet losses.
 * </p>
 *
 * <p>
 * For each media sender and after having calculated the score for each
 * media receiver, we consider only the Nth percentile to find the best
 * score and we then report that one.
 * </p>
 *
 * @author George Politis
 */
public class HighestQualityRTCPTerminationStrategy
        extends AbstractRTCPTerminationStrategy
{
    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private final FeedbackCacheProcessor feedbackCacheProcessor;

    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    private final FeedbackCache feedbackCache;

    /**
     * Ctor.
     */
    public HighestQualityRTCPTerminationStrategy()
    {
        this.feedbackCache = new FeedbackCache();
        this.feedbackCacheProcessor
                = new FeedbackCacheProcessor(feedbackCache);

        // TODO(gp) make percentile configurable.
        this.feedbackCacheProcessor.setPercentile(70);

        setTransformerChain(new RTCPPacketTransformer[]{
                new FeedbackCacheUpdater(feedbackCache),
                new ReceiverFeedbackFilter()
        });
    }

    @Override
    public RTCPPacket[] makeReports()
    {
        // Uses the cache processor to make the RTCP reports.

        RTPTranslator t = this.getRTPTranslator();
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        long localSSRC = ((RTPTranslatorImpl)t).getLocalSSRC(null);

        RTCPPacket[] packets = feedbackCacheProcessor.makeReports(
                (int) localSSRC);

        return packets;
    }
}