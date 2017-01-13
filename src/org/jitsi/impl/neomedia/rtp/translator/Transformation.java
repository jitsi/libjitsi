package org.jitsi.impl.neomedia.rtp.translator;

/**
 * @author George Politis
 */
public class Transformation
{
    public Transformation(long tsDelta, int seqNumDelta)
    {
        this.seqNumDelta = seqNumDelta;
        this.tsDelta = tsDelta;
    }
    /**
     * The sequence number delta (mod 16) to apply to the RTP packets of the
     * forwarded RTP stream.
     */
    private final long tsDelta;

    /**
     * The timestamp delta (mod 32) to apply to the RTP packets of the
     * forwarded RTP stream.
     */
    private final int seqNumDelta;

    /**
     *
     * @param ts
     * @return
     */
    public long rewriteTimestamp(long ts)
    {
        return tsDelta == 0 ? ts : (ts + tsDelta) & 0xFFFFFFFFL;
    }

    /**
     *
     * @param seqNum
     * @return
     */
    public int rewriteSeqNum(int seqNum)
    {
        return seqNumDelta == 0 ? seqNum : (seqNum + seqNumDelta) & 0xFFFF;
    }
}
