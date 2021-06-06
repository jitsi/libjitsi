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
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements an RTP packetizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
public class Packetizer
    extends AbstractCodec2
{
    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>Packetizer</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_INPUT_FORMATS
        = { new AudioFormat(Constants.AMR_WB) };

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>Packetizer</tt> instances.
     */
    static final AudioFormat[] SUPPORTED_OUTPUT_FORMATS
        = { new AudioFormat(Constants.AMR_WB_RTP) };

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super(
                "AMR-WB RTP Packetizer",
                AudioFormat.class,
                SUPPORTED_OUTPUT_FORMATS);

        inputFormats = SUPPORTED_INPUT_FORMATS;
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
