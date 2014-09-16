/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;

import java.util.*;

/**
* @author George Politis
*/
public class FeedbackCacheProcessor
{
    public FeedbackCacheProcessor(
            FeedbackCache feedbackCache)
    {
        this.feedbackCache = feedbackCache;
    }

    /**
     * A cache of media receiver feedback. It contains both receiver report
     * blocks and REMB packets.
     */
    private final FeedbackCache feedbackCache;

    /**
     * Adapt media senders to the needs of the this percentile of media
     * receivers.
     */
    private int percentile = 70;

    /**
     * Holds the last time in milliseconds we were asked to generate RTCP
     * reports. It is used to determine whether an item in the cache has
     * expired.
     */
    private long lastRun;

    /**
     * Specifies how much time before the <tt>lastRun</tt> we allow cache items
     * to live.
     */
    private long expireMillis = 0L;

    /**
     * Lightweight report block information placeholder.
     */
    static class RRData
    {
        int fraction;
        long lost;
        long jitter;
        long dlsr;
        long lsr;
        long seqnum;
    }

    /**
     * Lightweight REMB packet information placeholder.
     */
    static class REMBData
    {
        int exp;
        int mantissa;
    }

    /**
     * Lightweight report block and REMB information placeholder.An RTCPPacket
     * may hold a reference to the base UDP packet. It'd be a waste of space to
     * keep that in memory since we don't need it.
     */
    static class FeedbackData
    {
        REMBData remb;
        RRData rr;
    }

    /**
     * Builds the <dest, <source, feedback>> map map using the cached receiver
     * feedback information.
     *
     * @return <dest, <source, feedback>>
     */
    private Map<Integer, Map<Integer, FeedbackData>>
                                                getReverseFeedbackMapMap()
    {
        Map<Integer, Map<Integer, FeedbackData>> reverseFeedbackMapMap
                = new HashMap<Integer, Map<Integer, FeedbackData>>();

        if (feedbackCache != null && feedbackCache.size() != 0)
        {
            for (Map.Entry<Integer, FeedbackCacheEntry> entry
                    : feedbackCache.entrySet())
            {
                FeedbackCacheEntry item = entry.getValue();

                // Skip expired feedback.
                if (item.lastUpdate < lastRun - expireMillis)
                    continue;

                Integer sender = entry.getKey();

                // Process RRs in this cache item.
                if (item.reports != null && item.reports.length != 0)
                {
                    for (RTCPReportBlock b : item.reports)
                    {
                        FeedbackData feedback = new FeedbackData();
                        feedback.rr = new RRData();
                        feedback.rr.fraction = b.getFractionLost();
                        feedback.rr.lost = b.getNumLost();
                        feedback.rr.jitter = b.getJitter();
                        feedback.rr.dlsr = b.getDLSR();
                        feedback.rr.lsr = b.getLSR();
                        feedback.rr.seqnum = b.getXtndSeqNum();

                        Integer dest = Integer.valueOf((int) b.getSSRC());

                        // <dest, feedback>
                        Map<Integer, FeedbackData> reverseFeedbackMap
                                = new HashMap<Integer, FeedbackData>();

                        reverseFeedbackMapMap.put(dest, reverseFeedbackMap);
                        reverseFeedbackMap.put(sender, feedback);
                    }
                }

                // Process the REMB packet in this cache item.
                if (item.remb != null
                        && item.remb.dest != null
                        && item.remb.dest.length != 0)
                {
                    for (long destl : item.remb.dest)
                    {
                        Integer dest = Integer.valueOf((int) destl);
                        // <dest, feedback>
                        Map<Integer, FeedbackData> reverseFeedbackMap;
                        if (reverseFeedbackMapMap.containsKey(dest))
                        {
                            reverseFeedbackMap
                                    = reverseFeedbackMapMap.get(dest);
                        }
                        else
                        {
                            reverseFeedbackMap
                                    = new HashMap<Integer, FeedbackData>();

                            reverseFeedbackMapMap.put(dest,
                                    reverseFeedbackMap);

                        }

                        FeedbackData feedback;
                        if (reverseFeedbackMap.containsKey(sender))
                        {
                            feedback = reverseFeedbackMap.get(sender);
                        }
                        else
                        {
                            feedback = new FeedbackData();
                            reverseFeedbackMap.put(sender, feedback);
                        }

                        feedback.remb = new REMBData();
                        feedback.remb.exp = item.remb.exp;
                        feedback.remb.mantissa = item.remb.mantissa;
                    }

                }
            }
        }

        lastRun = System.currentTimeMillis();

        return reverseFeedbackMapMap;
    }

    /**
     * Calculates a score for the feedback.
     *
     * @param feedback
     * @return
     */
    private double calculateScore(FeedbackData feedback)
    {
        if (feedback == null)
            throw new IllegalArgumentException();

        if (feedback.remb == null)
            return -1;

        double score
                = feedback.remb.mantissa * Math.pow(2, feedback.remb.exp);

        if (feedback.rr != null)
        {
            score = ((100 - feedback.rr.lost) / 100) * score;
        }

        return score;
    }

    /**
     *
     * @return <dest, feedback>
     */
    public Map<Integer, FeedbackData> getReverseFeedbackMap()
    {
        // <dest, <source, feedback>>
        Map<Integer, Map<Integer, FeedbackData>> reverseFeedbackMapMap
                = getReverseFeedbackMapMap();

        if (reverseFeedbackMapMap == null || reverseFeedbackMapMap.size() == 0)
            return null;

        // let's build the <dest, feedback> map
        Map<Integer, FeedbackData> reverseFeedbackMap
                = new HashMap<Integer, FeedbackData>();

        // iterate over the destinations
        for (Map.Entry<Integer, Map<Integer, FeedbackData>> entry
                : reverseFeedbackMapMap.entrySet())
        {
            Integer dest = entry.getKey();

            // <score, feedback>
            NavigableMap<Double, FeedbackData> scoresMap = new TreeMap<Double, FeedbackData>();

            // rank feedbacks

            // <source, feedback>
            Map<Integer, FeedbackData> feedbackMap = entry.getValue();
            for (FeedbackData feedback : feedbackMap.values())
            {
                double score = calculateScore(feedback);
                scoresMap.put(score, feedback);
            }

            // satisfy the nth percentile (decide)
            int p = this.percentile;
            if (p > 100 || p < 0)
            {
                // set to something reasonable.
                p = 70;
            }

            int idxNearestBest
                    = (int) Math.ceil((p / 100.0) * scoresMap.size()) - 1;

            FeedbackData feedback
                    = new ArrayList<FeedbackData>(scoresMap.values())
                            .get(idxNearestBest);

            reverseFeedbackMap.put(dest, feedback);
        }

        return reverseFeedbackMap;
    }

    public int getPercentile()
    {
        return percentile;
    }

    public void setPercentile(int percentile)
    {
        this.percentile = percentile;
    }

    public RTCPPacket[] makeReports(int localSSRC)
    {
        Map<Integer, FeedbackData> reverseFeedback
                = getReverseFeedbackMap();

        if (reverseFeedback == null || reverseFeedback.size() == 0)
            return null;

        List<RTCPReportBlock> reportBlocks
                = new ArrayList<RTCPReportBlock>(reverseFeedback.size());
        List<RTCPREMBPacket> rembs
                = new ArrayList<RTCPREMBPacket>(reverseFeedback.size());

        for (Map.Entry<Integer, FeedbackData> entry
                : reverseFeedback.entrySet())
        {
            Integer dest = entry.getKey();
            FeedbackData feedback = entry.getValue();

            if (feedback.remb != null)
            {
                RTCPREMBPacket remb = new RTCPREMBPacket(
                        localSSRC, 0L, feedback.remb.exp,
                        feedback.remb.mantissa,
                        new long[] { dest & 0xFFFFFFFFL });

                rembs.add(remb);
            }

            if (feedback.rr != null)
            {
                RTCPReportBlock reportBlock = new RTCPReportBlock(
                        dest,
                        feedback.rr.fraction,
                        (int) feedback.rr.lost,
                        feedback.rr.seqnum,
                        (int) feedback.rr.jitter,
                        feedback.rr.lsr,
                        feedback.rr.dlsr
                );

                reportBlocks.add(reportBlock);
            }
        }

        // NOTE(gp) an RR is always needed, even if it's empty, as Chrome
        // ignores standalone REMB packets.
        RTCPPacket[] packets = new RTCPPacket[rembs.size() + 1];

        // Adds the RR.
        RTCPReportBlock[] rtcpReportBlocks = reportBlocks.toArray(
                new RTCPReportBlock[reportBlocks.size()]);
        packets[0] = new RTCPRRPacket(localSSRC, rtcpReportBlocks);

        // Adds the REMBs.
        RTCPREMBPacket[] rembarr = rembs.toArray(
                new RTCPREMBPacket[rembs.size()]);
        System.arraycopy(rembarr, 0, packets, 1, rembarr.length);

        lastRun = System.currentTimeMillis();

        return packets;
    }
}
