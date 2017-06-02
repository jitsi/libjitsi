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

import org.jitsi.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.jitsi.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;

import javax.media.Format;
import javax.media.control.FormatControl;
import java.io.IOException;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wavfile.WavMediaDevice.SUPPORTED_FORMATS;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the purposes of wav file streaming.
 *
 * @author Thomas Kuntz
 * @author Mike Saavedra
 */
public class DataSource extends AbstractPullBufferCaptureDevice {
    /**
     * The location of the wav file this <tt>DataSource</tt> will use.
     */
    private String fileLocation;

    /**
     * doConnect allows us to initialize the DataSource with information that
     * we couldn't have in the constructor, like the MediaLocator that give us
     * the path of the wav file which give us information on the format
     */
    public void doConnect() throws IOException {
        super.doConnect();
        this.fileLocation = getLocator().getRemainder();
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    protected WavStream createStream(int streamIndex, FormatControl formatControl) {
        return new WavStream(this, formatControl);
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of
     * <tt>Format</tt>s hardcoded as supported in
     * <tt>IVFCaptureDevice</tt> because the super looks them up by
     * <tt>CaptureDeviceInfo</tt> and it doesn't have some information
     * (like the framerate etc.).
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return SUPPORTED_FORMATS.clone();
    }
}
