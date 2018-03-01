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

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.function.*;

/**
 * @author George Politis
 */
public class AbstractRTPPacketPredicate
    implements Predicate<ByteArrayBuffer>
{
    /**
     * The <tt>Logger</tt> used by the <tt>AbstractRTPPacketPredicate</tt>
     * class.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractRTPPacketPredicate.class);

    /**
     * True if this predicate should test for RTCP, false for RTP.
     */
    private final boolean rtcp;

    /**
     * Ctor.
     *
     * @param rtcp true if this predicate should test for RTCP, false for RTP.
     */
    public AbstractRTPPacketPredicate(boolean rtcp)
    {
        this.rtcp = rtcp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(ByteArrayBuffer pkt)
    {
        // XXX inspired by RtpChannelDatagramFilter.accept().
        boolean result;

        if (pkt != null)
        {
            if (pkt.getLength() >= 4)
            {
                if (RawPacket.getVersion(pkt) == 2) // RTP/RTCP version field
                {
                    byte[] buff = pkt.getBuffer();
                    int off = pkt.getOffset();
                    int pt = buff[off + 1] & 0xff;

                    if (200 <= pt && pt <= 211)
                    {
                        result = rtcp;
                    }
                    else
                    {
                        result = !rtcp;
                    }
                }
                else
                {
                    result = false;
                }
            }
            else
            {
                result = false;
            }
        }
        else
        {
            result = false;
        }

        return result;
    }
}
