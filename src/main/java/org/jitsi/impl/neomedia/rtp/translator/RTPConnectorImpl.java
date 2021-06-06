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
package org.jitsi.impl.neomedia.rtp.translator;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javax.media.protocol.*;
import javax.media.rtp.*;

import org.jitsi.service.neomedia.*;

/**
 * Implements the <tt>RTPConnector</tt> with which this instance initializes
 * its <tt>RTPManager</tt>. It delegates to the <tt>RTPConnector</tt> of the
 * various <tt>StreamRTPManager</tt>s.
 *
 * @author Lyubomir Marinov
 */
class RTPConnectorImpl
    implements RTPConnector
{
    /**
     * The <tt>RTPConnector</tt>s this instance delegates to.
     */
    private final List<RTPConnectorDesc> connectors
        = new LinkedList<RTPConnectorDesc>();

    private PushSourceStreamImpl controlInputStream;

    private OutputDataStreamImpl controlOutputStream;

    private PushSourceStreamImpl dataInputStream;

    private OutputDataStreamImpl dataOutputStream;

    public final RTPTranslatorImpl translator;

    /**
     * The indicator which determines whether {@link #close()} has been
     * invoked on this instance.
     */
    private boolean closed = false;

    public RTPConnectorImpl(RTPTranslatorImpl translator)
    {
        this.translator = translator;
    }

    public synchronized void addConnector(RTPConnectorDesc connector)
    {
        // XXX Could we use a read/write lock instead of a synchronized here?
        // We acquire a write lock and as soon as add the connector to the
        // connectors we downgrade to a read lock.
        if (!connectors.contains(connector))
        {
            connectors.add(connector);
            if (this.controlInputStream != null)
            {
                PushSourceStream controlInputStream = null;

                try
                {
                    controlInputStream
                        = connector.connector.getControlInputStream();
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }
                if (controlInputStream != null)
                {
                    this.controlInputStream.addStream(
                            connector,
                            controlInputStream);
                }
            }
            if (this.controlOutputStream != null)
            {
                OutputDataStream controlOutputStream = null;

                try
                {
                    controlOutputStream
                        = connector.connector.getControlOutputStream();
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }
                if (controlOutputStream != null)
                {
                    this.controlOutputStream.addStream(
                            connector,
                            controlOutputStream);
                }
            }
            if (this.dataInputStream != null)
            {
                PushSourceStream dataInputStream = null;

                try
                {
                    dataInputStream
                        = connector.connector.getDataInputStream();
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }
                if (dataInputStream != null)
                {
                    this.dataInputStream.addStream(
                            connector,
                            dataInputStream);
                }
            }
            if (this.dataOutputStream != null)
            {
                OutputDataStream dataOutputStream = null;

                try
                {
                    dataOutputStream
                        = connector.connector.getDataOutputStream();
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }
                if (dataOutputStream != null)
                {
                    this.dataOutputStream.addStream(
                            connector,
                            dataOutputStream);
                }
            }
        }
    }

    @Override
    public synchronized void close()
    {
        if (controlInputStream != null)
        {
            controlInputStream.close();
            controlInputStream = null;
        }
        if (controlOutputStream != null)
        {
            controlOutputStream.close();
            controlOutputStream = null;
        }
        if (dataInputStream != null)
        {
            dataInputStream.close();
            dataInputStream = null;
        }
        if (dataOutputStream != null)
        {
            dataOutputStream.close();
            dataOutputStream = null;
        }

        this.closed = true;

        for (RTPConnectorDesc connectorDesc : connectors)
            connectorDesc.connector.close();
    }

    @Override
    public synchronized PushSourceStream getControlInputStream()
        throws IOException
    {
        if (this.controlInputStream == null)
        {
            this.controlInputStream = new PushSourceStreamImpl(this, false);
            for (RTPConnectorDesc connectorDesc : connectors)
            {
                PushSourceStream controlInputStream
                    = connectorDesc.connector.getControlInputStream();

                if (controlInputStream != null)
                {
                    this.controlInputStream.addStream(
                            connectorDesc,
                            controlInputStream);
                }
            }
        }
        return this.controlInputStream;
    }

    @Override
    public synchronized OutputDataStreamImpl getControlOutputStream()
        throws IOException
    {
        if (this.closed)
        {
            throw new IllegalStateException("Connector closed.");
        }

        if (this.controlOutputStream == null)
        {
            this.controlOutputStream = new OutputDataStreamImpl(this, false);
            for (RTPConnectorDesc connectorDesc : connectors)
            {
                OutputDataStream controlOutputStream
                    = connectorDesc.connector.getControlOutputStream();

                if (controlOutputStream != null)
                {
                    this.controlOutputStream.addStream(
                            connectorDesc,
                            controlOutputStream);
                }
            }
        }
        return this.controlOutputStream;
    }

    @Override
    public synchronized PushSourceStream getDataInputStream()
        throws IOException
    {
        if (this.dataInputStream == null)
        {
            this.dataInputStream = new PushSourceStreamImpl(this, true);
            for (RTPConnectorDesc connectorDesc : connectors)
            {
                PushSourceStream dataInputStream
                    = connectorDesc.connector.getDataInputStream();

                if (dataInputStream != null)
                {
                    this.dataInputStream.addStream(
                            connectorDesc,
                            dataInputStream);
                }
            }
        }
        return this.dataInputStream;
    }

    @Override
    public synchronized OutputDataStreamImpl getDataOutputStream()
        throws IOException
    {
        if (this.closed)
        {
            throw new IllegalStateException("Connector closed.");
        }

        if (this.dataOutputStream == null)
        {
            this.dataOutputStream = new OutputDataStreamImpl(this, true);
            for (RTPConnectorDesc connectorDesc : connectors)
            {
                OutputDataStream dataOutputStream
                    = connectorDesc.connector.getDataOutputStream();

                if (dataOutputStream != null)
                {
                    this.dataOutputStream.addStream(
                            connectorDesc,
                            dataOutputStream);
                }
            }
        }
        return this.dataOutputStream;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public int getReceiveBufferSize()
    {
        return -1;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public double getRTCPBandwidthFraction()
    {
        return -1;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public double getRTCPSenderBandwidthFraction()
    {
        return -1;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public int getSendBufferSize()
    {
        return -1;
    }

    public synchronized void removeConnector(RTPConnectorDesc connector)
    {
        if (connectors.contains(connector))
        {
            if (controlInputStream != null)
                controlInputStream.removeStreams(connector);
            if (controlOutputStream != null)
                controlOutputStream.removeStreams(connector);
            if (dataInputStream != null)
                dataInputStream.removeStreams(connector);
            if (dataOutputStream != null)
                dataOutputStream.removeStreams(connector);
            connectors.remove(connector);
        }
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public void setReceiveBufferSize(int receiveBufferSize)
        throws IOException
    {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public void setSendBufferSize(int sendBufferSize)
        throws IOException
    {
        // TODO Auto-generated method stub
    }

    /**
     * Writes an <tt>RTCPFeedbackMessage</tt> into a destination identified by
     * a specific <tt>MediaStream</tt>.
     *
     * @param controlPayload
     * @param destination
     * @return <tt>true</tt> if the <tt>controlPayload</tt> was written
     * into the <tt>destination</tt>; otherwise, <tt>false</tt>
     */
    boolean writeControlPayload(
            Payload controlPayload,
            MediaStream destination)
    {
        OutputDataStreamImpl controlOutputStream = this.controlOutputStream;

        return
            (controlOutputStream == null)
                ? false
                : controlOutputStream.writeControlPayload(
                    controlPayload,
                    destination);
    }
}
