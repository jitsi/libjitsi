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

/**
 * @author Boris Grozev
 * @author George Politis
 */
public interface RetransmissionRequester
{
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

    /**
     * Check if this {@link RetransmissionRequester} has nack packets ready
     * to be sent
     * @return true if there are nack packets ready to be sent, false otherwise
     */
    public boolean hasWork();

    /**
     * Set a callback for this {@link RetransmissionRequester} to use if
     * it wants to signal it has work available now
     * @param workReadyCallback callback invoked to indicate there is work
     * ready now
     */
    public void setWorkReadyCallback(Runnable workReadyCallback);

    /**
     * Generate and send ready nack packets.  Should ONLY be called if
     * {@link RetransmissionRequester#hasWork()} returned true.
     */
    public void doWork();
}
