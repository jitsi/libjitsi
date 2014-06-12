/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
