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
package org.jitsi.service.neomedia;

/**
 * Inspired by the ORTC RTCRtpEncodingParameters, the RTPEncoding provides
 * RTP encoding related information.
 *
 * TODO this class needs to own the RemoteClock of this encoding.
 * TODO this class needs to know whether or not the RTP encoding is streaming.
 *
 * @author George Politis
 */
public class RTPEncoding
{
    /**
     * Base simulcast stream quality order.
     */
    public static final int BASE_ORDER = 0;

    /**
     * The primary SSRC for this layering/encoding.
     */
    private final long primarySSRC;

    /**
     * The RTX SSRC for this layering/encoding.
     */
    private final long rtxSSRC;

    /**
     * The FEC SSRC for this layering/encoding.
     */
    private final long fecSSRC;

    /**
     * The {@link MediaStreamTrack} that owns this {@link RTPEncoding}.
     */
    private final MediaStreamTrack mediaStreamTrack;

    /**
     * Ctor.
     *
     * @param mediaStreamTrack
     * @param primarySSRC
     * @param rtxSSRC
     * @param fecSSRC
     */
    RTPEncoding(MediaStreamTrack mediaStreamTrack,
                       long primarySSRC, long rtxSSRC, long fecSSRC)
    {
        this.mediaStreamTrack = mediaStreamTrack;
        this.primarySSRC = primarySSRC;
        this.rtxSSRC = rtxSSRC;
        this.fecSSRC = fecSSRC;
    }

    /**
     * Gets the {@link MediaStreamTrack} that owns this {@link RTPEncoding}.
     *
     * @return the {@link MediaStreamTrack} that owns this {@link RTPEncoding}.
     */
    public MediaStreamTrack getMediaStreamTrack()
    {
        return mediaStreamTrack;
    }

    /**
     * Gets the primary SSRC for this layering/encoding.
     *
     * @return the primary SSRC for this layering/encoding.
     */
    public long getPrimarySSRC()
    {
        return primarySSRC;
    }

    /**
     * Gets the RTX SSRC for this layering/encoding.
     *
     * @return the RTX SSRC for this layering/encoding.
     */
    public long getRTXSSRC()
    {
        return rtxSSRC;
    }

    /**
     * Gets the FEC SSRC for this layering/encoding.
     *
     * @return the RTX SSRC for this layering/encoding.
     */
    public long getFECSSRC()
    {
        return fecSSRC;
    }

    /**
     *
     * @param ssrc
     * @return
     */
    public boolean matches(long ssrc)
    {
        return ssrc == primarySSRC || ssrc == rtxSSRC || ssrc == fecSSRC;
    }
}
