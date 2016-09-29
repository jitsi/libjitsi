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

import java.util.*;

/**
 * @author George Politis
 */
public class MediaStreamTrack
{
    /**
     * The RTP encodings of this track, keyed by their SSRC.
     */
    private final Map<Long, RTPEncoding> encodingsBySSRC
        = new TreeMap<Long, RTPEncoding>()
    {
        @Override
        public RTPEncoding put(Long key, RTPEncoding value)
        {
            if (key == null || key == -1)
            {
                return null;
            }

            return put(key, value);
        }
    };

    /**
     * The RTP encodings of this track, keyed by their order.
     */
    private final Map<Integer, RTPEncoding> encodingsByOrder = new TreeMap<>();

    /**
     *
     * @param ssrc
     * @return
     */
    public synchronized RTPEncoding getEncodingBySSRC(long ssrc)
    {
        return encodingsBySSRC.get(ssrc);
    }

    /**
     * Gets the RTPEncoding that matches the desired order.
     *
     * @param order the order of the RTPEncoding to get.
     * @return the RTPEncoding that matches the desired order, or null if
     * there's no matching encoding.
     */
    public RTPEncoding getEncodingByOrder(int order)
    {
        return encodingsByOrder.get(order);
    }

    /**
     * Gets the RTPEncodings keyed on their SSRC.
     *
     * @return the RTPEncodings keyed on their SSRC.
     */
    public synchronized Map<Long, RTPEncoding> getEncodingsBySSRC()
    {
        return new TreeMap<>(encodingsBySSRC);
    }

    /**
     * Adds an RTP encoding to the list of encodings of this track.
     *
     * @param primarySSRC the primary SSRC of the RTP encoding
     * @param rtxSSRC the RTX SSRC of the RTP encoding
     * @param fecSSRC the FEC SSRC of the RTP encoding.
     * @param order the order of the RTP encoding
     */
    public synchronized void addEncoding(
        long primarySSRC, long rtxSSRC, long fecSSRC, int order)
    {
        RTPEncoding encoding
            = new RTPEncoding(this, primarySSRC, rtxSSRC, fecSSRC);
        encodingsBySSRC.put(encoding.getPrimarySSRC(), encoding);
        encodingsBySSRC.put(encoding.getRTXSSRC(), encoding);
        encodingsBySSRC.put(encoding.getFECSSRC(), encoding);
        encodingsByOrder.put(order, encoding);
    }

    /**
     * Returns a boolean indicating whether this {@code MediaStreamTrack}
     * has multiple {@code RTPEncoding}s.
     *
     * @return true if this {@code MediaStreamTrack} has multiple
     * {@code RTPEncodings}s, false otherwise.
     */
    public synchronized boolean hasMultipleEncodings()
    {
        return encodingsBySSRC.size() > 1;
    }
}
