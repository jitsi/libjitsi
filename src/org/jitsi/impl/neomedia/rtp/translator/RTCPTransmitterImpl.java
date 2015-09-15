/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
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
        SSRCCache cache = getCache();

        // Iterate through the <tt>StreamRTPManager</tt>s of the
        // <tt>RTPTranslator</tt>.
        for (StreamRTPManager streamRTPManager
            : translator.getStreamRTPManagers())
        {
            MediaStream mediaStream = streamRTPManager.getMediaStream();

            // Get the RTCP termination strategy of the <tt>MediaStream</tt>.
            RTCPTerminationStrategy rtcpTerminationStrategy
                = mediaStream.getRTCPTerminationStrategy();

            if (rtcpTerminationStrategy == null)
            {
                // The <tt>MediaStream</tt> doesn't have an
                // <tt>RTCPTerminationStrategy</tt>. Move to the next
                // <tt>MediaStream</tt>.
                continue;
            }

            // Make the RTCP reports for the current <tt>MediaStream</tt>.
            RawPacket rawPacket = rtcpTerminationStrategy.report();

            if (rawPacket == null)
            {
                // The <tt>RTCPTerminationStrategy</tt> of the
                // <tt>MediaStream</tt> did not generate anything. Move to the
                // next <tt>MediaStream</tt>.
                continue;
            }

            // Transmit whatever the RTCP termination produced.

            try
            {
                mediaStream.injectPacket(rawPacket, false, true);

                if (ssrcInfo instanceof SendSSRCInfo)
                {
                    ((SendSSRCInfo) ssrcInfo).stats.total_rtcp++;
                    cache.sm.transstats.rtcp_sent++;
                }
                cache.updateavgrtcpsize(rawPacket.getLength());
                if (cache.initial)
                    cache.initial = false;
                if (!cache.rtcpsent)
                    cache.rtcpsent = true;
            }
            catch (TransmissionFailedException e)
            {
                cache.sm.defaultstats.update(OverallStats.TRANSMITFAILED, 1);
                cache.sm.transstats.transmit_failed++;
            }
        }
    }
}
