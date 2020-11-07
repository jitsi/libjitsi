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
package org.jitsi.impl.neomedia;

import java.io.*;
import java.net.*;

import org.jitsi.service.neomedia.*;

/**
 * RTPConnector implementation for UDP.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorUDPImpl
    extends AbstractRTPConnector
{
    /**
     * The UDP socket this instance uses to send and receive RTP packets.
     */
    private DatagramSocket dataSocket;

    /**
     * The UDP socket this instance uses to send and receive RTCP packets.
     */
    private DatagramSocket controlSocket;

    /**
     * Initializes a new <tt>RTPConnectorUDPImpl</tt> which is to use a given
     * pair of datagram sockets for RTP and RTCP traffic specified in the form
     * of a <tt>StreamConnector</tt>.
     *
     * @param connector the pair of datagram sockets for RTP and RTCP traffic
     * the new instance is to use
     */
    public RTPConnectorUDPImpl(StreamConnector connector)
    {
        super(connector);
    }

    /**
     * Gets the UDP socket this instance uses to send and receive RTP packets.
     *
     * @return the UDP socket this instance uses to send and receive RTP packets
     */
    public DatagramSocket getDataSocket()
    {
        if (dataSocket == null)
            dataSocket = connector.getDataSocket();
        return dataSocket;
    }

    /**
     * Gets the UDP Socket this instance uses to send and receive RTCP packets.
     *
     * @return the UDP Socket this instance uses to send and receive RTCP
     * packets
     */
    public DatagramSocket getControlSocket()
    {
        if (controlSocket == null)
            controlSocket = connector.getControlSocket();
        return controlSocket;
    }

    /**
     * Creates the RTCP packet input stream to be used by <tt>RTPManager</tt>.
     *
     * @return a new RTCP packet input stream to be used by <tt>RTPManager</tt>
     * @throws IOException if an error occurs during the creation of the RTCP
     * packet input stream
     */
    @Override
    protected RTPConnectorInputStream<?> createControlInputStream()
        throws IOException
    {
        return new RTCPConnectorInputStream(getControlSocket());
    }

    /**
     * Creates the RTCP packet output stream to be used by <tt>RTPManager</tt>.
     *
     * @return a new RTCP packet output stream to be used by <tt>RTPManager</tt>
     * @throws IOException if an error occurs during the creation of the RTCP
     * packet output stream
     */
    @Override
    protected RTPConnectorOutputStream createControlOutputStream()
        throws IOException
    {
        return new RTPConnectorUDPOutputStream(getControlSocket());
    }

    /**
     * Creates the RTP packet input stream to be used by <tt>RTPManager</tt>.
     *
     * @return a new RTP packet input stream to be used by <tt>RTPManager</tt>
     * @throws IOException if an error occurs during the creation of the RTP
     * packet input stream
     */
    @Override
    protected RTPConnectorInputStream<?> createDataInputStream()
        throws IOException
    {
        return new RTPConnectorUDPInputStream(getDataSocket());
    }

    /**
     * Creates the RTP packet output stream to be used by <tt>RTPManager</tt>.
     *
     * @return a new RTP packet output stream to be used by <tt>RTPManager</tt>
     * @throws IOException if an error occurs during the creation of the RTP
     * packet output stream
     */
    @Override
    protected RTPConnectorOutputStream createDataOutputStream()
        throws IOException
    {
        return new RTPConnectorUDPOutputStream(getDataSocket());
    }
}
