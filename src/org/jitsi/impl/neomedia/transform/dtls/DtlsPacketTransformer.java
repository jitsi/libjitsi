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

import java.beans.*;
import java.io.*;
import java.security.*;
import java.util.*;

import org.bouncycastle.crypto.tls.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.srtp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * Implements {@link PacketTransformer} for DTLS-SRTP. It's capable of working
 * in pure DTLS mode if appropriate flag was set in <tt>DtlsControlImpl</tt>.
 *
 * @author Lyubomir Marinov
 */
public class DtlsPacketTransformer
    implements PacketTransformer,
               PropertyChangeListener
{
    /**
     * The interval in milliseconds between successive tries to await successful
     * connections in
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)}.
     *
     * @see #CONNECT_TRIES
     */
    private static final long CONNECT_RETRY_INTERVAL = 500;

    /**
     * The maximum number of times that
     * {@link #runInConnectThread(DTLSProtocol, TlsPeer, DatagramTransport)} is
     * to retry the invocations of
     * {@link DTLSClientProtocol#connect(TlsClient, DatagramTransport)} and
     * {@link DTLSServerProtocol#accept(TlsServer, DatagramTransport)} in
     * anticipation of a successful connection.
     *
     * @see #CONNECT_RETRY_INTERVAL
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

    /**
     * The maximum number of elements of queues such as
     * {@link #_reverseTransformSrtpQueue} and {@link #_transformSrtpQueue}.
     * Defined in order to reduce excessive memory use (which may lead to
     * {@link OutOfMemoryError}s, for example).
     */
    private static final int TRANSFORM_QUEUE_CAPACITY
        = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;

    static
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        DROP_UNENCRYPTED_PKTS
            = ConfigUtils.getBoolean(cfg, DROP_UNENCRYPTED_PKTS_PNAME, false);
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

                if (major == ProtocolVersion.DTLSv10.getMajorVersion()
                        && minor == ProtocolVersion.DTLSv10.getMinorVersion())
                {
                    version = ProtocolVersion.DTLSv10;
                }
                if (version == null
                        && major == ProtocolVersion.DTLSv12.getMajorVersion()
                        && minor == ProtocolVersion.DTLSv12.getMinorVersion())
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
     * The {@code Queue} of SRTP {@code RawPacket}s which were received from the
     * remote while {@link #_srtpTransformer} was unavailable i.e. {@code null}.
     */
    private final LinkedList<RawPacket> _reverseTransformSrtpQueue
        = new LinkedList<>();

    /**
     * Whether rtcp-mux is in use.
     *
     * If enabled, and this is the transformer for RTCP, it will not establish
     * a DTLS session on its own, but rather wait for the RTP transformer to
     * do so, and reuse it to initialize the SRTP transformer.
     */
    private boolean rtcpmux;

    /**
     * The {@code SRTPTransformer} (to be) used by this instance.
     */
    private SinglePacketTransformer _srtpTransformer;

    /**
     * The last time (in milliseconds since the epoch) that
     * {@link #_srtpTransformer} was set to a non-{@code null} value.
     */
    private long _srtpTransformerLastChanged = -1;

    /**
     * The indicator which determines whether the <tt>TlsPeer</tt> employed by
     * this <tt>PacketTransformer</tt> has raised an
     * <tt>AlertDescription.close_notify</tt> <tt>AlertLevel.warning</tt> i.e.
     * the remote DTLS peer has closed the write side of the connection.
     */
    private boolean tlsPeerHasRaisedCloseNotifyWarning;

    /**
     * The {@code Queue} of SRTP {@code RawPacket}s which were to be sent to the
     * remote while {@link #_srtpTransformer} was unavailable i.e. {@code null}.
     */
    private final LinkedList<RawPacket> _transformSrtpQueue
        = new LinkedList<>();

    /**
     * The <tt>TransformEngine</tt> which has initialized this instance.
     */
    private final DtlsTransformEngine transformEngine;

    private boolean started = false;

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

        // Track the DTLS properties which control the conditional behaviors of
        // DtlsPacketTransformer.
        getProperties().addPropertyChangeListener(this);
        propertyChange(/* propertyName */ (String) null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close()
    {
        getProperties().removePropertyChangeListener(this);

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
     * Gets the properties of {@link DtlsControlImpl} and their values which
     * the associated {@code DtlsControlImpl} shares with this instance.
     *
     * @return the properties of {@code DtlsControlImpl} and their values which
     * the associated {@code DtlsControlImpl} shares with this instance
     */
    Properties getProperties()
    {
        return getTransformEngine().getProperties();
    }

    /**
     * Gets the {@code SRTPTransformer} (to be) used by this instance.
     *
     * @return the {@code SRTPTransformer} (to be) used by this instance
     */
    private SinglePacketTransformer getSRTPTransformer()
    {
        SinglePacketTransformer srtpTransformer = _srtpTransformer;

        if (srtpTransformer != null)
            return srtpTransformer;

        if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID)
            return initializeSRTCPTransformerFromRtp();

        // XXX It is our explicit policy to rely on the SrtpListener to notify
        // the user that the session is not secure. Unfortunately, (1) the
        // SrtpListener is not supported by this DTLS SrtpControl implementation
        // and (2) encrypted packets may arrive soon enough to be let through
        // while _srtpTransformer is still initializing. Consequently, we may
        // wait for _srtpTransformer (a bit) to initialize.
        boolean yield = true;

        do
        {
            synchronized (this)
            {
                srtpTransformer = _srtpTransformer;
                if (srtpTransformer != null)
                    break; // _srtpTransformer is initialized

                if (connectThread == null)
                {
                    // Though _srtpTransformer is NOT initialized, there is no
                    // point in waiting because there is no one to initialize
                    // it.
                    break;
                }
            }

            if (yield)
            {
                yield = false;
                Thread.yield();
            }
            else
            {
                break;
            }
        }
        while (true);

        return srtpTransformer;
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
     * {@code DtlsPacketTransformer} for RTP. (The method invocations should be
     * on the {@code DtlsPacketTransformer} for RTCP as the method name
     * suggests.)
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
                = rtpTransformer.getSRTPTransformer();

            if (srtpTransformer != null
                    && srtpTransformer instanceof SRTPTransformer)
            {
                synchronized (this)
                {
                    if (_srtpTransformer == null)
                    {
                        setSrtpTransformer(
                                new SRTCPTransformer(
                                        (SRTPTransformer) srtpTransformer));
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
            case DtlsTransformEngine.COMPONENT_RTCP:
            rtcp = true;
            break;
        case DtlsTransformEngine.COMPONENT_RTP:
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

        byte[] keyingMaterial = null;
        if (tlsContext.getSecurityParameters().getMasterSecret() == null
                && tlsContext.getResumableSession() != null) {
            // BouncyCastle 1.59 clears the master secret from its session
            // parameters immediately after connect, making them unavailable
            // for exporting keying material. The value can still be present
            // in the session parameters from the resumable session, which is
            // used here.
            final SessionParameters sessionParameters
                = tlsContext.getResumableSession().exportSessionParameters();
            if (sessionParameters != null
                    && sessionParameters.getMasterSecret() != null)
            {
                keyingMaterial = exportKeyingMaterial(
                        tlsContext,
                        ExporterLabel.dtls_srtp,
                        null,
                        2 * (cipher_key_length + cipher_salt_length),
                        sessionParameters.getMasterSecret()
                );
            }
        }
        else
        {
            // Original, BouncyCastle 1.54-compatible code.
            keyingMaterial
                    = tlsContext.exportKeyingMaterial(
                    ExporterLabel.dtls_srtp,
                    null,
                    2 * (cipher_key_length + cipher_salt_length) );
        }
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
     * Determines whether this {@code DtlsPacketTransformer} is to operate in
     * pure DTLS mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or
     * {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return getProperties().isSrtpDisabled();
    }

    private synchronized void maybeStart()
    {
        if (this.mediaType != null && this.connector != null && !started)
        {
            start();
        }
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
            Throwable cause)
    {
        if (AlertLevel.warning == alertLevel
                && AlertDescription.close_notify == alertDescription)
        {
            tlsPeerHasRaisedCloseNotifyWarning = true;
        }
    }

    public void propertyChange(PropertyChangeEvent ev)
    {
        propertyChange(ev.getPropertyName());
    }

    private void propertyChange(String propertyName)
    {
        // This DtlsPacketTransformer calls the method with null at construction
        // time to initialize the respective states.
        if (propertyName == null)
        {
            propertyChange(Properties.RTCPMUX_PNAME);
            propertyChange(Properties.MEDIA_TYPE_PNAME);
            propertyChange(Properties.CONNECTOR_PNAME);
        }
        else if (Properties.CONNECTOR_PNAME.equals(propertyName))
        {
            setConnector(
                    (AbstractRTPConnector) getProperties().get(propertyName));
        }
        else if (Properties.MEDIA_TYPE_PNAME.equals(propertyName))
        {
            setMediaType((MediaType) getProperties().get(propertyName));
        }
        else if (Properties.RTCPMUX_PNAME.equals(propertyName))
        {
            Object newValue = getProperties().get(propertyName);

            setRtcpmux((newValue == null) ? false : (Boolean) newValue);
        }
    }

    /**
     * Queues {@code RawPacket}s to be supplied to
     * {@link #transformSrtp(SinglePacketTransformer, Collection, boolean, List,
     * RawPacket)} when {@link #_srtpTransformer} becomes available.
     *
     * @param pkts the {@code RawPacket}s to queue
     * @param transform {@code true} if {@code pkts} are to be sent to the
     * remote peer or {@code false} if {@code pkts} were received from the
     * remote peer
     */
    private void queueTransformSrtp(RawPacket[] pkts, boolean transform)
    {
        if (pkts != null)
        {
            Queue<RawPacket> q
                = transform ? _transformSrtpQueue : _reverseTransformSrtpQueue;

            synchronized (q)
            {
                for (RawPacket pkt : pkts)
                {
                    if (pkt != null)
                    {
                        while (q.size() >= TRANSFORM_QUEUE_CAPACITY
                                && q.poll() != null);

                        q.add(pkt);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        return transform(pkts, /* transform */ false);
    }

    /**
     * Processes a DTLS {@code RawPacket} received from the remote peer, and
     * reads any available application data into {@code outPkts}.
     *
     * @param pkt the DTLS {@code RawPacket} received from the remote peer to
     * process.
     * @param outPkts a list of packets, to which application data read from
     * the DTLS transport should be appended. If {@code null}, application data
     * will not be read.
     */
    private void reverseTransformDtls(RawPacket pkt, List<RawPacket> outPkts)
    {
        if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID)
        {
            // This should never happen.
            logger.warn(
                    "Dropping a DTLS packet, because it was received on the"
                        + " RTCP channel while rtcpmux is in use.");
            return;
        }

        // First, make the input packet available for bouncycastle to read.
        synchronized (this)
        {
            if (datagramTransport == null)
            {
                logger.warn(
                        "Dropping a DTLS packet. This DtlsPacketTransformer has"
                            + " not been started successfully or has been"
                            + " closed.");
            }
            else
            {
                datagramTransport.queueReceive(
                        pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
            }
        }

        if (outPkts == null)
        {
            return;
        }

        // Next, try to read any available application data from bouncycastle.
        DTLSTransport dtlsTransport = this.dtlsTransport;

        if (dtlsTransport == null)
        {
            // The DTLS transport hasn't initialized yet.
        }
        else
        {
            // There might be more than one packet queued in datagramTransport,
            // if they were added prior to dtlsTransport being initialized. Read
            // all of them.
            try
            {
                do
                {
                    int receiveLimit = dtlsTransport.getReceiveLimit();
                    // FIXME This is at best inefficient, but it is not meant as
                    // a long-term solution. A major refactoring is planned,
                    // which will probably make this code obsolete.
                    byte[] buf = new byte[receiveLimit];
                    RawPacket p = new RawPacket(buf, 0, buf.length);

                    int received
                        = dtlsTransport.receive(
                                buf, 0, buf.length,
                                DTLS_TRANSPORT_RECEIVE_WAITMILLIS);

                    if (received <= 0)
                    {
                        // No (more) application data was decoded.
                        break;
                    }
                    else
                    {
                        p.setLength(received);
                        outPkts.add(p);
                    }
                }
                while (true);
            }
            catch (IOException ioe)
            {
                // SrtpControl.start(MediaType) starts its associated
                // TransformEngine. We will use that mediaType to signal the
                // normal stop then as well i.e. we will ignore exception after
                // the procedure to stop this PacketTransformer has begun.
                if (mediaType != null
                        && !tlsPeerHasRaisedCloseNotifyWarning)
                {
                    logger.error("Failed to decode a DTLS record!", ioe);
                }
            }
        }
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
        final boolean srtp = !isSrtpDisabled();
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
                setSrtpTransformer(srtpTransformer);
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
    private synchronized void setConnector(AbstractRTPConnector connector)
    {
        if (this.connector != connector)
        {
            AbstractRTPConnector oldValue = this.connector;

            this.connector = connector;

            DatagramTransportImpl datagramTransport = this.datagramTransport;

            if (datagramTransport != null)
                datagramTransport.setConnector(connector);

            if (connector != null)
                maybeStart();
        }
    }

    /**
     * Sets the <tt>MediaType</tt> of the stream which this instance is to work
     * for/be associated with.
     *
     * @param mediaType the <tt>MediaType</tt> of the stream which this instance
     * is to work for/be associated with
     */
    private synchronized void setMediaType(MediaType mediaType)
    {
        if (this.mediaType != mediaType)
        {
            MediaType oldValue = this.mediaType;

            this.mediaType = mediaType;

            if (oldValue != null)
                stop();
            if (this.mediaType != null)
                maybeStart();
        }
    }

    /**
     * Enables/disables rtcp-mux.
     *
     * @param rtcpmux {@code true} to enable rtcp-mux or {@code false} to
     * disable it.
     */
    void setRtcpmux(boolean rtcpmux)
    {
        this.rtcpmux = rtcpmux;
    }

    /**
     * Sets {@link #_srtpTransformer} to a specific value.
     *
     * @param srtpTransformer the {@code SinglePacketTransformer} to set on
     * {@code _srtpTransformer}
     */
    private synchronized void setSrtpTransformer(
            SinglePacketTransformer srtpTransformer)
    {
        if (_srtpTransformer != srtpTransformer)
        {
            _srtpTransformer = srtpTransformer;
            _srtpTransformerLastChanged = System.currentTimeMillis();
            // For the sake of completeness, we notify whenever we assign to
            // _srtpTransformer.
            notifyAll();
        }
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

        if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID)
        {
            // In the case of rtcp-mux, the RTCP transformer does not create
            // a DTLS session. The SRTP context (_srtpTransformer) will be
            // initialized on demand using initializeSRTCPTransformerFromRtp().
            return;
        }

        AbstractRTPConnector connector = this.connector;

        this.started = true;

        if (connector == null)
            throw new NullPointerException("connector");

        DtlsControl.Setup setup = getProperties().getSetup();
        final DTLSProtocol dtlsProtocolObj;
        final TlsPeer tlsPeer;

        if (DtlsControl.Setup.ACTIVE.equals(setup))
        {
            dtlsProtocolObj = new DTLSClientProtocol(new SecureRandom());
            tlsPeer = new TlsClientImpl(this);
        }
        else
        {
            dtlsProtocolObj = new DTLSServerProtocol(new SecureRandom());
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
        started = false;
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
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return transform(pkts, /* transform */ true);
    }

    /**
     * Processes {@code RawPacket}s to be sent to or received from (depending on
     * {@code transform}) the remote peer.
     *
     * @param inPkts the {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform} the remote peer
     * @param transform {@code true} of {@code inPkts} are to be sent to the
     * remote peer or {@code false} if {@code inPkts} have been received from
     * the remote peer
     * @return the {@code RawPacket}s which are the result of the processing
     */
    private RawPacket[] transform(RawPacket[] inPkts, boolean transform)
    {
        List<RawPacket> outPkts = new ArrayList<>();

        // DTLS and SRTP packets are distinct, separate (in DTLS-SRTP).
        // Additionally, the UDP transport does not guarantee the packet send
        // order. Consequently, it should be fine to process DTLS packets first.
        outPkts = transformDtls(inPkts, transform, outPkts);

        outPkts = transformNonDtls(inPkts, transform, outPkts);

        return outPkts.toArray(new RawPacket[outPkts.size()]);
    }

    /**
     * Processes DTLS {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer. The implementation
     * picks the elements of {@code inPkts} which look like DTLS records and
     * replaces them with {@code null}.
     *
     * @param inPkts the {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform} the remote peer among which there may (or
     * may not) be DTLS {@code RawPacket}s
     * @param transform {@code true} of {@code inPkts} are to be sent to the
     * remote peer or {@code false} if {@code inPkts} have been received from
     * the remote peer
     * @param outPkts the {@code List} of {@code RawPacket}s into which the
     * results of the processing of the DTLS {@code RawPacket}s are to be
     * written
     * @return the {@code List} of {@code RawPacket}s which are the result of
     * the processing including the elements of {@code outPkts}. Practically,
     * {@code outPkts} itself.
     */
    private List<RawPacket> transformDtls(
            RawPacket[] inPkts,
            boolean transform,
            List<RawPacket> outPkts)
    {
        if (inPkts != null)
        {
            for (int i = 0; i < inPkts.length; ++i)
            {
                RawPacket inPkt = inPkts[i];

                if (inPkt == null)
                    continue;

                byte[] buf = inPkt.getBuffer();
                int off = inPkt.getOffset();
                int len = inPkt.getLength();

                if (isDtlsRecord(buf, off, len))
                {
                    // In the outgoing/transform direction DTLS records pass
                    // through (e.g. DatagramTransportImpl has sent them).
                    if (transform)
                    {
                        outPkts.add(inPkt);
                    }
                    else
                    {
                        reverseTransformDtls(inPkt, outPkts);
                    }

                    // Whatever the outcome, inPkt has been consumed. The
                    // following is being done because there may be a subsequent
                    // iteration over inPkts later on.
                    inPkts[i] = null;
                }
            }
        }
        return outPkts;
    }

    /**
     * Processes non-DTLS {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer. The implementation
     * assumes that all elements of {@code inPkts} are non-DTLS
     * {@code RawPacket}s.
     *
     * @param inPkts the {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform} the remote peer
     * @param transform {@code true} of {@code inPkts} are to be sent to the
     * remote peer or {@code false} if {@code inPkts} have been received from
     * the remote peer
     * @param outPkts the {@code List} of {@code RawPacket}s into which the
     * results of the processing of {@code inPkts} are to be written
     * @return the {@code List} of {@code RawPacket}s which are the result of
     * the processing including the elements of {@code outPkts}. Practically,
     * {@code outPkts} itself.
     */
    private List<RawPacket> transformNonDtls(
            RawPacket[] inPkts,
            boolean transform,
            List<RawPacket> outPkts)
    {
        /* Pure/non-SRTP DTLS */ if (isSrtpDisabled())
        {
            // (1) In the incoming/reverseTransform direction, only DTLS records
            // pass through.
            // (2) In the outgoing/transform direction, the specified inPkts
            // will pass through this PacketTransformer only if they get
            // transformed into DTLS records.
            if (transform)
                outPkts = transformNonSrtp(inPkts, outPkts);
        }
        /* SRTP */ else
        {
            outPkts = transformSrtp(inPkts, transform, outPkts);
        }
        return outPkts;
    }

    /**
     * Processes non-SRTP {@code RawPacket}s to be sent to the remote peer. The
     * implementation assumes that all elements of {@code inPkts} are non-SRTP
     * {@code RawPacket}s.
     *
     * @param inPkts the {@code RawPacket}s to be sent to the remote peer
     * @param outPkts the {@code List} of {@code RawPacket}s into which the
     * results of the processing of {@code inPkts} are to be written
     * @return the {@code List} of {@code RawPacket}s which are the result of
     * the processing including the elements of {@code outPkts}. Practically,
     * {@code outPkts} itself. The implementation does not produce its own
     * {@code RawPacket}s though because it merely wraps {@code inPkts} into
     * DTLS application data.
     */
    private List<RawPacket> transformNonSrtp(
            RawPacket[] inPkts,
            List<RawPacket> outPkts)
    {
        if (inPkts != null)
        {
            for (RawPacket inPkt : inPkts)
            {
                if (inPkt == null)
                    continue;

                byte[] buf = inPkt.getBuffer();
                int off = inPkt.getOffset();
                int len = inPkt.getLength();

                sendApplicationData(buf, off, len);
            }
        }
        return outPkts;
    }

    /**
     * Processes SRTP {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer. The implementation
     * assumes that all elements of {@code inPkts} are SRTP {@code RawPacket}s.
     *
     * @param inPkts the SRTP {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer
     * @param transform {@code true} of {@code inPkts} are to be sent to the
     * remote peer or {@code false} if {@code inPkts} have been received from
     * the remote peer
     * @param outPkts the {@code List} of {@code RawPacket}s into which the
     * results of the processing of {@code inPkts} are to be written
     * @return the {@code List} of {@code RawPacket}s which are the result of
     * the processing including the elements of {@code outPkts}. Practically,
     * {@code outPkts} itself.
     */
    private List<RawPacket> transformSrtp(
            RawPacket[] inPkts,
            boolean transform,
            List<RawPacket> outPkts)
    {
        SinglePacketTransformer srtpTransformer = getSRTPTransformer();

        if (srtpTransformer == null)
        {
            // If unencrypted (SRTP) packets are to be dropped, they are dropped
            // by not being processed here.
            if (!DROP_UNENCRYPTED_PKTS)
            {
                queueTransformSrtp(inPkts, transform);
            }
        }
        else
        {
            // Process the (SRTP) packets provided to earlier (method)
            // invocations during which _srtpTransformer was unavailable.
            LinkedList<RawPacket> q
                = transform ? _transformSrtpQueue : _reverseTransformSrtpQueue;

            // XXX Don't obtain a lock if the queue is empty. If a thread was in
            // the process of adding packets to it, they will be handled in a
            // subsequent call. If the queue is empty, as it usually is, the
            // call to transformSrtp below is unnecessary, so we can avoid the
            // lock.
            if (q.size() > 0)
            {
                synchronized (q)
                {
                    // WARNING: this is a temporary workaround for an issue we
                    // have observed in which a DtlsPacketTransformer is shared
                    // between multiple MediaStream instances and the packet
                    // queue contains packets belonging to both. We try to
                    // recognize the packets belonging to each MediaStream by
                    // their RTP SSRC or RTP payload type, and pull only these
                    // packets into the output array. We use the input packet
                    // (or rather the first input packet) as a template, because
                    // it comes from the MediaStream which called us.
                    RawPacket template
                        = (inPkts != null && inPkts.length > 0)
                            ? inPkts[0]
                            : null;

                    try
                    {
                        outPkts
                            = transformSrtp(
                                    srtpTransformer,
                                    q,
                                    transform,
                                    outPkts,
                                    template);
                    }
                    finally
                    {
                        // If a RawPacket from q causes an exception, do not
                        // attempt to process it next time.
                        clearQueue(q, template);
                    }
                }
            }

            // Process the (SRTP) packets provided to the current (method)
            // invocation.
            if (inPkts != null && inPkts.length != 0)
            {
                outPkts
                    = transformSrtp(
                            srtpTransformer,
                            Arrays.asList(inPkts),
                            transform,
                            outPkts,
                            /* template */ null);
            }
        }
        return outPkts;
    }

    /**
     * Processes SRTP {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer. The implementation
     * assumes that all elements of {@code inPkts} are SRTP {@code RawPacket}s.
     *
     * @param srtpTransformer the {@code SinglePacketTransformer} to perform the
     * actual processing
     * @param inPkts the SRTP {@code RawPacket}s to be sent to or received from
     * (depending on {@code transform}) the remote peer
     * @param transform {@code true} of {@code inPkts} are to be sent to the
     * remote peer or {@code false} if {@code inPkts} have been received from
     * the remote peer
     * @param outPkts the {@code List} of {@code RawPacket}s into which the
     * results of the processing of {@code inPkts} are to be written
     * @param template A template to match input packets. Only input packets
     * matching this template (checked with {@link #match(RawPacket, RawPacket)})
     * will be processed. A null template matches all packets.
     * @return the {@code List} of {@code RawPacket}s which are the result of
     * the processing including the elements of {@code outPkts}. Practically,
     * {@code outPkts} itself.
     */
    private List<RawPacket> transformSrtp(
            SinglePacketTransformer srtpTransformer,
            Collection<RawPacket> inPkts,
            boolean transform,
            List<RawPacket> outPkts,
            RawPacket template)
    {
        for (RawPacket inPkt : inPkts)
        {
            if (inPkt != null && match(template, inPkt))
            {
                RawPacket outPkt
                    = transform
                        ? srtpTransformer.transform(inPkt)
                        : srtpTransformer.reverseTransform(inPkt);

                if (outPkt != null)
                    outPkts.add(outPkt);
            }
        }
        return outPkts;
    }

    /**
     * Removes from {@code q} all packets matching {@code template} (checked
     * with {@link #match(RawPacket, RawPacket)}. A null {@code template}
     * matches all packets.
     *
     * @param q the queue to remove packets from.
     * @param template the template
     */
    private void clearQueue(LinkedList<RawPacket> q, RawPacket template)
    {
        long srtpTransformerLastChanged = _srtpTransformerLastChanged;

        if (srtpTransformerLastChanged >= 0
                && System.currentTimeMillis() - srtpTransformerLastChanged
                    > 3000)
        {
            // The purpose of these queues is to queue packets while DTLS is in
            // the process of establishing a connection. If some of the packets
            // were not "read" 3 seconds after DTLS finished, they can safely be
            // dropped, and we do so to avoid looping through the queue on every
            // subsequent packet.
            q.clear();
            return;
        }

        for (Iterator<RawPacket> it = q.iterator(); it.hasNext();)
        {
            if (match(template, it.next()))
                it.remove();
        }
    }

    /**
     * Checks whether {@code pkt} matches the template {@code template}. A
     * {@code null} template matches all packets, while a {@code null} packet
     * will only be matched by a {@code null} template. Two non-{@code null}
     * packets match if they are both RTP or both RTCP and they have the same
     * SSRC or the same RTP Payload Type. The goal is for a template packet from
     * one {@code MediaStream} to match the packets for that stream, and only
     * these packets.
     *
     * @param template the template.
     * @param pkt the packet.
     * @return {@code true} if {@code template} matches {@code pkt} (i.e. they
     * have the same SSRC or RTP Payload Type).
     */
    private boolean match(RawPacket template, RawPacket pkt)
    {
        if (template == null)
            return true;
        if (pkt == null)
            return false;

        if (RTPPacketPredicate.INSTANCE.test(template))
        {
            return
                template.getSSRC() == pkt.getSSRC()
                    || template.getPayloadType() == pkt.getPayloadType();
        }
        else if (RTCPPacketPredicate.INSTANCE.test(template))
        {
            return template.getRTCPSSRC() == pkt.getRTCPSSRC();
        }

        return true;
    }

    /* Copied from TlsContext#exportKeyingMaterial and modified to work with
     * an externally provided masterSecret value.
     */
    private static byte[] exportKeyingMaterial(TlsContext context, String asciiLabel, byte[] context_value, int length, byte[] masterSecret )
    {
        if (context_value != null && !TlsUtils.isValidUint16(context_value.length))
        {
            throw new IllegalArgumentException("'context_value' must have length less than 2^16 (or be null)");
        }

        SecurityParameters sp = context.getSecurityParameters();
        byte[] cr = sp.getClientRandom(), sr = sp.getServerRandom();

        int seedLength = cr.length + sr.length;
        if (context_value != null)
        {
            seedLength += (2 + context_value.length);
        }

        byte[] seed = new byte[seedLength];
        int seedPos = 0;

        System.arraycopy(cr, 0, seed, seedPos, cr.length);
        seedPos += cr.length;
        System.arraycopy(sr, 0, seed, seedPos, sr.length);
        seedPos += sr.length;
        if (context_value != null)
        {
            TlsUtils.writeUint16(context_value.length, seed, seedPos);
            seedPos += 2;
            System.arraycopy(context_value, 0, seed, seedPos, context_value.length);
            seedPos += context_value.length;
        }

        if (seedPos != seedLength)
        {
            throw new IllegalStateException("error in calculation of seed for export");
        }

        return TlsUtils.PRF(context, masterSecret, asciiLabel, seed, length);
    }
}
