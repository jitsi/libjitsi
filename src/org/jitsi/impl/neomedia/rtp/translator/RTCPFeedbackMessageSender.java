/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;

/**
 * Allows sending RTCP feedback message packets such as FIR, takes care of their
 * (command) sequence numbers.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class RTCPFeedbackMessageSender
{
    /**
     * The <tt>RTPTranslatorImpl</tt> through which this
     * <tt>RTCPFeedbackMessageSender</tt> sends RTCP feedback message packets.
     * The synchronization source identifier (SSRC) of <tt>rtpTranslator</tt> is
     * used as the SSRC of packet sender.
     */
    private final RTPTranslatorImpl rtpTranslator;

    /**
     * The next (command) sequence numbers to be used for RTCP feedback message
     * packets per SSRC of packet sender-SSRC of media source pair.
     */
    private Map<Long,Integer> sequenceNumbers
        = new LinkedHashMap<Long,Integer>();

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
     * Gets a (command) sequence number to be used for an RTCP feedback message
     * packet which is to be sent from a specific SSRC of packet sender to a
     * specific SSRC of media source.
     *
     * @param sourceSSRC SSRC of packet sender
     * @param targetSSRC SSRC of media source
     * @return the (command) sequence number to be used for an RTCP feedback
     * message packet which is to be sent from <tt>sourceSSRC</tt> to
     * <tt>targetSSRC</tt>
     */
    private int getNextSequenceNumber(int sourceSSRC, int targetSSRC)
    {
        synchronized (sequenceNumbers)
        {
            Long key
                = Long.valueOf(
                        ((sourceSSRC & 0xffffffffl) << 32)
                            | (targetSSRC & 0xffffffffl));
            Integer value = sequenceNumbers.get(key);
            int seqNr = (value == null) ? 0 : value.intValue();

            sequenceNumbers.put(key, Integer.valueOf(seqNr + 1));
            return seqNr;
        }
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
     * specific synchronization source identifier (SSRC).
     * <p>
     * <b>Warning</b>: Due to (current) limitations in
     * <tt>RTPTranslatorImpl</tt> and/or <tt>StreamRTPManager</tt>, a FIR
     * command with the specified <tt>mediaSenderSSRC</tt> is sent to all
     * <tt>MediaStream</tt>s.
     * </p>
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return <tt>true</tt> if a FIR command was sent; otherwise,
     * <tt>false</tt>
     */
    public boolean sendFIR(int mediaSenderSSRC)
    {
        boolean sentFIR = false;
        /*
         * XXX Currently, the method effectively broadcasts a FIR i.e. sends it
         * to all streams connected to rtpTranslator because MediaStream's
         * method getRemoteSourceIds returns an empty list (possibly due to
         * RED).
         */
        for (StreamRTPManager streamRTPManager
                : rtpTranslator.getStreamRTPManagers())
        {
            MediaStream stream = streamRTPManager.getMediaStream();

            sentFIR |= sendFIR(stream, mediaSenderSSRC);
        }
        return sentFIR;
    }

    /**
     * Sends a Full Intra Request (FIR) Command to a media sender/source with a
     * specific synchronization source identifier (SSRC) through a specific
     * <tt>MediaStream</tt>.
     *
     * @param destination the <tt>MediaStream</tt> through which the FIR command
     * is to be sent
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return <tt>true</tt> if a FIR command was sent; otherwise,
     * <tt>false</tt>
     */
    public boolean sendFIR(MediaStream destination, int mediaSenderSSRC)
    {
        long senderSSRC = getSenderSSRC();

        if (senderSSRC == -1)
            return false;

        RTCPFeedbackMessagePacket fir
            = new RTCPFeedbackMessagePacket(
                    RTCPFeedbackMessageEvent.FMT_FIR,
                    RTCPFeedbackMessageEvent.PT_PS,
                    senderSSRC,
                    0xffffffffl & mediaSenderSSRC);

        fir.setSequenceNumber(
                getNextSequenceNumber((int) senderSSRC, mediaSenderSSRC));
        return rtpTranslator.writeRTCPFeedbackMessage(fir, destination);
    }

    /**
     * Sends Full Intra Request (FIR) Commands to media senders/sources with
     * specific synchronization source identifiers (SSRCs) through a specific
     * <tt>MediaStream</tt>.
     *
     * @param destination the <tt>MediaStream</tt> through which the FIR
     * commands are to be sent
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return <tt>true</tt> if a FIR command was sent; otherwise,
     * <tt>false</tt>
     */
    public boolean sendFIR(MediaStream destination, int[] mediaSenderSSRCs)
    {
        boolean sentFIR = false;

        for (int mediaSenderSSRC : mediaSenderSSRCs)
        {
            if (sendFIR(destination, mediaSenderSSRC))
                sentFIR = true;
        }
        return sentFIR;
    }
}
