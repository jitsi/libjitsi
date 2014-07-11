/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

import java.io.*;

/**
 * 
 * @author Thomas Kuntz
 * 
 * Implements a <tt>PullBufferStream</tt> which read an IVF file for the frames
 * of the video stream.
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
     * The <tt>IVFFileReader</tt> used to get the frame of the IVF file.
     */
    private IVFFileReader ivfFileReader;
    
    /**
     * The framerate of the video stream.
     * It start with a value of 1 (to avoid division by zero) but will take
     * the framerate of the IVF file.
     */
    private float FRAMERATE = 1;
    
    
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
        
        this.FRAMERATE = ((VideoFormat)getFormat()).getFrameRate();
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
                
        byte[] data = ivfFileReader.getNextFrame(true);
        
        buffer.setData(data);
        buffer.setOffset(0);
        buffer.setLength(data.length);
        
        
        buffer.setTimeStamp(System.nanoTime());
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);
        
        
        millis = System.currentTimeMillis() - this.timeLastRead;
        millis = (long)(1000.0 / this.FRAMERATE) - millis;
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
        this.timeLastRead=System.currentTimeMillis();
    }
}