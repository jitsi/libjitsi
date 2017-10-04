package org.jitsi.impl.neomedia.rtcp.fmj_port;

import javax.media.rtp.ReceptionStats;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.RTPStats
 */
public class RTPStats
    implements ReceptionStats
{
    public static final int PDULOST = 0;
    public static final int PDUPROCSD = 1;
    public static final int PDUMISORD = 2;
    public static final int PDUINVALID = 3;
    public static final int PDUDUP = 4;
    public static final int PAYLOAD = 5;
    public static final int ENCODE = 6;
    public static final int QSIZE = 7;
    public static final int PDUDROP = 8;
    public static final int ADUDROP = 9;
    private int numLost;
    private int numProc;
    private int numMisord;
    private int numInvalid;
    private int numDup;
    private int payload;
    private String encodeName;
    private int qSize;
    private int PDUDrop;
    private int ADUDrop;

    /**
     * The burst characteristics/metrics and the algorithm for measuring them.
     */
    private final BurstMetrics burstMetrics = new BurstMetrics();

    public RTPStats()
    {
        numLost = 0;
        numProc = 0;
        numMisord = 0;
        numInvalid = 0;
        numDup = 0;
        qSize = 0;
        PDUDrop = 0;
        ADUDrop = 0;
    }

    public int getADUDrop()
    {
        return ADUDrop;
    }

    public int getBufferSize()
    {
        return qSize;
    }

    public String getEncodingName()
    {
        return encodeName;
    }

    public int getPayloadType()
    {
        return payload;
    }

    public int getPDUDrop()
    {
        return PDUDrop;
    }

    public int getPDUDuplicate()
    {
        return numDup;
    }

    public int getPDUInvalid()
    {
        return numInvalid;
    }

    public int getPDUlost()
    {
        return numLost;
    }

    public int getPDUMisOrd()
    {
        return numMisord;
    }

    public int getPDUProcessed()
    {
        return numProc;
    }

    @Override
    public String toString()
    {
        String s = "PDULost " + getPDUlost() + "\nPDUProcessed "
                + getPDUProcessed() + "\nPDUMisord " + getPDUMisOrd()
                + "\nPDUInvalid " + getPDUInvalid() + "\nPDUDuplicate "
                + getPDUDuplicate();
        return s;
    }

    public synchronized void update(int which)
    {
        switch (which)
        {
            case PDULOST:
                numLost++;
                getBurstMetrics().update(which);
                break;

            case PDUPROCSD:
                numProc++;
                getBurstMetrics().update(which);
                break;

            case PDUMISORD:
                numMisord++;
                break;

            case PDUINVALID:
                numInvalid++;
                break;

            case PDUDUP:
                numDup++;
                break;

            case PDUDROP:
                PDUDrop++;
                getBurstMetrics().update(which);
                break;
        }
    }

    public synchronized void update(int which, int amount)
    {
        switch (which)
        {
            case PDULOST:
                if (amount > 0)
                {
                /*
                 * The incrementing of PDULOST is already handled by the method
                 * update(int). Do not duplicate the source code for the sake of
                 * maintenance.
                 */
                    while (amount-- > 0) update(which);
                }
                else
                {
                    numLost += amount;
                }
                break;

            case PAYLOAD:
                payload = amount;
                break;

            case QSIZE:
                qSize = amount;
                break;

            case PDUDROP:
                PDUDrop = amount;
                break;

            case ADUDROP:
                ADUDrop = amount;
                break;
        }
    }

    public synchronized void update(int which, String name)
    {
        if (which == ENCODE)
            encodeName = name;
    }

    /**
     * Gets the burst characteristics/metrics and the algorithm for measuring
     * them.
     *
     * @return the burst characteristics/metrics and the algorithm for measuring
     * them
     */
    public BurstMetrics getBurstMetrics()
    {
        return burstMetrics;
    }
}
