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

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * This class is inserted in the receive transform chain and it updates the
 * {@link MediaStreamTrackDesc}s that is configured to receive.
 *
 * @author George Politis
 */
public class MediaStreamTrackReceiver
    implements TransformEngine, PacketTransformer
{
    /**
     * The {@link MediaStreamImpl} that owns this instance.
     */
    private final MediaStreamImpl stream;

    /**
     * The {@link MediaStreamTrackDesc}s that this instance is configured to
     * receive.
     */
    private MediaStreamTrackDesc[] tracks;

    /**
     * Ctor.
     *
     * @param stream The {@link MediaStream} that this instance receives
     * {@link MediaStreamTrackDesc}s from.
     */
    public MediaStreamTrackReceiver(MediaStreamImpl stream)
    {
        this.stream = stream;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches the {@link RawPacket}
     * passed in as a parameter. Assumes that the packet is valid.
     *
     * @param pkt the packet to match.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(RawPacket pkt)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks))
        {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks)
        {
            RTPEncodingDesc encoding = track.findRTPEncodingDesc(pkt);
            if (encoding != null)
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches {@link ByteArrayBuffer}
     * passed in as a parameter.
     *
     * @param ssrc the SSRC of the {@link RTPEncodingDesc} to match. If multiple
     * encodings share the same SSRC, the first match will be returned.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(long ssrc)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks))
        {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks)
        {
            RTPEncodingDesc encoding = track.findRTPEncodingDesc(ssrc);
            if (encoding != null)
            {
                return encoding;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {

    }

    /**
     * Gets the {@link MediaStreamTrackDesc}s that this instance is configured
     * to receive.
     *
     * @return the {@link MediaStreamTrackDesc}s that this instance is
     * configured to receive.
     */
    public MediaStreamTrackDesc[] getMediaStreamTracks()
    {
        return tracks;
    }

    /**
     * Updates this {@link MediaStreamTrackReceiver} with the new RTP encoding
     * parameters.
     *
     * @param newTracks the {@link MediaStreamTrackDesc}s that this instance
     * will receive.
     * @return true if the MSTs have changed, otherwise false.
     */
    public boolean setMediaStreamTracks(MediaStreamTrackDesc[] newTracks)
    {
        MediaStreamTrackDesc[] oldTracks = tracks;
        int oldTracksLen = oldTracks == null ? 0 : oldTracks.length;
        int newTracksLen = newTracks == null ? 0 : newTracks.length;

        if (oldTracksLen == 0 || newTracksLen == 0)
        {
            tracks = newTracks;
            return oldTracksLen != newTracksLen;
        }
        else
        {
            int cntMatched = 0;
            MediaStreamTrackDesc[] mergedTracks
                = new MediaStreamTrackDesc[newTracks.length];

            for (int i = 0; i < newTracks.length; i++)
            {
                RTPEncodingDesc newEncoding = newTracks[i].getRTPEncodings()[0];

                for (int j = 0; j < oldTracks.length; j++)
                {
                    if (oldTracks[j] != null
                        && oldTracks[j].matches(newEncoding.getPrimarySSRC()))
                    {
                        mergedTracks[i] = oldTracks[j];
                        cntMatched++;
                        break;
                    }
                }

                if (mergedTracks[i] == null)
                {
                    mergedTracks[i] = newTracks[i];
                }
            }

            tracks = mergedTracks;

            return
                oldTracksLen != newTracksLen || cntMatched != oldTracks.length;
        }
    }

    /**
     * Gets the {@code RtpChannel} that owns this instance.
     *
     * @return the {@code RtpChannel} that owns this instance.
     */
    public MediaStreamImpl getStream()
    {
        return stream;
    }

    /**
     * Finds the {@link MediaStreamTrackDesc} that corresponds to the SSRC that
     * is specified in the arguments.
     *
     * @param ssrc the SSRC of the {@link MediaStreamTrackDesc} to match.
     * @return the {@link MediaStreamTrackDesc} that matches the specified SSRC.
     */
    public MediaStreamTrackDesc findMediaStreamTrackDesc(long ssrc)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks))
        {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks)
        {
            if (track.matches(ssrc))
            {
                return track;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        long nowMs = System.currentTimeMillis();
        for (RawPacket pkt : pkts)
        {
            if (!RTPPacketPredicate.INSTANCE.test(pkt)
                || pkt.isInvalid())
            {
                continue;
            }

            RTPEncodingDesc encoding = findRTPEncodingDesc(pkt);

            if (encoding != null)
            {
                encoding.update(pkt, nowMs);
            }
        }

        return pkts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
    }
}
