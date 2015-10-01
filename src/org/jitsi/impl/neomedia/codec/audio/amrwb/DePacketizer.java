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
package org.jitsi.impl.neomedia.codec.audio.amrwb;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;

/**
 * Implements an RTP depacketizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
public class DePacketizer
    extends AbstractCodec2
{
    /**
     * Initializes a new <tt>DePacketizer</tt> instance.
     */
    public DePacketizer()
    {
        super(
                "AMR-WB RTP DePacketizer",
                AudioFormat.class,
                Packetizer.SUPPORTED_INPUT_FORMATS);

        inputFormats = Packetizer.SUPPORTED_OUTPUT_FORMATS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
        throws ResourceUnavailableException
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        // TODO Auto-generated method stub
        return 0;
    }
}
