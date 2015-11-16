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

import java.util.concurrent.*;
import net.sf.fmj.media.rtp.*;

/**
 * Keeps track of the CNAMEs of the RTP streams that we've seen.
 *
 * @author George Politis
 */
class CNAMERegistry
    extends ConcurrentHashMap<Integer, byte[]>
{
    /**
     * @param inPacket
     */
    public void update(RTCPCompoundPacket inPacket)
    {
        // Update CNAMEs.
        if (inPacket == null
                || inPacket.packets == null
                || inPacket.packets.length == 0)
        {
            return;
        }

        for (RTCPPacket p : inPacket.packets)
        {
            switch (p.type)
            {
            case RTCPPacket.SDES:
                RTCPSDESPacket sdes = (RTCPSDESPacket) p;

                if (sdes.sdes == null || sdes.sdes.length == 0)
                {
                    continue;
                }

                for (RTCPSDES chunk : sdes.sdes)
                {
                    if (chunk.items == null || chunk.items.length == 0)
                    {
                        continue;
                    }

                    for (RTCPSDESItem item : chunk.items)
                    {
                        if (item.type != RTCPSDESItem.CNAME)
                        {
                            continue;
                        }

                        this.put(chunk.ssrc, item.data);
                    }
                }
                break;
            }
        }
    }
}
