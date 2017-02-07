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
package org.jitsi.impl.neomedia.transform.zrtp;

import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;

/**
 * PacketTransformer that delegates the forward/reverse transformation of
 * packets to different packet transformers.
 *
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 */
public class ZRTCPTransformer
    extends SinglePacketTransformer
{
    /**
     * We support different SRTCP contexts for input and output traffic:
     *
     * Transform() uses the srtcpOut to perform encryption
     * reverseTransform() uses srtcpIn to perform decryption
     */
    private SinglePacketTransformer srtcpIn = null;
    private SinglePacketTransformer srtcpOut = null;

    /**
     * Close the transformer engine.
     *
     * The close functions closes all stored default crypto contexts. This deletes key data
     * and forces a cleanup of the crypto contexts.
     */
    public void close()
    {
        if (srtcpOut != null)
        {
            srtcpOut.close();
            srtcpOut = null;
        }
        if (srtcpIn != null)
        {
            srtcpIn.close();
            srtcpIn = null;
        }
    }
    /**
     * Encrypt a SRTCP packet
     *
     * Currently SRTCP packet encryption / decryption is not supported
     * So this method does not change the packet content
     *
     * @param pkt plain SRTCP packet to be encrypted
     * @return encrypted SRTCP packet
     */
    @Override
    public RawPacket transform(RawPacket pkt)
    {
        return (srtcpOut == null) ? pkt : srtcpOut.transform(pkt);
    }

    /**
     * Decrypt a SRTCP packet
     *
     * Currently SRTCP packet encryption / decryption is not supported
     * So this method does not change the packet content
     *
     * @param pkt encrypted SRTCP packet to be decrypted
     * @return decrypted SRTCP packet
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        return (srtcpIn == null) ? pkt : srtcpIn.reverseTransform(pkt);
    }

    /**
     * @param srtcpIn the srtcpIn to set
     */
    public void setSrtcpIn(SinglePacketTransformer srtcpIn)
    {
        this.srtcpIn = srtcpIn;
    }

    /**
     * @param srtcpOut the srtcpOut to set
     */
    public void setSrtcpOut(SinglePacketTransformer srtcpOut)
    {
        this.srtcpOut = srtcpOut;
    }
}
