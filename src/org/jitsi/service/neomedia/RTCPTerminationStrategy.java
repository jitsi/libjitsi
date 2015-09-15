/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;

/**
 *
 * The purpose of an <tt>RTCPTerminationStrategy</tt> its purpose is to
 * terminate the RTCP traffic for a <tt>MediaStream</tt>.
 *
 * It extends a <tt>TransformEngine</tt> giving it full access to both the RTP
 * traffic for statistics extraction and the RTCP traffic for modification.
 *
 * @author George Politis
 */
public interface RTCPTerminationStrategy
    extends TransformEngine
{
    /**
     * Runs in the reporting thread and it generates RTCP reports for the
     * associated <tt>MediaStream</tt>.
     *
     * @return the <tt>RawPacket</tt> representing the RTCP compound packet to
     * inject to the <tt>MediaStream</tt>.
     */
    RawPacket report();
}
