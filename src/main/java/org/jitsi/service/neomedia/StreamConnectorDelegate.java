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

import java.net.*;

/**
 * Implements a {@link StreamConnector} which wraps a specific
 * <tt>StreamConnector</tt> instance.
 *
 * @param <T> the very type of the <tt>StreamConnector</tt> wrapped by
 * <tt>StreamConnectorDelegate</tt>
 *
 * @author Lyubomir Marinov
 */
public class StreamConnectorDelegate<T extends StreamConnector>
    implements StreamConnector
{
    /**
     * The <tt>StreamConnector</tt> wrapped by this instance.
     */
    protected final T streamConnector;

    /**
     * Initializes a new <tt>StreamConnectorDelegate</tt> which is to wrap a
     * specific <tt>StreamConnector</tt>.
     *
     * @param streamConnector the <tt>StreamConnector</tt> to be wrapped by the
     * new instance
     */
    public StreamConnectorDelegate(T streamConnector)
    {
        if (streamConnector == null)
            throw new NullPointerException("streamConnector");

        this.streamConnector = streamConnector;
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected. Calls
     * {@link StreamConnector#close()} on the <tt>StreamConnector</tt> wrapped
     * by this instance.
     */
    @Override
    public void close()
    {
        streamConnector.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DatagramSocket getControlSocket()
    {
        return streamConnector.getControlSocket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket getControlTCPSocket()
    {
        return streamConnector.getControlTCPSocket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DatagramSocket getDataSocket()
    {
        return streamConnector.getDataSocket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket getDataTCPSocket()
    {
        return streamConnector.getDataTCPSocket();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Protocol getProtocol()
    {
        return streamConnector.getProtocol();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void started()
    {
        streamConnector.started();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopped()
    {
        streamConnector.stopped();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRtcpmux()
    {
        return streamConnector.isRtcpmux();
    }
}
