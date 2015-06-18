package org.jitsi.service.neomedia.rtp;

import net.sf.fmj.media.rtp.util.*;

/**
 * Created by gp on 6/11/15.
 */
public interface RTPPacketTransformer
{
    RTPPacket transform(RTPPacket pkt);

    RTPPacket reverseTransform(RTPPacket pkt);
}
