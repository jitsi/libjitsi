package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTPHeader
 */
public class RTPHeader
{
    /**
     * The current RTP Header Version
     */
    public static final int VERSION = 2;

    /**
     * The maximum payload type
     */
    public static final int MAX_PAYLOAD = 127;

    /**
     * The size of the RTP Header
     */
    public static final int SIZE = 12;

    /**
     * The maximum RTP sequence
     */
    public static final int MAX_SEQUENCE = 65535;

    /**
     * Unsigned int to long conversion mask
     */
    public static final long UINT_TO_LONG_CONVERT = 0x00000000ffffffffL;

    // Header bit masks and shifts
    private static final int VERSION_MASK = 0xc000;

    private static final int VERSION_SHIFT = 14;

    private static final int PADDING_MASK = 0x2000;

    private static final int PADDING_SHIFT = 13;

    private static final int EXTENSION_MASK = 0x1000;

    private static final int EXTENSION_SHIFT = 12;

    private static final int CSRC_MASK = 0x0f00;

    private static final int CSRC_SHIFT = 8;

    private static final int MARKER_MASK = 0x0080;

    private static final int MARKER_SHIFT = 7;

    private static final int TYPE_MASK = 0x007f;

    private static final int TYPE_SHIFT = 0;

    // The first 16 bits of the header
    private int flags;

    // The second 16 bits of the header
    private int sequence;

    // The third and fourth 16 bits of the header
    private long timestamp;

    // The fifth and sixth 16 bits of the header
    private long ssrc;

    /**
     * Creates a new RTPHeader
     *
     * @param offset
     *            the offset after which the header starts
     * @param length
     *            the total length
     * @param data
     *            The packet to parse the header from
     * @throws IOException
     *             I/O Exception
     */
    public RTPHeader(byte[] data, int offset, int length) throws IOException
    {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(
                data, offset, length));

        // Read the header values
        this.flags = stream.readUnsignedShort();
        this.sequence = stream.readUnsignedShort();
        this.timestamp = stream.readInt() & UINT_TO_LONG_CONVERT;
        this.ssrc = stream.readInt() & UINT_TO_LONG_CONVERT;

        if (getVersion() != VERSION)
        {
            throw new IOException("Invalid Version");
        }
    }

    /**
     * Creates a new RTPHeader
     *
     * @param packet
     *            The packet to parse the header from
     * @throws IOException
     *             I/O Exception
     */
    public RTPHeader(DatagramPacket packet) throws IOException
    {
        this(packet.getData(), packet.getOffset(), packet.getLength());
    }

    /**
     * @return A count of Csrcs in the packet
     */
    short getCsrcCount()
    {
        return (short) ((getFlags() & CSRC_MASK) >> CSRC_SHIFT);
    }

    /**
     * @return Any extension to the header
     */
    short getExtension()
    {
        return (short) ((getFlags() & EXTENSION_MASK) >> EXTENSION_SHIFT);
    }

    /**
     * Returns the flags of the header
     *
     * @return The flags of the header
     */
    public int getFlags()
    {
        return flags;
    }

    /**
     * @return The marker of the packet
     */
    short getMarker()
    {
        return (short) ((getFlags() & MARKER_MASK) >> MARKER_SHIFT);
    }

    /**
     * @return The type of the data in the packet
     */
    short getPacketType()
    {
        return (short) ((getFlags() & TYPE_MASK) >> TYPE_SHIFT);
    }

    /**
     * @return The padding in the data of the packet
     */
    short getPadding()
    {
        return (short) ((getFlags() & PADDING_MASK) >> PADDING_SHIFT);
    }

    /**
     * @return The sequence number of the packet
     */
    int getSequence()
    {
        return sequence;
    }

    int getSize()
    {
        return SIZE + (getCsrcCount() * 4);
    }

    /**
     * @return The ssrc of the data source
     */
    long getSsrc()
    {
        return ssrc;
    }

    /**
     * @return The timestamp of the packet
     */
    long getTimestamp()
    {
        return timestamp;
    }

    /**
     * @return The RTP version implemented
     */
    short getVersion()
    {
        return (short) ((getFlags() & VERSION_MASK) >> VERSION_SHIFT);
    }

    /**
     * Prints the header
     *
     */
    public void print()
    {
        System.err.println(getVersion() + "|" + getPadding() + "|"
                + getExtension() + "|" + getCsrcCount() + "|" + getMarker()
                + "|" + getPacketType() + "|" + getSequence() + "|"
                + getTimestamp() + "|" + getSsrc());
    }
}
