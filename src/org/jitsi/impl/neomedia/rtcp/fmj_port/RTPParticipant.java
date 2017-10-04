package org.jitsi.impl.neomedia.rtcp.fmj_port;

import javax.media.rtp.RTPStream;
import java.util.HashMap;
import java.util.Vector;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTPParticipant
 */
public class RTPParticipant
    implements Participant
{
    // The streams of the participant
    private Vector streams = new Vector();

    // The RTCP reports of the participant
    private HashMap rtcpReports = new HashMap();

    // The CNAME of the particpant
    private String cName = "";

    // A vector of source description objects
    protected HashMap sourceDescriptions = new HashMap();

    // True if the participant is active
    private boolean active = false;

    // The size of the sdes elements combined in SDES format
    private int sdesSize = 0;

    // Time on which the last report from this participant was received
    // We set it to the current to avoid getting directly timed out
    protected long lastReportTime = System.currentTimeMillis();

    /**
     * Creates a new RTPParticipant
     *
     * @param cName
     *            the RTP CNAME of this participant.
     */
    public RTPParticipant(String cName)
    {
        this.cName = cName;
        addSourceDescription(new SourceDescription(
                SourceDescription.SOURCE_DESC_CNAME, cName, 1, false));
        addSourceDescription(new SourceDescription(
                SourceDescription.SOURCE_DESC_NAME, cName, 1, false));
    }

    /**
     * Adds an RTCP Report for this participant
     *
     * @param report
     *            The report to add
     */
    public void addReport(javax.media.rtp.rtcp.Report report)
    {
        lastReportTime = System.currentTimeMillis();
        rtcpReports.put(new Long(report.getSSRC()), report);
        Vector sdes = report.getSourceDescription();
        for (int i = 0; i < sdes.size(); i++)
        {
            addSourceDescription((SourceDescription) sdes.get(i));
        }

        if ((streams.size() == 0) && (report instanceof RTCPReport))
        {
            ((RTCPReport) report).sourceDescriptions = new Vector(
                    sourceDescriptions.values());
        }
    }

    /**
     * Adds a source description item to the participant
     *
     * @param sdes
     *            The SDES item to add
     */
    protected void addSourceDescription(SourceDescription sdes)
    {
        SourceDescription oldSdes = (SourceDescription) sourceDescriptions
                .get(new Integer(sdes.getType()));
        if (oldSdes != null)
        {
            sdesSize -= oldSdes.getDescription().length();
            sdesSize -= 2;
        }
        sourceDescriptions.put(new Integer(sdes.getType()), sdes);
        sdesSize += 2;
        sdesSize += sdes.getDescription().length();
    }

    /**
     * Adds a stream to the participant
     *
     * @param stream
     *            stream to associate with this participant
     */
    protected void addStream(RTPStream stream)
    {
        streams.add(stream);
    }

    /**
     * Returns this participant's RTP CNAME.
     *
     * @return this participant's RTP CNAME
     */
    public String getCNAME()
    {
        return cName;
    }

    /**
     * Returns this participant's last report time, which is the last time he's
     * sent us a report.
     *
     * @return the participant's last report time
     */
    public long getLastReportTime()
    {
        return lastReportTime;
    }

    /**
     * Returns the reports associated with this participant.
     *
     * @return the reports associated with this participant
     */
    public Vector getReports()
    {
        return new Vector(rtcpReports.values());
    }

    /**
     * Returns the number of bytes of sdes that this participant requires.
     *
     * @return the number of bytes of sdes that this participant requires
     */
    public int getSdesSize()
    {
        return sdesSize;
    }

    /**
     * Returns the sources descriptions (SDES) associated with this participant.
     *
     * @return the sources descriptions (SDES) associated with this participant
     */
    public Vector getSourceDescription()
    {
        return new Vector(sourceDescriptions.values());
    }

    /**
     * Returns the streams associated with this participant.
     *
     * @return the streams associated with this participant
     */
    public Vector getStreams()
    {
        return streams;
    }

    /**
     * Returns true if the participant is active
     *
     * @return true if the participant is active
     */
    public boolean isActive()
    {
        return active;
    }

    /**
     * Removes the specified stream from this participant's associated streams
     * list.
     *
     * @param stream
     *            the stream to erase
     */
    protected void removeStream(RTPStream stream)
    {
        streams.remove(stream);
    }

    /**
     * Sets the participant active or inactive
     *
     * @param active
     *            Activity of the participant, true if active
     */
    protected void setActive(boolean active)
    {
        this.active = active;
    }

}
