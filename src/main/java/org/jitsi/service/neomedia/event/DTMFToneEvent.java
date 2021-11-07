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
package org.jitsi.service.neomedia.event;

import java.util.*;

import org.jitsi.service.neomedia.*;

/**
 * This event represents starting or ending reception of a specific
 * <tt>DTMFRtpTone</tt>.
 *
 * @author Emil Ivov
 */
public class DTMFToneEvent
    extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The tone that this event is pertaining to.
     */
    private final DTMFRtpTone dtmfTone;

    /**
     * Creates an instance of this <tt>DTMFToneEvent</tt> with the specified
     * source stream and DTMF tone.
     *
     * @param source the <tt>AudioMediaSteam</tt> instance that received the
     * tone.
     * @param dtmfTone the tone that we (started/stopped) receiving.
     */
    public DTMFToneEvent(AudioMediaStream source, DTMFRtpTone dtmfTone)
    {
        super(source);

        this.dtmfTone = dtmfTone;
    }

    /**
     * Returns the <tt>DTMFTone</tt> instance that this event pertains to.
     *
     * @return the <tt>DTMFTone</tt> instance that this event pertains to.
     */
    public DTMFRtpTone getDtmfTone()
    {
        return dtmfTone;
    }
}
