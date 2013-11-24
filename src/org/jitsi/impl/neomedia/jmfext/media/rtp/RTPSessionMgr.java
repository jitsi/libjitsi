/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.rtp;

import net.sf.fmj.media.rtp.*;

import org.jitsi.service.neomedia.*;

/**
 * Implements {@link javax.media.rtp.RTPManager} for the purposes of the
 * libjitsi library in general and the neomedia package in particular.
 * <p>
 * Allows <tt>MediaStream</tt> to optionally utilize {@link SSRCFactory}.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class RTPSessionMgr
    extends net.sf.fmj.media.rtp.RTPSessionMgr
{
    /**
     * The <tt>SSRCFactory</tt> to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers. If <tt>null</tt>, this
     * instance will employ internal logic to generate new synchronization
     * source (SSRC) identifiers.
     */
    private SSRCFactory ssrcFactory;

    /**
     * Initializes a new <tt>RTPSessionMgr</tt> instance.
     */
    public RTPSessionMgr()
    {
    }

    /**
     * Gets the <tt>SSRCFactory</tt> utilized by this instance to generate new
     * synchronization source (SSRC) identifiers.
     *
     * @return the <tt>SSRCFactory</tt> utilized by this instance or
     * <tt>null</tt> if this instance employs internal logic to generate new
     * synchronization source (SSRC) identifiers
     */
    public SSRCFactory getSSRCFactory()
    {
        return ssrcFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long generateSSRC(GenerateSSRCCause cause)
    {
        SSRCFactory ssrcFactory = getSSRCFactory();

        return
            (ssrcFactory == null)
                ? super.generateSSRC(cause)
                : ssrcFactory.generateSSRC(
                        (cause == null) ? null : cause.name());
    }

    /**
     * Sets the <tt>SSRCFactory</tt> to be utilized by this instance to generate
     * new synchronization source (SSRC) identifiers.
     *
     * @param ssrcFactory the <tt>SSRCFactory</tt> to be utilized by this
     * instance to generate new synchronization source (SSRC) identifiers or
     * <tt>null</tt> if this instance is to employ internal logic to generate
     * new synchronization source (SSRC) identifiers
     */
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        this.ssrcFactory = ssrcFactory;
    }
}
