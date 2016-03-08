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
package org.jitsi.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;

import java.io.*;

/**
 * @author George Politis
 */
public class PLIPacket extends RTCPFBPacket
{
    /**
     * The PLI FB message is identified by PT=PSFB and FMT=1.
     */
    public static final int FMT = 1;

    /**
     * Ctor to use when creating a PLI from scratch.
     *
     * @param senderSSRC
     * @param sourceSSRC
     */
    public PLIPacket(long senderSSRC, long sourceSSRC)
    {
        super(FMT, PSFB, senderSSRC, sourceSSRC);
    }

    /**
     * Ctor to use when reading a PLI.
     * @param base
     */
    public PLIPacket(RTCPCompoundPacket base)
    {
        super(base);
        super.fmt = FMT;
        super.type = PSFB;
    }

    /**
     * Parses a PLI.
     */
    public static RTCPPacket parse(RTCPCompoundPacket base,
        int firstbyte, int rtpfb, int length, DataInputStream in,
        long senderSSRC, long sourceSSRC)
    {
        PLIPacket pliPacket = new PLIPacket(base);

        // And we're done!

        return pliPacket;
    }
}
