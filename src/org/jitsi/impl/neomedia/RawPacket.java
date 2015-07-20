/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;


/**
 * When using TransformConnector, a RTP/RTCP packet is represented using
 * RawPacket. RawPacket stores the buffer holding the RTP/RTCP packet, as well
 * as the inner offset and length of RTP/RTCP packet data.
 *
 * After transformation, data is also store in RawPacket objects, either the
 * original RawPacket (in place transformation), or a newly created RawPacket.
 *
 * Besides packet info storage, RawPacket also provides some other operations
 * such as readInt() to ease the development process.
 *
 * @author Werner Dittmann (Werner.Dittmann@t-online.de)
 * @author Bing SU (nova.su@gmail.com)
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class RawPacket
{
    /**
     * The size of the extension header as defined by RFC 3550.
     */
    public static final int EXT_HEADER_SIZE = 4;

    /**
     * The size of the fixed part of the RTP header as defined by RFC 3550.
     */
    public static final int FIXED_HEADER_SIZE = 12;

    /**
     * Byte array storing the content of this Packet
     */
    private byte[] buffer;

    /**
     * The bitmap/flag mask that specifies the set of boolean attributes enabled
     * for this <tt>RawPacket</tt>. The value is the logical sum of all of the
     * set flags. The possible flags are defined by the <tt>FLAG_XXX</tt>
     * constants of FMJ's {@link Buffer} class.
     */
    private int flags;

    /**
     * Length of this packet's data
     */
    private int length;

    /**
     * Start offset of the packet data inside buffer.
     * Usually this value would be 0. But in order to be compatible with
     * RTPManager we store this info. (Not assuming the offset is always zero)
     */
    private int offset;

    /**
     * Initializes a new empty <tt>RawPacket</tt> instance.
     */
    public RawPacket()
    {
    }

    /**
     * Initializes a new <tt>RawPacket</tt> instance with a specific
     * <tt>byte</tt> array buffer.
     *
     * @param buffer the <tt>byte</tt> array to be the buffer of the new
     * instance 
     * @param offset the offset in <tt>buffer</tt> at which the actual data to
     * be represented by the new instance starts
     * @param length the number of <tt>byte</tt>s in <tt>buffer</tt> which
     * constitute the actual data to be represented by the new instance
     */
    public RawPacket(byte[] buffer, int offset, int length)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Adds the <tt>extBuff</tt> buffer as an extension of this packet
     * according the rules specified in RFC 5285. Note that this method does
     * not replace extensions so if you add the same buffer twice it would be
     * added as a separate extension.
     *
     * @param extBuff the buffer that we'd like to add as an extension in this
     * packet.
     * @param newExtensionLen the length of the data in extBuff.
     */
    public void addExtension(byte[] extBuff, int newExtensionLen)
    {
        int newBuffLen = length + offset + newExtensionLen;
        int bufferOffset = offset;
        int newBufferOffset = offset;
        int lengthToCopy = FIXED_HEADER_SIZE + getCsrcCount()*4;
        boolean extensionBit = getExtensionBit();
        //if there was no extension previously, we also need to consider adding
        //the extension header.
        if (extensionBit)
        {
            // without copying the extension length value, will set it later
            lengthToCopy += EXT_HEADER_SIZE - 2;
        }
        else
            newBuffLen += EXT_HEADER_SIZE;

        byte[] newBuffer = new byte[ newBuffLen ];

        /*
         * Copy header, CSRC list and the leading two bytes of the extension
         * header if any.
         */
        System.arraycopy(buffer, bufferOffset,
            newBuffer, newBufferOffset, lengthToCopy);
        //raise the extension bit.
        newBuffer[newBufferOffset] |= 0x10;
        bufferOffset += lengthToCopy;
        newBufferOffset += lengthToCopy;

        // Set the extension header or modify the existing one.
        int totalExtensionLen = newExtensionLen + getExtensionLength();

        //if there were no extensions previously, we need to add the hdr now
        if(extensionBit)
        {
            // We've copied "defined by profile" already. Consequently, we have
            // to skip the length only.
            bufferOffset += 2;
        }
        else
        {
           // we will now be adding the RFC 5285 ext header which looks like
           // this:
           //
           //  0                   1                   2                   3
           //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
           // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           // |       0xBE    |    0xDE       |           length=3            |
           // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           newBuffer[newBufferOffset++] = (byte)0xBE;
           newBuffer[newBufferOffset++] = (byte)0xDE;
        }
        // length field counts the number of 32-bit words in the extension
        int lengthInWords = (totalExtensionLen + 3)/4;
        newBuffer[newBufferOffset++] = (byte)(lengthInWords >> 8);
        newBuffer[newBufferOffset++] = (byte)lengthInWords;

        // Copy the existing extension content if any.
        if (extensionBit)
        {
            lengthToCopy = getExtensionLength();
            System.arraycopy(buffer, bufferOffset,
                newBuffer, newBufferOffset, lengthToCopy);
            bufferOffset += lengthToCopy;
            newBufferOffset += lengthToCopy;
        }

        //copy the extension content from the new extension.
        System.arraycopy(extBuff, 0,
            newBuffer, newBufferOffset, newExtensionLen);
        newBufferOffset += newExtensionLen;

        //now copy the payload
        int payloadLength = getPayloadLength();

        System.arraycopy(
                buffer, bufferOffset,
                newBuffer, newBufferOffset,
                payloadLength);
        newBufferOffset += payloadLength;

        buffer = newBuffer;
        this.length = newBufferOffset - offset;
    }

    /**
     * Append a byte array to the end of the packet. This may change the data
     * buffer of this packet.
     *
     * @param data byte array to append
     * @param len the number of bytes to append
     */
    public void append(byte[] data, int len) {
        if (data == null || len == 0)  {
            return;
        }

        // re-allocate internal buffer if it is too small
        if ((this.length + len) > (buffer.length - this.offset)) {
            byte[] newBuffer = new byte[this.length + len];
            System.arraycopy(this.buffer, this.offset, newBuffer, 0, this.length);
            this.offset = 0;
            this.buffer = newBuffer;
        }
        // append data
        System.arraycopy(data, 0, this.buffer, this.length + this.offset, len);
        this.length = this.length + len;

    }

    /**
     * Returns a map binding CSRC IDs to audio levels as reported by the remote
     * party that sent this packet.
     *
     * @param csrcExtID the ID of the extension that's transporting csrc audio
     * levels in the session that this <tt>RawPacket</tt> belongs to.
     *
     * @return an array representing a map binding CSRC IDs to audio levels as
     * reported by the remote party that sent this packet. The entries of the
     * map are contained in consecutive elements of the returned array where
     * elements at even indices stand for CSRC IDs and elements at odd indices
     * stand for the associated audio levels
     */
    public long[] extractCsrcAudioLevels(byte csrcExtID)
    {
        if (!getExtensionBit() || (getExtensionLength() == 0))
            return null;

        int csrcCount = getCsrcCount();

        if (csrcCount == 0)
            return null;

        /*
         * XXX The guideline which is also supported by Google and recommended
         * for Android is that single-dimensional arrays should be preferred to
         * multi-dimensional arrays in Java because the former take less space
         * than the latter and are thus more efficient in terms of memory and
         * garbage collection.
         */
        long[] csrcLevels = new long[csrcCount * 2];

        //first extract the csrc IDs
        for (int i = 0, csrcStartIndex = offset + FIXED_HEADER_SIZE;
                i < csrcCount;
                i++, csrcStartIndex += 4)
        {
            int csrcLevelsIndex = 2 * i;

            csrcLevels[csrcLevelsIndex] = 0xFFFFFFFFL & readInt(csrcStartIndex);
            /*
             * The audio levels generated by Jitsi are not in accord with the
             * respective specification, they are backwards with respect to the
             * value domain. Which means that the audio level generated from a
             * muted audio source is 0/zero.
             */
            csrcLevels[csrcLevelsIndex + 1]
                = getCsrcAudioLevel(csrcExtID, i, (byte) 0);
        }

        return csrcLevels;
    }

    /**
     * Returns the list of CSRC IDs, currently encapsulated in this packet.
     *
     * @return an array containing the list of CSRC IDs, currently encapsulated
     * in this packet.
     */
    public long[] extractCsrcList()
    {
        int csrcCount = getCsrcCount();
        long[] csrcList = new long[csrcCount];

        for (int i = 0, csrcStartIndex = offset + FIXED_HEADER_SIZE;
                i < csrcCount;
                i++, csrcStartIndex += 4)
        {
            csrcList[i] = readInt(csrcStartIndex);
        }

        return csrcList;
    }

    /**
     * Extracts the source audio level reported by the remote party which sent
     * this packet and carried in this packet.
     *
     * @param ssrcExtID the ID of the extension that's transporting ssrc audio
     * levels in the session that this <tt>RawPacket</tt> belongs to
     * @return the source audio level reported by the remote party which sent
     * this packet and carried in this packet or a negative value if this packet
     * contains no extension such as the specified by <tt>ssrcExtID</tt>
     */
    public byte extractSsrcAudioLevel(byte ssrcExtID)
    {
        /*
         * The method getCsrcAudioLevel(byte, int) is implemented with the
         * awareness that there may be a flag bit V with a value other than 0.
         */
        /*
         * The audio levels sent by Google Chrome are in accord with the
         * specification i.e. the audio level generated from a muted audio
         * source is 127 and the values are non-negative. If there is no source
         * audio level in this packet, return a negative value.
         */
        return getCsrcAudioLevel(ssrcExtID, 0, Byte.MIN_VALUE);
    }

    /**
     * Returns the index of the element in this packet's buffer where the
     * content of the header with the specified <tt>extensionID</tt> starts.
     *
     * @param extensionID the ID of the extension whose content we are looking
     * for.
     *
     * @return the index of the first byte of the content of the extension
     * with the specified <tt>extensionID</tt> or -1 if no such extension was
     * found.
     */
    private int findExtension(int extensionID)
    {
        if( !getExtensionBit() || getExtensionLength() == 0)
            return 0;

        int extOffset = offset + FIXED_HEADER_SIZE
                + getCsrcCount()*4 + EXT_HEADER_SIZE;

        int extensionEnd = extOffset + getExtensionLength();
        int extHdrLen = getExtensionHeaderLength();

        if (extHdrLen != 1 && extHdrLen != 2)
        {
            return -1;
        }

        while (extOffset < extensionEnd)
        {
            int currType = -1;
            int currLen = -1;

            if(extHdrLen == 1)
            {
                //short header. type is in the lefter 4 bits and length is on
                //the right; like this:
                //      0
                //      0 1 2 3 4 5 6 7
                //      +-+-+-+-+-+-+-+-+
                //      |  ID   |  len  |
                //      +-+-+-+-+-+-+-+-+

                currType = buffer[extOffset] >> 4;
                currLen = (buffer[extOffset] & 0x0F) + 1; //add one as per 5285

                //now skip the header
                extOffset ++;
            }
            else
            {
                //long header. type is in the first byte and length is in the
                //second
                //       0                   1
                //       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                //      |       ID      |     length    |
                //      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                currType = buffer[extOffset];
                currLen = buffer[extOffset + 1];

                //now skip the header
                extOffset += 2;
            }

            if(currType == extensionID)
            {
                return extOffset;
            }

            extOffset += currLen;
        }

        return -1;
    }

    /**
     * Get buffer containing the content of this packet
     *
     * @return buffer containing the content of this packet
     */
    public byte[] getBuffer()
    {
        return this.buffer;
    }


    /**
     * Returns the CSRC level at the specified index or <tt>0</tt> if there was
     * no level at that index.
     *
     * @param csrcExtID the ID of the extension that's transporting csrc audio
     * levels in the session that this <tt>RawPacket</tt> belongs to.
     * @param index the sequence number of the CSRC audio level extension to
     * return.
     *
     * @return the CSRC audio level at the specified index of the csrc audio
     * level option or <tt>0</tt> if there was no level at that index.
     */
    private byte getCsrcAudioLevel(byte csrcExtID, int index, byte defaultValue)
    {
        byte level = defaultValue;

        if (getExtensionBit() && getExtensionLength() != 0)
        {
            int levelsStart = findExtension(csrcExtID);

            if (levelsStart != -1)
            {
                int levelsCount = getLengthForExtension(levelsStart);

                if (levelsCount < index)
                {
                    //apparently the remote side sent more CSRCs than levels.
                    // ... yeah remote sides do that now and then ...
                }
                else
                {
                    level = (byte) (0x7F & buffer[levelsStart + index]);
                }
            }
        }
        return level;
    }

    /**
     * Returns the number of CSRC identifiers currently included in this packet.
     *
     * @return the CSRC count for this <tt>RawPacket</tt>.
     */
    public int getCsrcCount()
    {
        return (buffer[offset] & 0x0f);
    }

    /**
     * Returns <tt>true</tt> if the extension bit of this packet has been set
     * and <tt>false</tt> otherwise.
     *
     * @return  <tt>true</tt> if the extension bit of this packet has been set
     * and <tt>false</tt> otherwise.
     */
    public boolean getExtensionBit()
    {
        return (buffer[offset] & 0x10) == 0x10;
    }

    /**
     * Returns the length of the extension header being used in this packet or
     * <tt>-1</tt> in case there were no extension headers here or we didn't
     * understand the kind of extension being used.
     *
     * @return  the length of the extension header being used in this packet or
     * <tt>-1</tt> in case there were no extension headers here or we didn't
     * understand the kind of extension being used.
     */
    private int getExtensionHeaderLength()
    {
        if (!getExtensionBit())
            return -1;

        //the type of the extension header comes right after the RTP header and
        //the CSRC list.
        int extLenIndex =  offset + FIXED_HEADER_SIZE + getCsrcCount()*4;

        //0xBEDE means short extension header.
        if (buffer[extLenIndex] == (byte)0xBE
            && buffer[extLenIndex + 1] == (byte)0xDE)
                return 1;

        //0x100 means a two-byte extension header.
        if (buffer[extLenIndex]== (byte)0x10
            && (buffer[extLenIndex + 1] >> 4)== 0)
                return 2;

        return -1;
    }

    /**
     * Returns the length of the extensions currently added to this packet.
     *
     * @return the length of the extensions currently added to this packet.
     */
    public int getExtensionLength()
    {
        if (!getExtensionBit())
            return 0;

        // The extension length comes after the RTP header, the CSRC list, and
        // two bytes in the extension header called "defined by profile".
        int extLenIndex = offset + FIXED_HEADER_SIZE + getCsrcCount() * 4 + 2;

        return
            ((buffer[extLenIndex] << 8) | (buffer[extLenIndex + 1] & 0xFF)) * 4;
    }

    /**
     * Gets the bitmap/flag mask that specifies the set of boolean attributes
     * enabled for this <tt>RawPacket</tt>.
     *
     * @return the bitmap/flag mask that specifies the set of boolean attributes
     * enabled for this <tt>RawPacket</tt>
     */
    public int getFlags()
    {
        return flags;
    }

    /**
     * Return the define by profile part of the extension header.
     * @return the starting two bytes of extension header.
     */
    public int getHeaderExtensionType()
    {
        if (!getExtensionBit())
            return 0;

        return
            readUnsignedShortAsInt(
                    offset + FIXED_HEADER_SIZE + getCsrcCount() * 4);
    }

    /**
     * Get RTP header length from a RTP packet
     *
     * @return RTP header length from source RTP packet
     */
    public int getHeaderLength()
    {
        int headerLength = FIXED_HEADER_SIZE + 4 * getCsrcCount();

        if (getExtensionBit())
            headerLength += EXT_HEADER_SIZE + getExtensionLength();

        return headerLength;
    }

    /**
     * Get the length of this packet's data
     *
     * @return length of this packet's data
     */
    public int getLength()
    {
        return length;
    }

    /**
     * Returns the length of the header extension that is carrying the content
     * starting at <tt>contentStart</tt>. In other words this method checks the
     * size of extension headers in this packet and then either returns the
     * value of the byte right before <tt>contentStart</tt> or its lower 4 bits.
     * This is a very basic method so if you are using it - make sure u know
     * what you are doing.
     *
     * @param contentStart the index of the first element of the content of
     * the extension whose size we are trying to obtain.
     *
     * @return the length of the extension carrying the content starting at
     * <tt>contentStart</tt>.
     */
    private int getLengthForExtension(int contentStart)
    {
        int hdrLen = getExtensionHeaderLength();

        if( hdrLen == 1 )
            return (buffer[contentStart - 1] & 0x0F) + 1;
        else
            return buffer[contentStart - 1];
    }

    /**
     * Get the start offset of this packet's data inside storing buffer
     *
     * @return start offset of this packet's data inside storing buffer
     */
    public int getOffset()
    {
        return this.offset;
    }

    /**
     * Gets the value of the "version" field of an RTP packet.
     * @return the value of the RTP "version" field.
     */
    public int getVersion()
    {
        return (buffer[offset] & 0xC0) >> 6;
    }

    /**
     * Get RTP padding size from a RTP packet
     *
     * @return RTP padding size from source RTP packet
     */
    public int getPaddingSize()
    {
        if ((buffer[offset] & 0x20) == 0)
            return 0;
        else
            return buffer[offset + length - 1];
    }

    /**
     * Get the RTP payload (bytes) of this RTP packet.
     *
     * @return an array of <tt>byte</tt>s which represents the RTP payload of
     * this RTP packet
     */
    public byte[] getPayload()
    {
        return readRegion(getHeaderLength(), getPayloadLength());
    }

    /**
     * Get RTP payload length from a RTP packet
     *
     * @return RTP payload length from source RTP packet
     */
    public int getPayloadLength()
    {
        return length - getHeaderLength();
    }

    /**
     * Get RTP payload type from a RTP packet
     *
     * @return RTP payload type of source RTP packet
     */
    public byte getPayloadType()
    {
        return (byte) (buffer[offset + 1] & (byte)0x7F);
    }

    /**
     * Get RTCP SSRC from a RTCP packet
     *
     * @return RTP SSRC from source RTP packet
     */
    public int getRTCPSSRC()
    {
        return readInt(4);
    }

    /**
     * Gets the packet type of this RTCP packet.
     *
     * @return the packet type of this RTCP packet.
     */
    public int getRTCPPacketType()
    {
        return 0xff & buffer[offset + 1];
    }

    /**
     * Get RTP sequence number from a RTP packet
     *
     * @return RTP sequence num from source packet
     */
    public int getSequenceNumber()
    {
        return readUnsignedShortAsInt(2);
    }

    /**
     * Get SRTCP sequence number from a SRTCP packet
     *
     * @param authTagLen authentication tag length
     * @return SRTCP sequence num from source packet
     */
    public int getSRTCPIndex(int authTagLen)
    {
        int offset = getLength() - (4 + authTagLen);
        return readInt(offset);
    }

    /**
     * Get RTP SSRC from a RTP packet
     *
     * @return RTP SSRC from source RTP packet
     */
    public int getSSRC()
    {
        return readInt(8);
    }

    /**
     * Returns the timestamp for this RTP <tt>RawPacket</tt>.
     *
     * @return the timestamp for this RTP <tt>RawPacket</tt>.
     */
    public long getTimestamp()
    {
        return readInt(4);
    }

    /**
     * Grow the internal packet buffer.
     *
     * This will change the data buffer of this packet but not the
     * length of the valid data. Use this to grow the internal buffer
     * to avoid buffer re-allocations when appending data.
     *
     * @param howMuch number of bytes to grow
     */
    public void grow(int howMuch) {
        if (howMuch == 0) {
            return;
        }
        byte[] newBuffer = new byte[this.length + howMuch];
        System.arraycopy(this.buffer, this.offset, newBuffer, 0, this.length);
        offset = 0;
        buffer = newBuffer;
    }

    /**
     * Perform checks on the packet represented by this instance and
     * return <tt>true</tt> if it is found to be invalid. A return value of
     * <tt>false</tt> does not necessarily mean that the packet is valid.
     *
     * @return <tt>true</tt> if the RTP/RTCP packet represented by this
     * instance is found to be invalid, <tt>false</tt> otherwise.
     */
    public boolean isInvalid()
    {
        return
            (buffer == null)
                || (buffer.length < offset + length)
                || (length < FIXED_HEADER_SIZE);
    }

    /**
     * Test whether the RTP Marker bit is set
     *
     * @return whether the RTP Marker bit is set
     */
    public boolean isPacketMarked()
    {
        return (buffer[offset + 1] & 0x80) != 0;
    }

    /**
     * Read a byte from this packet at specified offset
     *
     * @param off start offset of the byte
     * @return byte at offset
     */
    public byte readByte(int off)
    {
        return buffer[offset + off];
    }

    /**
     * Read a integer from this packet at specified offset
     *
     * @param off start offset of the integer to be read
     * @return the integer to be read
     */
    public int readInt(int off)
    {
        off += offset;
        return
            ((buffer[off++] & 0xFF) << 24)
                | ((buffer[off++] & 0xFF) << 16)
                | ((buffer[off++] & 0xFF) << 8)
                | (buffer[off] & 0xFF);
    }

    /**
     * Read a byte region from specified offset with specified length
     *
     * @param off start offset of the region to be read
     * @param len length of the region to be read
     * @return byte array of [offset, offset + length)
     */
    public byte[] readRegion(int off, int len)
    {
        int startOffset = this.offset + off;
        if (off < 0 || len <= 0 || startOffset + len > this.buffer.length)
            return null;

        byte[] region = new byte[len];

        System.arraycopy(this.buffer, startOffset, region, 0, len);

        return region;
    }

    /**
     * Read a byte region from specified offset with specified length in given
     * buffer
     *
     * @param off start offset of the region to be read
     * @param len length of the region to be read
     * @param outBuff output buffer
     */
    public void readRegionToBuff(int off, int len, byte[] outBuff)
    {
        int startOffset = this.offset + off;
        if (off < 0 || len <= 0 || startOffset + len > this.buffer.length)
            return;

        if (outBuff.length < len)
            return;

        System.arraycopy(this.buffer, startOffset, outBuff, 0, len);
    }

    /**
     * Read a short from this packet at specified offset
     *
     * @param off start offset of this short
     * @return short value at offset
     */
    public short readShort(int off)
    {
        return (short) ((this.buffer[this.offset + off + 0] << 8) |
                        (this.buffer[this.offset + off + 1] & 0xff));
    }

    /**
     * Get RTP timestamp from a RTP packet
     *
     * @return RTP timestamp of source RTP packet
     */
    public byte[] readTimeStampIntoByteArray()
    {
        return readRegion(4, 4);
    }

    /**
     * Read an unsigned integer as long at specified offset
     *
     * @param off start offset of this unsigned integer
     * @return unsigned integer as long at offset
     */
    public long readUnsignedIntAsLong(int off)
    {
        int b0 = (0x000000FF & (this.buffer[this.offset + off + 0]));
        int b1 = (0x000000FF & (this.buffer[this.offset + off + 1]));
        int b2 = (0x000000FF & (this.buffer[this.offset + off + 2]));
        int b3 = (0x000000FF & (this.buffer[this.offset + off + 3]));

        return  ((b0 << 24 | b1 << 16 | b2 << 8 | b3)) & 0xFFFFFFFFL;
    }

    /**
     * Read an unsigned short at specified offset as a int
     *
     * @param off start offset of the unsigned short
     * @return the int value of the unsigned short at offset
     */
    public int readUnsignedShortAsInt(int off)
    {
        int b1 = (0x000000FF & (this.buffer[this.offset + off + 0]));
        int b2 = (0x000000FF & (this.buffer[this.offset + off + 1]));
        int val = b1 << 8 | b2;
        return val;
    }

    /**
     * Removes the extension from the packet and its header.
     */
    public void removeExtension()
    {
        if(!getExtensionBit())
            return;

        int payloadOffset = offset + getHeaderLength();

        int extHeaderLen = getExtensionLength() + EXT_HEADER_SIZE;

        System.arraycopy(buffer, payloadOffset,
            buffer, payloadOffset - extHeaderLen, getPayloadLength());

        this.length -= extHeaderLen;

        setExtensionBit(false);
    }

    /**
     * @param buffer the buffer to set
     */
    public void setBuffer(byte[] buffer)
    {
        this.buffer = buffer;
    }

    /**
     * Replaces the existing CSRC list (even if empty) with <tt>newCsrcList</tt>
     * and updates the CC (CSRC count) field of this <tt>RawPacket</tt>
     * accordingly.
     *
     * @param newCsrcList the list of CSRC identifiers that we'd like to set for
     * this <tt>RawPacket</tt>.
     */
    public void setCsrcList(long[] newCsrcList)
    {
        int newCsrcCount = newCsrcList.length;
        byte[] csrcBuff = new byte[newCsrcCount * 4];
        int csrcOffset = 0;

        for(int i = 0; i < newCsrcList.length; i++)
        {
            long csrc = newCsrcList[i];

            csrcBuff[csrcOffset] = (byte)(csrc >> 24);
            csrcBuff[csrcOffset+1] = (byte)(csrc >> 16);
            csrcBuff[csrcOffset+2] = (byte)(csrc >> 8);
            csrcBuff[csrcOffset+3] = (byte)csrc;

            csrcOffset += 4;
        }

        int oldCsrcCount = getCsrcCount();

        byte[] oldBuffer = this.getBuffer();

        //the new buffer needs to be bigger than the new one in order to
        //accommodate the list of CSRC IDs (unless there were more of them
        //previously than after setting the new list).
        byte[] newBuffer
            = new byte[length + offset + csrcBuff.length - oldCsrcCount*4];

        //copy the part up to the CSRC list
        System.arraycopy(
                    oldBuffer, 0, newBuffer, 0, offset + FIXED_HEADER_SIZE);

        //copy the new CSRC list
        System.arraycopy( csrcBuff, 0, newBuffer,
                        offset + FIXED_HEADER_SIZE, csrcBuff.length);

        //now copy the payload from the old buff and make sure we don't copy
        //the CSRC list if there was one in the old packet
        int payloadOffsetForOldBuff
            = offset + FIXED_HEADER_SIZE + oldCsrcCount*4;

        int payloadOffsetForNewBuff
            = offset + FIXED_HEADER_SIZE + newCsrcCount*4;

        System.arraycopy( oldBuffer, payloadOffsetForOldBuff,
                          newBuffer, payloadOffsetForNewBuff,
                          length - payloadOffsetForOldBuff);

        //set the new CSRC count
        newBuffer[offset] = (byte)((newBuffer[offset] & 0xF0)
                                    | newCsrcCount);

        this.buffer = newBuffer;
        this.length = payloadOffsetForNewBuff + length
                - payloadOffsetForOldBuff - offset;
    }

    /**
     * Raises the extension bit of this packet is <tt>extBit</tt> is
     * <tt>true</tt> or set it to <tt>0</tt> if <tt>extBit</tt> is
     * <tt>false</tt>.
     *
     * @param extBit the flag that indicates whether we are to set or clear
     * the extension bit of this packet.
     */
    private void setExtensionBit(boolean extBit)
    {
        if(extBit)
            buffer[offset] |= 0x10;
        else
            buffer[offset] &= 0xEF;
    }

    /**
     * Sets the bitmap/flag mask that specifies the set of boolean attributes
     * enabled for this <tt>RawPacket</tt>.
     *
     * @param flags the bitmap/flag mask that specifies the set of boolean
     * attributes enabled for this <tt>RawPacket</tt>
     */
    public void setFlags(int flags)
    {
        this.flags = flags;
    }

    /**
     * @param length the length to set
     */
    public void setLength(int length)
    {
        this.length = length;
    }

    /**
     * Sets or resets the marker bit of this packet according to the
     * <tt>marker</tt> parameter.
     * @param marker <tt>true</tt> if we are to raise the marker bit and
     * <tt>false</tt> otherwise.
     */
    public void setMarker(boolean marker)
    {
        if(marker)
        {
             buffer[offset + 1] |= (byte) 0x80;
        }
        else
        {
            buffer[offset + 1] &= (byte) 0x7F;
        }
    }

    /**
     * @param offset the offset to set
     */
    public void setOffset(int offset)
    {
        this.offset = offset;
    }

    /**
     * Sets the payload type of this packet.
     *
     * @param payload the RTP payload type describing the content of this
     * packet.
     */
    public void setPayloadType(byte payload)
    {
        //this is supposed to be a 7bit payload so make sure that the leftmost
        //bit is 0 so that we don't accidentally overwrite the marker.
        payload &= (byte)0x7F;

        buffer[offset + 1] = (byte)((buffer[offset + 1] & 0x80) | payload);
    }

    /**
      * Set the RTP sequence number of an RTP packet
      * @param seq the sequence number to set (only the least-significant 16bits
      * are used)
      */
    public void setSequenceNumber(int seq)
    {
            writeByte(2, (byte) (seq>>8 & 0xff));
            writeByte(3, (byte) (seq & 0xff));
    }

    /**
     * Set the SSRC of this packet
     * @param ssrc SSRC to set
     */
    public void setSSRC(int ssrc)
    {
        writeInt(8, ssrc);
    }

    /**
     * Set the timestamp value of the RTP Packet
     *
     * @param timestamp : the RTP Timestamp
     */
    public void setTimestamp(long timestamp)
    {
        writeInt(4, (int)timestamp);
    }

    /**
     * Shrink the buffer of this packet by specified length
     *
     * @param len length to shrink
     */
    public void shrink(int len)
    {
        if (len <= 0)
            return;

        this.length -= len;
        if (this.length < 0)
            this.length = 0;
    }

    /**
     * Write a byte to this packet at specified offset
     *
     * @param off start offset of the byte
     * @param b byte to write
     */
    public void writeByte(int off, byte b)
    {
        buffer[offset + off] = b;
    }

    /**
     * Set an integer at specified offset in network order.
     *
     * @param off Offset into the buffer
     * @param data The integer to store in the packet
     */
    public void writeInt(int off, int data)
    {
        buffer[offset + off++] = (byte)(data>>24);
        buffer[offset + off++] = (byte)(data>>16);
        buffer[offset + off++] = (byte)(data>>8);
        buffer[offset + off] = (byte)data;
    }
}
