package org.jitsi.impl.neomedia.rtp.translator;

import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public interface BitstreamController
    extends PaddingParams, BufferFilter
{
    /**
     *
     * @return
     */
    int getCurrentIndex();

    /**
     *
     * @return
     */
    int getTargetIndex();

    /**
     *
     * @return
     */
    long getTL0SSRC();

    /**
     *
     * @param newOptimalIdx
     */
    void setOptimalIndex(int newOptimalIdx);

    /**
     *
     * @param newTargetIdx
     */
    void setTargetIndex(int newTargetIdx);

    /**
     *
     * @return
     */
    MediaStreamTrackDesc getSource();

    /**
     *
     * @param pktIn
     * @return
     */
    RawPacket[] rtpTransform(RawPacket pktIn);

    /**
     *
     * @param pktIn
     * @return
     */
    RawPacket rtcpTransform(RawPacket pktIn);

    int getMaxSeqNum();

    long getMaxTs();

    long getTransmittedBytes();

    long getTransmittedPackets();

    int getOptimalIndex();
}
