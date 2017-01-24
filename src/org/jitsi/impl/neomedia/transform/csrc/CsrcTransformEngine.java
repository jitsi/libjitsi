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

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * We use this engine to add the list of CSRC identifiers in RTP packets that
 * we send to conference participants during calls where we are the mixer.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class CsrcTransformEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    /**
     * The direction that we are supposed to handle audio levels in.
     */
    private MediaDirection csrcAudioLevelDirection = MediaDirection.INACTIVE;

    /**
     * The number currently assigned to CSRC audio level extensions or
     * <tt>-1</tt> if no such ID has been set and audio level extensions should
     * not be transmitted.
     */
    private byte csrcAudioLevelExtID = -1;

    /**
     * The dispatcher that is delivering audio levels to the media steam.
     */
    private final CsrcAudioLevelDispatcher csrcAudioLevelDispatcher;

    /**
     * The buffer that we use to encode the csrc audio level extensions.
     */
    private byte[] extensionBuff = null;

    /**
     * Indicates the length that we are currently using in the
     * <tt>extensionBuff</tt> buffer.
     */
    private int extensionBuffLen = 0;

    /**
     * The <tt>MediaStreamImpl</tt> that this transform engine was created to
     * transform packets for.
     */
    private final MediaStreamImpl mediaStream;

    /**
    * The flag that determines whether the list of CSRC identifiers are to be
    * discarded in all packets. The CSRC count will be 0 as well. The default
    * value is <tt>false</tt>.
    */
   private static final boolean discardContributingSrcs;

   /**
    * The name of the <tt>ConfigurationService</tt> and/or <tt>System</tt>
    * property which indicates whether the list of CSRC identifiers are to
    * be discarded from all packets. The default value is <tt>false</tt>.
    */
   private static final String DISCARD_CONTRIBUTING_SRCS_PNAME
       = CsrcTransformEngine.class.getName() + ".DISCARD_CONTRIBUTING_SOURCES";

   static
   {
       ConfigurationService cfg = LibJitsi.getConfigurationService();

       discardContributingSrcs =
           ConfigUtils.getBoolean(cfg, DISCARD_CONTRIBUTING_SRCS_PNAME,false);
   }

    /**
     * Creates an engine instance that will be adding CSRC lists to the
     * specified <tt>stream</tt>.
     *
     * @param mediaStream that <tt>MediaStream</tt> whose RTP packets we are
     * going to be adding CSRC lists. to
     */
    public CsrcTransformEngine(MediaStreamImpl mediaStream)
    {
        this.mediaStream = mediaStream;

        /*
         * Take into account that RTPExtension.CSRC_AUDIO_LEVEL_URN may have
         * already been activated.
         */
        Map<Byte,RTPExtension> activeRTPExtensions
            = this.mediaStream.getActiveRTPExtensions();

        if ((activeRTPExtensions != null) && !activeRTPExtensions.isEmpty())
        {
            for (Map.Entry<Byte,RTPExtension> e
                    : activeRTPExtensions.entrySet())
            {
                RTPExtension rtpExtension = e.getValue();
                String uri = rtpExtension.getURI().toString();

                if (RTPExtension.CSRC_AUDIO_LEVEL_URN.equals(uri))
                {
                    Byte extID = e.getKey();

                    setCsrcAudioLevelExtensionID(
                            (extID == null) ? -1 : extID.byteValue(),
                            rtpExtension.getDirection());
                }
            }
        }

        // Audio levels are received in RTP audio streams only.
        if (this.mediaStream instanceof AudioMediaStreamImpl)
        {
            csrcAudioLevelDispatcher
                = new CsrcAudioLevelDispatcher(
                        (AudioMediaStreamImpl) this.mediaStream);
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
            csrcAudioLevelDispatcher.setMediaStream(null);
    }

    /**
     * Creates a audio level extension buffer containing the level extension
     * header and the audio levels corresponding to (and in the same order as)
     * the <tt>CSRC</tt> IDs in the <tt>csrcList</tt>
     *
     * @param csrcList the list of CSRC IDs whose level we'd like the extension
     * to contain.
     * @return the extension buffer in the form that it should be added to the
     * RTP packet.
     */
    private byte[] createLevelExtensionBuffer(long[] csrcList)
    {
        int buffLen = 1 + //CSRC one byte extension hdr
                      csrcList.length;

        // calculate extension padding
        int padLen = 4 - buffLen%4;

        if(padLen == 4)
            padLen = 0;

        buffLen += padLen;

        byte[] extensionBuff = getExtensionBuff(buffLen);

        extensionBuff[0]
            = (byte)((csrcAudioLevelExtID << 4) | (csrcList.length - 1));

        int csrcOffset = 1; // initial offset is equal to ext hdr size

        for (int i = 0; i < csrcList.length; i++)
        {
            long csrc = csrcList[i];
            byte level
                = (byte)
                    ((AudioMediaStreamImpl) mediaStream)
                        .getLastMeasuredAudioLevel(csrc);

            extensionBuff[csrcOffset] = level;
            csrcOffset ++;
        }

        return extensionBuff;
    }

    /**
     * Returns a reusable byte array which is guaranteed to have the requested
     * <tt>ensureCapacity</tt> length and sets our internal length keeping
     * var.
     *
     * @param ensureCapacity the minimum length that we need the returned buffer
     * to have.
     * @return a reusable <tt>byte[]</tt> array guaranteed to have a length
     * equal to or greater than <tt>ensureCapacity</tt>.
     */
    private byte[] getExtensionBuff(int ensureCapacity)
    {
        if ((extensionBuff == null) || (extensionBuff.length < ensureCapacity))
            extensionBuff = new byte[ensureCapacity];

        extensionBuffLen = ensureCapacity;
        return extensionBuff;
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
     * <tt>CsrcTransformEngine</tt>.
     */
    @Override
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
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if ((csrcAudioLevelExtID > 0)
                && csrcAudioLevelDirection.allowsReceiving()
                && (csrcAudioLevelDispatcher != null))
        {
            //extract the audio levels and send them to the dispatcher.
            long[] levels = pkt.extractCsrcAudioLevels(csrcAudioLevelExtID);

            if (levels != null)
                csrcAudioLevelDispatcher.addLevels(levels, pkt.getTimestamp());
        }

        return pkt;
    }

    /**
     * Sets the ID that this transformer should be using for audio level
     * extensions or disables audio level extensions if <tt>extID</tt> is
     * <tt>-1</tt>.
     *
     * @param extID ID that this transformer should be using for audio level
     * extensions or <tt>-1</tt> if audio level extensions should be disabled
     * @param dir the direction that we are expected to hand this extension in.
     *
     */
    public void setCsrcAudioLevelExtensionID(byte extID, MediaDirection dir)
    {
        this.csrcAudioLevelExtID = extID;
        this.csrcAudioLevelDirection = dir;
    }

    /**
     * Extracts the list of CSRC identifiers representing participants currently
     * contributing to the media being sent by the <tt>MediaStream</tt>
     * associated with this engine and (unless the list is empty) encodes them
     * into the <tt>RawPacket</tt>.
     *
     * @param pkt the RTP <tt>RawPacket</tt> that we need to add a CSRC list to.
     *
     * @return the updated <tt>RawPacket</tt> instance containing the list of
     * CSRC identifiers.
     */
    @Override
    public synchronized RawPacket transform(RawPacket pkt)
    {
        // Only transform RTP packets (and not ZRTP/DTLS, etc)
        if (pkt == null || pkt.getVersion() != RTPHeader.VERSION)
            return pkt;

        long[] csrcList = mediaStream.getLocalContributingSourceIDs();

        if(csrcList == null || csrcList.length == 0 || this.discardContributingSrcs)
        {
            //nothing to do.
            return pkt;
        }

        pkt.setCsrcList(csrcList);

        //attach audio levels if we are expected to do so.
        if ((csrcAudioLevelExtID > 0)
                && csrcAudioLevelDirection.allowsSending()
                && (mediaStream instanceof AudioMediaStreamImpl))
        {
            byte[] levelsExt = createLevelExtensionBuffer(csrcList);

            pkt.addExtension(levelsExt, extensionBuffLen);
        }

        return pkt;
    }
}
