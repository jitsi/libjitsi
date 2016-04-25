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
package org.jitsi.impl.neomedia;

/**
 * RtpEncodingParameters provides information relating to the encoding.
 *
 * @author George Politis
 */
public class RtpEncodingParameters
{
    /**
     * The SSRC for this layering/encoding.
     */
    private final long ssrc;

    /**
     * The payload type of the codec of this layering/encoding.
     */
    private final byte codecPayloadType;

    /**
     * Specifies the RTX parameters of this layering/encoding, if set.
     */
    private final RtxParameters rtx;

    /**
     *
     * @param ssrc The SSRC for this layering/encoding.
     * @param codecPayloadType The payload type of the codec of this
     * layering/encoding.
     * @param rtx Specifies the RTX parameters, if set.
     */
    public RtpEncodingParameters(
        long ssrc, byte codecPayloadType, RtxParameters rtx)
    {
        this.ssrc = ssrc;
        this.codecPayloadType = codecPayloadType;
        this.rtx = rtx;
    }

    /**
     * Gets SSRC for this layering/encoding.
     * @return The SSRC for this layering/encoding.
     */
    public long getSsrc()
    {
        return ssrc;
    }

    /**
     * Gets the payload type of the codec of this layering/encoding.
     *
     * @return The payload type of the codec of this layering/encoding.
     */
    public byte getCodecPayloadType()
    {
        return codecPayloadType;
    }

    /**
     * Gets the RTX parameters of this layering/encoding, if set.
     *
     * @return The RTX parameters of this layering/encoding, if set, null
     * otherwise.
     */
    public RtxParameters getRtx()
    {
        return rtx;
    }

    /**
     * The RTX parameters of a layering/encoding
     */
    public static class RtxParameters
    {
        /**
         * The SSRC to use for retransmission.
         */
        public long ssrc;

        /**
         * The payload type to use for retransmission.
         */
        public byte payloadType;

        /**
         *
         * @param ssrc The SSRC to use for retransmission.
         * @param payloadType The payload type to use for retransmission.
         */
        public RtxParameters(long ssrc, byte payloadType)
        {
            this.ssrc = ssrc;
            this.payloadType = payloadType;
        }

        /**
         * Gets the SSRC to use for retransmission
         * @return The SSRC to use for retransmission
         */
        public long getSsrc()
        {
            return ssrc;
        }

        /**
         * Gets the payload type to use for retransmission.
         * @return The payload type to use for retransmission.
         */
        public byte getPayloadType()
        {
            return payloadType;
        }
    }
}
