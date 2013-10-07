/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.sdes;

import gnu.java.zrtp.utils.*;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;

import ch.imvs.sdes4j.srtp.*;

/**
 * Default implementation of {@link SDesControl} that supports the crypto suites
 * of the original RFC4568 and the KDR parameter, but nothing else.
 *
 * @author Ingo Bauersachs
 */
public class SDesControlImpl
    extends AbstractSrtpControl<SDesTransformEngine>
    implements SDesControl
{
    /**
     * List of enabled crypto suites.
     */
    private final List<String> enabledCryptoSuites = new ArrayList<String>(3);

    /**
     * List of supported crypto suites.
     */
    private final List<String> supportedCryptoSuites = new ArrayList<String>(3);

    private SrtpCryptoAttribute[] attributes;

    private SrtpSDesFactory sdesFactory;
    private SrtpCryptoAttribute selectedInAttribute;
    private SrtpCryptoAttribute selectedOutAttribute;

    /**
     * SDESControl
     */
    public SDesControlImpl()
    {
        super(SrtpControlType.SDES);

        {
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80);
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32);
            enabledCryptoSuites.add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80);
        }
        {
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80);
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32);
            supportedCryptoSuites.add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80);
        }

        sdesFactory = new SrtpSDesFactory();
        sdesFactory.setRandomGenerator(
                new Random()
                {
                    private static final long serialVersionUID = 0L;

                    @Override
                    public void nextBytes(byte[] bytes)
                    {
                        ZrtpFortuna.getInstance().getFortuna().nextBytes(bytes);
                    }
                });
    }

    public SrtpCryptoAttribute getInAttribute()
    {
        return selectedInAttribute;
    }

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    public SrtpCryptoAttribute[] getInitiatorCryptoAttributes()
    {
        initAttributes();

        return attributes;
    }

    public SrtpCryptoAttribute getOutAttribute()
    {
        return selectedOutAttribute;
    }

    public boolean getSecureCommunicationStatus()
    {
        return transformEngine != null;
    }

    public Iterable<String> getSupportedCryptoSuites()
    {
        return Collections.unmodifiableList(supportedCryptoSuites);
    }

    /**
     * Initializes a new <tt>SDesTransformEngine</tt> instance to be associated
     * with and used by this <tt>SDesControlImpl</tt> instance.
     *
     * @return a new <tt>SDesTransformEngine</tt> instance to be associated with
     * and used by this <tt>SDesControlImpl</tt> instance
     * @see AbstractSrtpControl#createTransformEngine()
     */
    protected SDesTransformEngine createTransformEngine()
    {
        return
            new SDesTransformEngine(selectedInAttribute, selectedOutAttribute);
    }

    /**
     * Initializes the available SRTP crypto attributes containing: he
     * crypto-suite, the key-param and the session-param.
     */
    private void initAttributes()
    {
        if(attributes == null)
        {
            attributes = new SrtpCryptoAttribute[enabledCryptoSuites.size()];
            for (int i = 0; i < attributes.length; i++)
            {
                attributes[i]
                    = sdesFactory.createCryptoAttribute(
                            i + 1,
                            enabledCryptoSuites.get(i));
            }
        }
    }

    /**
     * Select the local crypto attribute from the initial offering (@see
     * {@link #getInitiatorCryptoAttributes()}) based on the peer's first
     * matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     * @return A SrtpCryptoAttribute when a matching cipher suite was found;
     * <tt>null</tt>, otherwise.
     */
    public SrtpCryptoAttribute initiatorSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes)
    {
        for (SrtpCryptoAttribute peerCA : peerAttributes)
        {
            for (SrtpCryptoAttribute localCA : attributes)
            {
                if (localCA.getCryptoSuite().equals(peerCA.getCryptoSuite()))
                {
                    selectedInAttribute = peerCA;
                    selectedOutAttribute = localCA;
                    return peerCA;
                }
            }
        }
        return null;
    }

    /**
     * Returns <tt>true</tt>, SDES always requires the secure transport of its
     * keys.
     *
     * @return <tt>true</tt>
     */
    public boolean requiresSecureSignalingTransport()
    {
        return true;
    }

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and creates the local crypto attribute. Used when the control
     * is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     * @return The local crypto attribute for the answer of the offer or
     * <tt>null</tt> if no matching cipher suite could be found.
     */
    public SrtpCryptoAttribute responderSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes)
    {
        for (SrtpCryptoAttribute ea : peerAttributes)
        {
            for (String suite : enabledCryptoSuites)
            {
                if (suite.equals(ea.getCryptoSuite().encode()))
                {
                    selectedInAttribute = ea;
                    selectedOutAttribute
                        = sdesFactory.createCryptoAttribute(1, suite);
                    return selectedOutAttribute;
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>SDesControlImpl</tt> does nothing because
     * <tt>SDesControlImpl</tt> does not utilize the <tt>RTPConnector</tt>.
     */
    public void setConnector(AbstractRTPConnector connector)
    {
    }

    public void setEnabledCiphers(Iterable<String> ciphers)
    {
        enabledCryptoSuites.clear();
        for(String c : ciphers)
            enabledCryptoSuites.add(c);
    }

    public void start(MediaType mediaType)
    {
        SrtpListener srtpListener = getSrtpListener();
        // in srtp the started and security event is one after another in some
        // other security mechanisms (e.g. zrtp) there can be started and no
        // security one or security timeout event

        srtpListener.securityNegotiationStarted(mediaType, this);
        srtpListener.securityTurnedOn(
                mediaType,
                selectedInAttribute.getCryptoSuite().encode(),
                this);
    }
}
