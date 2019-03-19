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
package org.jitsi.impl.neomedia.transform.dtls;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements {@link SrtpControl.TransformEngine} (and, respectively,
 * {@link org.jitsi.impl.neomedia.transform.TransformEngine}) for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class DtlsTransformEngine
    implements SrtpControl.TransformEngine
{
    /**
     * The index of the RTP component.
     */
    static final int COMPONENT_RTP = 0;

    /**
     * The index of the RTCP component.
     */
    static final int COMPONENT_RTCP = 1;

    /**
     * The indicator which determines whether
     * {@link SrtpControl.TransformEngine#cleanup()} has been invoked on this
     * instance to prepare it for garbage collection.
     */
    private boolean disposed = false;

    /**
     * The <tt>DtlsControl</tt> which has initialized this instance.
     */
    private final DtlsControlImpl dtlsControl;

    /**
     * The <tt>PacketTransformer</tt>s of this <tt>TransformEngine</tt> for
     * data/RTP and control/RTCP packets.
     */
    private final DtlsPacketTransformer[] packetTransformers
        = new DtlsPacketTransformer[2];

    /**
     * Initializes a new <tt>DtlsTransformEngine</tt> instance.
     */
    public DtlsTransformEngine(DtlsControlImpl dtlsControl)
    {
        this.dtlsControl = dtlsControl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanup()
    {
        disposed = true;

        for (int i = 0; i < packetTransformers.length; i++)
        {
            DtlsPacketTransformer packetTransformer = packetTransformers[i];

            if (packetTransformer != null)
            {
                packetTransformer.close();
                packetTransformers[i] = null;
            }
        }
    }

    /**
     * Initializes a new <tt>DtlsPacketTransformer</tt> instance which is to
     * work on control/RTCP or data/RTP packets. The method is implemented as a
     * factory.
     *
     * @param componentID the ID of the component for which the new instance is
     * to work
     * @return a new <tt>DtlsPacketTransformer</tt> instance which is to work on
     * control/RTCP or data/RTP packets (in accord with <tt>data</tt>)
     */
    protected DtlsPacketTransformer createPacketTransformer(int componentID)
    {
        return new DtlsPacketTransformer(this, componentID);
    }

    /**
     * Gets the <tt>DtlsControl</tt> which has initialized this instance.
     *
     * @return the <tt>DtlsControl</tt> which has initialized this instance
     */
    DtlsControlImpl getDtlsControl()
    {
        return dtlsControl;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> of this <tt>TransformEngine</tt>
     * which is to work or works for the component with a specific ID.
     *
     * @param componentID the ID of the component for which the returned
     * <tt>PacketTransformer</tt> is to work or works
     * @return the <tt>PacketTransformer</tt>, if any, which is to work or works
     * for the component with the specified <tt>componentID</tt>
     */
    private DtlsPacketTransformer getPacketTransformer(int componentID)
    {
        DtlsPacketTransformer packetTransformer = packetTransformers[componentID];

        if ((packetTransformer == null) && !disposed)
        {
            packetTransformer = createPacketTransformer(componentID);
            if (packetTransformer != null)
                packetTransformers[componentID] = packetTransformer;
        }
        return packetTransformer;
    }

    /**
     * Gets the properties of {@code DtlsControlImpl} and their values which
     * {@link #dtlsControl} shares with this instance and
     * {@link DtlsPacketTransformer}.
     *
     * @return the properties of {@code DtlsControlImpl} and their values which
     * {@code dtlsControl} shares with this instance and
     * {@code DtlsPacketTransformer}
     */
    Properties getProperties()
    {
        return getDtlsControl().getProperties();
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTCPTransformer()
    {
        return getPacketTransformer(COMPONENT_RTCP);
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTPTransformer()
    {
        return getPacketTransformer(COMPONENT_RTP);
    }
}
