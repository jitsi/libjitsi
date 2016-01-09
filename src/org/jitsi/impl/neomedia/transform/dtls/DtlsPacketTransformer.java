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

import java.io.*;
import java.security.*;

import org.bouncycastle.crypto.tls.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements {@link PacketTransformer} for DTLS-SRTP. It's capable of working
 * in pure DTLS mode if appropriate flag was set in <tt>DtlsControlImpl</tt>.
 *
 * @author Lyubomir Marinov
 */
public class DtlsPacketTransformer
    extends SinglePacketTransformer
{
    private static final long CONNECT_RETRY_INTERVAL = 500;

    /**
     * The maximum number of times that
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)} is
     * to retry the invocations of
     * {@link DTLSClientProtocol#connect(TlsClient, DatagramTransport)} and
     * {@link DTLSServerProtocol#accept(TlsServer, DatagramTransport)} in
     * anticipation of a successful connection.
     */
    private static final int CONNECT_TRIES = 3;

    /**
     * The indicator which determines whether unencrypted packets sent or
     * received through <tt>DtlsPacketTransformer</tt> are to be dropped. The
     * default value is <tt>false</tt>.
     *
     * @see #DROP_UNENCRYPTED_PKTS_PNAME
     */
    private static final boolean DROP_UNENCRYPTED_PKTS;

    /**
     * The name of the <tt>ConfigurationService</tt> and/or <tt>System</tt>
     * property which indicates whether unencrypted packets sent or received
     * through <tt>DtlsPacketTransformer</tt> are to be dropped. The default
     * value is <tt>false</tt>.
     */
    private static final String DROP_UNENCRYPTED_PKTS_PNAME
        = DtlsPacketTransformer.class.getName() + ".dropUnencryptedPkts";

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

    static
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean dropUnencryptedPkts = false;

        if (cfg == null)
        {
            String s = System.getProperty(DROP_UNENCRYPTED_PKTS_PNAME);

            if (s != null)
                dropUnencryptedPkts = Boolean.parseBoolean(s);
        }
        else
        {
            dropUnencryptedPkts
                = cfg.getBoolean(
                        DROP_UNENCRYPTED_PKTS_PNAME,
                        dropUnencryptedPkts);
        }
        DROP_UNENCRYPTED_PKTS = dropUnencryptedPkts;
    }

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
                // Unless a new ContentType has been defined by the Bouncy
                // Castle Crypto APIs, the specified buf does not represent a
                // DTLS record.
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
     * The <tt>RTPConnector</tt> which uses this <tt>PacketTransformer</tt>.
     */
    private AbstractRTPConnector connector;

    /**
     * The background <tt>Thread</tt> which initializes {@link #dtlsTransport}.
     */
    private Thread connectThread;

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
     * Whether rtcp-mux is in use.
     *
     * If enabled, and this is the transformer for RTCP, it will not establish
     * a DTLS session on its own, but rather wait for the RTP transformer to
     * do so, and reuse it to initialize the SRTP transformer.
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
     * The {@code SRTPTransformer} (to be) used by this instance.
     */
    private SinglePacketTransformer _srtpTransformer;

    /**
     * The indicator which determines whether the <tt>TlsPeer</tt> employed by
     * this <tt>PacketTransformer</tt> has raised an
     * <tt>AlertDescription.close_notify</tt> <tt>AlertLevel.warning</tt> i.e.
     * the remote DTLS peer has closed the write side of the connection.
     */
    private boolean tlsPeerHasRaisedCloseNotifyWarning;

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
    @Override
    public synchronized void close()
    {
        // SrtpControl.start(MediaType) starts its associated TransformEngine.
        // We will use that mediaType to signal the normal stop then as well
        // i.e. we will call setMediaType(null) first.
        setMediaType(null);
        setConnector(null);
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
                // DatagramTransportImpl has no reason to fail because it is
                // merely an adapter of #connector and this PacketTransformer to
                // the terms of the Bouncy Castle Crypto API.
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
     * @param datagramTransport
     * @return <tt>true</tt> to try to establish a DTLS connection; otherwise,
     * <tt>false</tt>
     */
    private boolean enterRunInConnectThreadLoop(
            int i,
            DatagramTransport datagramTransport)
    {
        if (i < 0 || i > CONNECT_TRIES)
        {
            return false;
        }
        else
        {
            Thread currentThread = Thread.currentThread();

            synchronized (this)
            {
                if (i > 0 && i < CONNECT_TRIES - 1)
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
        // SrtpControl.start(MediaType) starts its associated TransformEngine.
        // We will use that mediaType to signal the normal stop then as well
        // i.e. we will ignore exception after the procedure to stop this
        // PacketTransformer has begun.
        if (mediaType == null)
            return false;

        if (ioe instanceof TlsFatalAlert)
        {
            TlsFatalAlert tfa = (TlsFatalAlert) ioe;
            short alertDescription = tfa.getAlertDescription();

            if (alertDescription == AlertDescription.unexpected_message)
            {
                msg += " Received fatal unexpected message.";
                if (i == 0
                        || !Thread.currentThread().equals(connectThread)
                        || connector == null
                        || mediaType == null)
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
     * Tries to initialize {@link #_srtpTransformer} by using the
     * <tt>DtlsPacketTransformer</tt> for RTP.
     *
     * @return the (possibly updated) value of {@link #_srtpTransformer}.
     */
    private SinglePacketTransformer initializeSRTCPTransformerFromRtp()
    {
        DtlsPacketTransformer rtpTransformer
            = (DtlsPacketTransformer) getTransformEngine().getRTPTransformer();

        // Prevent recursion (that is pretty much impossible to ever happen).
        if (rtpTransformer != this)
        {
            PacketTransformer srtpTransformer
                = rtpTransformer.waitInitializeAndGetSRTPTransformer();

            if (srtpTransformer != null
                    && srtpTransformer instanceof SRTPTransformer)
            {
                synchronized (this)
                {
                    if (_srtpTransformer == null)
                    {
                        _srtpTransformer
                            = new SRTCPTransformer(
                                    (SRTPTransformer) srtpTransformer);
                        // For the sake of completeness, we notify whenever we
                        // assign to _srtpTransformer.
                        notifyAll();
                    }
                }
            }
        }

        return _srtpTransformer;
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
     * Notifies this instance that the DTLS record layer associated with a
     * specific <tt>TlsPeer</tt> has raised an alert.
     *
     * @param tlsPeer the <tt>TlsPeer</tt> whose associated DTLS record layer
     * has raised an alert
     * @param alertLevel {@link AlertLevel}
     * @param alertDescription {@link AlertDescription}
     * @param message a human-readable message explaining what caused the alert.
     * May be <tt>null</tt>.
     * @param cause the exception that caused the alert to be raised. May be
     * <tt>null</tt>.
     */
    void notifyAlertRaised(
            TlsPeer tlsPeer,
            short alertLevel,
            short alertDescription,
            String message,
            Exception cause)
    {
        if (AlertLevel.warning == alertLevel
                && AlertDescription.close_notify == alertDescription)
        {
            tlsPeerHasRaisedCloseNotifyWarning = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        if (isDtlsRecord(buf, off, len))
        {
            if (rtcpmux && Component.RTCP == componentID)
            {
                // This should never happen.
                logger.warn(
                        "Dropping a DTLS record, because it was received on the"
                            + " RTCP channel while rtcpmux is in use.");
                return null;
            }

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
                    // The specified pkt looks like a DTLS record and it has
                    // been consumed for the purposes of the secure channel
                    // represented by this PacketTransformer.
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
                        }
                    }
                    catch (IOException ioe)
                    {
                        pkt = null;
                        // SrtpControl.start(MediaType) starts its associated
                        // TransformEngine. We will use that mediaType to signal
                        // the normal stop then as well i.e. we will ignore
                        // exception after the procedure to stop this
                        // PacketTransformer has begun.
                        if (mediaType != null
                                && !tlsPeerHasRaisedCloseNotifyWarning)
                        {
                            logger.error(
                                    "Failed to decode a DTLS record!",
                                    ioe);
                        }
                    }
                }
            }
            else
            {
                // The specified pkt looks like a DTLS record but it is
                // unexpected in the current state of the secure channel
                // represented by this PacketTransformer. This PacketTransformer
                // has not been started (successfully) or has been closed.
                pkt = null;
            }
        }
        else if (transformEngine.isSrtpDisabled())
        {
            // In pure DTLS mode only DTLS records pass through.
            pkt = null;
        }
        else
        {
            // DTLS-SRTP has not been initialized yet or has failed to
            // initialize.
            SinglePacketTransformer srtpTransformer
                = waitInitializeAndGetSRTPTransformer();

            if (srtpTransformer != null)
                pkt = srtpTransformer.reverseTransform(pkt);
            else if (DROP_UNENCRYPTED_PKTS)
                pkt = null;
            // XXX Else, it is our explicit policy to let the received packet
            // pass through and rely on the SrtpListener to notify the user that
            // the session is not secured.
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
        final boolean srtp = !transformEngine.isSrtpDisabled();
        int srtpProtectionProfile = 0;
        TlsContext tlsContext = null;

        // DTLS client
        if (dtlsProtocol instanceof DTLSClientProtocol)
        {
            DTLSClientProtocol dtlsClientProtocol
                = (DTLSClientProtocol) dtlsProtocol;
            TlsClientImpl tlsClient = (TlsClientImpl) tlsPeer;

            for (int i = CONNECT_TRIES - 1; i >= 0; i--)
            {
                if (!enterRunInConnectThreadLoop(i, datagramTransport))
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
                    if (!handleRunInConnectThreadException(
                            ioe,
                            "Failed to connect this DTLS client to a DTLS"
                                + " server!",
                            i))
                    {
                        break;
                    }
                }
            }
            if (dtlsTransport != null && srtp)
            {
                srtpProtectionProfile = tlsClient.getChosenProtectionProfile();
                tlsContext = tlsClient.getContext();
            }
        }
        // DTLS server
        else if (dtlsProtocol instanceof DTLSServerProtocol)
        {
            DTLSServerProtocol dtlsServerProtocol
                = (DTLSServerProtocol) dtlsProtocol;
            TlsServerImpl tlsServer = (TlsServerImpl) tlsPeer;

            for (int i = CONNECT_TRIES - 1; i >= 0; i--)
            {
                if (!enterRunInConnectThreadLoop(i, datagramTransport))
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
                    if (!handleRunInConnectThreadException(
                            ioe,
                            "Failed to accept a connection from a DTLS client!",
                            i))
                    {
                        break;
                    }
                }
            }
            if (dtlsTransport != null && srtp)
            {
                srtpProtectionProfile = tlsServer.getChosenProtectionProfile();
                tlsContext = tlsServer.getContext();
            }
        }
        else
        {
            // It MUST be either a DTLS client or a DTLS server.
            throw new IllegalStateException("dtlsProtocol");
        }

        SinglePacketTransformer srtpTransformer
            = (dtlsTransport == null || !srtp)
                ? null
                : initializeSRTPTransformer(srtpProtectionProfile, tlsContext);
        boolean closeSRTPTransformer;

        synchronized (this)
        {
            if (Thread.currentThread().equals(this.connectThread)
                    && datagramTransport.equals(this.datagramTransport))
            {
                this.dtlsTransport = dtlsTransport;
                _srtpTransformer = srtpTransformer;
                notifyAll();
            }
            closeSRTPTransformer = (_srtpTransformer != srtpTransformer);
        }
        if (closeSRTPTransformer && srtpTransformer != null)
            srtpTransformer.close();
    }

    /**
     * Sends the data contained in a specific byte array as application data
     * through the DTLS connection of this <tt>DtlsPacketTransformer</tt>.
     *
     * @param buf the byte array containing data to send.
     * @param off the offset in <tt>buf</tt> where the data begins.
     * @param len the length of data to send.
     */
    public void sendApplicationData(byte[] buf, int off, int len)
    {
        DTLSTransport dtlsTransport = this.dtlsTransport;
        Throwable throwable = null;

        if (dtlsTransport != null)
        {
            try
            {
                dtlsTransport.send(buf, off, len);
            }
            catch (IOException ioe)
            {
                throwable = ioe;
            }
        }
        else
        {
            throwable = new NullPointerException("dtlsTransport");
        }
        if (throwable != null)
        {
            // SrtpControl.start(MediaType) starts its associated
            // TransformEngine. We will use that mediaType to signal the normal
            // stop then as well i.e. we will ignore exception after the
            // procedure to stop this PacketTransformer has begun.
            if (mediaType != null && !tlsPeerHasRaisedCloseNotifyWarning)
            {
                logger.error(
                        "Failed to send application data over DTLS transport: ",
                        throwable);
            }
        }
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
            MediaType oldValue = this.mediaType;

            this.mediaType = mediaType;

            if (oldValue != null)
                stop();
            if (this.mediaType != null)
                start();
        }
    }

    /**
     * Enables/disables rtcp-mux.
     * @param rtcpmux whether to enable or disable.
     */
    void setRtcpmux(boolean rtcpmux)
    {
        this.rtcpmux = rtcpmux;
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
            if (this.connectThread == null && dtlsTransport == null)
            {
                logger.warn(
                        getClass().getName()
                            + " has been started but has failed to establish"
                            + " the DTLS connection!");
            }
            return;
        }

        if (rtcpmux && Component.RTCP == componentID)
        {
            // In the case of rtcp-mux, the RTCP transformer does not create
            // a DTLS session. The SRTP context (_srtpTransformer) will be
            // initialized on demand using initializeSRTCPTransformerFromRtp().
            return;
        }

        AbstractRTPConnector connector = this.connector;

        if (connector == null)
            throw new NullPointerException("connector");

        DtlsControl.Setup setup = this.setup;
        SecureRandom secureRandom = DtlsControlImpl.createSecureRandom();
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
        tlsPeerHasRaisedCloseNotifyWarning = false;

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
            // The dtlsTransport and _srtpTransformer SHOULD be closed, of
            // course. The datagramTransport MUST be closed.
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
            if (_srtpTransformer != null)
            {
                _srtpTransformer.close();
                _srtpTransformer = null;
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
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        // If the specified pkt represents a DTLS record, then it should pass
        // through this PacketTransformer (e.g. it has been sent through
        // DatagramTransportImpl).
        if (isDtlsRecord(buf, off, len))
            return pkt;

        // SRTP
        if (!transformEngine.isSrtpDisabled())
        {
            // DTLS-SRTP has not been initialized yet or has failed to
            // initialize.
            SinglePacketTransformer srtpTransformer
                = waitInitializeAndGetSRTPTransformer();

            if (srtpTransformer != null)
                pkt = srtpTransformer.transform(pkt);
            else if (DROP_UNENCRYPTED_PKTS)
                pkt = null;
            // XXX Else, it is our explicit policy to let the received packet
            // pass through and rely on the SrtpListener to notify the user that
            // the session is not secured.
        }
        // Pure/non-SRTP DTLS
        else
        {
            // The specified pkt will pass through this PacketTransformer only
            // if it gets transformed into a DTLS record.
            pkt = null;

            sendApplicationData(buf, off, len);
        }
        return pkt;
    }

    /**
     * Gets the {@code SRTPTransformer} used by this instance. If
     * {@link #_srtpTransformer} does not exist (yet) and the state of this
     * instance indicates that its initialization is in progess, then blocks
     * until {@code _srtpTransformer} is initialized and returns it.
     *
     * @return the {@code SRTPTransformer} used by this instance
     */
    private SinglePacketTransformer waitInitializeAndGetSRTPTransformer()
    {
        SinglePacketTransformer srtpTransformer = _srtpTransformer;

        if (srtpTransformer != null)
            return srtpTransformer;

        if (rtcpmux && Component.RTCP == componentID)
            return initializeSRTCPTransformerFromRtp();

        // XXX It is our explicit policy to rely on the SrtpListener to notify
        // the user that the session is not secure. Unfortunately, (1) the
        // SrtpListener is not supported by this DTLS SrtpControl implementation
        // and (2) encrypted packets may arrive soon enough to be let through
        // while _srtpTransformer is still initializing. Consequently, we will
        // block and wait for _srtpTransformer to initialize.
        boolean interrupted = false;

        try
        {
            synchronized (this)
            {
                do
                {
                    srtpTransformer = _srtpTransformer;
                    if (srtpTransformer != null)
                        break; // _srtpTransformer is initialized

                    if (connectThread == null)
                    {
                        // Though _srtpTransformer is NOT initialized, there is
                        // no point in waiting because there is no one to
                        // initialize it.
                        break;
                    }

                    try
                    {
                        // It does not really matter (enough) how much we wait
                        // here because we wait in a loop.
                        long timeout = CONNECT_TRIES * CONNECT_RETRY_INTERVAL;

                        wait(timeout);
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                }
                while (true);
            }
        }
        finally
        {
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        return srtpTransformer;
    }
}
