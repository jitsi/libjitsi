/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public class RTCPTransmitterFactoryImpl
    implements RTCPTransmitterFactory
{
    private final RTPTranslator translator;

    public RTCPTransmitterFactoryImpl(RTPTranslator translator)
    {
        this.translator = translator;
    }

    public RTCPTransmitter newRTCPTransmitter(SSRCCache cache, RTCPRawSender rtcpRawSender)
    {
        return new RTCPTransmitterImpl(rtcpRawSender, translator);
    }
}
