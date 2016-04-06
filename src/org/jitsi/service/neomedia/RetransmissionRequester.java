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

package org.jitsi.service.neomedia;

import java.util.*;

/**
 * @author Boris Grozev
 */
public interface RetransmissionRequester
{
    /**
     * Sets the RTX-related configuration for this
     * {@link RetransmissionRequester}. Any previous configuration will be
     * replaced.
     * @param pt the payload type number for the RTX format.
     * @param ssrcs maps an original stream's SSRC to the SSRC of the
     * retransmission (RTX) stream.
     */
    public void configureRtx(byte pt, Map<Long, Long> ssrcs);

    /**
     * Enables or disables this {@link RetransmissionRequester}.
     * @param enable {@code true} to enable, {@code false} to disable.
     */
    public void enable(boolean enable);

    /**
     * Sets the SSRC to be used by this {@link RetransmissionRequester} as
     * "packet sender SSRC" in outgoing NACK packets.
     * @param ssrc the SSRC to use as "packet sender SSRC".
     */
    public void setSenderSsrc(long ssrc);
}
