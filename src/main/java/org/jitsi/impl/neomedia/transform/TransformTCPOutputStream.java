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
package org.jitsi.impl.neomedia.transform;

import java.net.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;

/**
 * Extends <tt>RTPConnectorTCPOutputStream</tt> with transform logic.
 *
 * In this implementation, TCP socket is used to send the data out. When a
 * normal RTP/RTCP packet is passed down from RTPManager, we first transform
 * the packet using user define PacketTransformer and then send it out through
 * network to all the stream targets.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 */
public class TransformTCPOutputStream
    extends RTPConnectorTCPOutputStream
    implements TransformOutputStream
{
    /**
     * The {@code TransformOutputStream} which aids this instance in
     * implementing the interface in question.
     */
    private final TransformOutputStreamImpl _impl;

    /**
     * Initializes a new <tt>TransformTCPOutputStream</tt> which is to send
     * packet data out through a specific TCP socket.
     *
     * @param socket the TCP socket used to send packet data out
     */
    public TransformTCPOutputStream(Socket socket)
    {
        super(socket);

        _impl = new TransformOutputStreamImpl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getTransformer()
    {
        return _impl.getTransformer();
    }

    /**
     * {@inheritDoc}
     *
     * Transforms the array of {@code RawPacket}s returned by the super
     * {@link #packetize(byte[],int,int,Object)} implementation using the
     * associated {@code PacketTransformer}.
     */
    @Override
    protected RawPacket[] packetize(
            byte[] buf, int off, int len,
            Object context)
    {
        RawPacket[] pkts = super.packetize(buf, off, len, context);

        return _impl.transform(pkts, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTransformer(PacketTransformer transformer)
    {
        _impl.setTransformer(transformer);
    }
}
