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
package org.jitsi.service.packetlogging;

/**
 * A Packet Logging Service to log packets that were send/received
 * by protocols or any other network related services in various formats.
 * Its for debugging purposes.
 *
 * @author Damian Minkov
 */
public interface PacketLoggingService
{
    /**
     * These are the services that this packet logging service
     * cab handle.
     */
    public enum ProtocolName
    {
        /**
         * SIP protocol name.
         */
        SIP,

        /**
         * Jabber protocol name.
         */
        JABBER,

        /**
         * RTP protocol name.
         */
        RTP,

        /**
         * ICE protocol name.
         */
        ICE4J,

        /**
         * DNS protocol name.
         */
        DNS,

        /**
         * ARBITRARY protocol name.
         */
        ARBITRARY
    }

    /**
     * The transport names.
     */
    public enum TransportName
    {
        /**
         * UDP transport name.
         */
        UDP,

        /**
         * TCP transport name.
         */
        TCP
    }

    /**
     * Determines whether packet logging is globally enabled for this service.
     *
     * @return {@code true} if packet logging is globally enabled for this
     * service; otherwise, {@code false}
     */
    public boolean isLoggingEnabled();

    /**
     * Determines whether packet logging for a specific protocol is enabled for
     * this service.
     *
     * @param protocol the packet logging protocol to check
     * @return {@code true} if packet logging for {@code protocol} is enabled
     * for this service; otherwise, {@code false}
     */
    public boolean isLoggingEnabled(ProtocolName protocol);

    /**
     * Log a packet with all the required information.
     *
     * @param protocol the source protocol that logs this packet.
     * @param sourceAddress the source address of the packet.
     * @param sourcePort the source port of the packet.
     * @param destinationAddress the destination address.
     * @param destinationPort the destination port.
     * @param transport the transport this packet uses.
     * @param sender are we the sender of the packet or not.
     * @param packetContent the packet content.
     */
    public void logPacket(
            ProtocolName protocol,
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort,
            TransportName transport,
            boolean sender,
            byte[] packetContent);

    /**
     * Log a packet with all the required information.
     *
     * @param protocol the source protocol that logs this packet.
     * @param sourceAddress the source address of the packet.
     * @param sourcePort the source port of the packet.
     * @param destinationAddress the destination address.
     * @param destinationPort the destination port.
     * @param transport the transport this packet uses.
     * @param sender are we the sender of the packet or not.
     * @param packetContent the packet content.
     * @param packetOffset the packet content offset.
     * @param packetLength the packet content length.
     */
    public void logPacket(
            ProtocolName protocol,
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort,
            TransportName transport,
            boolean sender,
            byte[] packetContent,
            int packetOffset,
            int packetLength);

    /**
     * Returns the current Packet Logging Configuration.
     *
     * @return the Packet Logging Configuration.
     */
    public PacketLoggingConfiguration getConfiguration();
}
