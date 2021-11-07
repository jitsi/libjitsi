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

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the purposes of
 * ivf (vp8 raw file, extracted from webm) file streaming.
 *
 * @author Thomas Kuntz
 */
public class DataSource
    extends AbstractVideoPullBufferCaptureDevice
{
    /**
     * The format of the VP8 video contained in the IVF file.
     */
    private Format[] SUPPORTED_FORMATS = new Format[1];

    /**
     * The location of the IVF file this <tt>DataSource</tt> will use for the
     * VP8 frames.
     */
    private String fileLocation;

    /**
     * The header of the IVF file this <tt>DataSource</tt> will use for the
     * VP8 frames.
     */
    private IVFHeader ivfHeader;

    /**
     * doConnect allows us to initialize the DataSource with information that
     * we couldn't have in the constructor, like the MediaLocator that give us
     * the path of the ivf file which give us information on the format 
     */
    public void doConnect()
        throws IOException
    {
        super.doConnect();
        this.fileLocation = getLocator().getRemainder();
        ivfHeader = new IVFHeader(this.fileLocation);

        this.SUPPORTED_FORMATS[0] = new VideoFormat(
                Constants.VP8,
                ivfHeader.getDimension(),
                Format.NOT_SPECIFIED,
                Format.byteArray,
                Format.NOT_SPECIFIED);
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    protected IVFStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new IVFStream(this, formatControl);
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
