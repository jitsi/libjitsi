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
package org.jitsi.service.neomedia.control;

import javax.media.*;

/**
 * An interface used to notify encoders about the packet loss which is expected.
 *
 * @author Boris Grozev
 */
public interface PacketLossAwareEncoder extends Control
{
    /**
     * Tells the encoder to expect <tt>percentage</tt> percent packet loss.
     *
     * @return the percentage of expected packet loss
     */
    public void setExpectedPacketLoss(int percentage);
}
