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
