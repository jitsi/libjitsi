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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.vp8.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.*;

/**
 * Allows sending RTCP feedback message packets such as FIR, takes care of their
 * (command) sequence numbers.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author George Politis
 */
public class RTCPFeedbackMessageSender
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTCPFeedbackMessageSender</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RTCPFeedbackMessageSender.class);

    /**
     * The value of {@link Logger#isTraceEnabled()} from the time of the
     * initialization of the class {@code RTCPFeedbackMessageSender} cached for
     * the purposes of performance.
     */
    private static final boolean TRACE = logger.isTraceEnabled();

    /**
     * The interval in milliseconds at which we re-send an FIR, if the previous
     * one was not satisfied.
     */
    private static final int FIR_RETRY_INTERVAL_MS = 300;

    /**
     * The maximum number of times to send a FIR.
     */
    private static final int FIR_MAX_RETRIES = 10;

    /**
     * The <tt>RTPTranslatorImpl</tt> through which this
     * <tt>RTCPFeedbackMessageSender</tt> sends RTCP feedback message packets.
     * The synchronization source identifier (SSRC) of <tt>rtpTranslator</tt> is
     * used as the SSRC of packet sender.
     */
    private final RTPTranslatorImpl rtpTranslator;

    /**
     * The {@link RecurringProcessibleExecutor} which will periodically call
     * {@link KeyframeRequester#process()} and trigger their retry logic.
     */
    private final RecurringProcessibleExecutor recurringProcessibleExecutor
        = new RecurringProcessibleExecutor(
                RTCPFeedbackMessageSender.class.getSimpleName());

    /**
     * The FIR requesters. One per media sender SSRC.
     */
    private final ConcurrentMap<Integer, KeyframeRequester> kfRequesters
        = new ConcurrentHashMap<>();

    /**
     * Initializes a new <tt>RTCPFeedbackMessageSender</tt> instance which is to
     * send RTCP feedback message packets through a specific
     * <tt>RTPTranslatorImpl</tt>.
     *
     * @param rtpTranslator the <tt>RTPTranslatorImpl</tt> through which the new
     * instance is to send RTCP feedback message packets and the SSRC of which
     * is to be used as the SSRC of packet sender
     */
    public RTCPFeedbackMessageSender(RTPTranslatorImpl rtpTranslator)
    {
        this.rtpTranslator = rtpTranslator;
    }

    /**
     * Gets the synchronization source identifier (SSRC) to be used as SSRC of
     * packet sender in RTCP feedback message packets.
     *
     * @return the SSRC of packet sender
     */
    private long getSenderSSRC()
    {
        long ssrc = rtpTranslator.getLocalSSRC(null);

        return (ssrc == Long.MAX_VALUE) ? -1 : (ssrc & 0xffffffffl);
    }

    /**
     * Sends a Full Intra Request (FIR) Command to a media sender/source with a
     * specific synchronization source identifier (SSRC) through a specific
     * <tt>MediaStream</tt>.
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return <tt>true</tt> if a FIR command was sent; otherwise,
     * <tt>false</tt>
     */
    public boolean sendFIR(int mediaSenderSSRC)
    {
        boolean registerRecurringProcessible = false;
        KeyframeRequester keyframeRequester = kfRequesters.get(mediaSenderSSRC);
        if (keyframeRequester == null )
        {
            // Avoided repeated creation of unneeded objects until get fails.
            keyframeRequester = new KeyframeRequester(mediaSenderSSRC);
            KeyframeRequester existingKfRequester = kfRequesters.putIfAbsent(
                mediaSenderSSRC, keyframeRequester);
            if (existingKfRequester != null)
            {
                // Another thread beat this one to putting a keyframe requester.
                keyframeRequester = existingKfRequester;
                registerRecurringProcessible = true;
            }
        }

        if (registerRecurringProcessible)
        {
            recurringProcessibleExecutor
                .registerRecurringProcessible(keyframeRequester);
        }

        return keyframeRequester.maybeRequest(true);
    }

    /**
     * Sends Full Intra Request (FIR) Commands to media senders/sources with
     * specific synchronization source identifiers (SSRCs).
     *
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return <tt>true</tt> if a FIR command was sent; otherwise,
     * <tt>false</tt>
     */
    public boolean sendFIR(int[] mediaSenderSSRCs)
    {
        if (mediaSenderSSRCs == null || mediaSenderSSRCs.length == 0)
        {
            return false;
        }

        boolean sentFIR = false;

        for (int mediaSenderSSRC : mediaSenderSSRCs)
        {
            if (sendFIR(mediaSenderSSRC))
                sentFIR = true;
        }

        return sentFIR;
    }

    /**
     * Notifies this instance that an RTP packet has been received from a peer
     * represented by a specific <tt>StreamRTPManagerDesc</tt>.
     *
     * @param streamRTPManager a <tt>StreamRTPManagerDesc</tt> which identifies
     * the peer from which an RTP packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or
     * RTCP packet
     * @param off the zero-based index in <tt>buf</tt> at which the bytes of the
     * received RTP or RTCP packet begin
     * @param len the number of bytes in <tt>buf</tt> beginning at <tt>off</tt>
     * which represent the received RTP or RTCP packet
     */
    public void maybeStopRequesting(
        StreamRTPManagerDesc streamRTPManager,
        int ssrc,
        int pt,
        byte[] buf,
        int off,
        int len)
    {
        KeyframeRequester kfRequester = kfRequesters.get(ssrc);
        if (kfRequester != null)
        {
            kfRequester.maybeStopRequesting(streamRTPManager, pt, buf, off, len);
        }
    }

    /**
     * The <tt>KeyframeRequester</tt> is responsible for sending FIR requests to
     * a specific media sender identified by its SSRC.
     */
    class KeyframeRequester
        extends PeriodicProcessible
    {
        /**
         * The media sender SSRC of this <tt>KeyframeRequester</tt>
         */
        private final int mediaSenderSSRC;

        /**
         * The sequence number of the next FIR.
         */
        private final AtomicInteger sequenceNumber = new AtomicInteger(0);

        /**
         * The number of FIR that are left to be sent before stopping.
         */
        private int remainingRetries;

        /**
         * Ctor.
         *
         * @param mediaSenderSSRC
         */
        public KeyframeRequester(int mediaSenderSSRC)
        {
            super(FIR_RETRY_INTERVAL_MS);
            this.mediaSenderSSRC = mediaSenderSSRC;
            this.remainingRetries = 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long process()
        {
            long ret = super.process();

            this.maybeRequest(false);

            return ret; /* unused */
        }

        /**
         * Notifies this instance that an RTP packet has been received from a
         * peer represented by a specific <tt>StreamRTPManagerDesc</tt>.
         *
         * @param streamRTPManager a <tt>StreamRTPManagerDesc</tt> which
         * identifies the peer from which an RTP packet has been received
         * @param buf the buffer which contains the bytes of the received RTP or
         * RTCP packet
         * @param off the zero-based index in <tt>buf</tt> at which the bytes of
         * the received RTP or RTCP packet begin
         * @param len the number of bytes in <tt>buf</tt> beginning at
         * <tt>off</tt> which represent the received RTP or RTCP packet
         */
        public void maybeStopRequesting(
            StreamRTPManagerDesc streamRTPManager,
            int pt,
            byte[] buf,
            int off,
            int len)
        {
            if (remainingRetries == 0)
            {
                return;
            }

            // Reduce auto-boxing (even tho the compiler or the JIT should do
            // this automatically).
            Byte redPT = null, vp8PT = null;

            // XXX do we want to do this only once?
            for (Map.Entry<Byte, MediaFormat> entry : streamRTPManager
                .streamRTPManager.getMediaStream().getDynamicRTPPayloadTypes()
                .entrySet())
            {
                String encoding = entry.getValue().getEncoding();
                if (Constants.VP8.equals(encoding))
                {
                    vp8PT = entry.getKey();
                }
                else if (Constants.RED.equals(encoding))
                {
                    redPT = entry.getKey();
                }
            }

            if (vp8PT == null || vp8PT != pt)
            {
                return;
            }

            if (!Utils.isKeyFrame(buf, off, len, redPT, vp8PT))
            {
                return;
            }

            // This lock only runs while we're waiting for a key frame. It
            // should not slow things down significantly.
            if (TRACE)
            {
                logger.trace("Stopping FIRs to ssrc=" + mediaSenderSSRC);
            }

            synchronized (this)
            {
                remainingRetries = 0;
            }
        }

        /**
         * Sends an FIR RTCP message.
         *
         * @param allowResetRemainingRetries true if it's allowed to reset the
         * remaining retries, false otherwise.
         */
        public boolean maybeRequest(boolean allowResetRemainingRetries)
        {
            synchronized (this)
            {
                if (allowResetRemainingRetries)
                {
                    if (remainingRetries == 0)
                    {
                        if (TRACE)
                        {
                            logger.trace("Starting FIRs to ssrc="
                                + mediaSenderSSRC);
                        }

                        remainingRetries = FIR_MAX_RETRIES;
                    }
                    else
                    {
                        // There's a pending FIR. Pretend that we're sending an
                        // FIR.
                        if (TRACE)
                        {
                            logger.trace("Pending FIRs to ssrc="
                                + mediaSenderSSRC);
                        }

                        return true;
                    }
                }
                else if (remainingRetries == 0)
                {
                    return false;
                }

                remainingRetries--;

                if (TRACE)
                {
                    if (remainingRetries != 0)
                    {
                        logger.trace("Sending a FIR to ssrc=" + mediaSenderSSRC);
                    }
                    else
                    {
                        logger.trace("Sending the last FIR to ssrc=" +
                            mediaSenderSSRC);
                    }
                }
            }

            long senderSSRC = getSenderSSRC();

            if (senderSSRC == -1)
            {
                logger.warn("Not sending an FIR because the sender SSRC is -1.");
                return false;
            }

            StreamRTPManager streamRTPManager = rtpTranslator
                .findStreamRTPManagerByReceiveSSRC(mediaSenderSSRC);

            if (streamRTPManager == null)
            {
                logger.warn("Not sending an FIR because the stream RTP " +
                    "manager is null.");
                return false;
            }

            RTCPFeedbackMessagePacket fir
                = new RTCPFeedbackMessagePacket(
                RTCPFeedbackMessageEvent.FMT_FIR,
                RTCPFeedbackMessageEvent.PT_PS,
                senderSSRC,
                0xffffffffl & mediaSenderSSRC);

            fir.setSequenceNumber(sequenceNumber.incrementAndGet());

            return rtpTranslator.writeControlPayload(
                fir, streamRTPManager.getMediaStream());
        }
    }
}
