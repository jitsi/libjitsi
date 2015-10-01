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
 * Is owned by an <tt>RTPTranslator</tt> and it takes care of RTCP generation of
 * all the <tt>MediaStream</tt>s associated to the owning
 * <tt>RTPTranslator</tt>.
 *
 * @author George Politis
 */
public class RTCPTransmitterImpl
    implements RTCPTransmitter
{
    /**
     * Our <tt>SSRCInfo</tt>. We use this ssrc in the media sender SSRC of
     * our RTCP RRs.
     */
    private SSRCInfo ssrcInfo;

    /**
     * The <tt>RTPTranslator</tt> associated to this <tt>RTCPTransmitter</tt>.
     */
    private final RTPTranslator translator;

    /**
     * This does absolutely nothing. It's only here to make FMJ happy. In a
     * future re-design of the <tt>RTCPTransmitter</tt> interface, this should
     * be nuked.
     */
    private final RTCPRawSender rtcpRawSender;

    /**
     *
     * @param translator
     * @param rtcpRawSender does nothing. It's here for compatibility with FMJ.
     */
    public RTCPTransmitterImpl(
        RTCPRawSender rtcpRawSender, RTPTranslator translator)
    {
        this.ssrcInfo = null;
        this.translator = translator;
        this.rtcpRawSender = rtcpRawSender;
    }

    /**
     * {@inheritDoc}
     */
    public void bye(String reason)
    {
        // XXX the bridge can't not have an SSRC (in other words, it needs to
        // have an SSRC). Furthermore, we don't have SSRC collision management.
        // Well, in fact, we have that in FMJ but we don't use it in the bridge.
        // So, we need to ignore calls from FMJ telling us to "bye" our SSRC. In
        // the future, we need to implement SSRC collision management and fix
        // TAG(cat4-local-ssrc-hurricane).

        // report(true, reason);
        // ssrcInfo.setOurs(false);
        // ssrcInfo = null;
    }

    /**
     * {@inheritDoc}
     */
    public void close()
    {
        // Nothing to do here.
    }

    /**
     * {@inheritDoc}
     */
    public void setSSRCInfo(SSRCInfo info)
    {
        ssrcInfo = info;
    }

    /**
     * {@inheritDoc}
     */
    public SSRCInfo getSSRCInfo()
    {
        return ssrcInfo;
    }

    /**
     * {@inheritDoc}
     */
    public SSRCCache getCache()
    {
        return translator.getSSRCCache();
    }

    /**
     * {@inheritDoc}
     */
    public RTCPRawSender getSender()
    {
        return rtcpRawSender;
    }

    /**
     * Runs in the reporting thread and it invokes the report() method of the
     * <tt>RTCPTerminationStrategy</tt> of all the <tt>MediaStream</tt>s of the
     * associated <tt>RTPTranslator</tt>.
     */
    public void report()
    {
    }
}
