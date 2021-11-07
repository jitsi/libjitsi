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

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

import java.io.*;

/**
 * Implements a <tt>PullBufferStream</tt> which read an IVF file for the frames
 * of the video stream.
 *
 * @author Thomas Kuntz
 */
public class IVFStream
    extends AbstractVideoPullBufferStream<DataSource>
{
    /**
     * The timestamp of the last time the <tt>doRead</tt> function returned
     * (the timestamp is taken just before the return).
     */
    private long timeLastRead = 0;

    /**
     * The timestamp of the frame (of its header) read during the last call
     * to doRead. It's used to determine if enough time has passed during the
     * last frame and the next frame to be returned.
     */
    private long lastFrameTimestamp = 0;

    /**
     * The VP8Frame that is filled by the VP8 frame and header read in the IVF
     * file. We use the same VP8Frame object (set with the new frame each time
     * of course) to avoid the allocation of a new VP8Frame each time.
     */
    private VP8Frame frame = new VP8Frame();

    /**
     * The <tt>IVFFileReader</tt> used to get the frame of the IVF file.
     */
    private IVFFileReader ivfFileReader;

    /**
     * The timebase of the video stream (timescale / framerate)
     * 
     * an IVF file doesn't have a well-defined "framerate" :
     * each frame has its own timestamp. The two fields in the IVF file header,
     * "rate" and "scale" are used to specify the "timebase",
     * which controls how the frames' timestamps are to be interpreted.
     * A frame timestamp of "X" means "X * scale / rate" seconds.
     * 
     * Here the timebase should be in nanoseconds.
     */
    private final long TIMEBASE;

    /**
     * Initializes a new <tt>IVFStream</tt> instance which is to have a
     * specific <tt>FormatControl</tt>
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> of the new instance which
     * is to specify the format in which it is to provide its media data
     */
    IVFStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);
        this.ivfFileReader = new IVFFileReader(
                dataSource.getLocator().getRemainder());

        //this.TIMEBASE = (int)(1000. / ((VideoFormat)getFormat()).getFrameRate());
        IVFHeader header = ivfFileReader.getHeader();
        this.TIMEBASE = 1000000000 *  header.getTimeScale() / header.getFramerate();
    }

    /**
     * Reads available media data from this instance into a specific
     * <tt>Buffer</tt>.
     *
     * @param buffer the <tt>Buffer</tt> to write the available media data
     * into
     * @throws IOException if an I/O error has prevented the reading of
     * available media data from this instance into the specified
     * <tt>buffer</tt>
     */
    @Override
    protected void doRead(Buffer buffer)
        throws IOException
    {
        long nanos = 0;
        VideoFormat format;
        
        format = (VideoFormat)buffer.getFormat();
        if (format == null)
        {
            format = (VideoFormat)getFormat();
            if (format != null)
                buffer.setFormat(format);
        }

        ivfFileReader.getNextFrame(frame, true);

        buffer.setData(frame.getFrameData());
        buffer.setOffset(0);
        buffer.setLength(frame.getFrameLength());

        buffer.setTimeStamp(System.nanoTime());
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);

        /*
         * We just check if the time that has passed since the last call to doRead
         * took more or less milliseconds & nanoseconds than the number of
         * milliseconds & nanoseconds
         * between 2 consecutive frames (based on their timestamps and the
         * the timebase). If it's less, we wait the remaining
         * time (and if its more, we directly return).
         */
        nanos = System.nanoTime() - this.timeLastRead;
        nanos = (frame.getTimestamp() - lastFrameTimestamp) * TIMEBASE - nanos;

        /*
         * Even if we loop the IVF file, there won't be any problem with millis
         * when the last frame of the file was reached and the current frame is
         * the first one : (frame.getTimestamp() - lastFrameTimestamp) will
         * be negative so we won't sleep this time.
         */
        if(nanos > 0)
        {
            try
            {
                Thread.sleep(
                        nanos / 1000000,
                        (int) (nanos % 1000000));
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        this.lastFrameTimestamp = frame.getTimestamp();
        this.timeLastRead = System.nanoTime();
    }
}
