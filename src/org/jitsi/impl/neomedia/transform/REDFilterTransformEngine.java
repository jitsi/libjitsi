/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Removes the RED encapsulation (RFC2198) from outgoing packets, dropping
 * non-primary (redundancy) packets.
 *
 * @author George Politis
 */
public class REDFilterTransformEngine
        extends SinglePacketTransformer
        implements TransformEngine
{
    /**
     * The <tt>Logger</tt> used by the <tt>REDTransformEngine</tt> class and
     * its instances to print debug information.
     */
    private static final Logger logger
            = Logger.getLogger(REDFilterTransformEngine.class);

    /**
     * A boolean flag determining whether or not this transformer should strip
     * RED from outgoing packets.
     */
    private boolean enabled = false;

    /**
     * The RED payload type. This, of course, should be dynamic but in the
     * context of this transformer this doesn't matter.
     */
    private byte redPayloadType = -1;

    /**
     * Initializes a new <tt>REDFilterTransformEngine</tt> with the given
     * payload type number for RED.
     * @param redPayloadType the RED payload type number.
     */
    public REDFilterTransformEngine(byte redPayloadType)
    {
        this.redPayloadType = redPayloadType;
    }

    /**
     * Enables or disables this transformer.
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        // XXX: this method is heavily inspired by the
        // {@link REDTransformEngine#reverseTransformSingle} method only
        // here we do the transformation in the opposite direction, i.e.
        // we transform outgoing packets instead of incoming ones.
        //
        // This transform engine has bee added to enable support for FF
        // that, at the time of this writing, lacks support for
        // ulpfec/red. Thus its purpose is to strip ulpfec/red from the
        // outgoing packets that target FF.
        //
        // We could theoretically strip ulpfec/red from all the incoming
        // packets (in a reverseTransform method) and selectively re-add
        // it later-on (using the RED and FEC transform engines) only to
        // those outgoing streams that have announced ulpfec/red support
        // (currently those are the streams that target Chrome).
        //
        // But, given that the best supported client is Chrome and thus
        // assuming that most participants will use Chrome to connect to
        // a JVB-based service, it seems a waste of resources to do
        // that. It's more efficient to strip ulpfec/red only from the
        // outgoing streams that target FF.

        if (!enabled || redPayloadType == -1)
        {
            return pkt;
        }

        if (pkt == null
                || pkt.getBuffer() == null
                || pkt.getLength() == 0
                || pkt.getPayloadType() != redPayloadType)
        {
            logger.debug("Ignoring non-RED packet.");
            return pkt;
        }

        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        if (buf == null || off + len > buf.length)
        {
            // this should theoretically not happen, but theories are
            // just that, theories, and this does happen.
            logger.debug("Ignoring invalid packet.");
            return pkt;
        }

        int hdrLen = pkt.getHeaderLength();

        if (off + hdrLen > len)
        {
            // this should theoretically not happen, but theories are
            // just that, theories, and this does happen.
            logger.debug("Ignoring invalid packet.");
            return pkt;
        }

        int idx = off + hdrLen; //beginning of RTP payload
        int pktCount = 1; //number of packets inside RED

        // 0                   1                   2                   3
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        //|F|   block PT  |  timestamp offset         |   block length    |
        //+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        while ((buf[idx] & 0x80) != 0)
        {
            pktCount++;
            idx += 4;
        }

        idx = off + hdrLen; //back to beginning of RTP payload

        int payloadOffset = idx + (pktCount - 1) * 4 + 1 /* RED headers */;

        // skip non-primary packets.
        for (int i = 1; i < pktCount; i++)
        {
            int blockLen = (buf[idx + 2] & 0x03) << 8 | (buf[idx + 3]);

            idx += 4; // next RED header
            payloadOffset += blockLen;
        }

        //idx is now at the "primary encoding block header":
        // 0 1 2 3 4 5 6 7
        //+-+-+-+-+-+-+-+-+
        //|0|   Block PT  |
        //+-+-+-+-+-+-+-+-+

        int payloadLen = len - payloadOffset;

        if (payloadOffset + payloadLen > len)
        {
            // this should theoretically not happen, but theories are
            // just that, theories, and this does happen.
            logger.debug("Ignoring invalid primary packet carried in " +
                    "RED.");
            return pkt;
        }

        byte payloadType = (byte) (buf[idx] & 0x7f);

        // write the primary packet.

        // Shift the header right.
        pkt.setPayloadType(payloadType);
        System.arraycopy(buf, off, buf, payloadOffset-hdrLen, hdrLen);
        pkt.setOffset(payloadOffset-hdrLen);
        pkt.setLength(len - (payloadOffset - hdrLen - off));

        return pkt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return pkt;
    }

}
