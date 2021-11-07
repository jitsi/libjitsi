/*
 * Copyright @ 2018 Atlassian Pty Ltd
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
import org.jitsi.service.neomedia.*;

/**
 * Extends the generic {@link MediaStreamTrackReceiver} with logic to update
 * its {@link MediaStreamTrackDesc}s with received packets.
 *
 * @author George Politis
 * @author Boris Grozev
 */
public class VideoMediaStreamTrackReceiver
    extends MediaStreamTrackReceiver
{
    /**
     * Initializes a new {@link VideoMediaStreamTrackReceiver} instance.
     *
     * @param stream The {@link MediaStream} that this instance receives
     * {@link MediaStreamTrackDesc}s from.
     */
    public VideoMediaStreamTrackReceiver(MediaStreamImpl stream)
    {
        super(stream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (!pkt.isInvalid())
        {
            RTPEncodingDesc encoding = findRTPEncodingDesc(pkt);

            if (encoding != null)
            {
                encoding.update(pkt, System.currentTimeMillis());
            }
        }

        return pkt;
    }
}
