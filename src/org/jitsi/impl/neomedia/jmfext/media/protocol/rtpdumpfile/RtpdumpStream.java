/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import javax.media.*;
import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

import java.io.*;

/**
 * Implements a <tt>PullBufferStream</tt> which read an rtpdump file to generate
 * a RTP stream from the payloads recorded in a rtpdump file.
 * 
 * @author Thomas Kuntz
 */
public class RtpdumpStream
    extends AbstractVideoPullBufferStream<DataSource>
{
    /**
     * The timestamp of the last time the <tt>doRead</tt> function returned
     * (the timestamp is taken just before the return).
     */
    private long timeLastRead = 0;
    
    /**
     * The timestamp used for the rtp packet (the timestamp change only when
     * a marked packet has been sent).
     */
    private long rtpTimestamp = 0;
    
    /**
     * Boolean indicating if the last call to <tt>doRead</tt> return a marked
     * rtp packet (to know if <tt>rtpTimestamp</tt> needs to be updated).
     */
    private boolean lastReadWasMarked = true;
    
    /**
     * The <tt>RtpdumpFileReader</tt> used by this stream to get the rtp payload.
     */
    private RtpdumpFileReader rtpFileReader;
    
    
    
    /**
     * Initializes a new <tt>ImageStream</tt> instance which is to have a
     * specific <tt>FormatControl</tt>
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> of the new instance which
     * is to specify the format in which it is to provide its media data
     */
    RtpdumpStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);
        
        String rtpdumpFilePath = dataSource.getLocator().getRemainder();
        this.rtpFileReader = new RtpdumpFileReader(rtpdumpFilePath);
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
        Format format;
        
        format = buffer.getFormat();
        if (format == null)
        {
            format = getFormat();
            if (format != null)
                buffer.setFormat(format);
        }
           
        
        RtpdumpPacket rtpPacket = rtpFileReader.getNextPacket(true);
        byte[] data = rtpPacket.getPayload(); 
        
        buffer.setData(data);
        buffer.setOffset(0);
        buffer.setLength(data.length);
        
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);
        if(lastReadWasMarked == true)
        {
            rtpTimestamp = System.nanoTime();
        }
        if( (lastReadWasMarked = rtpPacket.isPacketMarked()) == true)
        {
            buffer.setFlags(buffer.getFlags() | Buffer.FLAG_RTP_MARKER);
        }
        buffer.setTimeStamp(rtpTimestamp);
        
        
        millis = rtpPacket.getRtpdumpTimestamp() - this.timeLastRead;
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
        this.timeLastRead=rtpPacket.getRtpdumpTimestamp();
    }
}