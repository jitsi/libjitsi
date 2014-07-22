/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by gp on 7/4/14.
 */
public class HighestQualityRTCPTerminationStrategy
        extends BasicRTCPTerminationStrategy
{
    /**
     * The cache processor that will be making the RTCP reports coming from
     * the bridge.
     */
    private FeedbackCacheProcessor feedbackCacheProcessor;

    @Override
    public RTCPPacket[] makeReports()
    {
        // Uses the cache processor to make the RTCP reports.

        RTPTranslator t = this.translator;
        if (t == null || !(t instanceof RTPTranslatorImpl))
            return new RTCPPacket[0];

        long localSSRC = ((RTPTranslatorImpl)t).getLocalSSRC(null);

        if (this.feedbackCacheProcessor == null)
        {
            this.feedbackCacheProcessor
                    = new FeedbackCacheProcessor(feedbackCache);

            // TODO(gp) make percentile configurable.
            this.feedbackCacheProcessor.setPercentile(70);
        }

        RTCPPacket[] packets = feedbackCacheProcessor.makeReports(
                (int) localSSRC);

        return packets;
    }
}