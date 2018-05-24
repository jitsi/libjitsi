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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Represents a collection of {@link RTPEncodingDesc}s that encode the same
 * media source. This specific implementation provides webrtc simulcast stream
 * suspension detection.
 *
 * @author George Politis
 */
public class MediaStreamTrackDesc
{
    /**
     * The {@link RTPEncodingDesc}s that this {@link MediaStreamTrackDesc}
     * possesses, ordered by their subjective quality from low to high.
     */
    private final RTPEncodingDesc[] rtpEncodings;

    /**
     * The {@link MediaStreamTrackReceiver} that receives this instance.
     */
    private final MediaStreamTrackReceiver mediaStreamTrackReceiver;

    /**
     * A string which identifies the owner of this track (e.g. the endpoint
     * which is the sender of the track).
     */
    private final String owner;

    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The {@link MediaStreamTrackReceiver} that
     * receives this instance.
     * @param rtpEncodings The {@link RTPEncodingDesc}s that this instance
     * possesses.
     */
    public MediaStreamTrackDesc(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingDesc[] rtpEncodings)
    {
        this(mediaStreamTrackReceiver, rtpEncodings, null);
    }

    /**
     * Ctor.
     *
     * @param mediaStreamTrackReceiver The {@link MediaStreamTrackReceiver} that
     * receives this instance.
     * @param rtpEncodings The {@link RTPEncodingDesc}s that this instance
     * possesses.
     */
    public MediaStreamTrackDesc(
        MediaStreamTrackReceiver mediaStreamTrackReceiver,
        RTPEncodingDesc[] rtpEncodings,
        String owner)
    {
        this.rtpEncodings = rtpEncodings;
        this.mediaStreamTrackReceiver = mediaStreamTrackReceiver;
        this.owner = owner;
    }

    /**
     * @return the identifier of the owner of this track.
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * @return the {@link MediaType} of this {@link MediaStreamTrackDesc}.
     */
    public MediaType getMediaType()
    {
        return getMediaStreamTrackReceiver().getStream().getMediaType();
    }

    /**
     * Returns an array of all the {@link RTPEncodingDesc}s for this instance,
     * in subjective quality ascending order.
     *
     * @return an array of all the {@link RTPEncodingDesc}s for this instance,
     * in subjective quality ascending order.
     */
    public RTPEncodingDesc[] getRTPEncodings()
    {
        return rtpEncodings;
    }

    /**
     * Gets the {@link MediaStreamTrackReceiver} that receives this instance.
     *
     * @return The {@link MediaStreamTrackReceiver} that receives this instance.
     */
    public MediaStreamTrackReceiver getMediaStreamTrackReceiver()
    {
        return mediaStreamTrackReceiver;
    }

    /**
     * Gets the last "stable" bitrate (in bps) of the encoding of the specified
     * index. The "stable" bitrate is measured on every new frame and with a
     * 5000ms window.
     *
     * @return the last "stable" bitrate (bps) of the encoding at the specified
     * index.
     */
    public long getBps(int idx)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return 0;
        }

        if (idx > -1)
        {
            for (int i = idx; i > -1; i--)
            {
                long bps = rtpEncodings[i].getLastStableBitrateBps();
                if (bps > 0)
                {
                    return bps;
                }
            }
        }

        return 0;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that corresponds to the packet that is
     * passed in as an argument. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the {@link RTPEncodingDesc} that corresponds to the packet that is
     * specified in the buffer passed in as an argument, or null.
     */
    RTPEncodingDesc findRTPEncodingDesc(RawPacket pkt)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return null;
        }

        for (RTPEncodingDesc encoding : rtpEncodings)
        {
            if (encoding.matches(pkt))
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that corresponds to the specified
     * {@code ssrc}.
     *
     * @param ssrc the SSRC of the {@link RTPEncodingDesc} to find. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the {@link RTPEncodingDesc} that corresponds to the specified
     * {@code ssrc}.
     */
    RTPEncodingDesc findRTPEncodingDesc(long ssrc)
    {
        if (ArrayUtils.isNullOrEmpty(rtpEncodings))
        {
            return null;
        }

        for (RTPEncodingDesc encoding : rtpEncodings)
        {
            if (encoding.matches(ssrc))
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * Finds the {@link FrameDesc} that corresponds to the given timestamp
     * for the given stream (identified by its ssrc)
     * @param ssrc the ssrc of the stream to which this frame belongs
     * @param timestamp the timestamp of the frame the caller is trying to find
     * @return the {@link FrameDesc} that corresponds to the ssrc and timestamp
     * given, or null
     */
    public FrameDesc findFrameDesc(long ssrc, long timestamp)
    {
        RTPEncodingDesc rtpEncoding = findRTPEncodingDesc(ssrc);
        if (rtpEncoding != null)
        {
            return rtpEncoding.findFrameDesc(timestamp);
        }
        return null;
    }

    /**
     * FIXME: this should probably check whether the specified SSRC is part
     * of this track (i.e. check all encodings and include secondary SSRCs).
     *
     * @param ssrc the SSRC to match.
     * @return {@code true} if the specified {@code ssrc} is the primary SSRC
     * for this track.
     */
    public boolean matches(long ssrc)
    {
        return rtpEncodings[0].getPrimarySSRC() == ssrc;
    }
}
