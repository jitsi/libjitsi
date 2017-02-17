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
package org.jitsi.util.function;

import net.sf.fmj.media.rtp.util.*;
import org.jitsi.service.neomedia.*;

/**
 * A <tt>Function</tt> that produces <tt>RawPacket</tt>s from
 * <tt>RTPPacket</tt>s.
 *
 * @author George Politis
 */
public class RTPGenerator extends AbstractFunction<RTPPacket, RawPacket>
{
    public RawPacket apply(RTPPacket input)
    {
        if (input == null)
        {
            throw new NullPointerException();
        }

        // Assemble the RTP packet.
        int len = input.calcLength();
        input.assemble(len, false);
        byte[] buf = input.data;

        RawPacket pktOut = new RawPacket();

        pktOut.setBuffer(buf);
        pktOut.setLength(buf.length);
        pktOut.setOffset(0);

        return pktOut;
    }
}
