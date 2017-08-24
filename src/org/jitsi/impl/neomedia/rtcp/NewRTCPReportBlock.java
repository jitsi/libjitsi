package org.jitsi.impl.neomedia.rtcp;

import javax.media.rtp.rtcp.*;
import java.io.*;

/*
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_1 (SSRC of first source)                 |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       | fraction lost |       cumulative number of packets lost       |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           extended highest sequence number received           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      interarrival jitter                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         last SR (LSR)                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                   delay since last SR (DLSR)                  |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 */
public class NewRTCPReportBlock implements Feedback
{
    public static String toString(NewRTCPReportBlock reports[])
    {
        String s = "";
        for (int i = 0; i < reports.length; i++)
            s = s + reports[i];

        return s;
    }

    protected long ssrc;
    protected int fractionLost;
    protected int packetsLost;
    protected long lastSeq;
    protected int jitter;
    protected long lsr;
    protected long dlsr;

    public NewRTCPReportBlock(long ssrc,
                           int fractionLost,
                           int packetsLost,
                           long lastSeq,
                           int jitter,
                           long lsr,
                           long dlsr)
    {
        this.ssrc = ssrc;
        this.fractionLost = fractionLost;
        this.packetsLost = packetsLost;
        this.lastSeq = lastSeq;
        this.jitter = jitter;
        this.lsr = lsr;
        this.dlsr = dlsr;
    }

    static NewRTCPReportBlock parse(DataInputStream inputStream)
        throws IOException
    {
        long ssrc = inputStream.readInt() & 0xFFFFFFFFL;
        long val = inputStream.readInt()  & 0xFFFFFFFFL;
        int fractionLost = (int) (val >> 24);
        int packetsLost = (int) (val & 0xffffffL);
        long lastSeq = inputStream.readInt() & 0xffffffffL;
        int jitter = inputStream.readInt();
        long lsr = inputStream.readInt() & 0xffffffffL;
        long dlsr = inputStream.readInt() & 0xffffffffL;

        return new NewRTCPReportBlock(ssrc,
            fractionLost,
            packetsLost,
            lastSeq,
            jitter,
            lsr,
            dlsr);
    }

    public long getDLSR()
    {
        return dlsr;
    }

    public int getFractionLost()
    {
        return fractionLost;
    }

    public long getJitter()
    {
        return jitter;
    }

    public long getLSR()
    {
        return lsr;
    }

    public long getNumLost()
    {
        return packetsLost;
    }

    public long getSSRC()
    {
        return ssrc;
    }

    public long getXtndSeqNum()
    {
        return lastSeq;
    }

    @Override
    public String toString()
    {
        long printssrc = 0xFFFFFFFFL & ssrc;

        return "\t\tFor source " + printssrc
            + "\n\t\t\tFraction of packets lost: " + fractionLost + " ("
            + fractionLost / 256D + ")" + "\n\t\t\tPackets lost: "
            + packetsLost + "\n\t\t\tLast sequence number: " + lastSeq
            + "\n\t\t\tJitter: " + jitter
            + "\n\t\t\tLast SR packet received at time " + lsr
            + "\n\t\t\tDelay since last SR packet received: " + dlsr + " ("
            + dlsr / 65536D + " seconds)\n";
    }
}