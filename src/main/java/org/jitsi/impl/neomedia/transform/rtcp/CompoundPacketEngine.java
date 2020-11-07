/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import java.util.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements a <tt>TransformEngine</tt> which splits incoming RTCP compound
 * packets into individual packets.
 *
 * @author Boris Grozev
 */
public class CompoundPacketEngine
    implements TransformEngine,
               PacketTransformer
{
    /**
     * The maximum number of individual RTCP packets contained in an RTCP
     * compound packet. If an input packet (seems to) contain more than this,
     * it will remain unchanged.
     */
    private final static int MAX_INDIVIDUAL = 20;

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

        int lengthInWords = ((buf[off + 2] & 0xFF) << 8) | (buf[off + 3] & 0xFF);
        int lengthInBytes = (lengthInWords + 1) * 4;
        if (len < lengthInBytes)
            return -1;

        return lengthInBytes;
    }

    /**
     * Used in <tt>reverseTransform</tt>, declared here to avoid recreation.
     */
    private final int[] counts = new int[MAX_INDIVIDUAL];

    /**
     * Closes the transformer and underlying transform engine.
     *
     * Nothing to do here.
     */
    @Override
    public void close()
    {
    }

    /**
     * Returns a reference to this class since it is performing RTCP
     * transformations in here.
     *
     * @return a reference to <tt>this</tt> instance of the
     * <tt>CompoundPacketEngine</tt>.
     */
    @Override
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
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    @Override
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

            System.arraycopy(pkts, 0, newPkts, 0, pkts.length);
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
                pkts[j] = new RawPacket(buf, 0, len);
                counts[j]++;

                pkts[i].setOffset(oldOff + len);
                pkts[i].setLength(oldLen - len);
                counts[i]--;

                i--; //try that packet once again
            }
        }

        return pkts;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>CompoundPacketEngine</tt> does not transform
     * when sending RTCP packets because the only purpose is to split received
     * compound RTCP packets.
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
    }
}
