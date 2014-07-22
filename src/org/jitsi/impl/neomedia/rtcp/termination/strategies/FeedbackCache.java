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
import java.util.concurrent.*;

/**
 * Created by gp on 22/07/14.
 */
public class FeedbackCache
{
    final Map<Integer, FeedbackCacheEntry> cache
            = new ConcurrentHashMap<Integer, FeedbackCacheEntry>();

    public int size()
    {
        return cache.size();
    }

    public Set<Map.Entry<Integer, FeedbackCacheEntry>> entrySet()
    {
        return cache.entrySet();
    }

    public void update(Integer ssrc, RTCPReportBlock[] reports,
                                    RTCPREMBPacket remb)
    {
        // Update the cache with the new data we've gathered.
        if (ssrc != 0
                && ((reports != null && reports.length != 0) || remb != null))
        {
            long lastUpdate = System.currentTimeMillis();

            FeedbackCacheEntry item = new FeedbackCacheEntry();
            item.reports = reports;
            item.remb = remb;
            item.lastUpdate = lastUpdate;

            if (reports == null || reports.length == 0 || remb == null)
            {
                // Complete the cache item from the cache item already in the
                // cache, if needed.
                if (cache.containsKey(ssrc))
                {
                    FeedbackCacheEntry base = cache.get(ssrc);

                    if (base != null
                            && (reports == null || reports.length == 0))
                    {
                        item.reports = base.reports;
                    }

                    if (base != null && remb == null)
                    {
                        item.remb = base.remb;
                    }
                }
            }

            cache.put(ssrc, item);
        }
    }
}
