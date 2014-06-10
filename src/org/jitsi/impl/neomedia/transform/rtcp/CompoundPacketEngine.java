/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.util.*;

import java.util.*;

/**
 * Implements a <tt>TransformEngine</tt> which splits incoming RTCP compound
 * packets into individual packets.
 *
 * @author Boris Grozev
 */
public class CompoundPacketEngine
        implements TransformEngine, PacketTransformer
{
    /**
     * The <tt>Logger</tt> used by the <tt>CompoundPacketEngine</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(CompoundPacketEngine.class);

    /**
     * The maximum number of individual RTCP packets contained in an RTCP
     * compound packet. If an input packet (seems to) contain more than this,
     * it will remain unchanged.
     */
    final private static int MAX_INDIVIDUAL = 20;

    /**
     * Used in <tt>reverseTransform</tt>, declared here to avoid recreation.
     */
    int[] counts = new int[MAX_INDIVIDUAL];

    /**
     * Close the transformer and underlying transform engine.
     *
     * Nothing to do here.
     */
    public void close()
    {
        System.err.println("compoundpacketengine new RawPacket:"+c);
    }

    /**
     * Returns a reference to this class since it is performing RTCP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>CompoundPacketEngine</tt>.
     */
    public PacketTransformer getRTCPTransformer()
    {
        return this;
    }

    /**
     * Always returns <tt>null</tt> since this engine does not require any
     * RTP transformations.
     *
     * @return <tt>null</tt> since this engine does not require any
     * RTP transformations.
     */
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    private int c=0;
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        int needed = 0; //total number of individual packets in pkts
        boolean needToSplit = false;

        Arrays.fill(counts, 0);

        // calculate needed and fill in this.counts
        for (int i = 0; i < pkts.length; i++)
        {
            RawPacket pkt = pkts[i];
            int individual = 0; //number of individual packets in pkt

            if (pkt != null)
            {
                byte[] buf = pkt.getBuffer();
                int off = pkt.getOffset();
                int len = pkt.getLength();
                int l;
                while( (l = getLengthInBytes(buf, off, len)) >= 0)
                {
                    individual++;
                    off+=l;
                    len-=l;
                }

                if (individual == 0)
                    pkts[i] = null; //invalid RTCP packet. drop it.
                if (individual > 1)
                    needToSplit = true;
                needed += individual;
                counts[i] = individual;
            }
        }

        if (!needToSplit)
            return pkts;

        if (needed > MAX_INDIVIDUAL)
            return pkts; //something went wrong. let the original packet(s) go.

        // allocate a new larger array, if necessary
        if (needed > pkts.length)
        {
            RawPacket[] newPkts = new RawPacket[needed];
            for (int i = 0; i < pkts.length; i++)
                newPkts[i] = pkts[i];

            pkts = newPkts;
        }

        // do the actual splitting
        for (int i = 0; i < pkts.length; i++)
        {
            if(counts[i] > 1) //need to split
            {
                int j; //empty spot always exists, because needed<=pkts.length
                for (j = 0; j<pkts.length; j++)
                    if (pkts[j] == null)
                        break;

                // total off/len
                byte[] oldBuf = pkts[i].getBuffer();
                int oldOff = pkts[i].getOffset();
                int oldLen = pkts[i].getLength();

                //length of the first packet
                int len = getLengthInBytes(oldBuf, oldOff, oldLen);

                byte[] buf = new byte[len];
                System.arraycopy(oldBuf, oldOff, buf, 0, len);
                c++;
                pkts[j] = new RawPacket(buf, 0, len);
                counts[j]++;

                pkts[i].setOffset(oldOff + len);
                pkts[i].setLength(oldLen - len);
                counts[i]--;

                i--; //try that packet one again
            }
        }

        return pkts;
    }

    /**
     * {@inheritDoc}
     */
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
    }

    /**
     * Returns the length in bytes of the RTCP packet contained in <tt>buf</tt>
     * at offset <tt>off</tt>. Assumes that <tt>buf</tt> is valid at least until
     * index <tt>off</tt>+3.
     * @return the length in bytes of the RTCP packet contained in <tt>buf</tt>
     * at offset <tt>off</tt>.
     */
    private static int getLengthInBytes(byte[] buf, int off, int len)
    {
        if (len < 4)
            return -1;
        int v = (buf[off] & 0xc0) >>> 6;
        if (RTCPHeader.VERSION != v)
            return -1;

        int lengthInWords = (buf[off + 2] << 8) + buf[off + 3];
        int lengthInBytes = (lengthInWords + 1) * 4;
        if (len < lengthInBytes)
            return -1;

        return lengthInBytes;
    }

}

