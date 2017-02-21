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

import net.sf.fmj.media.rtp.*;
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
     * Finds the {@link FrameDesc} that matches the RTP packet specified
     * in the {@link ByteArrayBuffer} that is passed in as an argument.
     *
     * @param buf the {@link ByteArrayBuffer} that specifies the
     * {@link RawPacket}.
     * @return the {@link FrameDesc} that matches the RTP packet specified
     * in the {@link ByteArrayBuffer} that is passed in as an argument, or null
     * if there is no matching {@link FrameDesc}.
     */
    public FrameDesc findFrameDesc(ByteArrayBuffer buf)
    {
        if (buf == null)
        {
            return null;
        }

        return findFrameDesc(
            buf.getBuffer(), buf.getOffset(), buf.getLength());
    }

    /**
     * Finds the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as an argument.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     * @return the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as a parameter, or null if there is no matching
     * {@link FrameDesc}.
     */
    public FrameDesc findFrameDesc(byte[] buf, int off, int len)
    {
        RTPEncodingDesc rtpEncoding = findRTPEncodingDesc(buf, off, len);
        if (rtpEncoding == null)
        {
            return null;
        }

        return rtpEncoding.findFrameDesc(buf, off, len);
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches {@link ByteArrayBuffer}
     * passed in as a parameter.
     *
     * @param buf the {@link ByteArrayBuffer} of the {@link RTPEncodingDesc}
     * to match.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(ByteArrayBuffer buf)
    {
        if (buf == null)
        {
            return null;
        }

        return findRTPEncodingDesc(
            buf.getBuffer(), buf.getOffset(), buf.getLength());
    }

    /**
     * Finds the {@link RTPEncodingDesc} that matches {@link ByteArrayBuffer}
     * passed in as a parameter.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     * @return the {@link RTPEncodingDesc} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncodingDesc}.
     */
    public RTPEncodingDesc findRTPEncodingDesc(byte[] buf, int off, int len)
    {
        MediaStreamTrackDesc[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks))
        {
            return null;
        }

        for (MediaStreamTrackDesc track : localTracks)
        {
            RTPEncodingDesc encoding = track.findRTPEncodingDesc(buf, off, len);
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
        for (int i = 0; i < pkts.length; i++)
        {
            if (!RTPPacketPredicate.INSTANCE.test(pkts[i]))
            {
                continue;
            }

            RTPEncodingDesc encoding = findRTPEncodingDesc(pkts[i]);

            if (encoding != null)
            {
                encoding.update(pkts[i], nowMs);
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
