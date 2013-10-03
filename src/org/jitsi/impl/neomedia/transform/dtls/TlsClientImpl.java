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
 * Implements {@link TlsClient} for the purposes of supporting DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 */
public class TlsClientImpl
    extends DefaultTlsClient
{
    /**
     * The <tt>Logger</tt> used by the <tt>TlsClientImpl</tt> class and its
     * instances to print debug information.
     */
    private static final Logger logger = Logger.getLogger(TlsClientImpl.class);

    private final TlsAuthentication authentication
        = new TlsAuthenticationImpl();

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    /**
     * Initializes a new <tt>TlsClientImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is
     * initializing the new instance
     */
    public TlsClientImpl(DtlsPacketTransformer packetTransformer)
    {
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized TlsAuthentication getAuthentication()
        throws IOException
    {
        return authentication;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>TlsClientImpl</tt> always returns
     * <tt>ProtocolVersion.DTLSv10</tt> because <tt>ProtocolVersion.DTLSv12</tt>
     * does not work with the Bouncy Castle Crypto APIs at the time of this
     * writing.
     */
    @Override
    public ProtocolVersion getClientVersion()
    {
        return ProtocolVersion.DTLSv10;
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
     */
    @Override
    public ProtocolVersion getMinimumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * Implements {@link TlsAuthentication} for the purposes of supporting
     * DTLS-SRTP.
     *
     * @author Lyubomir Marinov
     */
    private class TlsAuthenticationImpl
        implements TlsAuthentication
    {
        private TlsCredentials clientCredentials;

        /**
         * {@inheritDoc}
         */
        public TlsCredentials getClientCredentials(
                CertificateRequest certificateRequest)
            throws IOException
        {
            if (clientCredentials == null)
            {
                DtlsControlImpl dtlsControl = getDtlsControl();

                clientCredentials
                    = new DefaultTlsSignerCredentials(
                            context,
                            dtlsControl.getCertificate(),
                            dtlsControl.getKeyPair().getPrivate());
            }
            return clientCredentials;
        }

        /**
         * {@inheritDoc}
         */
        public void notifyServerCertificate(Certificate serverCertificate)
            throws IOException
        {
            try
            {
                getDtlsControl().verifyAndValidateCertificate(
                        serverCertificate);
            }
            catch (Exception e)
            {
                logger.error(
                        "Failed to verify and/or validate server certificate!",
                        e);
                if (e instanceof IOException)
                    throw (IOException) e;
                else
                    throw new IOException(e);
            }
        }
    }
}
