package org.jitsi.impl.neomedia.rtcp.fmj_port;

import net.sf.fmj.media.rtp.RTCPSenderInfo;

import javax.media.rtp.RTPStream;
import java.io.IOException;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPSenderReport
 */
public class RTCPSenderReport
    extends RTCPReport
    implements SenderReport
{
    // The sender information
    RTCPSenderInfo senderInformation = null;

    // The RTPStream associated with the sender
    private RTPStream stream = null;

    /**
     * Creates a new RTCPSenderReport
     *
     * @param data
     *            The data of the report
     * @param offset
     *            The offset of the report in the data
     * @param length
     *            The length of the data
     * @throws IOException
     *             I/O Exception
     */
    public RTCPSenderReport(byte data[], int offset, int length)
            throws IOException
    {
        super(data, offset, length);

        senderInformation
                = new RTCPSenderInfo(
                data,
                offset + RTCPHeader.SIZE,
                length - RTCPHeader.SIZE);
    }

    /**
     * Returns the sender's timestamp's least significant word.
     *
     * @return the sender's timestamp's least significant word
     */
    public long getNTPTimeStampLSW()
    {
        return senderInformation.getNtpTimestampLSW();
    }

    /**
     * Returns the sender's timestamp's most significant word.
     *
     * @return the sender's timestamp's most significant word
     */
    public long getNTPTimeStampMSW()
    {
        return senderInformation.getNtpTimestampMSW();
    }

    /**
     * Returns the RTP timestamp.
     *
     * @return the RTP timestamp
     */
    public long getRTPTimeStamp()
    {
        return senderInformation.getRtpTimestamp();
    }

    /**
     * Returns the number of bytes sent by this sender.
     *
     * @return the number of bytes sent by this sender
     */
    public long getSenderByteCount()
    {
        return senderInformation.getOctetCount();
    }

    /**
     * Returns the sender's feedbacks.
     *
     * @return the sender's feedbacks
     */
    public Feedback getSenderFeedback()
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Returns the number of packets sent by this sender.
     *
     * @return the number of packets sent by this sender
     */
    public long getSenderPacketCount()
    {
        return senderInformation.getPacketCount();
    }

    /**
     * Returns the RTPStream associated with the sender.
     *
     * @return the RTPStream associated with the sender
     */
    public RTPStream getStream()
    {
        return stream;
    }

    /**
     * Sets the RTPStream associated with the sender.
     *
     * @param stream
     *            the RTPStream associated with the sender
     */
    protected void setStream(RTPStream stream)
    {
        this.stream = stream;
    }
}
