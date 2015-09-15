/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import javax.media.rtp.*;

/**
 * A <tt>Payload</tt> type that can be written to an <tt>OutputDataStream</tt>.
 *
 * @author George Politis
 */
public interface Payload
{
    public void writeTo(OutputDataStream stream);
}
