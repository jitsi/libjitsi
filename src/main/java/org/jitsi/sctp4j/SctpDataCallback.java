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

/**
 * Callback used to listen for incoming data on SCTP socket.
 *
 * @author Pawel Domas
 */
public interface SctpDataCallback
{
    /**
     * Callback fired by <tt>SctpSocket</tt> to notify about incoming data.
     * @param data buffer holding received data.
     * @param sid SCTP stream identifier.
     * @param ssn
     * @param tsn
     * @param ppid payload protocol identifier.
     * @param context
     * @param flags
     */
    void onSctpPacket(byte[] data, int sid, int ssn, int tsn, long ppid,
                      int context, int flags);
}
