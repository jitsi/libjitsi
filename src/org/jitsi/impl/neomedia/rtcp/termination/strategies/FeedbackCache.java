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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author George Politis
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
