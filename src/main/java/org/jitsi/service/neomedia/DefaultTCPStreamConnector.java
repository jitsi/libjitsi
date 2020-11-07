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

import java.io.*;
import java.net.*;

import org.jitsi.utils.logging.*;

/**
 * Represents a default implementation of <tt>StreamConnector</tt> which is
 * initialized with a specific pair of control and data <tt>Socket</tt>s
 * and which closes them (if they exist) when its {@link #close()} is invoked.
 *
 * @author Lubomir Marinov
 * @author Sebastien Vincent
 */
public class DefaultTCPStreamConnector
    implements StreamConnector
{

    /**
     * The <tt>Logger</tt> used by the <tt>DefaultTCPStreamConnector</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(DefaultTCPStreamConnector.class);

    /**
     * The <tt>Socket</tt> that a stream should use for control data
     * (e.g. RTCP) traffic.
     */
    protected Socket controlSocket;

    /**
     * The <tt>Socket</tt> that a stream should use for data (e.g. RTP)
     * traffic.
     */
    protected Socket dataSocket;

    /**
     * Whether this <tt>DefaultStreamConnector</tt> uses rtcp-mux.
     */
    protected boolean rtcpmux = false;

    /**
     * Initializes a new <tt>DefaultTCPStreamConnector</tt> instance with no
     * control and data <tt>Socket</tt>s.
     * <p>
     * Suitable for extenders willing to delay the creation of the control and
     * data sockets. For example, they could override
     * {@link #getControlSocket()} and/or {@link #getDataSocket()} and create
     * them on demand.
     */
    public DefaultTCPStreamConnector()
    {
        this(null, null);
    }

    /**
     * Initializes a new <tt>DefaultTCPStreamConnector</tt> instance which is to
     * represent a specific pair of control and data <tt>Socket</tt>s.
     *
     * @param dataSocket the <tt>Socket</tt> to be used for data (e.g.
     * RTP) traffic
     * @param controlSocket the <tt>Socket</tt> to be used for control
     * data (e.g. RTCP) traffic
     */
    public DefaultTCPStreamConnector(
            Socket dataSocket,
            Socket controlSocket)
    {
        this(dataSocket, controlSocket, false);
    }

    /**
     * Initializes a new <tt>DefaultTCPStreamConnector</tt> instance which is to
     * represent a specific pair of control and data <tt>Socket</tt>s.
     *
     * @param dataSocket the <tt>Socket</tt> to be used for data (e.g.
     * RTP) traffic
     * @param controlSocket the <tt>Socket</tt> to be used for control
     * data (e.g. RTCP) traffic
     * @param rtcpmux whether rtcpmux is used.
     */
    public DefaultTCPStreamConnector(
            Socket dataSocket,
            Socket controlSocket,
            boolean rtcpmux)
    {
        this.controlSocket = controlSocket;
        this.dataSocket = dataSocket;
        this.rtcpmux = rtcpmux;
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     *
     * @see StreamConnector#close()
     */
    @Override
    public void close()
    {
        try
        {
            if (controlSocket != null)
                controlSocket.close();
            if (dataSocket != null)
                dataSocket.close();
        }
        catch(IOException ioe)
        {
            logger.debug("Failed to close TCP socket", ioe);
        }
    }

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP) traffic.
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for control data (e.g. RTCP) traffic
     * @see StreamConnector#getControlSocket()
     */
    @Override
    public DatagramSocket getControlSocket()
    {
        return null;
    }

    /**
     * Returns a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>DatagramSocket</tt> that a stream should
     * use for data (e.g. RTP) traffic
     * @see StreamConnector#getDataSocket()
     */
    @Override
    public DatagramSocket getDataSocket()
    {
        return null;
    }

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for data (e.g. RTP) traffic.
     */
    @Override
    public Socket getDataTCPSocket()
    {
        return dataSocket;
    }

    /**
     * Returns a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP).
     *
     * @return a reference to the <tt>Socket</tt> that a stream should
     * use for control data (e.g. RTCP).
     */
    @Override
    public Socket getControlTCPSocket()
    {
        return controlSocket;
    }

    /**
     * Returns the protocol of this <tt>StreamConnector</tt>.
     *
     * @return the protocol of this <tt>StreamConnector</tt>
     */
    @Override
    public Protocol getProtocol()
    {
        return Protocol.TCP;
    }

    /**
     * Notifies this instance that utilization of its <tt>Socket</tt>s
     * for data and/or control traffic has started.
     *
     * @see StreamConnector#started()
     */
    @Override
    public void started()
    {
    }

    /**
     * Notifies this instance that utilization of its <tt>Socket</tt>s
     * for data and/or control traffic has temporarily stopped. This instance
     * should be prepared to be started at a later time again though.
     *
     * @see StreamConnector#stopped()
     */
    @Override
    public void stopped()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRtcpmux()
    {
        return rtcpmux;
    }
}
