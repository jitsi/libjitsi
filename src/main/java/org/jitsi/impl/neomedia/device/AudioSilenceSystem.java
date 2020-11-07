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
package org.jitsi.impl.neomedia.device;

import org.jitsi.impl.neomedia.jmfext.media.protocol.audiosilence.*;

import javax.media.*;
import java.util.*;

/**
 * Implements an {@llink AudioSystem} which produces silence without capturing
 * from an actual hardware device. Hence, it is suitable for server-side
 * technologies that do not need to and/or cannot capture speech.
 *
 * @author Pawel Domas
 */
public class AudioSilenceSystem
    extends AudioSystem
{
    /**
     * The protocol of the {@code MediaLocator} of {@code AudioSilenceSystem}.
     */
    private static final String LOCATOR_PROTOCOL
        = LOCATOR_PROTOCOL_AUDIOSILENCE;

    /**
     * Initializes a new {@code AudioSilenceSystem} instance.
     */
    protected AudioSilenceSystem()
        throws Exception
    {
        super(AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        List<CaptureDeviceInfo2> captureDevices
            = new ArrayList<CaptureDeviceInfo2>(2);

        captureDevices.add(
            new CaptureDeviceInfo2(
                    "AudioSilenceCaptureDevice",
                    new MediaLocator(LOCATOR_PROTOCOL + ":"),
                    DataSource.SUPPORTED_FORMATS,
                    null, null, null));

        // The following is a dummy audio capture device which does not even
        // produce silence. It is suitable for scenarios in which an audio
        // capture device is required but no audio samples from it are necessary
        // such as negotiating signalling for audio but actually RTP translating
        // other participants/peers' audio.
        captureDevices.add(
            new CaptureDeviceInfo2(
                    "AudioSilenceCaptureDevice:" + DataSource.NO_TRANSFER_DATA,
                    new MediaLocator(
                            LOCATOR_PROTOCOL
                                + ":"
                                + DataSource.NO_TRANSFER_DATA),
                    DataSource.SUPPORTED_FORMATS,
                    null, null, null));

        setCaptureDevices(captureDevices);
    }
}
