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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Appends an Original Header Block packet extension to incoming packets.
 * Note that we currently do NOT follow the PERC format, but rather an extended
 * backward compatible format.
 * {@see "https://tools.ietf.org/html/draft-ietf-perc-double-02"}
 *
 * Specifically the format the we currently append is
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-------------+---------------+-------------------------------+
 *  |  id  | len  |R|     PT      |        Sequence Number        |
 *  +-------------+---------------+-------------------------------+
 *  |                         Timestamp                           |
 *  +-------------------------------------------------------------+
 *  |                            SSRC                             |
 *  +-------------------------------------------------------------+
 *
 * }</pre>
 *
 * @author Boris Grozev
 */
public class OriginalHeaderBlockTransformEngine
    extends SinglePacketTransformerAdapter
    implements TransformEngine
{
    /**
     * The <tt>Logger</tt> used by the
     * {@link OriginalHeaderBlockTransformEngine} class and its instances.
     */
    private static final Logger logger
        = Logger.getLogger(OriginalHeaderBlockTransformEngine.class);

    /**
     * The ID of the OHB RTP header extension, or -1 if it is not enabled.
     */
    private int extensionID = -1;

    /**
     * Initializes a new {@link OriginalHeaderBlockTransformEngine} instance.
     */
    public OriginalHeaderBlockTransformEngine()
    {
        super(RTPPacketPredicate.INSTANCE);
    }

    /**
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (extensionID != -1)
        {
            // TODO: check if an OHB ext already exists.
            addExtension(pkt);
        }
        return pkt;
    }

    /**
     * Here we would re-form or remove the OHB extension to only include fields
     * which we modified, in order to reduce the overhead.
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        return pkt;
        // We would want to do something like the below if we wanted to optimize
        // the packet size (by only including the modified fields in the OHB).
        /*
        if (extensionID != -1)
        {
            RawPacket.HeaderExtension ohb = pkt.getHeaderExtension(extensionID);
            if (ohb != null)
            {
                rebuildOhb(pkt, ohb);
            }
        }
        return pkt;
        */
    }

    /**
     * Removes any unmodified fields from the OHB header extension of a
     * {@link RawPacket}.
     * @param pkt the packet.
     * @param ohb the OHB header extension.
     */
    private void rebuildOhb(RawPacket pkt, RawPacket.HeaderExtension ohb)
    {
        byte[] buf = ohb.getBuffer();
        int off = ohb.getOffset();

        // Make sure it was us who added the OHB (in reverseTransform). If it
        // came from elsewhere (i.e. the sender), we should handle it in
        // another way.
        int len = ohb.getExtLength();
        if (len != 11)
        {
            logger.warn("Unexpected OHB length.");
            return;
        }

        // The new, potentially modified values.
        byte pt = pkt.getPayloadType();
        int seq = pkt.getSequenceNumber();
        long ts = pkt.getTimestamp();
        long ssrc = pkt.getSSRCAsLong();

        // The original values.
        byte origPt = buf[off + 1];
        int origSeq = RTPUtils.readUint16AsInt(buf, off + 2);
        long origTs = RTPUtils.readUint32AsLong(buf, off + 4);
        long origSsrc = RTPUtils.readUint32AsLong(buf, off + 8);

        int newLen
            = getLength(pt != origPt, seq != origSeq,
                        ts != origTs, ssrc != origSsrc);

        // If the lengths match, we don't have anything to change.
        if (newLen != len)
        {
            // TODO:
            // 1. remove the old extension
            // 2. maybe add a new one
        }
    }

    /**
     * @return the length of the OHB extension, given the fields which differ
     * from the original packet.
     * @param pt whether the PR was modified.
     * @param seq whether the sequence number was modified.
     * @param ts whether the timestamp was modified.
     * @param ssrc whether the SSRC was modified.
     */
    private int getLength(boolean pt, boolean seq, boolean ts, boolean ssrc)
    {
        if (!pt && !seq && !ts && !ssrc)
            return 0;
        else if (pt && !seq && !ts && !ssrc)
            return  1;
        else if (!pt && seq && !ts && !ssrc)
            return 2;
        else if (!pt && !seq && ts && !ssrc)
            return 4;
        else if (!pt && !seq && !ts && ssrc)
            return 5;
        else if (pt && seq && !ts && !ssrc)
            return 3;
        else if (pt && !seq && ts && !ssrc)
            return 5;
        else if (pt && !seq && !ts && ssrc)
            return 5;
        else if (!pt && seq && ts && !ssrc)
            return 6;
        else if (!pt && seq && !ts && ssrc)
            return 7;
        else if (!pt && !seq && ts && ssrc)
            return 8;
        else if (pt && seq && ts && !ssrc)
            return 7;
        else if (pt && seq && !ts && ssrc)
            return 7;
        else if (pt && !seq && ts && ssrc)
            return 9;
        else if (!pt && seq && ts && ssrc)
            return 10;
        else if (pt && seq && ts && ssrc)
            return 11;

        // The above is exhaustive, but the compiler (and IDE) can't figure it
        // out.
        throw new IllegalStateException();
    }

    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     *
     * This <tt>TransformEngine</tt> does not transform RTCP packets.
     *
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Adds an abs-send-time RTP header extension with an ID of {@link
     * #extensionID} and value derived from the current system time to the
     * packet {@code pkt}.
     * @param pkt the packet to add an extension to.
     */
    private void addExtension(RawPacket pkt)
    {
        RawPacket.HeaderExtension he = pkt.addExtension((byte) extensionID, 11);

        byte[] buf = he.getBuffer();
        int off = he.getOffset();

        // skip the first ID/len byte, which has been already set.
        buf[off + 1] = pkt.getPayloadType();
        RTPUtils.writeShort(buf, off + 2, (short) pkt.getSequenceNumber());
        RTPUtils.writeInt(buf, off + 4, (int) pkt.getTimestamp());
        RTPUtils.writeInt(buf, off + 8, pkt.getSSRC());
    }

    /**
     * Sets the ID of the abs-send-time RTP extension. Set to -1 to effectively
     * disable this transformer.
     * @param id the ID to set.
     */
    public void setExtensionID(int id)
    {
        extensionID = id;
    }
}
