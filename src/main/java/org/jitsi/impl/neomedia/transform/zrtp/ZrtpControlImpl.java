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
package org.jitsi.impl.neomedia.transform.zrtp;

import gnu.java.zrtp.*;
import gnu.java.zrtp.utils.*;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;

/**
 * Controls zrtp in the MediaStream.
 *
 * @author Damian Minkov
 */
public class ZrtpControlImpl
    extends AbstractSrtpControl<ZRTPTransformEngine>
    implements ZrtpControl
{
    /**
     * Additional info codes for and data to support ZRTP4J.
     * These could be added to the library. However they are specific for this
     * implementation, needing them for various GUI changes.
     */
    public static enum ZRTPCustomInfoCodes
    {
        ZRTPDisabledByCallEnd,
        ZRTPEnabledByDefault,
        ZRTPEngineInitFailure,
        ZRTPNotEnabledByUser
    }

    /**
     * Whether current is master session.
     */
    private boolean masterSession = false;

    /**
     * This is the connector, required to send ZRTP packets
     * via the DatagramSocket.
     */
    private AbstractRTPConnector zrtpConnector = null;

    /**
     * Creates the control.
     */
    public ZrtpControlImpl()
    {
        super(SrtpControlType.ZRTP);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doCleanup()
    {
        super.doCleanup();

        zrtpConnector = null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.java.sip.communicator.service.neomedia.ZrtpControl#getCiperString()
     */
    public String getCipherString()
    {
        return getTransformEngine().getUserCallback().getCipherString();
    }

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    public int getCurrentProtocolVersion()
    {
        ZRTPTransformEngine zrtpEngine = this.transformEngine;

        return
            (zrtpEngine != null) ? zrtpEngine.getCurrentProtocolVersion() : 0;
    }

    /**
     * Return the zrtp hello hash String.
     *
     * @param  index
     *         Hello hash of the Hello packet identfied by index. Index must
     *         be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String the zrtp hello hash.
     */
    public String getHelloHash(int index)
    {
        return getTransformEngine().getHelloHash(index);
    }

    /**
     * Get the ZRTP Hello Hash data - separate strings.
     *
     * @param  index
     *         Hello hash of the Hello packet identfied by index. Index must
     *         be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String array containing the version string at offset 0, the Hello
     *         hash value as hex-digits at offset 1. Hello hash is available
     *         immediately after class instantiation. Returns <code>null</code>
     *         if ZRTP is not available.
     */
    public String[] getHelloHashSep(int index)
    {
        return getTransformEngine().getHelloHashSep(index);
    }

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    public int getNumberSupportedVersions()
    {
        ZRTPTransformEngine zrtpEngine = this.transformEngine;

        return
            (zrtpEngine != null) ? zrtpEngine.getNumberSupportedVersions() : 0;
    }

    /**
     * Get the peer's Hello Hash data.
     *
     * Use this method to get the peer's Hello Hash data. The method returns the
     * data as a string.
     *
     * @return a String containing the Hello hash value as hex-digits.
     *         Peer Hello hash is available after we received a Hello packet
     *         from our peer. If peer's hello hash is not available return null.
     */
    public String getPeerHelloHash() {
        ZRTPTransformEngine zrtpEngine = this.transformEngine;

        return (zrtpEngine != null) ? zrtpEngine.getPeerHelloHash() : "";
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZid
     * ()
     */
    public byte[] getPeerZid()
    {
        return getTransformEngine().getPeerZid();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZidString()
     */
    public String getPeerZidString()
    {
        byte[] zid = getPeerZid();
        String s = new String(ZrtpUtils.bytesToHexString(zid, zid.length));
        return s;
    }

    /**
     * Method for getting the default secure status value for communication
     *
     * @return the default enabled/disabled status value for secure
     * communication
     */
    public boolean getSecureCommunicationStatus()
    {
        ZRTPTransformEngine zrtpEngine = this.transformEngine;

        return
            (zrtpEngine != null) && zrtpEngine.getSecureCommunicationStatus();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.java.sip.communicator.service.neomedia.ZrtpControl#getSecurityString
     * ()
     */
    public String getSecurityString()
    {
        return getTransformEngine().getUserCallback().getSecurityString();
    }

    /**
     * Returns the timeout value that will we will wait
     * and fire timeout secure event if call is not secured.
     * The value is in milliseconds.
     * @return the timeout value that will we will wait
     *  and fire timeout secure event if call is not secured.
     */
    public long getTimeoutValue()
    {
        // this is the default value as mentioned in rfc6189
        // we will later grab this setting from zrtp
        return 3750;
    }

    /**
     * Initializes a new <tt>ZRTPTransformEngine</tt> instance to be associated
     * with and used by this <tt>ZrtpControlImpl</tt> instance.
     *
     * @return a new <tt>ZRTPTransformEngine</tt> instance to be associated with
     * and used by this <tt>ZrtpControlImpl</tt> instance
     */
    protected ZRTPTransformEngine createTransformEngine()
    {
        ZRTPTransformEngine transformEngine = new ZRTPTransformEngine();

        // NOTE: set paranoid mode before initializing
        // zrtpEngine.setParanoidMode(paranoidMode);
        transformEngine.initialize(
                "GNUZRTP4J.zid",
                false,
                ZrtpConfigureUtils.getZrtpConfiguration());
        transformEngine.setUserCallback(new SecurityEventManager(this));

        return transformEngine;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * net.java.sip.communicator.service.neomedia.ZrtpControl#isSecurityVerified
     * ()
     */
    public boolean isSecurityVerified()
    {
        return getTransformEngine().getUserCallback().isSecurityVerified();
    }

    /**
     * Returns {@code false}, ZRTP exchanges its keys over the media path.
     *
     * @return {@code false}
     */
    public boolean requiresSecureSignalingTransport()
    {
        return false;
    }

    /**
     * Sets the <tt>RTPConnector</tt> which is to use or uses this ZRTP engine.
     *
     * @param connector the <tt>RTPConnector</tt> which is to use or uses this
     * ZRTP engine
     */
    public void setConnector(AbstractRTPConnector connector)
    {
        zrtpConnector = connector;
    }

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session
     */
    @Override
    public void setMasterSession(boolean masterSession)
    {
        // by default its not master, change only if set to be master
        // sometimes (jingle) streams are re-initing and
        // we must reuse old value (true) event that false is submitted
        if(masterSession)
            this.masterSession = masterSession;
    }

    /**
     * Start multi-stream ZRTP sessions. After the ZRTP Master (DH) session
     * reached secure state the SCCallback calls this method to start the
     * multi-stream ZRTP sessions. Enable auto-start mode (auto-sensing) to the
     * engine.
     *
     * @param master master SRTP data
     */
    @Override
    public void setMultistream(SrtpControl master)
    {
        if(master == null || master == this)
            return;

        if(!(master instanceof ZrtpControlImpl))
            throw new IllegalArgumentException("master is no ZRTP control");

        ZrtpControlImpl zm = (ZrtpControlImpl)master;
        ZRTPTransformEngine engine = getTransformEngine();

        engine.setMultiStrParams(zm.getTransformEngine().getMultiStrParams());
        engine.setEnableZrtp(true);
        engine.getUserCallback().setMasterEventManager(
                zm.getTransformEngine().getUserCallback());
    }

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    public void setSASVerification(boolean verified)
    {
        ZRTPTransformEngine engine = getTransformEngine();

        if (verified)
            engine.SASVerified();
        else
            engine.resetSASVerified();
    }

    /**
     * Starts and enables zrtp in the stream holding this control.
     * @param mediaType the media type of the stream this control controls.
     */
    public void start(MediaType mediaType)
    {
        boolean zrtpAutoStart;

        // ZRTP engine initialization
        ZRTPTransformEngine engine = getTransformEngine();
        // Create security user callback for each peer.
        SecurityEventManager securityEventManager = engine.getUserCallback();

        // Decide if this will become the ZRTP Master session:
        // - Statement: audio media session will be started before video
        //   media session
        // - if no other audio session was started before then this will
        //   become
        //   ZRTP Master session
        // - only the ZRTP master sessions start in "auto-sensing" mode
        //   to immediately catch ZRTP communication from other client
        // - after the master session has completed its key negotiation
        //   it will start other media sessions (see SCCallback)
        if (masterSession)
        {
            zrtpAutoStart = true;

            // we know that audio is considered as master for zrtp
            securityEventManager.setSessionType(mediaType);
        }
        else
        {
            // check whether video was not already started
            // it may happen when using multistreams, audio has inited
            // and started video
            // initially engine has value enableZrtp = false
            zrtpAutoStart = transformEngine.isEnableZrtp();
            securityEventManager.setSessionType(mediaType);
        }
        engine.setConnector(zrtpConnector);

        securityEventManager.setSrtpListener(getSrtpListener());

        // tells the engine whether to autostart(enable)
        // zrtp communication, if false it just passes packets without
        // transformation
        engine.setEnableZrtp(zrtpAutoStart);
        engine.sendInfo(
                ZrtpCodes.MessageSeverity.Info,
                (EnumSet<?>) EnumSet.of(ZRTPCustomInfoCodes.ZRTPEnabledByDefault));
    }
}
