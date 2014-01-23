/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.dtls;

import java.io.*;
import java.security.*;

import org.bouncycastle.crypto.tls.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements {@link PacketTransformer} for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class DtlsPacketTransformer
    extends SinglePacketTransformer
{
    /**
     * The maximum number of times that
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)} is
     * to retry the invocations of
     * {@link DTLSClientProtocol#connect(TlsClient, DatagramTransport)} and
     * {@link DTLSServerProtocol#accept(TlsServer, DatagramTransport)} in
     * anticipation of a successful connection.
     */
    private static final int CONNECT_TRIES = 3;

    private static final long CONNECT_RETRY_INTERVAL = 500;

    /**
     * The length of the header of a DTLS record.
     */
    static final int DTLS_RECORD_HEADER_LENGTH = 13;

    /**
     * The number of milliseconds a <tt>DtlsPacketTransform</tt> is to wait on
     * its {@link #dtlsTransport} in order to receive a packet.
     */
    private static final int DTLS_TRANSPORT_RECEIVE_WAITMILLIS = -1;

    /**
     * The <tt>Logger</tt> used by the <tt>DtlsPacketTransformer</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(DtlsPacketTransformer.class);

    /**
     * Determines whether a specific array of <tt>byte</tt>s appears to contain
     * a DTLS record.
     *
     * @param buf the array of <tt>byte</tt>s to be analyzed
     * @param off the offset within <tt>buf</tt> at which the analysis is to
     * start
     * @param len the number of bytes within <tt>buf</tt> starting at
     * <tt>off</tt> to be analyzed
     * @return <tt>true</tt> if the specified <tt>buf</tt> appears to contain a
     * DTLS record
     */
    public static boolean isDtlsRecord(byte[] buf, int off, int len)
    {
        boolean b = false;

        if (len >= DTLS_RECORD_HEADER_LENGTH)
        {
            short type = TlsUtils.readUint8(buf, off);

            switch (type)
            {
            case ContentType.alert:
            case ContentType.application_data:
            case ContentType.change_cipher_spec:
            case ContentType.handshake:
                int major = buf[off + 1] & 0xff;
                int minor = buf[off + 2] & 0xff;
                ProtocolVersion version = null;

                if ((major == ProtocolVersion.DTLSv10.getMajorVersion())
                        && (minor == ProtocolVersion.DTLSv10.getMinorVersion()))
                {
                    version = ProtocolVersion.DTLSv10;
                }
                if ((version == null)
                        && (major == ProtocolVersion.DTLSv12.getMajorVersion())
                        && (minor == ProtocolVersion.DTLSv12.getMinorVersion()))
                {
                    version = ProtocolVersion.DTLSv12;
                }
                if (version != null)
                {
                    int length = TlsUtils.readUint16(buf, off + 11);

                    if (DTLS_RECORD_HEADER_LENGTH + length <= len)
                        b = true;
                }
                break;
            default:
                /*
                 * Unless a new ContentType has been defined by the Bouncy
                 * Castle Crypto APIs, the specified buf does not represent a
                 * DTLS record.
                 */
                break;
            }
        }
        return b;
    }

    /**
     * The ID of the component which this instance works for/is associated with.
     */
    private final int componentID;

    /**
     * The background <tt>Thread</tt> which initializes {@link #dtlsTransport}.
     */
    private Thread connectThread;

    /**
     * The <tt>RTPConnector</tt> which uses this <tt>PacketTransformer</tt>.
     */
    private AbstractRTPConnector connector;

    /**
     * The <tt>DatagramTransport</tt> implementation which adapts
     * {@link #connector} and this <tt>PacketTransformer</tt> to the terms of
     * the Bouncy Castle Crypto APIs.
     */
    private DatagramTransportImpl datagramTransport;

    /**
     * The <tt>DTLSTransport</tt> through which the actual packet
     * transformations are being performed by this instance.
     */
    private DTLSTransport dtlsTransport;

    /**
     * The <tt>MediaType</tt> of the stream which this instance works for/is
     * associated with.
     */
    private MediaType mediaType;

    /**
     * The <tt>SRTPTransformer</tt> to be used by this instance.
     */
    private SinglePacketTransformer srtpTransformer;

    /**
     * The value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     */
    private DtlsControl.Setup setup;

    /**
     * The <tt>TransformEngine</tt> which has initialized this instance.
     */
    private final DtlsTransformEngine transformEngine;

    /**
     * Initializes a new <tt>DtlsPacketTransformer</tt> instance.
     *
     * @param transformEngine the <tt>TransformEngine</tt> which is initializing
     * the new instance
     * @param componentID the ID of the component for which the new instance is
     * to work
     */
    public DtlsPacketTransformer(
            DtlsTransformEngine transformEngine,
            int componentID)
    {
        this.transformEngine = transformEngine;
        this.componentID = componentID;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void close()
    {
        setConnector(null);
        setMediaType(null);
    }

    /**
     * Closes {@link #datagramTransport} if it is non-<tt>null</tt> and logs and
     * swallows any <tt>IOException</tt>.
     */
    private void closeDatagramTransport()
    {
        if (datagramTransport != null)
        {
            try
            {
                datagramTransport.close();
            }
            catch (IOException ioe)
            {
                /*
                 * DatagramTransportImpl has no reason to fail because it is
                 * merely an adapter of #connector and this PacketTransformer to
                 * the terms of the Bouncy Castle Crypto API.
                 */
                logger.error(
                        "Failed to (properly) close "
                            + datagramTransport.getClass(),
                        ioe);
            }
            datagramTransport = null;
        }
    }

    /**
     * Determines whether
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)} is
     * to try to establish a DTLS connection.
     *
     * @param i the number of tries remaining after the current one
     * @return <tt>true</tt> to try to establish a DTLS connection; otherwise,
     * <tt>false</tt>
     */
    private boolean enterRunInConnectThreadLoop(int i)
    {
        if ((i < 0) || (i > CONNECT_TRIES))
        {
            return false;
        }
        else
        {
            Thread currentThread = Thread.currentThread();

            synchronized (this)
            {
                if ((i > 0) && (i < CONNECT_TRIES - 1))
                {
                    boolean interrupted = false;

                    try
                    {
                        wait(CONNECT_RETRY_INTERVAL);
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                    if (interrupted)
                        currentThread.interrupt();
                }

                return
                    currentThread.equals(this.connectThread)
                        && datagramTransport.equals(this.datagramTransport);
            }
        }
    }

    /**
     * Gets the <tt>DtlsControl</tt> implementation associated with this
     * instance.
     *
     * @return the <tt>DtlsControl</tt> implementation associated with this
     * instance
     */
    DtlsControlImpl getDtlsControl()
    {
        return getTransformEngine().getDtlsControl();
    }

    /**
     * Gets the <tt>TransformEngine</tt> which has initialized this instance.
     *
     * @return the <tt>TransformEngine</tt> which has initialized this instance
     */
    DtlsTransformEngine getTransformEngine()
    {
        return transformEngine;
    }

    /**
     * Handles a specific <tt>IOException</tt> which was thrown during the
     * execution of
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)}
     * while trying to establish a DTLS connection
     *
     * @param ioe the <tt>IOException</tt> to handle
     * @param msg the human-readable message to log about the specified
     * <tt>ioe</tt>
     * @param i the number of tries remaining after the current one
     * @return <tt>true</tt> if the specified <tt>ioe</tt> was successfully
     * handled; <tt>false</tt>, otherwise
     */
    private boolean handleRunInConnectThreadException(
            IOException ioe,
            String msg,
            int i)
    {
        if (ioe instanceof TlsFatalAlert)
        {
            TlsFatalAlert tfa = (TlsFatalAlert) ioe;
            short alertDescription = tfa.getAlertDescription();

            if (alertDescription == AlertDescription.unexpected_message)
            {
                msg += " Received fatal unexpected message.";
                if ((i == 0)
                        || !Thread.currentThread().equals(connectThread)
                        || (connector == null)
                        || (mediaType == null))
                {
                    msg
                        += " Giving up after " + (CONNECT_TRIES - i)
                            + " retries.";
                }
                else
                {
                    msg += " Will retry.";
                    logger.error(msg, ioe);

                    return true;
                }
            }
            else
            {
                msg += " Received fatal alert " + alertDescription + ".";
            }
        }

        logger.error(msg, ioe);
        return false;
    }

    /**
     * Initializes a new <tt>SRTPTransformer</tt> instance with a specific
     * (negotiated) <tt>SRTPProtectionProfile</tt> and the keying material
     * specified by a specific <tt>TlsContext</tt>.
     *
     * @param srtpProtectionProfile the (negotiated)
     * <tt>SRTPProtectionProfile</tt> to initialize the new instance with
     * @param tlsContext the <tt>TlsContext</tt> which represents the keying
     * material
     * @return a new <tt>SRTPTransformer</tt> instance initialized with
     * <tt>srtpProtectionProfile</tt> and <tt>tlsContext</tt>
     */
    private SinglePacketTransformer initializeSRTPTransformer(
            int srtpProtectionProfile,
            TlsContext tlsContext)
    {
        boolean rtcp;

        switch (componentID)
        {
        case Component.RTCP:
            rtcp = true;
            break;
        case Component.RTP:
            rtcp = false;
            break;
        default:
            throw new IllegalStateException("componentID");
        }

        int cipher_key_length;
        int cipher_salt_length;
        int cipher;
        int auth_function;
        int auth_key_length;
        int RTCP_auth_tag_length, RTP_auth_tag_length;

        switch (srtpProtectionProfile)
        {
        case SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32:
            cipher_key_length = 128 / 8;
            cipher_salt_length = 112 / 8;
            cipher = SRTPPolicy.AESCM_ENCRYPTION;
            auth_function = SRTPPolicy.HMACSHA1_AUTHENTICATION;
            auth_key_length = 160 / 8;
            RTCP_auth_tag_length = 80 / 8;
            RTP_auth_tag_length = 32 / 8;
            break;
        case SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80:
            cipher_key_length = 128 / 8;
            cipher_salt_length = 112 / 8;
            cipher = SRTPPolicy.AESCM_ENCRYPTION;
            auth_function = SRTPPolicy.HMACSHA1_AUTHENTICATION;
            auth_key_length = 160 / 8;
            RTCP_auth_tag_length = RTP_auth_tag_length = 80 / 8;
            break;
        case SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_32:
            cipher_key_length = 0;
            cipher_salt_length = 0;
            cipher = SRTPPolicy.NULL_ENCRYPTION;
            auth_function = SRTPPolicy.HMACSHA1_AUTHENTICATION;
            auth_key_length = 160 / 8;
            RTCP_auth_tag_length = 80 / 8;
            RTP_auth_tag_length = 32 / 8;
            break;
        case SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_80:
            cipher_key_length = 0;
            cipher_salt_length = 0;
            cipher = SRTPPolicy.NULL_ENCRYPTION;
            auth_function = SRTPPolicy.HMACSHA1_AUTHENTICATION;
            auth_key_length = 160 / 8;
            RTCP_auth_tag_length = RTP_auth_tag_length = 80 / 8;
            break;
        default:
            throw new IllegalArgumentException("srtpProtectionProfile");
        }

        byte[] keyingMaterial
            = tlsContext.exportKeyingMaterial(
                    ExporterLabel.dtls_srtp,
                    null,
                    2 * (cipher_key_length + cipher_salt_length));
        byte[] client_write_SRTP_master_key = new byte[cipher_key_length];
        byte[] server_write_SRTP_master_key = new byte[cipher_key_length];
        byte[] client_write_SRTP_master_salt = new byte[cipher_salt_length];
        byte[] server_write_SRTP_master_salt = new byte[cipher_salt_length];
        byte[][] keyingMaterialValues
            = {
                client_write_SRTP_master_key,
                server_write_SRTP_master_key,
                client_write_SRTP_master_salt,
                server_write_SRTP_master_salt
            };

        for (int i = 0, keyingMaterialOffset = 0;
                i < keyingMaterialValues.length;
                i++)
        {
            byte[] keyingMaterialValue = keyingMaterialValues[i];

            System.arraycopy(
                    keyingMaterial, keyingMaterialOffset,
                    keyingMaterialValue, 0,
                    keyingMaterialValue.length);
            keyingMaterialOffset += keyingMaterialValue.length;
        }

        SRTPPolicy srtcpPolicy
            = new SRTPPolicy(
                    cipher,
                    cipher_key_length,
                    auth_function,
                    auth_key_length,
                    RTCP_auth_tag_length,
                    cipher_salt_length);
        SRTPPolicy srtpPolicy
            = new SRTPPolicy(
                    cipher,
                    cipher_key_length,
                    auth_function,
                    auth_key_length,
                    RTP_auth_tag_length,
                    cipher_salt_length);
        SRTPContextFactory clientSRTPContextFactory
            = new SRTPContextFactory(
                    /* sender */ tlsContext instanceof TlsClientContext,
                    client_write_SRTP_master_key,
                    client_write_SRTP_master_salt,
                    srtpPolicy,
                    srtcpPolicy);
        SRTPContextFactory serverSRTPContextFactory
            = new SRTPContextFactory(
                    /* sender */ tlsContext instanceof TlsServerContext,
                    server_write_SRTP_master_key,
                    server_write_SRTP_master_salt,
                    srtpPolicy,
                    srtcpPolicy);
        SRTPContextFactory forwardSRTPContextFactory;
        SRTPContextFactory reverseSRTPContextFactory;

        if (tlsContext instanceof TlsClientContext)
        {
            forwardSRTPContextFactory = clientSRTPContextFactory;
            reverseSRTPContextFactory = serverSRTPContextFactory;
        }
        else if (tlsContext instanceof TlsServerContext)
        {
            forwardSRTPContextFactory = serverSRTPContextFactory;
            reverseSRTPContextFactory = clientSRTPContextFactory;
        }
        else
        {
            throw new IllegalArgumentException("tlsContext");
        }

        SinglePacketTransformer srtpTransformer;

        if (rtcp)
        {
            srtpTransformer
                = new SRTCPTransformer(
                        forwardSRTPContextFactory,
                        reverseSRTPContextFactory);
        }
        else
        {
            srtpTransformer
                = new SRTPTransformer(
                        forwardSRTPContextFactory,
                        reverseSRTPContextFactory);
        }
        return srtpTransformer;
    }

    /**
     * {@inheritDoc}
     */
    public RawPacket reverseTransform(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        if (isDtlsRecord(buf, off, len))
        {
            boolean receive;

            synchronized (this)
            {
                if (datagramTransport == null)
                {
                    receive = false;
                }
                else
                {
                    datagramTransport.queueReceive(buf, off, len);
                    receive = true;
                }
            }
            if (receive)
            {
                DTLSTransport dtlsTransport = this.dtlsTransport;

                if (dtlsTransport == null)
                {
                    /*
                     * The specified pkt looks like a DTLS record and it has
                     * been consumed for the purposes of the secure channel
                     * represented by this PacketTransformer.
                     */
                    pkt = null;
                }
                else
                {
                    try
                    {
                        int receiveLimit = dtlsTransport.getReceiveLimit();
                        int delta = receiveLimit - len;

                        if (delta > 0)
                        {
                            pkt.grow(delta);
                            buf = pkt.getBuffer();
                            off = pkt.getOffset();
                            len = pkt.getLength();
                        }
                        else if (delta < 0)
                        {
                            pkt.shrink(-delta);
                            buf = pkt.getBuffer();
                            off = pkt.getOffset();
                            len = pkt.getLength();
                        }

                        int received
                            = dtlsTransport.receive(
                                buf, off, len,
                                DTLS_TRANSPORT_RECEIVE_WAITMILLIS);

                        if (received <= 0)
                        {
                            // No application data was decoded.
                            pkt = null;
                        }
                        else
                        {
                            delta = len - received;
                            if (delta > 0)
                                pkt.shrink(delta);

                            /*
                             * In DTLS-SRTP no application data is transmitted
                             * over the DTLS channel.
                             */
                            pkt = null;
                        }
                    }
                    catch (IOException ioe)
                    {
                        pkt = null;
                        logger.error("Failed to decode a DTLS record!", ioe);
                    }
                }
            }
            else
            {
                /*
                 * The specified pkt looks like a DTLS record but it is
                 * unexpected in the current state of the secure channel
                 * represented by this PacketTransformer.
                 */
                pkt = null;
            }
        }
        else
        {
            /*
             * XXX If DTLS-SRTP has not been initialized yet or has failed to
             * initialize, it is our explicit policy to let the received packet
             * pass through and rely on the SrtpListener to notify the user that
             * the session is not secured.
             */
            SinglePacketTransformer srtpTransformer = this.srtpTransformer;

            if (srtpTransformer != null)
                pkt = srtpTransformer.reverseTransform(pkt);
        }
        return pkt;
    }

    /**
     * Runs in {@link #connectThread} to initialize {@link #dtlsTransport}.
     *
     * @param dtlsProtocol
     * @param tlsPeer
     * @param datagramTransport
     */
    private void runInConnectThread(
            DTLSProtocol dtlsProtocol,
            TlsPeer tlsPeer,
            DatagramTransport datagramTransport)
    {
        DTLSTransport dtlsTransport = null;
        int srtpProtectionProfile = 0;
        TlsContext tlsContext = null;

        if (dtlsProtocol instanceof DTLSClientProtocol)
        {
            DTLSClientProtocol dtlsClientProtocol
                = (DTLSClientProtocol) dtlsProtocol;
            TlsClientImpl tlsClient = (TlsClientImpl) tlsPeer;

            for (int i = CONNECT_TRIES - 1; i >= 0; i--)
            {
                if (!enterRunInConnectThreadLoop(i))
                    break;
                try
                {
                    dtlsTransport
                        = dtlsClientProtocol.connect(
                                tlsClient, 
                                datagramTransport);
                    break;
                }
                catch (IOException ioe)
                {
                    if (handleRunInConnectThreadException(
                            ioe,
                            "Failed to connect this DTLS client to a DTLS"
                                + " server!",
                            i))
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
            }
            if (dtlsTransport != null)
            {
                srtpProtectionProfile = tlsClient.getChosenProtectionProfile();
                tlsContext = tlsClient.getContext();
            }
        }
        else if (dtlsProtocol instanceof DTLSServerProtocol)
        {
            DTLSServerProtocol dtlsServerProtocol
                = (DTLSServerProtocol) dtlsProtocol;
            TlsServerImpl tlsServer = (TlsServerImpl) tlsPeer;

            for (int i = CONNECT_TRIES - 1; i >= 0; i--)
            {
                if (!enterRunInConnectThreadLoop(i))
                    break;
                try
                {
                    dtlsTransport
                        = dtlsServerProtocol.accept(
                                tlsServer,
                                datagramTransport);
                    break;
                }
                catch (IOException ioe)
                {
                    if (handleRunInConnectThreadException(
                            ioe,
                            "Failed to accept a connection from a DTLS client!",
                            i))
                    {
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
            }
            if (dtlsTransport != null)
            {
                srtpProtectionProfile = tlsServer.getChosenProtectionProfile();
                tlsContext = tlsServer.getContext();
            }
        }
        else
            throw new IllegalStateException("dtlsProtocol");

        SinglePacketTransformer srtpTransformer
            = (dtlsTransport == null)
                ? null
                : initializeSRTPTransformer(srtpProtectionProfile, tlsContext);
        boolean closeSRTPTransformer;

        synchronized (this)
        {
            if (Thread.currentThread().equals(this.connectThread)
                    && datagramTransport.equals(this.datagramTransport))
            {
                this.dtlsTransport = dtlsTransport;
                this.srtpTransformer = srtpTransformer;
                notifyAll();
            }
            closeSRTPTransformer
                = (this.srtpTransformer != srtpTransformer);
        }
        if (closeSRTPTransformer && (srtpTransformer != null))
            srtpTransformer.close();
    }

    /**
     * Sets the <tt>RTPConnector</tt> which is to use or uses this
     * <tt>PacketTransformer</tt>.
     *
     * @param connector the <tt>RTPConnector</tt> which is to use or uses this
     * <tt>PacketTransformer</tt>
     */
    void setConnector(AbstractRTPConnector connector)
    {
        if (this.connector != connector)
        {
            this.connector = connector;

            DatagramTransportImpl datagramTransport = this.datagramTransport;

            if (datagramTransport != null)
                datagramTransport.setConnector(connector);
        }
    }

    /**
     * Sets the <tt>MediaType</tt> of the stream which this instance is to work
     * for/be associated with.
     *
     * @param mediaType the <tt>MediaType</tt> of the stream which this instance
     * is to work for/be associated with
     */
    synchronized void setMediaType(MediaType mediaType)
    {
        if (this.mediaType != mediaType)
        {
            if (this.mediaType != null)
                stop();

            this.mediaType = mediaType;

            if (this.mediaType != null)
                start();
        }
    }

    /**
     * Sets the DTLS protocol according to which this
     * <tt>DtlsPacketTransformer</tt> is to act either as a DTLS server or a
     * DTLS client.
     *
     * @param setup the value of the <tt>setup</tt> SDP attribute to set on this
     * instance in order to determine whether this instance is to act as a DTLS
     * client or a DTLS server
     */
    void setSetup(DtlsControl.Setup setup)
    {
        if (this.setup != setup)
            this.setup = setup;
    }

    /**
     * Starts this <tt>PacketTransformer</tt>.
     */
    private synchronized void start()
    {
        if (this.datagramTransport != null)
        {
            if ((this.connectThread == null) && (dtlsTransport == null))
            {
                logger.warn(
                        getClass().getName()
                            + " has been started but has failed to establish"
                            + " the DTLS connection!");
            }
            return;
        }

        AbstractRTPConnector connector = this.connector;

        if (connector == null)
            throw new NullPointerException("connector");

        DtlsControl.Setup setup = this.setup;
        SecureRandom secureRandom = new SecureRandom();
        final DTLSProtocol dtlsProtocolObj;
        final TlsPeer tlsPeer;

        if (DtlsControl.Setup.ACTIVE.equals(setup))
        {
            dtlsProtocolObj = new DTLSClientProtocol(secureRandom);
            tlsPeer = new TlsClientImpl(this);
        }
        else
        {
            dtlsProtocolObj = new DTLSServerProtocol(secureRandom);
            tlsPeer = new TlsServerImpl(this);
        }

        final DatagramTransportImpl datagramTransport
            = new DatagramTransportImpl(componentID);

        datagramTransport.setConnector(connector);

        Thread connectThread
            = new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        runInConnectThread(
                                dtlsProtocolObj,
                                tlsPeer,
                                datagramTransport);
                    }
                    finally
                    {
                        if (Thread.currentThread().equals(
                                DtlsPacketTransformer.this.connectThread))
                        {
                            DtlsPacketTransformer.this.connectThread = null;
                        }
                    }
                }
            };

        connectThread.setDaemon(true);
        connectThread.setName(
                DtlsPacketTransformer.class.getName() + ".connectThread");

        this.connectThread = connectThread;
        this.datagramTransport = datagramTransport;

        boolean started = false;

        try
        {
            connectThread.start();
            started = true;
        }
        finally
        {
            if (!started)
            {
                if (connectThread.equals(this.connectThread))
                    this.connectThread = null;
                if (datagramTransport.equals(this.datagramTransport))
                    this.datagramTransport = null;
            }
        }

        notifyAll();
    }

    /**
     * Stops this <tt>PacketTransformer</tt>.
     */
    private synchronized void stop()
    {
        if (connectThread != null)
            connectThread = null;
        try
        {
            /*
             * The dtlsTransport and srtpTransformer SHOULD be closed, of
             * course. The datagramTransport MUST be closed.
             */
            if (dtlsTransport != null)
            {
                try
                {
                    dtlsTransport.close();
                }
                catch (IOException ioe)
                {
                    logger.error(
                            "Failed to (properly) close "
                                + dtlsTransport.getClass(),
                            ioe);
                }
                dtlsTransport = null;
            }
            if (srtpTransformer != null)
            {
                srtpTransformer.close();
                srtpTransformer = null;
            }
        }
        finally
        {
            try
            {
                closeDatagramTransport();
            }
            finally
            {
                notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public RawPacket transform(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        /*
         * If the specified pkt represents a DTLS record, then it should pass
         * through this PacketTransformer (e.g. it has been sent through
         * DatagramPacketImpl).
         */
        if (!isDtlsRecord(buf, off, len))
        {
            /*
             * XXX If DTLS-SRTP has not been initialized yet or has failed to
             * initialize, it is our explicit policy to let the received packet
             * pass through and rely on the SrtpListener to notify the user that
             * the session is not secured.
             */
            SinglePacketTransformer srtpTransformer = this.srtpTransformer;

            if (srtpTransformer != null)
                pkt = srtpTransformer.transform(pkt);
        }
        return pkt;
    }
}
