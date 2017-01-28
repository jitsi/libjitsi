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

/**
 * @author George Politis
 */
public class FIRPacket
    extends RTCPFBPacket
{
    /**
     * The FMT of an FIR packet.
     */
    public static final int FMT = 4;

    /**
     * Ctor.
     *
     * @param senderSSRC
     * @param sourceSSRC
     */
    public FIRPacket(long senderSSRC, long sourceSSRC)
    {
        super(FMT, PSFB, senderSSRC, sourceSSRC);
    }
}
