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
 * Audio system used by server side technologies that do not capture any sound.
 * The device is producing silence.
 *
 * @author Pawel Domas
 */
public class AudioSilenceSystem
    extends AudioSystem
{
    private static final String LOCATOR_PROTOCOL
        = LOCATOR_PROTOCOL_AUDIOSILENCE;

    protected AudioSilenceSystem()
        throws Exception
    {
        super(AudioSystem.LOCATOR_PROTOCOL_AUDIOSILENCE);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
        CaptureDeviceInfo2 captureDevice
            = new CaptureDeviceInfo2(
                    "AudioSilenceCaptureDevice",
                    new MediaLocator(LOCATOR_PROTOCOL + ":"),
                    DataSource.SUPPORTED_FORMATS,
                    null, null, null);

        List<CaptureDeviceInfo2> captureDevices
            = new ArrayList<CaptureDeviceInfo2>(1);

        captureDevices.add(captureDevice);

        setCaptureDevices(captureDevices);
    }
}
