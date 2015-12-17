package org.jitsi.impl.neomedia;

import org.jitsi.util.*;
import org.jitsi.util.function.*;

/**
 * @author George Politis
 */
public class AbstractRTPPacketPredicate
    implements Predicate<RawPacket>
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTCPPacketPredicate</tt> class.
     */
    private static final Logger logger
        = Logger.getLogger(RTPPacketPredicate.class);

    /**
     * True
     */
    private final boolean rtcp;

    public AbstractRTPPacketPredicate(boolean rtcp)
    {
        this.rtcp = rtcp;
    }

    @Override
    public boolean test(RawPacket pkt)
    {
        // XXX inspired by RtpChannelDatagramFilter.accept().
        boolean result;
        if (pkt.getLength() >= 4)
        {
            byte[] buff = pkt.getBuffer();
            int off = pkt.getOffset();

            if (pkt.getVersion() == 2) // RTP/RTCP version field
            {
                int pt = buff[off + 1] & 0xff;

                if (200 <= pt && pt <= 211)
                {
                    result = rtcp;
                }
                else
                {
                    result = !rtcp;
                }
            }
            else
            {
                result = false;
            }
        }
        else
        {
            result = false;
        }

        if (!result)
        {
            logger.debug("Caught a non-RTCP/RTP packet.");
        }

        return result;
    }
}
