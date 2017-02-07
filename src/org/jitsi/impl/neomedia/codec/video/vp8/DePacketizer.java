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
package org.jitsi.impl.neomedia.codec.video.vp8;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;
import javax.media.*;
import javax.media.format.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A depacketizer from VP8.
 * See {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-11"}
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class DePacketizer
    extends AbstractCodec2
{
    /**
     * The <tt>Logger</tt> used by the <tt>DePacketizer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DePacketizer.class);

    /**
     * Whether trace logging is enabled.
     */
    private static final boolean TRACE = logger.isTraceEnabled();

    /**
     * A <tt>Comparator</tt> implementation for RTP sequence numbers.
     * Compares <tt>a</tt> and <tt>b</tt>, taking into account the wrap at 2^16.
     *
     * IMPORTANT: This is a valid <tt>Comparator</tt> implementation only if
     * used for subsets of [0, 2^16) which don't span more than 2^15 elements.
     *
     * E.g. it works for: [0, 2^15-1] and ([50000, 2^16) u [0, 10000])
     * Doesn't work for: [0, 2^15] and ([0, 2^15-1] u {2^16-1}) and [0, 2^16)
     *
     * NOTE: An identical implementation for Integers can be found in the class
     * SeqNumComparator. Sequence numbers are 16 bits and unsigned, so an
     * Integer should be sufficient to hold that.
     */
    private static final Comparator<? super Long> seqNumComparator
            = new Comparator<Long>() {
        @Override
        public int compare(Long a, Long b)
        {
            if (a.equals(b))
                return 0;
            else if (a > b)
            {
                if (a - b < 32768)
                    return 1;
                else
                    return -1;
            }
            else //a < b
            {
                if (b - a < 32768)
                    return -1;
                else
                    return 1;
            }
        }
    };

    /**
     * Stores the RTP payloads (VP8 payload descriptor stripped) from RTP packets
     * belonging to a single VP8 compressed frame.
     */
    private SortedMap<Long, Container> data
            = new TreeMap<Long, Container>(seqNumComparator);

    /**
     * Stores unused <tt>Container</tt>'s.
     */
    private Queue<Container> free = new ArrayBlockingQueue<Container>(100);

    /**
     * Stores the first (earliest) sequence number stored in <tt>data</tt>, or
     * -1 if <tt>data</tt> is empty.
     */
    private long firstSeq = -1;

    /**
     * Stores the last (latest) sequence number stored in <tt>data</tt>, or -1
     * if <tt>data</tt> is empty.
     */
    private long lastSeq = -1;

    /**
     * Stores the value of the <tt>PictureID</tt> field for the VP8 compressed
     * frame, parts of which are currently stored in <tt>data</tt>, or -1 if
     * the <tt>PictureID</tt> field is not in use or <tt>data</tt> is empty.
     */
    private int pictureId = -1;

    /**
     * Stores the RTP timestamp of the packets stored in <tt>data</tt>, or -1
     * if they don't have a timestamp set.
     */
    private long timestamp = -1;

    /**
     * Whether we have stored any packets in <tt>data</tt>. Equivalent to
     * <tt>data.isEmpty()</tt>.
     */
    private boolean empty = true;

    /**
     * Whether we have stored in <tt>data</tt> the last RTP packet of the VP8
     * compressed frame, parts of which are currently stored in <tt>data</tt>.
     */
    private boolean haveEnd = false;

    /**
     * Whether we have stored in <tt>data</tt> the first RTP packet of the VP8
     * compressed frame, parts of which are currently stored in <tt>data</tt>.
     */
    private boolean haveStart = false;

    /**
     * Stores the sum of the lengths of the data stored in <tt>data</tt>, that
     * is the total length of the VP8 compressed frame to be constructed.
     */
    private int frameLength = 0;

    /**
     * The sequence number of the last RTP packet, which was included in the
     * output.
     */
    private long lastSentSeq = -1;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public DePacketizer()
    {
        super("VP8 RTP DePacketizer",
                VideoFormat.class,
                new VideoFormat[]{ new VideoFormat(Constants.VP8) });
        inputFormats = new VideoFormat[] { new VideoFormat(Constants.VP8_RTP) };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException
    {
        if(logger.isInfoEnabled())
            logger.info("Opened VP8 depacketizer");
    }

    /**
     * Re-initializes the fields which store information about the currently
     * held data. Empties <tt>data</tt>.
     */
    private void reinit()
    {
        firstSeq = lastSeq = timestamp = -1;
        pictureId = -1;
        empty = true;
        haveEnd = haveStart = false;
        frameLength = 0;

        Iterator<Map.Entry<Long,Container>> it = data.entrySet().iterator();
        Map.Entry<Long, Container> e;
        while (it.hasNext())
        {
            e = it.next();
            free.offer(e.getValue());
            it.remove();
        }
    }

    /**
     * Checks whether the currently held VP8 compressed frame is complete (e.g
     * all its packets are stored in <tt>data</tt>).
     * @return <tt>true</tt> if the currently help VP8 compressed frame is
     * complete, <tt>false</tt> otherwise.
     */
    private boolean frameComplete()
    {
        return haveStart && haveEnd && !haveMissing();
    }

    /**
     * Checks whether there are packets with sequence numbers between
     * <tt>firstSeq</tt> and <tt>lastSeq</tt> which are *not* stored in
     * <tt>data</tt>.
     * @return <tt>true</tt> if there are packets with sequence numbers between
     * <tt>firstSeq</tt> and <tt>lastSeq</tt> which are *not* stored in
     * <tt>data</tt>.
     */
    private boolean haveMissing()
    {
        Set<Long> seqs = data.keySet();
        long s = firstSeq;
        while (s != lastSeq)
        {
            if (!seqs.contains(s))
                return true;
            s = (s+1) % (1<<16);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        byte[] inData = (byte[])inBuffer.getData();
        int inOffset = inBuffer.getOffset();

        if (!VP8PayloadDescriptor.isValid(inData, inOffset))
        {
            logger.warn("Invalid RTP/VP8 packet discarded.");
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_FAILED; //XXX: FAILED or OK?
        }

        long inSeq = inBuffer.getSequenceNumber();
        long inRtpTimestamp = inBuffer.getRtpTimeStamp();
        int inPictureId = VP8PayloadDescriptor.getPictureId(inData, inOffset);
        boolean inMarker = (inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0;
        boolean inIsStartOfFrame
                = VP8PayloadDescriptor.isStartOfFrame(inData, inOffset);
        int inLength = inBuffer.getLength();
        int inPdSize = VP8PayloadDescriptor.getSize(inData, inOffset);
        int inPayloadLength = inLength - inPdSize;

        if (empty
                && lastSentSeq != -1
                && seqNumComparator.compare(inSeq, lastSentSeq) != 1)
        {
            if (logger.isInfoEnabled())
                logger.info("Discarding old packet (while empty) " + inSeq);
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        if (!empty)
        {
            // if the incoming packet has a different PictureID or timestamp
            // than those of the current frame, then it belongs to a different
            // frame.
            if ( (inPictureId != -1 && pictureId != -1
                    && inPictureId != pictureId)
                 | (timestamp != -1 && inRtpTimestamp != -1
                    && inRtpTimestamp != timestamp) )
            {
                if (seqNumComparator
                        .compare(inSeq, firstSeq) != 1) //inSeq <= firstSeq
                {
                    // the packet belongs to a previous frame. discard it
                    if (logger.isInfoEnabled())
                        logger.info("Discarding old packet " + inSeq);
                    outBuffer.setDiscard(true);
                    return BUFFER_PROCESSED_OK;
                }
                else //inSeq > firstSeq (and also presumably isSeq > lastSeq)
                {
                    // the packet belongs to a subsequent frame (to the one
                    // currently being held). Drop the current frame.

                    if (logger.isInfoEnabled())
                        logger.info("Discarding saved packets on arrival of" +
                                " a packet for a subsequent frame: " + inSeq);

                    // TODO: this would be the place to complain about the
                    // not-well-received PictureID by sending a RTCP SLI or NACK.
                    reinit();
                }
            }
        }

        // a whole frame in a single packet. avoid the extra copy to
        // this.data and output it immediately.
        if (empty && inMarker && inIsStartOfFrame)
        {
            byte[] outData
                    = validateByteArraySize(outBuffer, inPayloadLength, false);
            System.arraycopy(
                    inData,
                    inOffset + inPdSize,
                    outData,
                    0,
                    inPayloadLength);
            outBuffer.setOffset(0);
            outBuffer.setLength(inPayloadLength);
            outBuffer.setRtpTimeStamp(inBuffer.getRtpTimeStamp());

            if (TRACE)
                logger.trace("Out PictureID=" + inPictureId);

            lastSentSeq = inSeq;

            return BUFFER_PROCESSED_OK;
        }

        // add to this.data
        Container container = free.poll();
        if (container == null)
            container = new Container();
        if (container.buf == null || container.buf.length < inPayloadLength)
            container.buf = new byte[inPayloadLength];

        if (data.get(inSeq) != null)
        {
            if (logger.isInfoEnabled())
                logger.info("(Probable) duplicate packet detected, discarding "
                                    + inSeq);
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        System.arraycopy(
                inData,
                inOffset + inPdSize,
                container.buf,
                0,
                inPayloadLength);
        container.len = inPayloadLength;
        data.put(inSeq, container);

        // update fields
        frameLength += inPayloadLength;
        if (firstSeq == -1
                || (seqNumComparator.compare(firstSeq, inSeq) == 1))
            firstSeq = inSeq;
        if (lastSeq == -1
                || (seqNumComparator.compare(inSeq, lastSeq) == 1))
            lastSeq = inSeq;

        if (empty)
        {
            // the first received packet for the current frame was just added
            empty = false;
            timestamp = inRtpTimestamp;
            pictureId = inPictureId;
        }

        if (inMarker)
            haveEnd = true;
        if (inIsStartOfFrame)
            haveStart = true;

        // check if we have a full frame
        if (frameComplete())
        {
            byte[] outData
                    = validateByteArraySize(outBuffer, frameLength, false);
            int ptr = 0;
            Container b;
            for (Map.Entry<Long, Container> entry : data.entrySet())
            {
                b = entry.getValue();
                System.arraycopy(
                        b.buf,
                        0,
                        outData,
                        ptr,
                        b.len);
                ptr += b.len;
            }

            outBuffer.setOffset(0);
            outBuffer.setLength(frameLength);
            outBuffer.setRtpTimeStamp(inBuffer.getRtpTimeStamp());

            if (TRACE)
                logger.trace("Out PictureID=" + inPictureId);
            lastSentSeq = lastSeq;

            // prepare for the next frame
            reinit();

            return BUFFER_PROCESSED_OK;
        }
        else
        {
            // frame not complete yet
            outBuffer.setDiscard(true);
            return OUTPUT_BUFFER_NOT_FILLED;
        }
    }

    /**
     * Returns true if the buffer contains a VP8 key frame at offset
     * <tt>offset</tt>.
     *
     * @param buff the byte buffer to check
     * @param off the offset in the byte buffer where the actual data starts
     * @param len the length of the data in the byte buffer
     * @return true if the buffer contains a VP8 key frame at offset
     * <tt>offset</tt>.
     */
    public static boolean isKeyFrame(byte[] buff, int off, int len)
    {
        if (buff == null || buff.length < off + len
            || len < RawPacket.FIXED_HEADER_SIZE)
        {
            return false;
        }

        // Check if this is the start of a VP8 partition in the payload
        // descriptor.
        if (!DePacketizer.VP8PayloadDescriptor.isValid(buff, off))
        {
            return false;
        }

        if (!DePacketizer.VP8PayloadDescriptor.isStartOfFrame(buff, off))
        {
            return false;
        }

        int szVP8PayloadDescriptor = DePacketizer
            .VP8PayloadDescriptor.getSize(buff, off);

        return DePacketizer.VP8PayloadHeader.isKeyFrame(
                buff, off + szVP8PayloadDescriptor);
    }

    /**
     * A class that represents the VP8 Payload Descriptor structure defined
     * in {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-10"}
     */
    public static class VP8PayloadDescriptor
    {
        /**
         * I bit from the X byte of the Payload Descriptor.
         */
        private static final byte I_BIT = (byte) 0x80;

        /**
         * K bit from the X byte of the Payload Descriptor.
         */
        private static final byte K_BIT = (byte) 0x10;
        /**
         * L bit from the X byte of the Payload Descriptor.
         */
        private static final byte L_BIT = (byte) 0x40;

        /**
         * I bit from the I byte of the Payload Descriptor.
         */
        private static final byte M_BIT = (byte) 0x80;
        /**
         * Maximum length of a VP8 Payload Descriptor.
         */
        public static final int MAX_LENGTH = 6;
        /**
         * S bit from the first byte of the Payload Descriptor.
         */
        private static final byte S_BIT = (byte) 0x10;
        /**
         * T bit from the X byte of the Payload Descriptor.
         */
        private static final byte T_BIT = (byte) 0x20;

        /**
         * X bit from the first byte of the Payload Descriptor.
         */
        private static final byte X_BIT = (byte) 0x80;

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         *
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        public static int getTemporalLayerIndex(byte[] buf, int off, int len)
        {
            if (buf == null || buf.length < off + len || len < 2)
            {
                return -1;
            }

            if ((buf[off] & X_BIT) == 0 || (buf[off+1] & T_BIT) == 0)
            {
                return -1;
            }

            int sz = getSize(buf, off);
            if (buf.length < off + sz || sz < 1)
            {
                return -1;
            }

            return (buf[off + sz - 1] & 0xc0) >> 6;
        }

        /**
         * Returns a simple Payload Descriptor, with PartID = 0, the 'start
         * of partition' bit set according to <tt>startOfPartition</tt>, and
         * all other bits set to 0.
         * @param startOfPartition whether to 'start of partition' bit should be
         * set
         * @return a simple Payload Descriptor, with PartID = 0, the 'start
         * of partition' bit set according to <tt>startOfPartition</tt>, and
         * all other bits set to 0.
         */
        public static byte[] create(boolean startOfPartition)
        {
            byte[] pd = new byte[1];
            pd[0] = startOfPartition ? (byte) 0x10 : 0;
            return pd;
        }

        /**
         * The size in bytes of the Payload Descriptor at offset
         * <tt>offset</tt> in <tt>input</tt>. The size is between 1 and 6.
         *
         * @param input input
         * @param offset offset
         * @return The size in bytes of the Payload Descriptor at offset
         * <tt>offset</tt> in <tt>input</tt>, or -1 if the input is not a valid
         * VP8 Payload Descriptor. The size is between 1 and 6.
         */
        public static int getSize(byte[] input, int offset)
        {
            if (!isValid(input, offset))
                return -1;

            if ((input[offset] & X_BIT) == 0)
                return 1;

            int size = 2;
            if ((input[offset+1] & I_BIT) != 0)
            {
                size++;
                if ((input[offset+2] & M_BIT) != 0)
                    size++;
            }
            if ((input[offset+1] & L_BIT) != 0)
                size++;
            if ((input[offset+1] & (T_BIT | K_BIT)) != 0)
                size++;

            return size;
        }

        /**
         * Gets the value of the PictureID field of a VP8 Payload Descriptor.
         * @param input
         * @param offset
         * @return the value of the PictureID field of a VP8 Payload Descriptor,
         * or -1 if the fields is not present.
         */
        private static int getPictureId(byte[] input, int offset)
        {
            if (!isValid(input, offset))
                return -1;

            if ((input[offset] & X_BIT) == 0
                || (input[offset+1] & I_BIT) == 0)
                return -1;

            boolean isLong = (input[offset+2] & M_BIT) != 0;
            if (isLong)
                return (input[offset+2] & 0x7f) << 8
                     | (input[offset+3] & 0xff);
            else
                return input[offset+2] & 0x7f;

        }

        public static boolean isValid(byte[] input, int offset)
        {
            return true;
        }

        /**
         * Checks whether the '<tt>start of partition</tt>' bit is set in the
         * VP8 Payload Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         * @param input input
         * @param offset offset
         * @return <tt>true</tt> if the '<tt>start of partition</tt>' bit is set,
         * <tt>false</tt> otherwise.
         */
        public static boolean isStartOfPartition(byte[] input, int offset)
        {
            return (input[offset] & S_BIT) != 0;
        }

        /**
         * Returns <tt>true</tt> if both the '<tt>start of partition</tt>' bit
         * is set and the <tt>PID</tt> fields has value 0 in the VP8 Payload
         * Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         * @param input
         * @param offset
         * @return <tt>true</tt> if both the '<tt>start of partition</tt>' bit
         * is set and the <tt>PID</tt> fields has value 0 in the VP8 Payload
         * Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         */
        public static boolean isStartOfFrame(byte[] input, int offset)
        {
            return isStartOfPartition(input, offset)
                    && getPartitionId(input, offset) == 0;
        }

        /**
         * Returns the value of the <tt>PID</tt> (partition ID) field of the
         * VP8 Payload Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         * @param input
         * @param offset
         * @return the value of the <tt>PID</tt> (partition ID) field of the
         * VP8 Payload Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         */
        public static int getPartitionId(byte[] input, int offset)
        {
            return input[offset] & 0x07;
        }
    }

    /**
     * A class that represents the VP8 Payload Header structure defined
     * in {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-10"}
     */
    public static class VP8PayloadHeader
    {
        /**
         * S bit of the Payload Descriptor.
         */
        private static final byte S_BIT = (byte) 0x01;

        /**
         * Returns true if the <tt>P</tt> (inverse key frame flag) field of the
         * VP8 Payload Header at offset <tt>offset</tt> in <tt>input</tt> is 0.
         *
         * @return true if the <tt>P</tt> (inverse key frame flag) field of the
         * VP8 Payload Header at offset <tt>offset</tt> in <tt>input</tt> is 0,
         * false otherwise.
         */
        public static boolean isKeyFrame(byte[] input, int offset)
        {
            // When set to 0 the current frame is a key frame.  When set to 1
            // the current frame is an interframe. Defined in [RFC6386]

            return (input[offset] & S_BIT) == 0;
        }
    }

    /**
     * A simple container for a <tt>byte[]</tt> and an integer.
     */
    private static class Container
    {
        /**
         * This <tt>Container</tt>'s data.
         */
        private byte[] buf;

        /**
         * Length used.
         */
        private int len = 0;
    }
}
