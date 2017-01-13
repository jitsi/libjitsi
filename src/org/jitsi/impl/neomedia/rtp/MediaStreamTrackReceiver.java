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
 * {@link MediaStreamTrack}s that is configured to receive.
 *
 * @author George Politis
 */
public class MediaStreamTrackReceiver
    implements TransformEngine
{
    /**
     * The {@link MediaStreamImpl} that owns this instance.
     */
    private final MediaStreamImpl stream;

    /**
     * The {@link PacketTransformer} that transforms RTP packets for this
     * instance.
     */
    private final RTPPacketTransformer
        rtpTransformer = new RTPPacketTransformer();

    /**
     * The {@link MediaStreamTrack}s that this instance is configured to
     * receive.
     */
    private MediaStreamTrackImpl[] tracks;

    /**
     * Ctor.
     *
     * @param stream The {@link MediaStream} that this instance receives
     * {@link MediaStreamTrack}s from.
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
     *
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
     *
     * @return the {@link FrameDesc} that matches the RTP packet specified
     * in the buffer passed in as a parameter, or null if there is no matching
     * {@link FrameDesc}.
     */
    public FrameDesc findFrameDesc(byte[] buf, int off, int len)
    {
        RTPEncodingImpl rtpEncoding = findRTPEncoding(buf, off, len);
        if (rtpEncoding == null)
        {
            return null;
        }

        return rtpEncoding.findFrameDesc(buf, off, len);
    }

    /**
     * Finds the {@link RTPEncoding} that matches {@link ByteArrayBuffer} passed
     * in as a parameter.
     *
     * @param buf the {@link ByteArrayBuffer} of the {@link RTPEncoding}
     * to match.
     *
     * @return the {@link RTPEncoding} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncoding}.
     */
    public RTPEncodingImpl findRTPEncoding(ByteArrayBuffer buf)
    {
        if (buf == null)
        {
            return null;
        }

        return findRTPEncoding(
            buf.getBuffer(), buf.getOffset(), buf.getLength());
    }

    /**
     * Finds the {@link RTPEncoding} that matches {@link ByteArrayBuffer} passed
     * in as a parameter.
     *
     * @param buf the <tt>byte</tt> array that contains the RTP packet data.
     * @param off the offset in <tt>buf</tt> at which the actual data starts.
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> which
     * constitute the actual data.
     *
     * @return the {@link RTPEncoding} that matches the pkt passed in as
     * a parameter, or null if there is no matching {@link RTPEncoding}.
     */
    public RTPEncodingImpl findRTPEncoding(byte[] buf, int off, int len)
    {
        MediaStreamTrackImpl[] localTracks = tracks;
        if (ArrayUtils.isNullOrEmpty(localTracks))
        {
            return null;
        }

        for (MediaStreamTrackImpl track : localTracks)
        {
            RTPEncodingImpl encoding = track.findRTPEncoding(buf, off, len);
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
        return rtpTransformer;
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
     * Gets the {@link MediaStreamTrack}s that this instance is configured to
     * receive.
     *
     * @return the {@link MediaStreamTrack}s that this instance is configured to
     * receive.
     */
    public MediaStreamTrackImpl[] getMediaStreamTracks()
    {
        return tracks;
    }

    /**
     * Updates this {@link MediaStreamTrackReceiver} with the new RTP encoding
     * parameters.
     *
     * @param newTracks the {@link MediaStreamTrack}s that this instance will
     * receive.
     * @return true if the MSTs have changed, otherwise false.
     */
    public boolean setMediaStreamTracks(
        MediaStreamTrackImpl[] newTracks)
    {
        boolean changed = false;

        MediaStreamTrackImpl[] oldTracks = tracks;
        int tracksLen = oldTracks == null ? 0 : oldTracks.length;
        int newTracksLen = newTracks == null ? 0 : newTracks.length;

        if (tracksLen == 0 || newTracksLen == 0)
        {
            tracks = newTracks;
            changed = tracksLen != newTracksLen;
        }
        else
        {
            MediaStreamTrackImpl[] mergedTracks
                = new MediaStreamTrackImpl[newTracks.length];

            for (int i = 0; i < newTracks.length; i++)
            {
                MediaStreamTrackImpl newTrack = newTracks[i];

                RTPEncodingImpl newEncoding = newTrack.getRTPEncodings()[0];

                RTPEncodingImpl oldEncoding = null;
                for (MediaStreamTrackImpl mst : oldTracks)
                {
                    if (newEncoding.getPrimarySSRC()
                        == mst.getRTPEncodings()[0].getPrimarySSRC())
                    {
                        oldEncoding = mst.getRTPEncodings()[0];
                        break;
                    }
                }

                if (oldEncoding != null)
                {
                    mergedTracks[i] = oldEncoding.getMediaStreamTrack();
                }
                else
                {
                    mergedTracks[i] = newTrack;
                    changed = true;
                }
            }

            tracks = mergedTracks;
        }

        return changed;
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
     * Updates rate statistics for the encodings of the tracks that this
     * receiver is managing. Detects simulcast stream suspension/resuming.
     */
    private class RTPPacketTransformer
        implements PacketTransformer
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket[] reverseTransform(RawPacket[] pkts)
        {
            RawPacket[] cumulExtras = null;
            for (int i = 0; i < pkts.length; i++)
            {
                RTPEncodingImpl encoding = findRTPEncoding(pkts[i]);

                if (encoding != null)
                {
                    RawPacket[] extras = encoding
                        .getMediaStreamTrack().update(pkts[i], encoding);

                    if (ArrayUtils.isNullOrEmpty(extras))
                    {
                        cumulExtras = ArrayUtils.concat(cumulExtras, extras);
                    }
                }
            }

            // XXX these array concatenation methods are fast most of the time.
            return ArrayUtils.concat(cumulExtras, pkts);
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
}
