/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.dtls;

import java.io.*;

import org.bouncycastle.crypto.tls.*;
import org.jitsi.util.*;

/**
 * Implements {@link TlsServer} for the purposes of supporting DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class TlsServerImpl
    extends DefaultTlsServer
{
    /**
     * The <tt>Logger</tt> used by the <tt>TlsServerImpl</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(TlsServerImpl.class);

    private final CertificateRequest certificateRequest
        = new CertificateRequest(
                new short[] { ClientCertificateType.rsa_sign },
                /* certificateAuthorities */ null);

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    private TlsSignerCredentials rsaSignerCredentials;

    /**
     * Initializes a new <tt>TlsServerImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is
     * initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer)
    {
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateRequest getCertificateRequest()
    {
        return certificateRequest;
    }

    /**
     * Gets the <tt>DtlsControl</tt> implementation associated with this
     * instance.
     *
     * @return the <tt>DtlsControl</tt> implementation associated with this
     * instance
     */
    private DtlsControlImpl getDtlsControl()
    {
        return packetTransformer.getDtlsControl();
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>TlsServerImpl</tt> always returns
     * <tt>ProtocolVersion.DTLSv10</tt> because <tt>ProtocolVersion.DTLSv12</tt>
     * does not work with the Bouncy Castle Crypto APIs at the time of this
     * writing.
     */
    @Override
    protected ProtocolVersion getMaximumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ProtocolVersion getMinimumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TlsSignerCredentials getRSASignerCredentials()
        throws IOException
    {
        if (rsaSignerCredentials == null)
        {
            DtlsControlImpl dtlsControl = getDtlsControl();

            rsaSignerCredentials
                = new DefaultTlsSignerCredentials(
                        context,
                        dtlsControl.getCertificate(),
                        dtlsControl.getKeyPair().getPrivate());
        }
        return rsaSignerCredentials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyClientCertificate(Certificate clientCertificate)
        throws IOException
    {
        try
        {
            getDtlsControl().verifyAndValidateCertificate(clientCertificate);
        }
        catch (Exception e)
        {
            logger.error(
                    "Failed to verify and/or validate client certificate!",
                    e);
            if (e instanceof IOException)
                throw (IOException) e;
            else
                throw new IOException(e);
        }
    }
}
