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
 * Represents a collection of {@link RTPEncoding}s that encode the same media
 * source.
 *
 * @author George Politis
 */
public interface MediaStreamTrack
{
    /**
     * Gets the unique identifier for this instance.
     * @return
     */
    String getMediaStreamTrackId();

    /**
     * Returns an array of all the {@link RTPEncoding}s for this instance, in
     * subjective quality ascending order.
     *
     * @return
     */
    RTPEncoding[] getRTPEncodings();

    /**
     * Returns a boolean indicating whether this track is a "multistream" track
     * i.e. it has multiple independent RTP encodings like in simulcast.
     *
     * @return true if this track is multi-stream, false otherwise.
     */
    boolean isMultiStream();
}
