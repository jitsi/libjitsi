/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.vp8;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;

/**
 * Packetizes VP8 encoded frames in accord with
 * {@link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
 *
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that
 * changes is the Start of Partition bit, which is set only for the first packet
 * encoding a frame.
 *
 * @author Boris Grozev
 */
public class Packetizer
    extends AbstractCodecExt
{
    /**
     * The <tt>Logger</tt> used by the <tt>Packetizer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(Packetizer.class);

    /**
     * Maximum size of packets (excluding the payload descriptor and any other
     * headers (RTP, UDP))
     */
    static final int MAX_SIZE = 1350;

    /**
     * Whether this is the first packet from the frame.
     */
    private boolean firstPacket = true;

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super("VP8 Packetizer",
                VideoFormat.class,
                new VideoFormat[] { new VideoFormat(Constants.VP8_RTP)});
        inputFormats = new VideoFormat[] { new VideoFormat(Constants.VP8)};
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
    protected void doOpen()
    {
        if(logger.isTraceEnabled())
            logger.trace("Opened VP8 packetizer");
        return;
    }

    /**
     * {@inheritDoc}
     * @param inputBuffer input <tt>Buffer</tt>
     * @param outputBuffer output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> or <tt>INPUT_BUFFER_NOT_CONSUMED</tt>
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        if(inputBuffer.isDiscard() || ((byte[])inputBuffer.getData()).length == 0)
        {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        byte[] output;
        int offset;
        final int pdMaxLen = DePacketizer.VP8PayloadDescriptor.MAX_LENGTH;

        //The input will fit in a single packet
        if(inputBuffer.getLength() <= MAX_SIZE)
        {
            output = validateByteArraySize(outputBuffer,
                                           inputBuffer.getLength() + pdMaxLen);
            offset = pdMaxLen;
        }
        else
        {
            output = validateByteArraySize(outputBuffer, MAX_SIZE + pdMaxLen);
            offset = pdMaxLen;
        }

        int len = inputBuffer.getLength() <= MAX_SIZE
                    ? inputBuffer.getLength()
                    : MAX_SIZE;

        System.arraycopy((byte[])inputBuffer.getData(),
                         inputBuffer.getOffset(),
                         output,
                         offset,
                         len);

        //get the payload descriptor and copy it to the output
        byte[] pd = DePacketizer.VP8PayloadDescriptor.create(firstPacket);
        System.arraycopy(pd,
                            0,
                            output,
                            offset - pd.length,
                            pd.length);
        offset -= pd.length;

        //set up the output buffer
        outputBuffer.setFormat(new VideoFormat(Constants.VP8_RTP));
        outputBuffer.setOffset(offset);
        outputBuffer.setLength(len + pd.length);

        if(inputBuffer.getLength() <= MAX_SIZE)
        {
            firstPacket = true;
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            firstPacket = false;
            inputBuffer.setLength(inputBuffer.getLength()- MAX_SIZE);
            inputBuffer.setOffset(inputBuffer.getOffset()+ MAX_SIZE);
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }

    /**
     * Represents the VP8 Payload Descriptor structure defined in
     * @{link "http://tools.ietf.org/html/draft-ietf-payload-vp8-07#section-4.2"}
     */
    class VP8PayloadDescriptor
    {
        public static final int NOT_SET = -1;

        byte I; //PictureID present
        byte K; //KEYIDX present
        int KEYIDX = NOT_SET;
        byte L; //TL0PICIDX present
        byte N; //Non-reference frame

        byte PartID;//Partition index
        int PictureID = NOT_SET; //PictureID field
        byte S; //Start of VP8 partition


        byte T; //TID present
        int TID = NOT_SET;
        int TL0PICIDX = NOT_SET;
        byte X; //Extended control bits present
        int Y = NOT_SET;

        public byte[] getPayloadDescriptor()
        {
            int len = getSizeInBytes();
            byte[] pd = new byte[len];
            int idx = 0;
            pd[idx] = (byte) (PartID + 0x10*S + 0x20*N);
            if(PictureID != NOT_SET || TL0PICIDX != NOT_SET ||
                    TID != NOT_SET || Y != NOT_SET || KEYIDX != NOT_SET)
                pd[idx] += 0x80;

            idx++;
            if(idx == len)
                return pd;
            pd[idx] = 0;
            if(PictureID != NOT_SET)
                pd[idx] |= 0x80;
            if(TL0PICIDX != NOT_SET)
                pd[idx] |= 0x60; //set both the L and T bits
            if(TID != NOT_SET || Y != NOT_SET)
                pd[idx] |= 0x20;
            if(KEYIDX != NOT_SET)
                pd[idx] |= 0x10;

            if(PictureID != NOT_SET)
            {
                idx++;
                if(PictureID < 128)
                    pd[idx] = (byte) PictureID;
                else
                {
                    pd[idx] = (byte) ((PictureID >> 8) | 0x80);
                    idx++;
                    pd[idx] = (byte) (PictureID % 256);
                }
            }

            if(TL0PICIDX != NOT_SET)
            {
                idx++;
                pd[idx] = (byte)TL0PICIDX;
            }

            if(TID != NOT_SET || Y != NOT_SET || KEYIDX != NOT_SET)
            {
                idx++;
                pd[idx] = 0;
                if(TID != NOT_SET)
                    pd[idx] = (byte) (TID << 6);
                if(Y != NOT_SET)
                    pd[idx] |= 0x20;
                if(KEYIDX != NOT_SET)
                    pd[idx] |= (byte) KEYIDX;
            }

            return pd;
        }

        /**
         * @return size in bytes of the VP8 Payload Descriptor structure.
         */
        public int getSizeInBytes()
        {
            int size = 1;
            if(TID != NOT_SET || Y != NOT_SET || KEYIDX != NOT_SET)
                size++;
            if(TL0PICIDX != NOT_SET)
                size++;
            if(PictureID != NOT_SET)
            {
                size++;
                if(PictureID >= 128)
                    size++;
            }

            //The X byte is not counted above. It will be present if and only
            //if the total size is more than 2.
            if(size > 1)
                size++;
            return size;
        }

        /**
         * Sets the fields of this <tt>VP8PayloadDescriptor</tt> instance by
         * parsing a byte array.
         *
         * @param input the byte array to parse
         * @return the length in bytes of the VP8 Payload Descriptor structure
         * found in the beginning of <tt>input</tt>. It should always be
         * between 1 and 6.
         *
         * @throws Exception if the input was detected to be an invalid VP8
         * Payload Descriptor
         * TODO:make the above exception more specific
         * @throws ArrayIndexOutOfBoundsException if <tt>input</tt> contains
         * less than the required amount of bytes (e.g. if <tt>input</tt> has
         * length 1, but the 'extended control bits present' bit is set)
         */
        int parse(byte[] input) throws Exception
        {
            int idx = 0;

            X = (byte) (input[idx] & 0x80);
            //second bit is a reserved field
            N = (byte) (input[idx] & 0x20);
            S = (byte) (input[idx] & 0x10);
            PartID = (byte) (input[idx] & 0x0f);

            if(X == 0)
            {
                return idx+1;
            }

            idx++;
            I = (byte) (input[idx] & 0x80);
            L = (byte) (input[idx] & 0x40);
            T = (byte) (input[idx] & 0x20);
            K = (byte) (input[idx] & 0x10);

            if(I != 0)
            {
                idx++;
                PictureID = input[idx] & 0x7f;
                if( (input[idx] & 0x80) == 1)
                {
                    idx++;
                    PictureID = PictureID * 128 + input[idx];
                }
            }

            if(L != 0)
            {
                idx++;
                TL0PICIDX = input[idx];
                if(T == 0)
                    throw new Exception("Could not parse VP8 Payload Descriptor");
            }

            if( (T != 0) || (K != 0))
            {
                idx++;

                if(T != 0)
                {
                   TID = (input[idx] & 0xc0) >> 6;
                   Y = input[idx] & 0x20;
                }
                if(K != 0)
                {
                    KEYIDX = input[idx] & 0x1f;
                }
            }

            return idx+1;
        }
    }
}
