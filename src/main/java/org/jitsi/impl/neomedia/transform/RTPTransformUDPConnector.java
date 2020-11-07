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

import java.io.*;

import javax.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * TransformConnector implements the RTPConnector interface. RTPConnector
 * is originally designed for programmers to abstract the underlying transport
 * mechanism for RTP control and data from the RTPManager. However, it provides
 * the possibility to modify / transform the RTP and RTCP packets before
 * they are sent to network, or after the have been received from the network.
 *
 * The RTPConnector interface is very powerful. But just to perform packets
 * transformation, we do not need all the flexibility. So, we designed this
 * TransformConnector, which uses UDP to transfer RTP/RTCP packets just like
 * normal RTP stack, and then provides the TransformInputStream interface for
 * people to define their own transformation.
 *
 * With TransformConnector, people can implement RTP/RTCP packets transformation
 * and/or manipulation by implementing the TransformEngine interface.
 *
 * @see TransformEngine
 * @see RTPConnector
 * @see RTPManager
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 */
public class RTPTransformUDPConnector
    extends RTPConnectorUDPImpl
{
    /**
     * The <tt>Logger</tt> used by the <tt>TransformConnector</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTPTransformUDPConnector.class);

    /**
     * The customized <tt>TransformEngine</tt> which contains the concrete
     * transform logic.
     */
    private TransformEngine engine;

    /**
     * Initializes a new <tt>TransformConnector</tt> which is to use a given
     * pair of datagram sockets for RTP and RTCP traffic specified in the form
     * of a <tt>StreamConnector</tt>.
     *
     * @param connector the pair of datagram sockets for RTP and RTCP traffic
     * the new instance is to use
     */
    public RTPTransformUDPConnector(StreamConnector connector)
    {
        super(connector);
    }

    /**
     * Overrides RTPConnectorImpl#createControlInputStream() to use
     * TransformInputStream.
     */
    @Override
    protected RTPConnectorUDPInputStream createControlInputStream()
        throws IOException
    {
        RTPConnectorUDPInputStream controlInputStream
            = new ControlTransformInputStream(getControlSocket());

        controlInputStream.setTransformer(getRTCPTransformer());
        return controlInputStream;
    }

    /**
     * Overrides RTPConnectorImpl#createControlOutputStream() to use
     * TransformOutputStream.
     */
    @Override
    protected TransformUDPOutputStream createControlOutputStream()
        throws IOException
    {
        TransformUDPOutputStream controlOutputStream
            = new TransformUDPOutputStream(getControlSocket());

        controlOutputStream.setTransformer(getRTCPTransformer());
        return controlOutputStream;
    }

    /**
     * Overrides RTPConnectorImpl#createDataInputStream() to use
     * TransformInputStream.
     */
    @Override
    protected RTPConnectorUDPInputStream createDataInputStream()
        throws IOException
    {
        RTPConnectorUDPInputStream dataInputStream
            = new RTPConnectorUDPInputStream(getDataSocket());

        dataInputStream.setTransformer(getRTPTransformer());
        return dataInputStream;
    }

    /**
     * Overrides RTPConnectorImpl#createDataOutputStream() to use
     * TransformOutputStream.
     */
    @Override
    protected TransformUDPOutputStream createDataOutputStream()
        throws IOException
    {
        TransformUDPOutputStream dataOutputStream
            = new TransformUDPOutputStream(getDataSocket());

        dataOutputStream.setTransformer(getRTPTransformer());
        return dataOutputStream;
    }

    /**
     * Gets the customized <tt>TransformEngine</tt> which contains the concrete
     * transform logic.
     *
     * @return the <tt>TransformEngine</tt> which contains the concrete
     * transform logic
     */
    public TransformEngine getEngine()
    {
        return engine;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> specified by the current
     * <tt>TransformerEngine</tt> which is used to transform and
     * reverse-transform RTCP packets.
     *
     * @return the <tt>PacketTransformer</tt> specified by the current
     * <tt>TransformEngine</tt> which is used to transform and reverse-transform
     * RTCP packets if there is currently a <tt>TransformEngine</tt> and it
     * specifies a <tt>TransformEngine</tt> for RTCP data; otherwise,
     * <tt>null</tt>
     */
    private PacketTransformer getRTCPTransformer()
    {
        TransformEngine engine = getEngine();

        return (engine == null) ? null : engine.getRTCPTransformer();
    }

    /**
     * Gets the <tt>PacketTransformer</tt> specified by the current
     * <tt>TransformerEngine</tt> which is used to transform and
     * reverse-transform RTP packets.
     *
     * @return the <tt>PacketTransformer</tt> specified by the current
     * <tt>TransformEngine</tt> which is used to transform and reverse-transform
     * RTP packets if there is currently a <tt>TransformEngine</tt> and it
     * specifies a <tt>TransformEngine</tt> for RTP data; otherwise,
     * <tt>null</tt>
     */
    private PacketTransformer getRTPTransformer()
    {
        TransformEngine engine = getEngine();

        return (engine == null) ? null : engine.getRTPTransformer();
    }

    /**
     * Sets the customized <tt>TransformEngine</tt> which contains the concrete
     * transform logic.
     *
     * @param engine the <tt>TransformEngine</tt> which contains the concrete
     * transform logic
     */
    public void setEngine(TransformEngine engine)
    {
        if (this.engine != engine)
        {
            this.engine = engine;

            /*
             * Deliver the new PacketTransformers defined by the new
             * TransformEngine to the respective streams.
             */
            RTPConnectorUDPInputStream controlInputStream;
            try
            {
                controlInputStream
                    = (RTPConnectorUDPInputStream) getControlInputStream(false);
            }
            catch (IOException ioex)
            {
                logger.error("The impossible happened", ioex);
                controlInputStream = null;
            }
            if (controlInputStream != null)
                controlInputStream.setTransformer(getRTCPTransformer());
            TransformUDPOutputStream controlOutputStream;
            try
            {
                controlOutputStream
                    = (TransformUDPOutputStream) getControlOutputStream(false);
            }
            catch (IOException ioex)
            {
                logger.error("The impossible happened", ioex);
                controlOutputStream = null;
            }
            if (controlOutputStream != null)
                controlOutputStream.setTransformer(getRTCPTransformer());
            RTPConnectorUDPInputStream dataInputStream;
            try
            {
                dataInputStream
                    = (RTPConnectorUDPInputStream) getDataInputStream(false);
            }
            catch (IOException ioex)
            {
                logger.error("The impossible happened", ioex);
                dataInputStream = null;
            }
            if (dataInputStream != null)
                dataInputStream.setTransformer(getRTPTransformer());
            TransformUDPOutputStream dataOutputStream;
            try
            {
                dataOutputStream
                    = (TransformUDPOutputStream) getDataOutputStream(false);
            }
            catch (IOException ioex)
            {
                logger.error("The impossible happened", ioex);
                dataOutputStream = null;
            }
            if (dataOutputStream != null)
                dataOutputStream.setTransformer(getRTPTransformer());
        }
    }
}
