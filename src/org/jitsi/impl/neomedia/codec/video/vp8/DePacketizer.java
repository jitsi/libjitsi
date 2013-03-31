/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.vp8;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;
import javax.media.*;
import javax.media.format.*;

/**
 * A depacketizer from VP8.
 * See {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
 *
 * It is not yet fully compliant with the draft above, it can't successfully
 * process all valid streams.
 * It works by concatenating packets' payload (stripping the payload descriptor),
 * until it encounters a packet with it's Start of Partition bit set, at which
 * point it outputs the concatenated data and starts again.
 *
 * @author Boris Grozev
 */
public class DePacketizer
    extends AbstractCodec2
{
    /**
     * Size of <tt>buffer</tt>
     */
    private static final int BUFFER_SIZE = 100000;

    /**
     * The <tt>Logger</tt> used by the <tt>DePacketizer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DePacketizer.class);

    /**
     * Certain output will only be logged if this is set to true in addition to
     * 'trace' being enable in the logger. This is because the output is long
     * and would be rarely used and to let compiler optimise the conditionals.
     */
    private static final boolean TRACE = false;

    /**
     * Buffer used to store the payload of packets
     */
    private byte[] buffer = new byte[BUFFER_SIZE];

    /**
     * Pointer to the last byte used in buffer.
     */
    private int bufferPointer = 0;

    /**
     * Whether a frame has been output
     */
    private boolean haveSent = false;

    /**
     * The buffer was corrupted for some reason, wait for a new 'start of
     * partition' packet before resuming.
     */
    private boolean waitForNewStart = false;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public DePacketizer()
    {
        super("VP8  RTP DePacketizer",
                VideoFormat.class,
                new VideoFormat[]{new VideoFormat(Constants.VP8)});
        inputFormats = new VideoFormat[] {new VideoFormat(Constants.VP8_RTP)};
    }

    /**
     * {@inheritDoc}
     */
    protected void doClose()
    {
        return;
    }

    /**
     * {@inheritDoc}
     */
    protected void doOpen() throws ResourceUnavailableException
    {
        if(logger.isTraceEnabled())
            logger.trace("Opened VP8 de-packetizer");
        return;
    }


    /**
     * {@inheritDoc}
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        int ret;
        byte[] input = (byte[]) inputBuffer.getData();
        boolean start = VP8PayloadDescriptor.
                            isStartOfPartition(input, inputBuffer.getOffset());
        if(waitForNewStart)
        {
            if(start)
            {
                waitForNewStart = false;
            }
            else
            {
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }
        }

        int pdSize;
        try
        {
            pdSize = VP8PayloadDescriptor.getSize(input, inputBuffer.getOffset());
        }
        catch (Exception e)
        {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_FAILED;
        }

        if(logger.isTraceEnabled() && TRACE)
        {
            logger.trace("Packet: "+inputBuffer.getSequenceNumber()
                    + ", length=" + inputBuffer.getLength()
                    + ", pdSize=" + pdSize
                    + ", start="+start
                    + "\nPayload descriptor:" );
            for(int i = inputBuffer.getOffset();
                i < inputBuffer.getOffset() + pdSize + 1;
                i++)
            {
                logger.trace("\t\t"
                        + ((input[i]&0x80)==0?"0":"1")
                        + ((input[i]&0x40)==0?"0":"1")
                        + ((input[i]&0x20)==0?"0":"1")
                        + ((input[i]&0x10)==0?"0":"1")
                        + ((input[i]&0x08)==0?"0":"1")
                        + ((input[i]&0x04)==0?"0":"1")
                        + ((input[i]&0x02)==0?"0":"1")
                        + ((input[i]&0x01)==0?"0":"1"));
            }
        }

        if(start && haveSent)
        {
            //start of a new frame, flush the buffer
            if(logger.isTraceEnabled())
                logger.trace("Sending a frame, size=" + bufferPointer);

            byte[] output
                = validateByteArraySize(outputBuffer, bufferPointer, false);

            System.arraycopy(buffer, 0, output, 0, bufferPointer);
            outputBuffer.setFormat(new VideoFormat(Constants.VP8));
            outputBuffer.setLength(bufferPointer);
            outputBuffer.setOffset(0);

            bufferPointer = 0;
            ret = BUFFER_PROCESSED_OK;
        }
        else
        {
            ret = OUTPUT_BUFFER_NOT_FILLED;
        }

        int len = inputBuffer.getLength();
        if(bufferPointer + len - pdSize >= BUFFER_SIZE)
        {
            //our buffer is not big enough
            bufferPointer = 0;
            outputBuffer.setDiscard(true);
            waitForNewStart = true;
            return BUFFER_PROCESSED_FAILED;
        }
        System.arraycopy(input,
                         inputBuffer.getOffset() + pdSize,
                         buffer,
                         bufferPointer,
                         len - pdSize);
        bufferPointer += len - pdSize;


        if(logger.isTraceEnabled() && TRACE)
            logger.trace("Saving payload to buffer, seq num:"
                    + inputBuffer.getSequenceNumber()
                    + ", bufferPointer=" + bufferPointer);

        haveSent = true;
        return ret;
    }

    /**
     * A class that represents the VP8 Payload Descriptor structure defined
     * in {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
     */
    static class VP8PayloadDescriptor
    {
        /**
         * I bit from the X byte of the Payload Descriptor
         */
        private static final byte I_BIT = (byte) 0x80;

        /**
         * K bit from the X byte of the Payload Descriptor
         */
        private static final byte K_BIT = (byte) 0x10;
        /**
         * L bit from the X byte of the Payload Descriptor
         */
        private static final byte L_BIT = (byte) 0x40;

        /**
         * I bit from the I byte of the Payload Descriptor
         */
        private static final byte M_BIT = (byte) 0x80;
        /**
         * Maximum length of a VP8 Payload Descriptor
         */
        public static final int MAX_LENGTH = 6;
        /**
         * S bit from the first byte of the Payload Descriptor
         */
        private static final byte S_BIT = (byte) 0x10;
        /**
         * T bit from the X byte of the Payload Descriptor
         */
        private static final byte T_BIT = (byte) 0x20;

        /**
         * X bit from the first byte of the Payload Descriptor
         */
        private static final byte X_BIT = (byte) 0x80;

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
         * <tt>offset</tt> in <tt>input</tt>. The size is between 1 and 6.
         * @throws Exception if there isn't a valid Payload Descriptor structure
         * at offset <tt>offset</tt> in <tt>input</tt>.
         */
        public static int getSize(byte[] input, int offset) throws Exception
        {

            if(input.length < offset+1)
                throw new Exception("Invalid VP8 Payload Descriptor");

            if((input[offset] & X_BIT) == 0)
                return 1;

            int size = 1;
            if((input[offset+1] & I_BIT) != 0)
            {
                size++;
                if((input[offset+2] & M_BIT) != 0)
                    size++;
            }
            if((input[offset+1] & L_BIT) != 0)
                size++;
            if((input[offset+1] & (T_BIT | K_BIT)) != 0)
                size++;

            return size;
        }

        /**
         * Checks whether the 'start of partition' bit is set in the the
         * Payload Descriptor at offset <tt>offset</tt> in <tt>input</tt>
         * @param input input
         * @param offset offset
         * @return
         */
        public static boolean isStartOfPartition(byte[] input, int offset)
        {
            return (input[offset] & S_BIT) != 0;
        }
    }
}
