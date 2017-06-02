/*
 * Copyright @ 2015 Atlassian Pty Ltd
 * Copyright @ 2017 Alianza, Inc.
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

package org.jitsi.impl.neomedia.jmfext.media.protocol.wavfile;

import org.jitsi.impl.neomedia.device.AudioMediaDeviceImpl;

import javax.media.CaptureDeviceInfo;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.format.AudioFormat;

/**
 * Implements a <tt>MediaDevice</tt> which provides a wrapper around a wav file
 *
 * @author Thomas Kuntz
 * @author Mike Saavedra
 */
public class WavMediaDevice extends AudioMediaDeviceImpl {
    /**
     * The list of <tt>Format</tt>s supported by the <tt>WavMediaDevice</tt> instances.
     */
    protected static final Format[] SUPPORTED_FORMATS = new Format[] {
            new AudioFormat(AudioFormat.LINEAR, 8000d, 16, 1)
    };

    /**
     * Initializes a new <tt>WavMediaDevice</tt> instance which will read the wav file located at <tt>filename</tt>.
     * 
     * @param filename the location of the wav file that the <tt>WavStream<tt> will read.
     */
    public WavMediaDevice(String filename) {
        super(new CaptureDeviceInfo(filename, new MediaLocator("wavfile:"+filename), SUPPORTED_FORMATS));
    }
}
