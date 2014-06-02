/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import javax.media.rtp.*;

/**
 * Describes an <tt>RTPConnector</tt> associated with an endpoint from and to
 * which an <tt>RTPTranslatorImpl</tt> is translating.
 *
 * @author Lyubomir Marinov
 */
class RTPConnectorDesc
{
    /**
     * The <tt>RTPConnector</tt> associated with an endpoint from and to which
     * an <tt>RTPTranslatorImpl</tt> is translating.
     */
    public final RTPConnector connector;

    public final StreamRTPManagerDesc streamRTPManagerDesc;

    public RTPConnectorDesc(
            StreamRTPManagerDesc streamRTPManagerDesc,
            RTPConnector connector)
    {
        this.streamRTPManagerDesc = streamRTPManagerDesc;
        this.connector = connector;
    }
}
