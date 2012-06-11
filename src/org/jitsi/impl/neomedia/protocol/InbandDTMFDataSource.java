/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.protocol;

import org.jitsi.service.neomedia.*;

/**
 * All datasources that support inband DTMF functionalities implement
 * <tt>InbandDTMFDataSource</tt>.
 *
 * @author Vincent Lucas
 */
public interface InbandDTMFDataSource
{
    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone);
}
