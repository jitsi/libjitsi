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
package org.jitsi.sctp4j;

import java.io.*;

/**
 * Interface used by {@link SctpSocket} for sending network packets.
 *
 * FIXME: introduce offset and length parameters in order to be able to
 *        re-use single buffer instance
 *
 * @author Pawel Domas
 */
public interface NetworkLink
{
    /**
     * Callback triggered by <tt>SctpSocket</tt> whenever it wants to send some
     * network packet.
     * @param s source <tt>SctpSocket</tt> instance.
     * @param packet network packet buffer.
     *
     * @throws java.io.IOException in case of transport error.
     */
    public void onConnOut(final SctpSocket s, final byte[] packet)
        throws IOException;
}
