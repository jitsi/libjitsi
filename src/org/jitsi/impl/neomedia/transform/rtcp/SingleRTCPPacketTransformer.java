/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.rtcp;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.util.*;

/**
 * @author George Politis
 */
public abstract class SingleRTCPPacketTransformer
        extends SinglePacketTransformer
{
    /**
     * The instance used to parse the input <tt>RawPacket</tt>s/bytes into
     * <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPPacketParserEx parser = new RTCPPacketParserEx();

    /**
     * The <tt>Logger</tt> used by the
     * <tt>SingleRTCPPacketTransformer</tt> class and its instances to
     * print debug information.
     */
    private static final Logger logger
            = Logger.getLogger(SingleRTCPPacketTransformer.class);

    private boolean enabled = true;

    class RawPacketProcessor
    {
        private final RawPacket pkt;
        private RTCPCompoundPacket inRTCPPacket;
        private RTCPCompoundPacket outRTCPPacket;

        public RawPacketProcessor(RawPacket pkt)
        {
            this.pkt = pkt;
        }

        public RawPacketProcessor parse()
        {
            if (pkt == null)
                return null;

            try
            {
                inRTCPPacket = (RTCPCompoundPacket) parser.parse(
                        pkt.getBuffer(),
                        pkt.getOffset(),
                        pkt.getLength());
            }
            catch (BadFormatException e)
            {
                // TODO(gp) decide what to do with malformed packets!
                logger.error("Could not parse RTCP packet.", e);
            }

            return this;
        }

        public RawPacketProcessor transform()
        {
            if (inRTCPPacket != null)
                outRTCPPacket = SingleRTCPPacketTransformer
                        .this.transform(inRTCPPacket);

            return this;
        }

        public RawPacketProcessor reverseTransform()
        {
            if (inRTCPPacket != null)
                outRTCPPacket = SingleRTCPPacketTransformer
                        .this.reverseTransform(inRTCPPacket);

            return this;
        }

        public RawPacket compile()
        {
            if (inRTCPPacket == null)
            {
                // => Parsing failed or input pkt null.
                return pkt;
            }
            else if (outRTCPPacket == null)
            {
                return null;
            }

            if (outRTCPPacket == null
                    || outRTCPPacket.packets == null
                    || outRTCPPacket.packets.length == 0)
                return null;

            // Assemble the RTCP packet.
            int len = outRTCPPacket.calcLength();
            outRTCPPacket.assemble(len, false);
            byte[] buf = outRTCPPacket.data;

            RawPacket pktOut = new RawPacket();

            pktOut.setBuffer(buf);
            pktOut.setLength(buf.length);
            pktOut.setOffset(0);

            return pktOut;
        }
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public abstract RTCPCompoundPacket transform(RTCPCompoundPacket pkt);

    public abstract RTCPCompoundPacket reverseTransform(RTCPCompoundPacket pkt);

    @Override
    public RawPacket transform(RawPacket pkt)
    {
        return isEnabled() ? new RawPacketProcessor(pkt)
                .parse()
                .transform()
                .compile() : pkt;
    }

    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return isEnabled() ? new RawPacketProcessor(pkt)
                .parse()
                .reverseTransform()
                .compile() : pkt;
    }
}
