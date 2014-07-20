/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
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
     * The timestamp of the last rtp packet (the timestamp change only when
     * a marked packet has been sent).
     * 
     * It is initialize with Long.MAX_VALUE because the timestamp of the first
     * packet read isn't generally 0, and the difference between it and the
     * first value of lastRtpTimestamp would in this case be huge, making the
     * stream sleep forever. 
     */
    private long lastRtpTimestamp = Long.MAX_VALUE;

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
     * The timebase of the stream in nanoseconds.
     * timebase = 1sec / clock rate.
     * The clock rate is the sample rate for audio format, and framerate for
     * video format.
     */
    private final long TIMEBASE;

    /**
     * The timestamp use for the timestamp of the RTP packet.
     */
    private long rtpTimestamp;



    /**
     * Initializes a new <tt>RtpdumpStream</tt> instance
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> of the new instance which
     * is to specify the format in which it is to provide its media data
     */
    RtpdumpStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);

        Format format = getFormat();
        if(format instanceof AudioFormat)
        {
            AudioFormat audioFormat = ((AudioFormat)format);
            this.TIMEBASE = (long) (1000000000 / audioFormat.getSampleRate());
        }
        else if(format instanceof VideoFormat)
        {
            VideoFormat videoFormat = ((VideoFormat)format);
            this.TIMEBASE = (long) (1000000000 / videoFormat.getFrameRate());
        }
        else
        {
            this.TIMEBASE = 1;
        }

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
        long nanos = 0;
        Format format;

        format = buffer.getFormat();
        if (format == null)
        {
            format = getFormat();
            if (format != null)
                buffer.setFormat(format);
        }


        RawPacket rtpPacket = rtpFileReader.getNextPacket(true);
        byte[] data = rtpPacket.getPayload(); 

        buffer.setData(data);
        buffer.setOffset(rtpPacket.getOffset());
        buffer.setLength(rtpPacket.getPayloadLength());



        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);
        if(lastReadWasMarked == true)
        {
            rtpTimestamp = System.nanoTime();
        }
        lastReadWasMarked = rtpPacket.isPacketMarked();
        if(rtpPacket.isPacketMarked())
        {
            buffer.setFlags(buffer.getFlags() | Buffer.FLAG_RTP_MARKER);
        }
        buffer.setTimeStamp(rtpTimestamp);


        nanos = rtpPacket.getTimestamp() - this.lastRtpTimestamp;
        nanos = this.TIMEBASE * nanos;
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
        this.lastRtpTimestamp=rtpPacket.getTimestamp();
    }
}