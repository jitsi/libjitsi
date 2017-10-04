package org.jitsi.impl.neomedia.rtcp.fmj_port;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTCPReport
 */
public abstract class RTCPReport
    implements Report
{
    // The reason for leaving if this is a bye packet
    private String byeReason = "";

    // The CNAME of the source
    private String cName = null;

    // The vector of feedback reports in this report
    protected Vector<RTCPFeedback> feedbackReports = new Vector<RTCPFeedback>();

    // The header of the report
    protected RTCPHeader header;

    // True if this is a bye event
    private boolean isBye = false;

    // The participant that sent the report
    protected Participant participant;

    // The number of bytes of SDES packets read
    protected int sdesBytes = 0;

    // The source descriptions in the report
    protected Vector<SourceDescription> sourceDescriptions
            = new Vector<SourceDescription>();

    // The ssrc of the report
    private long ssrc = 0;

    /**
     * The <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPReport</tt> has been received or sent by the local endpoint.
     */
    private long systemTimeStamp;

    /**
     * Creates a new <tt>RTCPReport</tt> instance.
     *
     * @param data the data of the report
     * @param offset the offset in the data where the report starts
     * @param length the length of the report in the data
     * @throws IOException if an I/O-related error is encountered
     */
    public RTCPReport(byte data[], int offset, int length) throws IOException
    {
        header = new RTCPHeader(data, offset, length);

        if (header.getPadding() == 1)
            throw new IOException("First packet has padding");
        else if (((header.getLength() + 1) * 4) > length)
            throw new IOException("Invalid Length");

        ssrc = header.getSsrc();

        /*
         * We know the forms of the RTCP sender report (SR) and receiver report
         * (RR). Additionally, we know that they differ, besides the packet type
         * (PT), by a 20-byte sender information section.
         */
        int rtcpReportBlockOffset = RTCPHeader.SIZE;

        switch (header.getPacketType())
        {
            case RTCPPacket.SR:
                rtcpReportBlockOffset += RTCPSenderInfo.SIZE;
                //$FALL-THROUGH$
            case RTCPPacket.RR:
                readFeedbackReports(
                        data,
                        offset + rtcpReportBlockOffset,
                        length - rtcpReportBlockOffset);
                // Read any source descriptions
                offset += (header.getLength() + 1) * 4;
                length -= (header.getLength() + 1) * 4;
                readSourceDescription(data, offset, length);
                offset += sdesBytes;
                length -= sdesBytes;
                readBye(data, offset, length);
                break;
            default:
                // We do not know about any other RTCP report.
                break;
        }
    }

    /**
     * Returns the reason for the bye
     *
     * @return the reason announced for this BYE packet
     */
    public String getByeReason()
    {
        return byeReason;
    }

    /**
     * Gets the cName of the source of the report
     *
     * @return the cName, or null if none sent
     */
    public String getCName()
    {
        return cName;
    }

    /**
     * Returns the feedback reports for this RTCP report.
     *
     * @return the feedback reports for this RTCP report
     */
    public Vector<RTCPFeedback> getFeedbackReports()
    {
        return feedbackReports;
    }

    /**
     * Returns the participant linked with this RTCP report
     *
     * @return the participant identified previously as being linked with this
     *         RTCP report
     */
    public Participant getParticipant()
    {
        return participant;
    }

    /**
     * Returns the sources descriptions (SDES) in this RTCP report.
     *
     * @return the sources descriptions (SDES) in this RTCP report
     */
    public Vector<SourceDescription> getSourceDescription()
    {
        return sourceDescriptions;
    }

    /**
     * Returns the SSRC announced in this report.
     *
     * @return SSRC in this report
     */
    public long getSSRC()
    {
        return ssrc;
    }

    /**
     * Gets the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPReport</tt> has been received or sent by the local endpoint.
     *
     * @return the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPReport</tt> has been received or sent by the local endpoint
     */
    public long getSystemTimeStamp()
    {
        return systemTimeStamp;
    }

    /**
     * Returns true if a bye packet was added to the report
     *
     * @return true if a BYE packet was added to the report
     */
    public boolean isByePacket()
    {
        return isBye;
    }

    /**
     * Reads and handles the BYE part of an RTCP report.
     *
     * @param data
     *            the raw data in which the packet is contained
     * @param offset
     *            the offset where the BYE starts
     * @param length
     *            the length of the report
     * @throws java.io.IOException
     *             I/O Exception
     */
    protected void readBye(byte data[], int offset, int length)
            throws IOException
    {
        if (length > 0)
        {
            RTCPHeader sdesHeader = new RTCPHeader(data, offset, length);
            if (sdesHeader.getPacketType() == RTCPPacket.BYE)
            {
                isBye = true;
                if (((sdesHeader.getLength() + 1) * 4) > RTCPHeader.SIZE)
                {
                    int reasonLen = data[offset + RTCPHeader.SIZE] & 0xFF;
                    if ((reasonLen < (length - RTCPHeader.SIZE))
                            && (reasonLen > 0))
                    {
                        byeReason = new String(data, offset + RTCPHeader.SIZE
                                + 1, reasonLen);
                    }
                }
            }
        }
    }

    /**
     * Reads feedback reports from the data
     *
     * @param data
     *            The data to read the feedback reports from
     * @param offset
     *            The offset into the data where the reports start
     * @param length
     *            The length of the data
     * @throws IOException
     *             I/O Exception
     */
    protected void readFeedbackReports(byte data[], int offset, int length)
            throws IOException
    {
        for (int i = 0; i < header.getReceptionCount(); i++)
        {
            RTCPFeedback feedback = new RTCPFeedback(data, offset, length);
            feedbackReports.add(feedback);
            offset += RTCPFeedback.SIZE;
        }
    }

    /**
     * Reads the source description from the data
     *
     * @param data
     *            The data to read the source description from
     * @param offset
     *            The offset into the data where the SDES packet starts
     * @param length
     *            The length of the data
     * @throws IOException
     *             I/O Exception
     */
    protected void readSourceDescription(byte data[], int offset, int length)
            throws IOException
    {
        if (length > 0)
        {
            // Only do this if there is an SDES header
            RTCPHeader sdesHeader = new RTCPHeader(data, offset, length);

            if (sdesHeader.getPacketType() == RTCPPacket.SDES)
            {
                ssrc = sdesHeader.getSsrc();
                sdesBytes = (sdesHeader.getLength() + 1) * 4;
                DataInputStream stream = new DataInputStream(
                        new ByteArrayInputStream(data,
                                offset + RTCPHeader.SIZE, length));
                int type = SourceDescription.SOURCE_DESC_CNAME;
                while (type != 0)
                {
                    type = stream.readUnsignedByte();
                    if (type != 0)
                    {
                        int len = stream.readUnsignedByte();
                        byte[] desc = new byte[len];
                        stream.readFully(desc);
                        String descStr = new String(desc, "UTF-8");
                        SourceDescription description = new SourceDescription(
                                type, descStr, 0, false);
                        sourceDescriptions.add(description);
                        if (type == SourceDescription.SOURCE_DESC_CNAME)
                        {
                            cName = descStr;
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the participant linked with this RTCP report.
     *
     * @param participant
     *            the participant identified as linked with this RTCP report
     */
    protected void setParticipant(RTPParticipant participant)
    {
        this.participant = participant;
        Vector streams = participant.getStreams();
        if (streams.size() == 0)
        {
            Vector sdes = participant.getSourceDescription();
            for (int i = 0; i < sdes.size(); i++)
            {
                SourceDescription sdesItem = (SourceDescription) sdes.get(i);
                participant.addSourceDescription(sdesItem);
            }
        }
    }

    /**
     * Sets the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPReport</tt> has been received or sent by the local endpoint.
     *
     * @param systemTimeStamp the <tt>System</tt> time in milliseconds at which
     * this <tt>RTCPReport</tt> has been received or sent by the local endpoint
     */
    public void setSystemTimeStamp(long systemTimeStamp)
    {
        this.systemTimeStamp = systemTimeStamp;
    }
}
