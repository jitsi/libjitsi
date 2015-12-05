
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

import net.sf.fmj.media.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

/**
 * Represents a predicate (boolean-valued function) of a <tt>RawPacket</tt>.
 *
 * @author George Politis
 */
public class RTCPPacketPredicate
    implements Predicate<RawPacket>
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTCPPacketPredicate</tt> class.
     */
    private static final Logger logger
        = Logger.getLogger(RTPPacketPredicate.class);

    /**
     * The singleton instance of this class.
     */
    public static final RTCPPacketPredicate instance
        = new RTCPPacketPredicate();

    public boolean test(RawPacket pkt)
    {
        // TODO this needs to be smarter.
        boolean result = pkt != null && pkt.getVersion() == RTCPHeader.VERSION;

        if (!result)
        {
            logger.debug("Caught a non-RTCP packet.");
        }

        return result;
    }
}
