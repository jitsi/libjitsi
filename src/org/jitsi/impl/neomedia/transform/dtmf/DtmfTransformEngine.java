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
package org.jitsi.impl.neomedia.transform.dtmf;

import java.util.*;

import javax.media.*;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;

/**
 * The class is responsible for sending DTMF tones in an RTP audio stream as
 * described by RFC4733.
 *
 * @author Emil Ivov
 * @author Romain Philibert
 * @author Damian Minkov
 */
public class DtmfTransformEngine
    extends SinglePacketTransformer
    implements TransformEngine
{
    /**
     * The <tt>AudioMediaStreamImpl</tt> that this transform engine was created
     * by and that it's going to deliver DTMF packets for.
     */
    private final AudioMediaStreamImpl mediaStream;

    /**
     * The enumeration contains a set of states that reflect the progress of
     */
    private enum ToneTransmissionState
    {
        /**
         * Indicates that this engine is not currently sending DTMF.
         */
        IDLE,

        /**
         * Indicates that we are currently in the process of sending a DTMF
         * tone, and we have already sent at least one packet.
         */
        SENDING,

        /**
         * Indicates that the user has requested that DTMF transmission be
         * stopped but we haven't acted upon that request yet (i.e. we have yet
         * to send a single retransmission)
         */
        END_REQUESTED,

        /**
         * Indicates that the user has requested that DTMF transmission be
         * stopped we have already sent a retransmission of the final packet.
         */
        END_SEQUENCE_INITIATED
    }

    /**
     * Array of all supported tones.
     */
    private static final DTMFRtpTone[] supportedTones =
        new DTMFRtpTone[]
        {DTMFRtpTone.DTMF_0, DTMFRtpTone.DTMF_1, DTMFRtpTone.DTMF_2,
            DTMFRtpTone.DTMF_3, DTMFRtpTone.DTMF_4, DTMFRtpTone.DTMF_5,
            DTMFRtpTone.DTMF_6, DTMFRtpTone.DTMF_7, DTMFRtpTone.DTMF_8,
            DTMFRtpTone.DTMF_9, DTMFRtpTone.DTMF_A, DTMFRtpTone.DTMF_B,
            DTMFRtpTone.DTMF_C, DTMFRtpTone.DTMF_D, DTMFRtpTone.DTMF_SHARP,
            DTMFRtpTone.DTMF_STAR};

    /**
     * The dispatcher that is delivering tones to the media steam.
     */
    private DTMFDispatcher dtmfDispatcher = null;

    /**
     * The status that this engine is currently in.
     */
    private ToneTransmissionState toneTransmissionState
                                                = ToneTransmissionState.IDLE;

    /**
     * The tone that we are supposed to be currently transmitting.
     */
    private Vector<DTMFRtpTone> currentTone = new Vector<DTMFRtpTone>(1, 1);

    /**
     * The number of tone for which we have received a stop request. This is
     * used to signal that stop is already received for "currentTone" not yet
     * sent.
     */
    private int nbToneToStop = 0;

    /**
     * A mutex used to control the start and the stop of a tone and thereby to
     * control concurrent modification access to "currentTone",
     * "nbToneToStop" and "toneTransmissionState".
     */
    private Object startStopToneMutex = new Object();

    /**
     * The duration (in timestamp units or in other words ms*8) that we have
     * transmitted the current tone for.
     */
    private int currentDuration = 0;

    /**
     * The current transmitting timestamp.
     */
    private long currentTimestamp = 0;

    /**
     * We send 3 end packets and this is the counter of remaining packets.
     */
    private int remainingsEndPackets = 0;

    /**
     * Current duration of every event we send.
     */
    private int currentSpacingDuration = Format.NOT_SPECIFIED;

    /**
     * Tells if the current tone has been sent for at least the minimal
     * duration.
     */
    private boolean lastMinimalDuration = false;

    /**
     * The minimal DTMF tone dration. The default value is <tt>560</tt>
     * corresponding to 70 ms. This can be changed by using the
     * "org.jitsi.impl.neomedia.transform.dtmf.minimalToneDuration" property.
     */
    private int minimalToneDuration;

    /**
     * The maximal DTMF tone dration. The default value is -1 telling
     * to stop only when the user asks to. This can be changed by using the
     * "org.jitsi.impl.neomedia.transform.dtmf.maximalToneDuration" property.
     */
    private int maximalToneDuration;

    /**
     * The DTMF tone volume.
     */
    private int volume;

    /**
     * Creates an engine instance that will be replacing audio packets
     * with DTMF ones upon request.
     *
     * @param stream the <tt>AudioMediaStream</tt> whose RTP packets we are
     * going to be replacing with DTMF.
     */
    public DtmfTransformEngine(AudioMediaStreamImpl stream)
    {
        this.mediaStream = stream;
    }

    /**
     * Close the transformer and underlying transform engine.
     *
     * Nothing to do here.
     */
    public void close()
    {
    }

    /**
     * Gets the current duration of every event we send.
     *
     * @return the current duration of every event we send
     */
    private int getCurrentSpacingDuration()
    {
        if (currentSpacingDuration == Format.NOT_SPECIFIED)
        {
            MediaFormat format = mediaStream.getFormat();
            double clockRate;

            if (format == null)
            {
                MediaType mediaType = mediaStream.getMediaType();

                if (MediaType.VIDEO.equals(mediaType))
                    clockRate = 90000;
                else
                    clockRate = -1;
            }
            else
                clockRate = format.getClockRate();

            // the default is 50 ms. RECOMMENDED in rfc4733.
            if (clockRate > 0)
                currentSpacingDuration = (int) clockRate / 50;
        }
        return currentSpacingDuration;
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
     * <tt>DtmfTransformEngine</tt>.
     */
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * A stub meant to handle incoming DTMF packets.
     *
     * @param pkt an incoming packet that we need to parse and handle in case
     * we determine it to be DTMF.
     *
     * @return the <tt>pkt</tt> if it is not a DTMF tone and <tt>null</tt>
     * otherwise since we will be handling the packet ourselves and their's
     * no point in feeding it to the application.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        byte currentDtmfPayload
            = mediaStream.getDynamicRTPPayloadType(Constants.TELEPHONE_EVENT);

        if(currentDtmfPayload == pkt.getPayloadType())
        {
            DtmfRawPacket p = new DtmfRawPacket(pkt);

            if (dtmfDispatcher == null)
            {
                dtmfDispatcher = new DTMFDispatcher();
                new Thread(dtmfDispatcher).start();
            }
            dtmfDispatcher.addTonePacket(p);

            // ignore received dtmf packets
            // if jmf receive change in rtp payload stops reception
            return null;
        }

        return pkt;
    }

    /**
     * Replaces <tt>pkt</tt> with a DTMF packet if this engine is in a DTMF
     * transmission mode or returns it unchanged otherwise.
     *
     * @param pkt the audio packet that we may want to replace with a DTMF one.
     *
     * @return <tt>pkt</tt> with a DTMF packet if this engine is in a DTMF
     * transmission mode or returns it unchanged otherwise.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        if (currentTone.isEmpty()
                || pkt == null
                || pkt.getVersion() != RTPHeader.VERSION)
        {
            return pkt;
        }

        byte toneCode = currentTone.firstElement().getCode();
        byte currentDtmfPayload
            = mediaStream.getDynamicRTPPayloadType(Constants.TELEPHONE_EVENT);

        if ( currentDtmfPayload == -1 )
            throw new IllegalStateException("Can't send DTMF when no payload "
                            +"type has been negotiated for DTMF events.");

        DtmfRawPacket dtmfPkt = new DtmfRawPacket(
                pkt.getBuffer(),
                pkt.getOffset(),
                pkt.getLength(),
                currentDtmfPayload);

        long audioPacketTimestamp = dtmfPkt.getTimestamp();
        boolean pktEnd = false;
        boolean pktMarker = false;
        int pktDuration = 0;

        checkIfCurrentToneMustBeStopped();

        if(toneTransmissionState == ToneTransmissionState.IDLE)
        {
            lastMinimalDuration = false;
            currentDuration = 0;
            currentDuration += getCurrentSpacingDuration();
            pktDuration = currentDuration;

            pktMarker = true;
            currentTimestamp = audioPacketTimestamp;

            synchronized(startStopToneMutex)
            {
                toneTransmissionState = ToneTransmissionState.SENDING;
            }
        }
        else if(toneTransmissionState == ToneTransmissionState.SENDING
                || (toneTransmissionState == ToneTransmissionState.END_REQUESTED
                    && !lastMinimalDuration))
        {
            currentDuration += getCurrentSpacingDuration();
            pktDuration = currentDuration;
            if(currentDuration > minimalToneDuration)
            {
                lastMinimalDuration = true;
            }
            if(maximalToneDuration != -1
                    && currentDuration > maximalToneDuration)
            {
                toneTransmissionState = ToneTransmissionState.END_REQUESTED;
            }
            // Check for long state event
            if (currentDuration > 0xFFFF)
            {
                // When duration > 0xFFFF we first send a packet with duration =
                // 0xFFFF. For the next packet, the duration start from beginning
                // but the audioPacketTimestamp is set to the time when the long
                // duration event occurs.
                pktDuration = 0xFFFF;
                currentDuration = 0;
                currentTimestamp = audioPacketTimestamp;
            }
        }
        else if(toneTransmissionState == ToneTransmissionState.END_REQUESTED)
        {
            // The first ending packet do have the End flag set.
            // But the 2 next will have the End flag set.
            //
            // The audioPacketTimestamp and the duration field stay unchanged
            // for the 3 last packets
            currentDuration += getCurrentSpacingDuration();
            pktDuration = currentDuration;

            pktEnd = true;
            remainingsEndPackets = 2;

            synchronized(startStopToneMutex)
            {
                toneTransmissionState
                    = ToneTransmissionState.END_SEQUENCE_INITIATED;
            }
        }
        else if(toneTransmissionState
                == ToneTransmissionState.END_SEQUENCE_INITIATED)
        {
            pktEnd = true;
            pktDuration = currentDuration;
            remainingsEndPackets--;

            if(remainingsEndPackets == 0)
            {
                synchronized(startStopToneMutex)
                {
                    toneTransmissionState = ToneTransmissionState.IDLE;
                    currentTone.remove(0);
                }
            }
        }

        dtmfPkt.init(
            toneCode,
            pktEnd,
            pktMarker,
            pktDuration,
            currentTimestamp,
            volume);
        pkt = dtmfPkt;

        return pkt;
    }


    /**
     * DTMF sending stub: this is where we should set the transformer in the
     * proper state so that it would start replacing packets with dtmf codes.
     *
     * @param tone the tone that we'd like to start sending.
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume.
     */
    public void startSending(
            DTMFRtpTone tone,
            int minimalToneDuration,
            int maximalToneDuration,
            int volume)
    {
        synchronized(startStopToneMutex)
        {
            // If the GUI throws several start and only one stop (i.e. when
            // holding a key pressed on Windows), then check that we have the
            // good number of tone to stop.
            this.stopSendingDTMF();

            currentTone.add(tone);
        }
        // Converts duration in ms into duration in timestamp units (here the
        // codec of telephone-event is 8000 Hz).
        this.minimalToneDuration = minimalToneDuration * 8;
        this.maximalToneDuration = maximalToneDuration * 8;
        if(maximalToneDuration == -1)
        {
            this.maximalToneDuration = -1;
        }
        else if(this.maximalToneDuration < this.minimalToneDuration)
        {
            this.maximalToneDuration = this.minimalToneDuration;
        }

        if(volume > 0)
            this.volume = volume;
        else
            this.volume = 0; // we used to sent 0 for
                             // this field, keep it that way if not set.
    }

    /**
     * Interrupts transmission of a <tt>DTMFRtpTone</tt> started with the
     * <tt>startSendingDTMF()</tt> method. Has no effect if no tone is currently
     * being sent.
     *
     * @see AudioMediaStream#stopSendingDTMF(DTMFMethod dtmfMethod)
     */
    public void stopSendingDTMF()
    {
        synchronized(startStopToneMutex)
        {
            // Check if there is currently one tone in a stopping state.
            int stoppingTone =
                (toneTransmissionState == ToneTransmissionState.END_REQUESTED
                 || toneTransmissionState
                     == ToneTransmissionState.END_SEQUENCE_INITIATED)
                ? 1: 0;

            // Verify that the number of tone to stop does not exceed the number
            // of waiting or sending tones.
            if(currentTone.size() > nbToneToStop + stoppingTone)
            {
                ++nbToneToStop;
            }
        }
    }

    /**
     * Stops threads that this transform engine is using for even delivery.
     */
    public void stop()
    {
        if(dtmfDispatcher != null)
            dtmfDispatcher.stop();
    }

    /**
     * Changes the current tone state, and requests to stop it if necessary.
     */
    private void checkIfCurrentToneMustBeStopped()
    {
        synchronized(startStopToneMutex)
        {
            if(nbToneToStop > 0
                    && toneTransmissionState == ToneTransmissionState.SENDING)
            {
                --nbToneToStop;
                toneTransmissionState = ToneTransmissionState.END_REQUESTED;
            }
        }
    }

    /**
     * A simple thread that waits for new tones to be reported from incoming
     * RTP packets and then delivers them to the <tt>AudioMediaStream</tt>
     * associated with this engine. The reason we need to do this in a separate
     * thread is of course the time sensitive nature of incoming RTP packets.
     */
    private class DTMFDispatcher
        implements Runnable
    {
        /** Indicates whether this thread is supposed to be running */
        private boolean isRunning = false;

        /** The tone that we last received from the reverseTransform thread*/
        private DTMFRtpTone lastReceivedTone = null;

        /** The tone that we last received from the reverseTransform thread*/
        private DTMFRtpTone lastReportedTone = null;

        /**
         * Have we received end of the currently started tone.
         */
        private boolean toEnd = false;

        /**
         * Waits for new tone to be reported via the <tt>addTonePacket()</tt>
         * method and then delivers them to the <tt>AudioMediaStream</tt> that
         * we are associated with.
         */
        public void run()
        {
            isRunning = true;

            DTMFRtpTone temp = null;

            while(isRunning)
            {
                synchronized(this)
                {
                    if(lastReceivedTone == null)
                    {
                        try
                        {
                            wait();
                        }
                        catch (InterruptedException ie) {}
                    }

                    temp = lastReceivedTone;
                    // make lastReportedLevels to null
                    // so we will wait for the next tone on next iteration
                    lastReceivedTone = null;
                }

                if(temp != null
                    && ((lastReportedTone == null && !toEnd)
                        || (lastReportedTone != null && toEnd)))
                {
                    //now notify our listener
                    if (mediaStream != null)
                    {
                        mediaStream.fireDTMFEvent(temp, toEnd);
                        if(toEnd)
                            lastReportedTone = null;
                        else
                            lastReportedTone = temp;
                        toEnd = false;
                    }
                }
            }
        }

        /**
         * A packet that we should convert to tone and deliver
         * to our media stream and its listeners in a separate thread.
         *
         * @param p the packet we will convert and deliver.
         */
        public void addTonePacket(DtmfRawPacket p)
        {
            synchronized(this)
            {
                this.lastReceivedTone = getToneFromPacket(p);
                this.toEnd = p.isEnd();

                notifyAll();
            }
        }

        /**
         * Causes our run method to exit so that this thread would stop
         * handling levels.
         */
        public void stop()
        {
            synchronized(this)
            {
                this.lastReceivedTone = null;
                isRunning = false;

                notifyAll();
            }
        }

        /**
         * Maps DTMF packet codes to our DTMFRtpTone objects.
         * @param p the packet
         * @return the corresponding tone.
         */
        private DTMFRtpTone getToneFromPacket(DtmfRawPacket p)
        {
            for (int i = 0; i < supportedTones.length; i++)
            {
                DTMFRtpTone t = supportedTones[i];
                if(t.getCode() == p.getCode())
                    return t;
            }

            return null;
        }
    }
}
