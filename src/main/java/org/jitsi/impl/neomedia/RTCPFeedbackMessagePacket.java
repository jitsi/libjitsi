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
package org.jitsi.impl.neomedia;

import javax.media.rtp.*;

import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.event.*;

/**
 * Represents an RTCP feedback message packet as described by RFC 4585
 * &quot;Extended RTP Profile for Real-time Transport Control Protocol
 * (RTCP)-Based Feedback (RTP/AVPF)&quot;.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class RTCPFeedbackMessagePacket
    implements Payload
{
    /**
     * Feedback message type (FMT).
     */
    private int fmt;

    /**
     * Packet type (PT).
     */
    private int pt;

    /**
     * SSRC of packet sender.
     */
    private long senderSSRC;

    /**
     * SSRC of media source.
     */
    private long sourceSSRC;

    /**
     * The (command) sequence number of this Full Intra Request (FIR) RTCP
     * feedback message as defined by RFC 5104 &quot;Codec Control Messages in
     * the RTP Audio-Visual Profile with Feedback (AVPF)&quot. The sequence
     * number space is unique for each pairing of the SSRC of command source and
     * the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command.  A repetition SHALL NOT increase the
     * sequence number. The initial value is arbitrary.
     */
    private int seqNr;

    /**
     * Constructor.
     *
     * @param fmt feedback message type
     * @param pt packet type
     * @param senderSSRC SSRC of packet sender
     * @param sourceSSRC SSRC of media source
     */
    public RTCPFeedbackMessagePacket(
            int fmt,
            int pt,
            long senderSSRC,
            long sourceSSRC)
    {
        setFeedbackMessageType(fmt);
        setPayloadType(pt);
        setSenderSSRC(senderSSRC);
        setSourceSSRC(sourceSSRC);
    }

    /**
     * Gets the feedback message type (FMT) of this
     * <tt>RTCPFeedbackMessagePacket</tt>.
     * 
     * @return the feedback message type (FMT) of this
     * <tt>RTCPFeedbackMessagePacket</tt>
     */
    public int getFeedbackMessageType()
    {
        return fmt;
    }

    /**
     * Gets the packet type (PT) of this <tt>RTCPFeedbackMessagePacket</tt>.
     *
     * @return the packet type (PT) of this <tt>RTCPFeedbackMessagePacket</tt>
     */
    public int getPayloadType()
    {
        return pt;
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the originator of
     * this packet.
     *
     * @return the synchronization source identifier (SSRC) of the originator of
     * this packet
     */
    public long getSenderSSRC()
    {
        return senderSSRC;
    }

    /**
     * Gets the (command) sequence number of this Full Intra Request (FIR) RTCP
     * feedback message as defined by RFC 5104 &quot;Codec Control Messages in
     * the RTP Audio-Visual Profile with Feedback (AVPF)&quot. The sequence
     * number space is unique for each pairing of the SSRC of command source and
     * the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command. A repetition SHALL NOT increase the
     * sequence number. The initial value is arbitrary.
     *
     * @return the (command) sequence number of this Full Intra Request (FIR)
     * RTCP feedback message
     */
    public int getSequenceNumber()
    {
        return seqNr;
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the media source
     * that this piece of feedback information is related to.
     *
     * @return the synchronization source identifier (SSRC) of the media source
     * that this piece of feedback information is related to
     */
    public long getSourceSSRC()
    {
        return sourceSSRC;
    }

    /**
     * Sets the feedback message type (FMT) of this
     * <tt>RTCPFeedbackMessagePacket</tt>.
     *
     * @param fmt the feedback message type (FMT) to set on this
     * <tt>RTCPFeedbackMessagePacket</tt>
     */
    public void setFeedbackMessageType(int fmt)
    {
        this.fmt = fmt;
    }

    /**
     * Sets the packet type (PT) of this <tt>RTCPFeedbackMessagePacket</tt>.
     *
     * @param pt the packet type (PT) to set on this
     * <tt>RTCPFeedbackMessagePacket</tt>
     */
    public void setPayloadType(int pt)
    {
        this.pt = pt;
    }

    /**
     * Sets the synchronization source identifier (SSRC) of the originator of
     * this packet.
     *
     * @param senderSSRC the synchronization source identifier (SSRC) of the
     * originator of this packet
     */
    public void setSenderSSRC(long senderSSRC)
    {
        this.senderSSRC = senderSSRC;
    }

    /**
     * Sets the (command) sequence number of this Full Intra Request (FIR) RTCP
     * feedback message as defined by RFC 5104 &quot;Codec Control Messages in
     * the RTP Audio-Visual Profile with Feedback (AVPF)&quot. The sequence
     * number space is unique for each pairing of the SSRC of command source and
     * the SSRC of the command target. The sequence number SHALL be increased by
     * 1 modulo 256 for each new command.
     *
     * @param seqNr the (command) sequence number to set on this Full Intra
     * Request (FIR) RTCP feedback message
     */
    public void setSequenceNumber(int seqNr)
    {
        this.seqNr = seqNr;
    }

    /**
     * Sets the synchronization source identifier (SSRC) of the media source
     * that this piece of feedback information is related to.
     *
     * @param sourceSSRC the synchronization source identifier (SSRC) of the
     * media source that this piece of feedback information is related to
     */
    public void setSourceSSRC(long sourceSSRC)
    {
        this.sourceSSRC = sourceSSRC;
    }

    /**
     * Writes a specific synchronization source identifier (SSRC) into a
     * specific <tt>byte</tt> array starting at a specific offset.
     * 
     * @param ssrc the synchronization source identifier (SSRC) to write into
     * <tt>buf</tt> starting at <tt>off</tt>
     * @param buf the <tt>byte</tt> array to write the specified <tt>ssrc</tt>
     * into starting at <tt>off</tt>
     * @param off the offset in <tt>buf</tt> at which the writing of
     * <tt>ssrc</tt> is to start
     */
    public static void writeSSRC(long ssrc, byte[] buf, int off)
    {
        buf[off++] = (byte) (ssrc >> 24);
        buf[off++] = (byte) ((ssrc >> 16) & 0xFF);
        buf[off++] = (byte) ((ssrc >> 8) & 0xFF);
        buf[off]  = (byte) (ssrc & 0xFF);
    }

    /**
     * Write the RTCP packet representation of this instance into a specific
     * <tt>OutputDataStream</tt>.
     *
     * @param out the <tt>OutputDataStream</tt> into which the RTCP packet
     * representation of this instance is to be written
     */
    public void writeTo(OutputDataStream out)
    {
        /*
         * The length of this RTCP packet in 32-bit words minus one, including
         * the header and any padding.
         */
        int fmt = getFeedbackMessageType();
        int pt = getPayloadType();
        boolean fir = pt == RTCPFeedbackMessageEvent.PT_PS
            && fmt == RTCPFeedbackMessageEvent.FMT_FIR;
        int rtcpPacketLength = 2;

        if (fir)
        {
            /*
             * RFC 5104 "Codec Control Messages in the RTP Audio-Visual Profile
             * with Feedback (AVPF)" defines that The length of the FIR feedback
             * message MUST be set to 2 + 2 * N, where N is the number of FCI
             * entries.
             */
            rtcpPacketLength += 2 * /* N */ 1;
        }

        int len = (rtcpPacketLength + 1) * 4;
        byte[] buf = new byte[len];
        int off = 0;

        /*
         * version (V): 2 bits,
         * padding (P): 1 bit,
         * feedback message type (FMT): 5 bits.
         */
        buf[off++] = (byte) (0x80 /* RTP version */ | (fmt & 0x1F));
        // packet type (PT): 8 bits
        buf[off++] = (byte) pt;

        // length: 16 bits
        buf[off++] = (byte) ((rtcpPacketLength & 0xFF00) >> 8);
        buf[off++] = (byte) (rtcpPacketLength & 0x00FF);

        // SSRC of packet sender: 32 bits
        writeSSRC(getSenderSSRC(), buf, off);
        off += 4;

        // SSRC of media source: 32 bits
        long sourceSSRC = getSourceSSRC();

        /*
         * RFC 5104 "Codec Control Messages in the RTP Audio-Visual Profile with
         * Feedback (AVPF)" defines that the "SSRC of media source" is not used
         * by the FIR feedback message and SHALL be set to 0.
         */
        writeSSRC(
                fir ? 0 : sourceSSRC,
                buf,
                off);
        off += 4;

        // FCI entries
        if (fir)
        {
            /*
             * SSRC: 32 bits. The SSRC value of the media sender that is
             * requested to send a decoder refresh point.
             */
            writeSSRC(sourceSSRC, buf, off);
            off += 4;
            // Seq nr.: 8 bits
            buf[off++] = (byte) (getSequenceNumber() % 256);
            /*
             * Reserved: 24 bits. All bits SHALL be set to 0 by the sender and
             * SHALL be ignored on reception.
             */
            buf[off++] = 0;
            buf[off++] = 0;
            buf[off++] = 0;
        }

        out.write(buf, 0, len);
    }
}
