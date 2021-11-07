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
package org.jitsi.impl.neomedia.transform.csrc;

import java.util.*;

import javax.media.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
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
    extends SinglePacketTransformerAdapter
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
     * The indicator which determines whether <tt>SsrcTransformEngine</tt> is to
     * drop RTP packets indicated as generated from a muted audio source in
     * {@link #reverseTransform(RawPacket)}.
     */
    private static boolean dropMutedAudioSourceInReverseTransform = false;

    /**
     * The indicator which determines whether the method
     * {@link #readConfigurationServicePropertiesOnce()} is to read the values
     * of certain <tt>ConfigurationService</tt> properties of concern to
     * <tt>SsrcTransformEngine</tt> once during the initialization of the first
     * instance.
     */
    private static boolean readConfigurationServicePropertiesOnce = true;

    /**
     * The dispatcher that is delivering audio levels to the media steam.
     */
    private final CsrcAudioLevelDispatcher csrcAudioLevelDispatcher;

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

        readConfigurationServicePropertiesOnce();

        // Audio levels are received in RTP audio streams only.
        if (mediaStream instanceof AudioMediaStreamImpl)
        {
            csrcAudioLevelDispatcher
                = new CsrcAudioLevelDispatcher(
                        (AudioMediaStreamImpl) mediaStream);
        }
        else
        {
            csrcAudioLevelDispatcher = null;
        }
    }

    /**
     * Closes this <tt>PacketTransformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    @Override
    public void close()
    {
        if (csrcAudioLevelDispatcher != null)
            csrcAudioLevelDispatcher.close();
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTCP transformations.
     */
    @Override
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
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Reads the values of certain <tt>ConfigurationService</tt> properties of
     * concern to <tt>SsrcTransformEngine</tt> once during the initialization of
     * the first instance.
     */
    private static synchronized void readConfigurationServicePropertiesOnce()
    {
        if (readConfigurationServicePropertiesOnce)
            readConfigurationServicePropertiesOnce = false;
        else
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            dropMutedAudioSourceInReverseTransform
                = cfg.getBoolean(
                        SsrcTransformEngine
                            .DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
                        dropMutedAudioSourceInReverseTransform);
        }
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
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        boolean dropPkt = false;

        if ((ssrcAudioLevelExtID > 0)
                && ssrcAudioLevelDirection.allowsReceiving()
                && !pkt.isInvalid()
                && RTPHeader.VERSION == pkt.getVersion())
        {
            byte level = pkt.extractSsrcAudioLevel(ssrcAudioLevelExtID);

            if (level == 127 /* a muted audio source */)
            {
                if (dropMutedAudioSourceInReverseTransform)
                {
                    dropPkt = true;
                }
                else
                {
                    pkt.setFlags(Buffer.FLAG_SILENCE | pkt.getFlags());
                }
            }

            /*
             * Notify the AudioMediaStream associated with this instance about
             * the received audio level.
             */
            if (!dropPkt && (csrcAudioLevelDispatcher != null) && (level >= 0))
            {
                long[] levels = new long[2];

                levels[0] = pkt.getSSRCAsLong();
                levels[1] = 127 - level;
                csrcAudioLevelDispatcher.addLevels(levels, pkt.getTimestamp());
            }
        }
        if (dropPkt)
        {
            pkt.setFlags(Buffer.FLAG_DISCARD | pkt.getFlags());
        }

        return pkt;
    }

    public void setSsrcAudioLevelExtensionID(byte extID, MediaDirection dir)
    {
        this.ssrcAudioLevelExtID = extID;
        this.ssrcAudioLevelDirection = dir;
    }
}
