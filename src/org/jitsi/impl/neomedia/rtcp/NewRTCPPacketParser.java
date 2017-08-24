package org.jitsi.impl.neomedia.rtcp;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

import java.io.*;
import java.util.*;

/**
 * Created by bbaldino on 8/23/17.
 */
public class NewRTCPPacketParser
{
    /**
     * The {@link Logger} used by the {@link NewRTCPPacketParser} class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(NewRTCPPacketParser.class);

    public static NewRTCPPacket[] parse(RawPacket packet)
    {
        // PR-NOTE(brian): should we do some check/verification that this is, in fact,
        // a compound rtcp packet?  Or can we expect to only be called with
        // compound packets?
        RTCPIterator rtcpIterator = new RTCPIterator(packet);
        List<NewRTCPPacket> parsedPackets = new ArrayList<>();

        while (rtcpIterator.hasNext())
        {
            ByteArrayBuffer baf = rtcpIterator.next();
            RawPacket subPacket =
                new RawPacket(baf.getBuffer(), baf.getOffset(), baf.getLength());

            try
            {
                switch (subPacket.getRTCPPacketType())
                {
                    case NewRTCPPacket.SR:
                        NewRTCPSRPacket srPacket = NewRTCPSRPacket.parse(subPacket);
                        if (srPacket != null)
                        {
                            parsedPackets.add(srPacket);
                        }
                        // We remove here because, for now, this parser is only
                        // parsing a subset of types, so this compound packet
                        // will later be passed to the old parser as well.
                        // We'll remove the ones that we handle here and combine
                        // all the parsed packets at the call site.  In an
                        // effort to not mask problems with the new code, we'll
                        // remove it even if we failed to parse it for some reason
                        // (since, when detecting an sr packet, we should have
                        // parsed it successfully)
                        rtcpIterator.remove();
                        break;
                    default:
                }
            }
            catch (IOException e)
            {
                logger.error("Error parsing rtcp packet: " + e.toString());
            }
        }
        return parsedPackets.toArray(new NewRTCPPacket[0]);
    }
}
