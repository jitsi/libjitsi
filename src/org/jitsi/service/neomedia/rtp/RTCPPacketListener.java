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
package org.jitsi.service.neomedia.rtp;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;

/**
 * A simple interface that enables listening for RTCP packets.
 *
 * @author Boris Grozev
 * @author George Politis
 */
public interface RTCPPacketListener
{
    /**
     * Notifies this listener that a {@link NACKPacket} has been received.
     *
     * @param nackPacket the received {@link NACKPacket}.
     */
    void nackReceived(NACKPacket nackPacket);

    /**
     * Notifies this listener that a {@link RTCPREMBPacket} has been received.
     *
     * @param rembPacket the received {@link RTCPREMBPacket}.
     */
    void rembReceived(RTCPREMBPacket rembPacket);

    /**
     * Notifies this listener that an {@link RTCPSRPacket} has been received.
     *
     * @param srPacket the received {@link RTCPSRPacket}.
     */
    void srReceived(RTCPSRPacket srPacket);
}
