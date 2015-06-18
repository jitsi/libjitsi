/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * @author George Politis
 */
public abstract class SingleRTPPacketTransformer
        extends SinglePacketTransformer
{
    /**
     * The instance used to parse the input <tt>RawPacket</tt>s/bytes into
     * <tt>RTPPacket</tt>s.
     */
    private final RTPPacketParserEx parser = new RTPPacketParserEx();

    /**
     * The <tt>Logger</tt> used by the
     * <tt>SingleRTPPacketTransformer</tt> class and its instances to
     * print debug information.
     */
    private static final Logger logger
            = Logger.getLogger(SingleRTPPacketTransformer.class);

    /**
     * the "norm" in libjitsi is that transformers are enabled, let's not break
     * this rule.
     */
    private boolean enabled = true;

    /**
     *
     */
    class RawPacketProcessor
    {
        /**
         *
         */
        private final RawPacket pkt;

        /**
         *
         */
        private RTPPacket inRTPPacket;

        /**
         *
         */
        private RTPPacket outRTPPacket;

        /**
         * Ctor.
         *
         * @param pkt
         */
        public RawPacketProcessor(RawPacket pkt)
        {
            this.pkt = pkt;
        }

        /**
         *
         * @return
         */
        public RawPacketProcessor parse()
        {
            if (pkt != null)
            {
                try
                {
                    inRTPPacket = parser.parse(pkt);
                }
                catch (BadFormatException e)
                {
                    logger.error("Could not parse RTP packet.", e);
                }
            }

            return this;
        }

        /**
         *
         * @return
         */
        public RawPacketProcessor transform()
        {
            if (inRTPPacket != null)
            {
                outRTPPacket = SingleRTPPacketTransformer
                        .this.transform(inRTPPacket);
            }

            return this;
        }

        /**
         *
         * @return
         */
        public RawPacketProcessor reverseTransform()
        {
            if (inRTPPacket != null)
            {
                outRTPPacket = SingleRTPPacketTransformer
                        .this.reverseTransform(inRTPPacket);
            }

            return this;
        }

        /**
         *
         * @return
         */
        public RawPacket compile()
        {
            if (inRTPPacket == null)
            {
                // => Parsing failed or input pkt null.
                return pkt;
            }
            else if (outRTPPacket == null)
            {
                return null;
            }

            // Assemble the RTP packet.
            int len = outRTPPacket.calcLength();
            outRTPPacket.assemble(len, false);
            byte[] buf = outRTPPacket.data;

            RawPacket pktOut = new RawPacket();

            pktOut.setBuffer(buf);
            pktOut.setLength(buf.length);
            pktOut.setOffset(0);

            return pktOut;
        }
    }

    /**
     *
     * @param enabled
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     *
     * @return
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    public abstract RTPPacket transform(RTPPacket pkt);

    public abstract RTPPacket reverseTransform(RTPPacket pkt);

    @Override
    public RawPacket transform(RawPacket pkt)
    {
        return isEnabled() && pkt != null
                ? new RawPacketProcessor(pkt)
                .parse()
                .transform()
                .compile() : pkt;

    }

    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return isEnabled() && pkt != null
                ? new RawPacketProcessor(pkt)
                .parse()
                .reverseTransform()
                .compile() : pkt;
    }
}
