package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPFeedback
 */
public class RTCPFeedback
    implements Feedback
{
    /**
     * The size of a feedback report in bytes
     */
    public static final int SIZE = 24;

    // The SSRC that this is feedback for
    private long ssrc = 0;

    // The fraction of packets lost
    private int fractionLost = 0;

    // The number of packets lost
    private long numLost = 0;

    // The extended highest sequence number
    private long xtndSeqNum = 0;

    // The jitter
    private long jitter = 0;

    // The LSR
    private long lsr = 0;

    // The DLSR
    private long dlsr = 0;

    /**
     * Creates a new RTCP Feedback report
     *
     * @param data
     *            The data to read from
     * @param offset
     *            The offset into the data where the report starts
     * @param length
     *            The length of the report
     * @throws IOException
     *             I/O Exception
     */
    public RTCPFeedback(byte[] data, int offset, int length) throws IOException
    {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(
                data, offset, length));
        ssrc = stream.readInt() & RTPHeader.UINT_TO_LONG_CONVERT;
        fractionLost = stream.readUnsignedByte();
        numLost = (stream.readUnsignedShort() << 8) | stream.readUnsignedByte();
        xtndSeqNum = stream.readInt() & RTPHeader.UINT_TO_LONG_CONVERT;
        jitter = stream.readInt() & RTPHeader.UINT_TO_LONG_CONVERT;
        lsr = stream.readInt() & RTPHeader.UINT_TO_LONG_CONVERT;
        dlsr = stream.readInt() & RTPHeader.UINT_TO_LONG_CONVERT;
    }

    /**
     * Returns the delay since last SR (DLSR).
     *
     * @return the delay since last SR (DLSR)
     */
    public long getDLSR()
    {
        return dlsr;
    }

    /**
     * Returns the fraction of RTP data packets from source SSRC_n lost since
     * the previous SR or RR packet was sent
     *
     * @return Returns the fraction of packets lost
     */
    public int getFractionLost()
    {
        return fractionLost;
    }

    /**
     * Returns the interarrival jitter An estimate of the statistical variance
     * of the RTP data packet interarrival time, measured in timestamp units and
     * expressed as an unsigned integer.
     *
     * @return the interarrival jitter
     */
    public long getJitter()
    {
        return jitter;
    }

    /**
     * Returns last SR timestamp (LSR).
     *
     * @return the last SR timestamp (LSR)
     */
    public long getLSR()
    {
        return lsr;
    }

    /**
     * Returns the number of RTP data packets from source SSRC_n lost since the
     * previous SR or RR packet was sent. This number is a cumulatative count
     * (like most of the values used in the RFC).
     *
     * @return Returns the number of packets lost
     */
    public long getNumLost()
    {
        return numLost;
    }

    /**
     * Returns the SSRC corresponding to the feedback.
     *
     * @return The SSRC
     */
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * Returns the extended highest sequence number received. The low 16 bits
     * contain the highest sequence number received in an RTP data packet from
     * source SSRC_n, and the most significant 16 bits extend that sequence
     * number with the corresponding count of sequence number cycles
     *
     * @return the extended highest sequence number received.
     */
    public long getXtndSeqNum()
    {
        return xtndSeqNum;
    }

}
