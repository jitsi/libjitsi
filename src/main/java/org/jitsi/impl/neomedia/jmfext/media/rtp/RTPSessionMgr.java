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
