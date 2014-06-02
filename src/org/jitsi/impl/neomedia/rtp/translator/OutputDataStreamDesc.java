/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import javax.media.rtp.*;

/**
 * Describes an <tt>OutputDataStream</tt> associated with an endpoint to which
 * an <tt>RTPTranslatorImpl</tt> is translating.
 *
 * @author Lyubomir Marinov
 */
class OutputDataStreamDesc
{
    /**
     * The endpoint <tt>RTPConnector</tt> which owns {@link #stream}. 
     */
    public final RTPConnectorDesc connectorDesc;

    /**
     * The <tt>OutputDataStream</tt> associated with an endpoint to which an
     * <tt>RTPTranslatorImpl</tt> is translating.
     */
    public final OutputDataStream stream;

    /**
     * Initializes a new <tt>OutputDataStreamDesc</tt> instance which is to
     * describe an endpoint <tt>OutputDataStream</tt> for an
     * <tt>RTPTranslatorImpl</tt>.
     *
     * @param connectorDesc the endpoint <tt>RTPConnector</tt> which own the
     * specified <tt>stream</tt>
     * @param stream the endpoint <tt>OutputDataStream</tt> to be described by
     * the new instance for an <tt>RTPTranslatorImpl</tt>
     */
    public OutputDataStreamDesc(
            RTPConnectorDesc connectorDesc,
            OutputDataStream stream)
    {
        this.connectorDesc = connectorDesc;
        this.stream = stream;
    }
}
