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
 * @author George Politis
 */
public interface RTPEncoding
{
    /**
     * Gets the identifier for this layering/encoding.
     *
     * @return the identifier for this layering/encoding.
     */
    String getEncodingId();

    /**
     * Gets the primary SSRC for this layering/encoding.
     *
     * @return the primary SSRC for this layering/encoding.
     */
    long getPrimarySSRC();

    /**
     * Gets the RTX SSRC for this layering/encoding.
     *
     * @return the RTX SSRC for this layering/encoding.
     */
    long getRTXSSRC();

    /**
     * Gets a boolean value indicating whether or not this instance is
     * streaming.
     *
     * @return true if this instance is streaming, false otherwise.
     */
    boolean isActive();

    /**
     * Gets Inverse of the input framerate fraction to be encoded.
     *
     * @return  Inverse of the input framerate fraction to be encoded.
     */
    double getFrameRateScale();

    /**
     * If the sender's kind is "video", the video's resolution will be scaled
     * down in each dimension by the given value before sending.
     *
     * @return
     */
    double getResolutionScale();

    /**
     * Gets the {@link RTPEncoding}s on which this layering/encoding depends.
     *
     * @return the {@link RTPEncoding}s on which this layering/encoding depends,
     * or null if it has no dependencies.
     */
    RTPEncoding[] getDependencyEncodings();

    /**
     * Gets the {@link MediaStreamTrack} that this instance belongs to.
     *
     * @return the {@link MediaStreamTrack} that this instance belongs to.
     */
    MediaStreamTrack getMediaStreamTrack();
}
