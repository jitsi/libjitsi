/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.dtls;

import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.*;
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
     * The <tt>RTPConnector</tt> which uses this <tt>TransformEngine</tt>.
     */
    private AbstractRTPConnector connector;

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
     * The <tt>MediaType</tt> of the stream which this instance works for/is
     * associated with.
     */
    private MediaType mediaType;

    /**
     * The <tt>PacketTransformer</tt>s of this <tt>TransformEngine</tt> for
     * data/RTP and control/RTCP packets.
     */
    private final DtlsPacketTransformer[] packetTransformers
        = new DtlsPacketTransformer[2];

    /**
     * Whether rtcp-mux is in use.
     *
     * When enabled, the <tt>DtlsPacketTransformer</tt> will, instead of
     * establishing a DTLS session, wait for the transformer for RTP to
     * establish one, and reuse it to initialize its SRTP transformer.
     */
    private boolean rtcpmux = false;

    /**
     * The value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     */
    private DtlsControl.Setup setup;

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

        /*
         * SrtpControl.start(MediaType) starts its associated TransformEngine.
         * We will use that mediaType to signal the normal stop then as well
         * i.e. we will call setMediaType(null) first.
         */
        setMediaType(null);

        for (int i = 0; i < packetTransformers.length; i++)
        {
            DtlsPacketTransformer packetTransformer = packetTransformers[i];

            if (packetTransformer != null)
            {
                packetTransformer.close();
                packetTransformers[i] = null;
            }
        }

        setConnector(null);
    }

    /**
     * Initializes a new <tt>DtlsPacketTransformer</tt> instance which is to
     * work on control/RTCP or data/RTP packets.
     *
     * @param componentID the ID of the component for which the new instance is
     * to work
     * @return a new <tt>DtlsPacketTransformer</tt> instance which is to work on
     * control/RTCP or data/RTP packets (in accord with <tt>data</tt>)
     */
    private DtlsPacketTransformer createPacketTransformer(int componentID)
    {
        DtlsPacketTransformer packetTransformer
            = new DtlsPacketTransformer(this, componentID);

        packetTransformer.setConnector(connector);
        packetTransformer.setSetup(setup);
        packetTransformer.setRtcpmux(rtcpmux);
        packetTransformer.setMediaType(mediaType);
        return packetTransformer;
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
        int index = componentID - 1;
        DtlsPacketTransformer packetTransformer = packetTransformers[index];

        if ((packetTransformer == null) && !disposed)
        {
            packetTransformer = createPacketTransformer(componentID);
            if (packetTransformer != null)
                packetTransformers[index] = packetTransformer;
        }
        return packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTCPTransformer()
    {
        return getPacketTransformer(Component.RTCP);
    }

    /**
     * {@inheritDoc}
     */
    public PacketTransformer getRTPTransformer()
    {
        return getPacketTransformer(Component.RTP);
    }

    /**
     * Indicates if SRTP extensions should be disabled which means we are
     * currently working in pure DTLS mode.
     * @return <tt>true</tt> if SRTP extensions should be disabled.
     */
    boolean isSrtpDisabled()
    {
        return dtlsControl.isSrtpDisabled();
    }

    /**
     * Sets the <tt>RTPConnector</tt> which is to use or uses this
     * <tt>TransformEngine</tt>.
     *
     * @param connector the <tt>RTPConnector</tt> which is to use or uses this
     * <tt>TransformEngine</tt>
     */
    void setConnector(AbstractRTPConnector connector)
    {
        if (this.connector != connector)
        {
            this.connector = connector;

            for (DtlsPacketTransformer packetTransformer : packetTransformers)
            {
                if (packetTransformer != null)
                    packetTransformer.setConnector(this.connector);
            }
        }
    }

    /**
     * Sets the <tt>MediaType</tt> of the stream which this instance is to work
     * for/be associated with.
     *
     * @param mediaType the <tt>MediaType</tt> of the stream which this instance
     * is to work for/be associated with
     */
    private void setMediaType(MediaType mediaType)
    {
        if (this.mediaType != mediaType)
        {
            this.mediaType = mediaType;

            for (DtlsPacketTransformer packetTransformer : packetTransformers)
            {
                if (packetTransformer != null)
                    packetTransformer.setMediaType(this.mediaType);
            }
        }
    }

    /**
     * Enables/disables rtcp-mux.
     * @param rtcpmux whether to enable or disable.
     */
    void setRtcpmux(boolean rtcpmux)
    {
        if (this.rtcpmux != rtcpmux)
        {
            this.rtcpmux = rtcpmux;

            for (DtlsPacketTransformer packetTransformer : packetTransformers)
            {
                if (packetTransformer != null)
                    packetTransformer.setRtcpmux(rtcpmux);
            }
        }
    }

    /**
     * Sets the DTLS protocol according to which this
     * <tt>DtlsTransformEngine</tt> is to act either as a DTLS server or a DTLS
     * client.
     *
     * @param setup the value of the <tt>setup</tt> SDP attribute to set on this
     * instance in order to determine whether this instance is to act as a DTLS
     * client or a DTLS server
     */
    void setSetup(DtlsControl.Setup setup)
    {
        if (this.setup != setup)
        {
            this.setup = setup;

            for (DtlsPacketTransformer packetTransformer : packetTransformers)
            {
                if (packetTransformer != null)
                    packetTransformer.setSetup(this.setup);
            }
        }
    }

    /**
     * Starts this instance in the sense that it becomes fully operational.
     *
     * @param mediaType the <tt>MediaType</tt> of the stream which this instance
     * is to work for/be associated with
     */
    void start(MediaType mediaType)
    {
        setMediaType(mediaType);
    }
}
