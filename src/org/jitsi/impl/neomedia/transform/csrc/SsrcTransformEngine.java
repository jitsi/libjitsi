/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.csrc;

import java.util.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class SsrcTransformEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    private MediaDirection ssrcAudioLevelDirection = MediaDirection.INACTIVE;

    private byte ssrcAudioLevelExtID = -1;

    public SsrcTransformEngine(MediaStreamImpl mediaStream)
    {
        /*
         * Take into account that RTPExtension.CSRC_AUDIO_LEVEL_URN may have
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
     * <tt>CsrcTransformEngine</tt>.
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
     * @param pkt the RTP <tt>RawPacket</tt> that we are to extract a CSRC list
     * from.
     *
     * @return the same <tt>RawPacket</tt> that was received as a parameter
     * since we don't need to worry about hiding the CSRC list from the rest
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
                /*
                 * XXX We do not want to waste processing power such as
                 * decrypting, decoding, and audio mixing.
                 */
                pkt = null;
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
