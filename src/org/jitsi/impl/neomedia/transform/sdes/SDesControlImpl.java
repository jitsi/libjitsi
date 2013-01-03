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
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.zrtp.*;
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
    implements SDesControl
{
    /**
     * List of enabled crypto suites.
     */
    private List<String> enabledCryptoSuites = new ArrayList<String>(3)
    {
        private static final long serialVersionUID = 0L;

        {
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80);
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32);
            add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80);
        }
    };


    /**
     * List of supported crypto suites.
     */
    private final List<String> supportedCryptoSuites = new ArrayList<String>(3)
     {
        private static final long serialVersionUID = 0L;

        {
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80);
            add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32);
            add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80);
        }
     };

    private SrtpSDesFactory sdesFactory;
    private SrtpCryptoAttribute[] attributes;
    private SDesTransformEngine engine;
    private SrtpCryptoAttribute selectedInAttribute;
    private SrtpCryptoAttribute selectedOutAttribute;
    private SrtpListener srtpListener;

    /**
     * SDESControl
     */
    public SDesControlImpl()
    {
        sdesFactory = new SrtpSDesFactory();
        Random r = new Random()
        {
            private static final long serialVersionUID = 0L;

            @Override
            public void nextBytes(byte[] bytes)
            {
                ZrtpFortuna.getInstance().getFortuna().nextBytes(bytes);
            }
        };
        sdesFactory.setRandomGenerator(r);
    }

    public void setEnabledCiphers(Iterable<String> ciphers)
    {
        enabledCryptoSuites.clear();
        for(String c : ciphers)
            enabledCryptoSuites.add(c);
    }

    public Iterable<String> getSupportedCryptoSuites()
    {
        return Collections.unmodifiableList(supportedCryptoSuites);
    }

    public void cleanup()
    {
        if (engine != null) 
        {
            engine.close();
            engine = null;
        }
    }

    public void setSrtpListener(SrtpListener srtpListener)
    {
        this.srtpListener = srtpListener;
    }

    public SrtpListener getSrtpListener()
    {
        return srtpListener;
    }

    public boolean getSecureCommunicationStatus()
    {
        return engine != null;
    }

    /**
     * Not used.
     * @param masterSession not used.
     */
    public void setMasterSession(boolean masterSession)
    {}

    public void start(MediaType type)
    {
        // in srtp the started and security event is one after another
        // in some other security mechanisms (e.g. zrtp) there can be started
        // and no security one or security timeout event
        srtpListener.securityNegotiationStarted(
            type.equals(MediaType.AUDIO) ?
                SecurityEventManager.AUDIO_SESSION
                : SecurityEventManager.VIDEO_SESSION,
                this);

        srtpListener.securityTurnedOn(
            type.equals(MediaType.AUDIO) ?
                SecurityEventManager.AUDIO_SESSION
                : SecurityEventManager.VIDEO_SESSION,
            selectedInAttribute.getCryptoSuite().encode(), this);
    }

    public void setMultistream(SrtpControl master)
    {
    }

    public TransformEngine getTransformEngine()
    {
        if(engine == null)
        {
            engine = new SDesTransformEngine(selectedInAttribute,
                    selectedOutAttribute);
        }
        return engine;
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
                attributes[i] = sdesFactory.createCryptoAttribute(
                        i + 1,
                        enabledCryptoSuites.get(i));
            }
        }
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

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and creates the local crypto attribute. Used when the control
     * is running in the role as responder.
     * 
     * @param peerAttributes The peer's crypto attribute offering.
     *
     * @return The local crypto attribute for the answer of the offer or null if
     *         no matching cipher suite could be found.
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
     * Select the local crypto attribute from the initial offering (@see
     * {@link #getInitiatorCryptoAttributes()}) based on the peer's first
     * matching cipher suite.
     * 
     * @param peerAttributes The peer's crypto offers.
     *
     * @return A SrtpCryptoAttribute when a matching cipher suite was found.
     * Null otherwise.
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

    public SrtpCryptoAttribute getInAttribute()
    {
        return selectedInAttribute;
    }

    public SrtpCryptoAttribute getOutAttribute()
    {
        return selectedOutAttribute;
    }

    public void setConnector(AbstractRTPConnector newValue)
    {
    }

    /**
     * Returns true, SDES always requires the secure transport of its keys.
     *
     * @return true
     */
    public boolean requiresSecureSignalingTransport()
    {
        return true;
    }
}
