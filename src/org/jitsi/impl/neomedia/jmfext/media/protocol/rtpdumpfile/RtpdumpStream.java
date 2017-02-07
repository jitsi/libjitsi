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

package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

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
     * The <tt>Logger</tt> used by <tt>RtpdumpStream</tt> and its instances
     * for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(RtpdumpStream.class);

    /**
     * The <tt>RawPacketScheduler</tt> responsible for throttling our RTP packet
     * reading.
     */
    private final RawPacketScheduler rawPacketScheduler;

    /**
     * Boolean indicating if the last call to <tt>doRead</tt> return a marked
     * rtp packet (to know if <tt>timestamp</tt> needs to be updated).
     */
    private boolean lastReadWasMarked = true;

    /**
     * The <tt>RtpdumpFileReader</tt> used by this stream to get the rtp payload.
     */
    private RtpdumpFileReader rtpFileReader;

    /**
     * The timestamp to use for the timestamp of the next <tt>Buffer</tt> filled
     * in {@link #doRead(javax.media.Buffer)}
     */
    private long timestamp;

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

        /*
         * NOTE: We use the sampleRate or frameRate field of the format to
         * piggyback the RTP clock rate. See
         * RtpdumpMediaDevice#createRtpdumpMediaDevice.
         */
        Format format = getFormat();
        long clockRate;
        if (format instanceof AudioFormat)
        {
            clockRate = (long) ((AudioFormat) format).getSampleRate();
        }
        else if (format instanceof VideoFormat)
        {
            clockRate = (long) ((VideoFormat) format).getFrameRate();
        }
        else
        {
            logger.warn("Unknown format. Creating RtpdumpStream with clock" +
                                "rate 1 000 000 000.");
            clockRate = 1000 * 1000 * 1000;
        }

        this.rawPacketScheduler =  new RawPacketScheduler(clockRate);
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
     * <tt>Buffer</tt>
     */
    @Override
    protected void doRead(Buffer buffer)
        throws IOException
    {
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
        if(lastReadWasMarked)
        {
            timestamp = System.nanoTime();
        }
        lastReadWasMarked = rtpPacket.isPacketMarked();
        if(lastReadWasMarked)
        {
            buffer.setFlags(buffer.getFlags() | Buffer.FLAG_RTP_MARKER);
        }
        buffer.setTimeStamp(timestamp);

        try
        {
            rawPacketScheduler.schedule(rtpPacket);
        }
        catch (InterruptedException e)
        {

        }
    }
}
