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

package org.jitsi.impl.neomedia.jmfext.media.protocol.greyfading;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

import java.awt.Dimension;
import java.io.*;
import java.util.*;

/**
 * Implements a <tt>PullBufferStream</tt> which provides a fading animation from
 * white to black to white... in form of video.
 * 
 * @author Thomas Kuntz
 */
public class VideoGreyFadingStream
    extends AbstractVideoPullBufferStream<DataSource>
{
    /**
     * The value for the color of the RGB bytes
     */
    private int color = 0;
    
    /**
     * Indicate if the video is fading toward white (if true) or toward
     * black (if false)
     */
    private boolean increment = true;
    
    /**
     * The timestamp of the last time the <tt>doRead</tt> function returned
     * (the timestamp is taken just before the return).
     */
    private long timeLastRead = 0;

    /**
     * Initializes a new <tt>VideoGreyFadingStream</tt> which is to be exposed
     * by a specific <tt>VideoGreyFadingCaptureDevice</tt> and which is to have
     * its <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>VideoGreyFadingCaptureDevice</tt> which is
     * initializing the new instance and which is to expose it in its array
     * of <tt>PushBufferStream</tt>s
     * @param formatControl the <tt>FormatControl</tt> which is to abstract
     * the <tt>Format</tt>-related information of the new instance
     */
    public VideoGreyFadingStream(
            DataSource dataSource,
            FormatControl formatControl)
    {
        super(dataSource, formatControl);
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
        long millis = 0;
        VideoFormat format;
        
        
        format = (VideoFormat)buffer.getFormat();

        if (format == null)
        {
            format = (VideoFormat)getFormat();
            if (format != null)
                buffer.setFormat(format);
        }
        
        Dimension size = format.getSize();
        int frameSizeInBytes
        = (int) (
          size.getHeight()
        * size.getWidth()
        * 4);
        
        byte[] data
        = AbstractCodec2.validateByteArraySize(
                buffer,
                frameSizeInBytes,
                false);

        Arrays.fill(data, 0, frameSizeInBytes, (byte) color);
        
        if(increment)
        {
            color+=3;
        }
        else
        {
            color-=3;
        }
        if(color >= 255)
        {
            increment = false;
            color=255;
        }
        else if(color <= 0) 
        {
            increment = true;
            color=0;
        }

        buffer.setData(data);
        buffer.setOffset(0);
        buffer.setLength(frameSizeInBytes);
        
        
        buffer.setTimeStamp(System.nanoTime());
        
        
        //To respect the framerate, we wait for the remaining milliseconds since
        //last doRead call.
        millis = System.currentTimeMillis() - timeLastRead;
        millis = (long)(1000.0 / format.getFrameRate()) - millis;
        if(millis > 0)
        {
            try
            {
                Thread.sleep(millis);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        timeLastRead=System.currentTimeMillis();
    }
}
