/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.csrc;

import java.util.*;

import javax.media.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements read-only support for &quot;A Real-Time Transport Protocol (RTP)
 * Header Extension for Client-to-Mixer Audio Level Indication&quot;.
 * Optionally, drops RTP packets indicated to be generated from a muted audio
 * source in order to avoid wasting processing power such as decrypting,
 * decoding and audio mixing.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class SsrcTransformEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * whether <tt>SsrcTransformEngine</tt> is to drop RTP packets indicated as
     * generated from a muted audio source in
     * {@link #reverseTransform(RawPacket)}.
     */
    public static final String DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM
        = SsrcTransformEngine.class.getName()
            + ".dropMutedAudioSourceInReverseTransform";

    /**
     * The indicator which determines whether this <tt>SsrcTransformEngine</tt>
     * is to drop RTP packets indicated as generated from a muted audio source
     * in {@link #reverseTransform(RawPacket)}.
     */
    private final boolean dropMutedAudioSourceInReverseTransform;

    /**
     * The <tt>MediaDirection</tt> in which this RTP header extension is active.
     */
    private MediaDirection ssrcAudioLevelDirection = MediaDirection.INACTIVE;

    /**
     * The negotiated ID of this RTP header extension.
     */
    private byte ssrcAudioLevelExtID = -1;

    /**
     * Initializes a new <tt>SsrcTransformEngine</tt> to be utilized by a
     * specific <tt>MediaStreamImpl</tt>.
     *
     * @param mediaStream the <tt>MediaStreamImpl</tt> to utilize the new
     * instance
     */
    public SsrcTransformEngine(MediaStreamImpl mediaStream)
    {
        /*
         * Take into account that RTPExtension.SSRC_AUDIO_LEVEL_URN may have
         * already been activated.
         */
        Map<Byte,RTPExtension> activeRTPExtensions
            = mediaStream.getActiveRTPExtensions();

        if ((activeRTPExtensions != null) && !activeRTPExtensions.isEmpty())
        {
            for (Map.Entry<Byte,RTPExtension> e
                    : activeRTPExtensions.entrySet())
            {
                RTPExtension rtpExtension = e.getValue();
                String uri = rtpExtension.getURI().toString();

                if (RTPExtension.SSRC_AUDIO_LEVEL_URN.equals(uri))
                {
                    Byte extID = e.getKey();

                    setSsrcAudioLevelExtensionID(
                            (extID == null) ? -1 : extID.byteValue(),
                            rtpExtension.getDirection());
                }
            }
        }

        /*
         * Should we simply drop RTP packets from muted audio sources? It turns
         * out that we cannot do that because SRTP at the receiver will
         * eventually fail to guess the rollover counter (ROC) of the sender.
         * That is why we will never drop RTP packets from muted audio sources
         * here and we will rather raise Buffer.FLAG_SILENCE and have SRTP drop
         * RTP packets from muted audio sources.
         */
        dropMutedAudioSourceInReverseTransform = false;
    }

    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    public void close()
    {
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     */
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Returns a reference to this class since it is performing RTP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>SsrcTransformEngine</tt>.
     */
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Extracts the list of CSRC identifiers and passes it to the
     * <tt>MediaStream</tt> associated with this engine. Other than that the
     * method does not do any transformations since CSRC lists are part of
     * RFC 3550 and they shouldn't be disrupting the rest of the application.
     *
     * @param pkt the RTP <tt>RawPacket</tt> that we are to extract a SSRC list
     * from.
     *
     * @return the same <tt>RawPacket</tt> that was received as a parameter
     * since we don't need to worry about hiding the SSRC list from the rest
     * of the RTP stack.
     */
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if ((ssrcAudioLevelExtID > 0)
                && ssrcAudioLevelDirection.allowsReceiving()
                && !pkt.isInvalid()
                && (RTPHeader.VERSION == ((pkt.readByte(0) & 0xC0) >>> 6)))
        {
            byte level = pkt.extractSsrcAudioLevel(ssrcAudioLevelExtID);

            if (level == 127 /* a muted audio source */)
            {
                if (dropMutedAudioSourceInReverseTransform)
                    pkt = null;
                else
                    pkt.setFlags(Buffer.FLAG_SILENCE | pkt.getFlags());
            }
        }

        return pkt;
    }

    public void setSsrcAudioLevelExtensionID(byte extID, MediaDirection dir)
    {
        this.ssrcAudioLevelExtID = extID;
        this.ssrcAudioLevelDirection = dir;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the specified <tt>pkt</tt> as is without modifications because
     * <tt>SsrcTransformEngine</tt> supports only reading SSRC audio levels,
     * not writing them.
     */
    public RawPacket transform(RawPacket pkt)
    {
        return pkt;
    }
}
