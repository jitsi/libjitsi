/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.control;

import javax.media.*;

/**
 * An interface used to communicate with a decoder that supports decoding FEC
 *
 * @author Boris Grozev
 */
public interface FECDecoderControl extends Control
{
    /**
     * Returns the number of packets for which FEC was decoded
     *
     * @return the number of packets for which FEC was decoded
     */
    public int fecPacketsDecoded();
}
