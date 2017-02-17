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

import com.musicg.wave.Wave;
import org.jitsi.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream;

import javax.media.Buffer;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import java.io.IOException;

/**
 * Implements a <tt>PullBufferStream</tt> which read a wav file
 *
 * @author Thomas Kuntz
 * @author Mike Saavedra
 */
public class WavStream extends AbstractPullBufferStream<DataSource> {

    protected Wave wave;

    /**
     * Initializes a new <tt>WavStream</tt> instance which is to have a specific <tt>FormatControl</tt>
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> of the new instance which
     * is to specify the format in which it is to provide its media data
     */
    WavStream(DataSource dataSource, FormatControl formatControl) {
        super(dataSource, formatControl);
        String filePath = dataSource.getLocator().getRemainder();
        wave = new Wave(filePath);
    }

    /**
     * Reads available media data from this instance into a specific <tt>Buffer</tt>.
     *
     * @param buffer the <tt>Buffer</tt> to write the available media data into
     * @throws IOException if an I/O error has prevented the reading of
     * available media data from this instance into the specified <tt>buffer</tt>
     */
    @Override
    public void read(Buffer buffer) throws IOException {
        AudioFormat format;
        
        format = (AudioFormat)buffer.getFormat();
        if (format == null)
        {
            format = (AudioFormat)getFormat();
            if (format != null)
                buffer.setFormat(format);
        }

        byte[] data = wave.getBytes();
        buffer.setData(data);
        buffer.setOffset(0);
        buffer.setLength(data.length);

        buffer.setTimeStamp(System.nanoTime());
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);
    }

}
