package org.jitsi.impl.neomedia.rtcp.fmj_port;

/**
 * NOTE(brian): was net.sf.fmj.media.rtp.BurstMetrics
 */

/**
 * Represents the burst metrics (and packet loss and discard metrics) defined by
 * RFC 3611 for VoIP Metrics Report Block and the algorithm for measuring them
 * implemented in Appendix A.2.
 *
 * @author Lyubomir Marinov
 */
public class BurstMetrics
{
    /**
     * The gap threshold.
     */
    private static final short GMIN = 16;

    /**
     * The burst metrics (and packet loss and discard metrics) measured by this
     * algorithm.
     */
    private long burstMetrics;

    /**
     * The state transition counts defined by Appendix A.2 of RFC 3611.
     */
    private long c11, c13, c14, c22, c23, c33;

    /**
     * The indicator which determines whether the method {@link #calculate()} is
     * to be invoked before reading {@link #burstMetrics} i.e. <tt>false</tt> if
     * <tt>burstMetrics</tt> is calculated; otherwise, <tt>true</tt>.
     */
    private boolean calculate;

    /**
     * The number of packets discarded since the start of the reception.
     */
    private long discardCount;

    /**
     * The number of packets lost since the start of the reception.
     */
    private long lossCount;

    /**
     * The number of packets lost within the current burst.
     */
    private long lost;

    /**
     * The number of packets received since the last packet was declared lost or
     * discarded.
     */
    private long pkt;

    /**
     * Calculates {@link #burstMetrics}.
     */
    private synchronized void calculate()
    {
        // Calculate additional transition counts.
        long s1x = c11 + c13 + c14;
        long s2x = c22 + c23;
        long s3x = c13 + c23 + c33;
        long s = s1x + s2x + s3x;

        // Calculate burst and gap densities.
        double p23 = (c22 <= 0) ? 1 : (1 - c22 / (double) s2x);
        double p32 = (c23 <= 0) ? 0 : (c23 / (double) s3x);
        long burstDensity;
        long gapDensity;

        if (p23 <= 0)
        {
            burstDensity = 0;
        }
        else
        {
            burstDensity = (long) (256 * p23 / (p23 + p32));
            if (burstDensity > 255)
                burstDensity = 255;
        }
        if (c14 <= 0)
        {
            gapDensity = 0;
        }
        else
        {
            gapDensity = (long) (256 * c14 / (double) (c11 + c14));
            if (gapDensity > 255)
                gapDensity = 255;
        }

        // Calculate burst and gap durations in ms.
        long gapDuration;
        long burstDuration;
        int msPerPkt = 20;

        if (c13 <= 0)
        {
            gapDuration = 0;
            burstDuration = 0;
        }
        else
        {
            gapDuration = s1x * msPerPkt / c13;
            burstDuration = s * msPerPkt / c13 - gapDuration;
        }

        // Calculate loss and discard rates.
        long lossRate;
        long discardRate;

        if (s <= 0)
        {
            lossRate = 0;
            discardRate = 0;
        }
        else
        {
            lossRate = 256 * lossCount / s;
            if (lossRate > 255)
                lossRate = 255;
            discardRate = 256 * discardCount / s;
            if (discardRate > 255)
                discardRate = 255;
        }

        burstMetrics = lossRate & 0xFFL;
        burstMetrics <<= 8;
        burstMetrics |= discardRate & 0xFFL;
        burstMetrics <<= 8;
        burstMetrics |= burstDensity & 0xFFL;
        burstMetrics <<= 8;
        burstMetrics |= gapDensity & 0xFFL;
        burstMetrics <<= 8;
        burstMetrics |= burstDuration & 0xFFFFL;
        burstMetrics <<= 16;
        burstMetrics |= gapDuration & 0xFFFFL;
        calculate = false;
    }

    /**
     * Gets the burst metrics (and packet loss and discard metrics) measured by
     * this algorithm.
     *
     * @return the burst metrics (and packet loss and discard metrics) measured
     * by this algorithm as they appear in the VoIP Metrics Report Block i.e.
     * 8-bit loss rate, 8-bit discard rate, 8-bit burst density, 8-bit gap
     * density, 16-bit burst duration, and 16-bit gap duration.
     */
    public synchronized long getBurstMetrics()
    {
        if (calculate)
            calculate();
        return burstMetrics;
    }

    /**
     * Gets the gap threshold.
     *
     * @return the gap threshold
     */
    public short getGMin()
    {
        return GMIN;
    }

    /**
     * Notifies this algorithm that a packet has been received, lost, or
     * discarded.
     *
     * @param which {@link RTPStats#PDUPROCSD}, {@link RTPStats#PDULOST}, or
     * {@link RTPStats#PDUDROP}
     */
    public synchronized void update(int which)
    {
        boolean calculate;

        if (which == RTPStats.PDULOST)
        {
            lossCount++;
            calculate = true;
        }
        else if (which == RTPStats.PDUDROP)
        {
            discardCount++;
            calculate = true;
        }
        else
        {
            pkt++;
            calculate = false;
        }
        if (calculate)
        {
            if (pkt >= GMIN)
            {
                if (lost == 1)
                {
                    c14++;
                }
                else
                {
                    c13++;
                    lost = 1;
                }
                c11 += pkt;
            }
            else
            {
                lost++;
                if (pkt == 0)
                {
                    c33++;
                }
                else
                {
                    c23++;
                    c22 += (pkt - 1);
                }
            }
            pkt = 0;

            this.calculate = true;
        }
    }
}
