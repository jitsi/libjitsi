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

package org.jitsi.impl.neomedia.codec.video;

import org.jitsi.impl.neomedia.codec.video.vp8.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.*;

/**
 * This class contains utility methods for video codecs.
 *
 * @author George Politis
 */
public class Utils
{
    /**
     * Utility method that determines whether or not a packet is a key frame.
     */
    public static boolean isKeyFrame(RawPacket pkt, byte redPT, byte vp8PT)
    {
        // XXX this will not work correctly when RTX gets enabled!
        boolean isKeyFrame;
        if (redPT == pkt.getPayloadType())
        {
            REDBlock block = REDBlockIterator.getPrimaryBlock(pkt.getBuffer(),
                    pkt.getPayloadOffset(), pkt.getPayloadLength());

            if (block != null && vp8PT == block.getPayloadType())
            {
                // FIXME What if we're not using VP8?
                isKeyFrame
                    = DePacketizer.isKeyFrame(
                            pkt.getBuffer(),
                            block.getOffset(),
                            block.getLength());
            }
            else
            {
                isKeyFrame = false;
            }
        }
        else if (vp8PT == pkt.getPayloadType())
        {
            // XXX There's RawPacket#getPayloadLength() but the implementation
            // includes pkt.paddingSize at the time of this writing and we do
            // not know whether that's going to stay that way.

            // FIXME What if we're not using VP8?
            isKeyFrame
                = DePacketizer.isKeyFrame(
                        pkt.getBuffer(),
                        pkt.getPayloadOffset(),
                        pkt.getLength()
                            - pkt.getHeaderLength()
                            - pkt.getPaddingSize());
        }
        else
        {
            isKeyFrame = false;
        }

        return isKeyFrame;
    }
}
