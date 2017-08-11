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
package org.jitsi.impl.neomedia.rtp.translator;

import java.util.*;

import javax.media.*;
import javax.media.rtp.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.RTPHeader;

import org.ice4j.util.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.util.Logger; // Disambiguation.

/**
 * Implements <tt>OutputDataStream</tt> for an <tt>RTPTranslatorImpl</tt>. The
 * packets written into <tt>OutputDataStreamImpl</tt> are copied into multiple
 * endpoint <tt>OutputDataStream</tt>s.
 *
 * @author Lyubomir Marinov
 * @author Maryam Daneshi
 * @author George Politis
 * @author Boris Grozev
 */
class OutputDataStreamImpl
    implements OutputDataStream,
               Runnable
{
    /**
     * The <tt>Logger</tt> used by the <tt>OutputDataStreamImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(OutputDataStreamImpl.class);

    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether the RTP header extension(s) are to be removed
     * from received RTP packets prior to relaying them. The default value is
     * <tt>false</tt>.
     */
    private static final String REMOVE_RTP_HEADER_EXTENSIONS_PNAME
        = RTPTranslatorImpl.class.getName() + ".removeRTPHeaderExtensions";

    private static final int WRITE_Q_CAPACITY
        = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY;

    private boolean closed;

    private final RTPConnectorImpl connector;

    /**
     * The indicator which determines whether RTP data ({@code true}) is written
     * into this {@code OutputDataStreamImpl} or RTP control i.e. RTCP
     * ({@code false}).
     */
    private final boolean _data;

    /**
     * The indicator which determines whether the RTP header extension(s)
     * are to be removed from received RTP packets prior to relaying them.
     * The default value is <tt>false</tt>.
     */
    private final boolean _removeRTPHeaderExtensions;

    /**
     * The {@code List} of {@code OutputDataStream}s into which this
     * {@code OutputDataStream} copies written data/packets. Implemented as a
     * copy-on-write storage in order to reduce synchronized blocks and deadlock
     * risks. I do NOT want to use {@code CopyOnWriteArrayList} because I want
     * to (1) avoid {@code Iterator}s and (2) reduce synchronization. The access
     * to {@link #_streams} is synchronized by {@link #_streamsSyncRoot}.
     */
    private List<OutputDataStreamDesc> _streams = Collections.emptyList();

    /**
     * The {@code Object} which synchronizes the access to {@link #_streams}.
     */
    private final Object _streamsSyncRoot = new Object();

    private final RTPTranslatorBuffer[] writeQ
        = new RTPTranslatorBuffer[WRITE_Q_CAPACITY];

    private int writeQHead;

    private int writeQLength;

    private final QueueStatistics writeQStats;

    /**
     * The number of packets dropped because a packet was inserted while
     * {@link #writeQ} was full.
     */
    private int numDroppedPackets = 0;

    private Thread writeThread;

    public OutputDataStreamImpl(RTPConnectorImpl connector, boolean data)
    {
        this.connector = connector;
        _data = data;

        _removeRTPHeaderExtensions
            = ConfigUtils.getBoolean(
                    LibJitsi.getConfigurationService(),
                    REMOVE_RTP_HEADER_EXTENSIONS_PNAME,
                    false);

        if (logger.isTraceEnabled())
        {
            writeQStats
                = new QueueStatistics(
                    getClass().getSimpleName() + "-" + hashCode());
        }
        else
        {
            writeQStats = null;
        }
    }

    /**
     * Adds a new {@code OutputDataStream} to the list of
     * {@code OutputDataStream}s into which this {@code OutputDataStream} copies
     * written data/packets. If this instance contains the specified
     * {@code stream} already, does nothing.
     *
     * @param connectorDesc the endpoint {@code RTPConnector} which owns
     * {@code stream}
     * @param stream the {@code OutputDataStream} to add to this instance
     */
    public void addStream(
            RTPConnectorDesc connectorDesc,
            OutputDataStream stream)
    {
        synchronized (_streamsSyncRoot)
        {
            // Prevent repetitions.
            for (OutputDataStreamDesc streamDesc : _streams)
            {
                if (streamDesc.connectorDesc == connectorDesc
                        && streamDesc.stream == stream)
                {
                    return;
                }
            }

            // Add. Copy on write.
            List<OutputDataStreamDesc> newStreams
                = new ArrayList<>(_streams.size() * 3 / 2 + 1);

            newStreams.addAll(_streams);
            newStreams.add(new OutputDataStreamDesc(connectorDesc, stream));
            _streams = newStreams;
        }
    }

    public synchronized void close()
    {
        closed = true;
        writeThread = null;
        notify();
    }

    private synchronized void createWriteThread()
    {
        writeThread = new Thread(this, getClass().getName());
        writeThread.setDaemon(true);
        writeThread.start();
    }

    private int doWrite(
            byte[] buf, int off, int len,
            Format format,
            StreamRTPManagerDesc exclusion)
    {
        RTPTranslatorImpl translator = getTranslator();

        if (translator == null)
            return 0;

        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization and, especially, here where
        // I'm to invoke writes on multiple other OutputDataStreams.
        List<OutputDataStreamDesc> streams = _streams;
        boolean removeRTPHeaderExtensions = _removeRTPHeaderExtensions;
        int written = 0;

        // XXX I do NOT want to use an Iterator.
        for (int i = 0, end = streams.size(); i < end; ++i)
        {
            OutputDataStreamDesc s = streams.get(i);
            StreamRTPManagerDesc streamRTPManager
                = s.connectorDesc.streamRTPManagerDesc;

            if (streamRTPManager == exclusion)
                continue;

            boolean write;

            if (_data)
            {
                // TODO The removal of the RTP header extensions is an
                // experiment inspired by
                // https://code.google.com/p/webrtc/issues/detail?id=1095
                // "Chrom WebRTC VP8 RTP packet retransmission does not
                // follow RFC 4588"
                if (removeRTPHeaderExtensions)
                {
                    removeRTPHeaderExtensions = false;
                    len = removeRTPHeaderExtensions(buf, off, len);
                }

                write
                    = willWriteData(
                            streamRTPManager,
                            buf, off, len,
                            format,
                            exclusion);
            }
            else
            {
                write
                    = willWriteControl(
                            streamRTPManager,
                            buf, off, len,
                            format,
                            exclusion);
            }

            if (write)
            {
                // Allow the RTPTranslatorImpl a final chance to filter out the
                // packet on a source-destination basis.
                write
                    = translator.willWrite(
                        /* source */ exclusion,
                    new RawPacket(buf, off, len),
                        /* destination */ streamRTPManager,
                    _data);
            }

            if (write)
            {
                int w = s.stream.write(buf, off, len);

                if (written < w)
                    written = w;
            }
        }
        return written;
    }

    private RTPTranslatorImpl getTranslator()
    {
        return connector.translator;
    }

    /**
     * Removes the RTP header extension(s) from an RTP packet.
     *
     * @param buf the <tt>byte</tt>s of a datagram packet which may contain
     * an RTP packet
     * @param off the offset in <tt>buf</tt> at which the actual data in
     * <tt>buf</tt> starts
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> starting at
     * <tt>off</tt> comprising the actual data
     * @return the number of <tt>byte</tt>s in <tt>buf</tt> starting at
     * <tt>off</tt> comprising the actual data after the possible removal of
     * the RTP header extension(s)
     */
    private static int removeRTPHeaderExtensions(byte[] buf, int off, int len)
    {
        // Do the bytes in the specified buffer resemble (the header of) an
        // RTP packet?
        if (len >= RTPHeader.SIZE)
        {
            byte b0 = buf[off];
            int v = (b0 & 0xC0) >>> 6; /* version */

            if (v == RTPHeader.VERSION)
            {
                boolean x = (b0 & 0x10) == 0x10; /* extension */

                if (x)
                {
                    int cc = b0 & 0x0F; /* CSRC count */
                    int xBegin = off + RTPHeader.SIZE + 4 * cc;
                    int xLen = 2 /* defined by profile */ + 2 /* length */;
                    int end = off + len;

                    if (xBegin + xLen < end)
                    {
                        xLen
                            += RTPUtils.readUint16AsInt(
                                    buf,
                                    xBegin + 2 /* defined by profile */)
                                * 4;

                        int xEnd = xBegin + xLen;

                        if (xEnd <= end)
                        {
                            // Remove the RTP header extension bytes.
                            for (int src = xEnd, dst = xBegin; src < end;)
                                buf[dst++] = buf[src++];
                            len -= xLen;
                            // Switch off the extension bit.
                            buf[off] = (byte) (b0 & 0xEF);
                        }
                    }
                }
            }
        }
        return len;
    }

    /**
     * Removes the {@code OutputDataStream}s owned by a specific
     * {@code RTPConnector} from the list of {@code OutputDataStream}s into
     * which this {@code OutputDataStream} copies written data/packets.
     *
     * @param connectorDesc the {@code RTPConnector} that is the owner of the
     * {@code OutputDataStream}s to remove from this instance.
     */
    public void removeStreams(RTPConnectorDesc connectorDesc)
    {
        synchronized (_streamsSyncRoot)
        {
            // Copy on write. Well, we aren't sure yet whether a write is going
            // to happen but it's the caller's fault if they ask this instance
            // to remove an RTPConnector which this instance doesn't contain.
            List<OutputDataStreamDesc> newStreams = new ArrayList<>(_streams);

            for (Iterator<OutputDataStreamDesc> i = newStreams.iterator();
                    i.hasNext();)
            {
                if (i.next().connectorDesc == connectorDesc)
                    i.remove();
            }
            _streams = newStreams;
        }
    }

    @Override
    public void run()
    {
        try
        {
            do
            {
                int writeIndex;
                byte[] buffer;
                StreamRTPManagerDesc exclusion;
                Format format;
                int length;

                synchronized (this)
                {
                    if (closed || !Thread.currentThread().equals(writeThread))
                        break;
                    if (writeQLength < 1)
                    {
                        boolean interrupted = false;

                        try
                        {
                            wait();
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                        if (interrupted)
                            Thread.currentThread().interrupt();
                        continue;
                    }

                    writeIndex = writeQHead;

                    RTPTranslatorBuffer write = writeQ[writeIndex];

                    buffer = write.data;
                    write.data = null;
                    exclusion = write.exclusion;
                    write.exclusion = null;
                    format = write.format;
                    write.format = null;
                    length = write.length;
                    write.length = 0;

                    writeQHead++;
                    if (writeQHead >= writeQ.length)
                        writeQHead = 0;
                    writeQLength--;
                    if (writeQStats != null)
                    {
                        writeQStats.remove(System.currentTimeMillis());
                    }
                }

                try
                {
                    doWrite(buffer, 0, length, format, exclusion);
                }
                finally
                {
                    synchronized (this)
                    {
                        RTPTranslatorBuffer write = writeQ[writeIndex];

                        if (write != null && write.data == null)
                            write.data = buffer;
                    }
                }
            }
            while (true);
        }
        catch (Throwable t)
        {
            logger.error("Failed to translate RTP packet", t);
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        finally
        {
            synchronized (this)
            {
                if (Thread.currentThread().equals(writeThread))
                    writeThread = null;
                if (!closed && writeThread == null && writeQLength > 0)
                    createWriteThread();
            }
        }
    }

    /**
     * Notifies this instance that a specific <tt>byte</tt> buffer will be
     * written into the control <tt>OutputDataStream</tt> of a specific
     * <tt>StreamRTPManagerDesc</tt>.
     *
     * @param destination the <tt>StreamRTPManagerDesc</tt> which is the
     * destination of the write
     * @param buffer the data to be written into <tt>destination</tt>
     * @param offset the offset in <tt>buffer</tt> at which the data to be
     * written into <tt>destination</tt> starts
     * @param length the number of <tt>byte</tt>s in <tt>buffer</tt>
     * beginning at <tt>offset</tt> which constitute the data to the written
     * into <tt>destination</tt>
     * @param format the FMJ <tt>Format</tt> of the data to be written into
     * <tt>destination</tt>
     * @param exclusion the <tt>StreamRTPManagerDesc</tt> which is exclude
     * from the write batch, possibly because it is the cause of the write
     * batch in the first place
     * @return <tt>true</tt> to write the specified data into the specified
     * <tt>destination</tt> or <tt>false</tt> to not write the specified
     * data into the specified <tt>destination</tt>
     */
    private boolean willWriteControl(
            StreamRTPManagerDesc destination,
            byte[] buffer, int offset, int length,
            Format format,
            StreamRTPManagerDesc exclusion)
    {
        boolean write = true;

        // Do the bytes in the specified buffer resemble (the header of) an RTCP
        // packet?
        if (length >= 12 /* FB */)
        {
            byte b0 = buffer[offset];
            int v = (b0 & 0xc0) >>> 6; /* version */

            if (v == RTCPHeader.VERSION)
            {
                byte b1 = buffer[offset + 1];
                int pt = b1 & 0xff; /* payload type */
                int fmt = b0 & 0x1f; /* feedback message type */

                if ((pt == 205 /* RTPFB */) || (pt == 206 /* PSFB */))
                {
                    // Verify the length field.
                    int rtcpLength
                        = (RTPUtils.readUint16AsInt(
                                    buffer,
                                    offset + 2)
                                + 1)
                            * 4;

                    if (rtcpLength <= length)
                    {
                        int ssrcOfMediaSource = 0;
                        if (pt == 206 && fmt == 4) //FIR
                        {
                            if (rtcpLength < 20)
                            {
                                // FIR messages are at least 20 bytes long
                                write = false;
                            }
                            else
                            {
                                // FIR messages don't have a valid 'media
                                // source' field, use the SSRC from the first
                                // FCI entry instead
                                ssrcOfMediaSource
                                    = RTPUtils.readInt(buffer, offset + 12);
                            }
                        }
                        else
                        {
                            ssrcOfMediaSource
                                = RTPUtils.readInt(buffer, offset + 8);
                        }

                        if (destination.containsReceiveSSRC(
                                ssrcOfMediaSource))
                        {
                            if (logger.isTraceEnabled())
                            {
                                int ssrcOfPacketSender
                                    = RTPUtils.readInt(buffer, offset + 4);
                                String message
                                    = getClass().getName()
                                        + ".willWriteControl: FMT " + fmt
                                        + ", PT " + pt
                                        + ", SSRC of packet sender "
                                        + Long.toString(
                                                ssrcOfPacketSender
                                                    & 0xffffffffl)
                                        + ", SSRC of media source "
                                        + Long.toString(
                                                ssrcOfMediaSource
                                                    & 0xffffffffl);

                                logger.trace(message);
                            }
                        }
                        else
                        {
                            write = false;
                        }
                    }
                }
            }
        }

        if (write && logger.isTraceEnabled())
            RTPTranslatorImpl.logRTCP(this, "doWrite", buffer, offset, length);
        return write;
    }

    /**
     * Notifies this instance that a specific <tt>byte</tt> buffer will be
     * written into the data <tt>OutputDataStream</tt> of a specific
     * <tt>StreamRTPManagerDesc</tt>.
     *
     * @param destination the <tt>StreamRTPManagerDesc</tt> which is the
     * destination of the write
     * @param buf the data to be written into <tt>destination</tt>
     * @param off the offset in <tt>buf</tt> at which the data to be written
     * into <tt>destination</tt> starts
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> beginning at
     * <tt>off</tt> which constitute the data to the written into
     * <tt>destination</tt>
     * @param format the FMJ <tt>Format</tt> of the data to be written into
     * <tt>destination</tt>
     * @param exclusion the <tt>StreamRTPManagerDesc</tt> which is exclude
     * from the write batch, possibly because it is the cause of the write
     * batch in the first place
     * @return <tt>true</tt> to write the specified data into the specified
     * <tt>destination</tt> or <tt>false</tt> to not write the specified
     * data into the specified <tt>destination</tt>
     */
    private boolean willWriteData(
            StreamRTPManagerDesc destination,
            byte[] buf, int off, int len,
            Format format,
            StreamRTPManagerDesc exclusion)
    {
        // Only write data packets to OutputDataStreams for which the
        // associated MediaStream allows sending.
        if (!destination.streamRTPManager.getMediaStream().getDirection()
                .allowsSending())
        {
            return false;
        }

        if (format != null && len > 0)
        {
            Integer pt = destination.getPayloadType(format);

            if (pt == null && exclusion != null)
            {
                pt = exclusion.getPayloadType(format);
            }
            if (pt != null)
            {
                int ptByteIndex = off + 1;

                buf[ptByteIndex]
                    = (byte) ((buf[ptByteIndex] & 0x80) | (pt & 0x7f));
            }
        }

        return true;
    }

    @Override
    public int write(byte[] buf, int off, int len)
    {
        // FIXME It's unclear at the time of this writing why the method doWrite
        // is being invoked here and not the overloaded method write.
        return doWrite(buf, off, len, /* format */ null, /* exclusion */ null);
    }

    public synchronized void write(
            byte[] buf, int off, int len,
            Format format,
            StreamRTPManagerDesc exclusion)
    {
        if (closed)
            return;

        int writeIndex;

        if (writeQLength < writeQ.length)
        {
            writeIndex = (writeQHead + writeQLength) % writeQ.length;
        }
        else
        {
            writeIndex = writeQHead;
            writeQHead++;
            if (writeQHead >= writeQ.length)
                writeQHead = 0;
            writeQLength--;
            if (writeQStats != null)
            {
                writeQStats.remove(System.currentTimeMillis());
            }

            numDroppedPackets++;
            if (RTPConnectorOutputStream.logDroppedPacket(numDroppedPackets))
            {
                logger.warn(
                        "Dropped " + numDroppedPackets + " packets "
                                + "hashCode=" + hashCode() + "): ");
            }
        }

        RTPTranslatorBuffer write = writeQ[writeIndex];

        if (write == null)
            writeQ[writeIndex] = write = new RTPTranslatorBuffer();

        byte[] data = write.data;

        if (data == null || data.length < len)
            write.data = data = new byte[len];
        System.arraycopy(buf, off, data, 0, len);

        write.exclusion = exclusion;
        write.format = format;
        write.length = len;

        writeQLength++;
        if (writeQStats != null)
        {
            writeQStats.add(System.currentTimeMillis());
        }

        if (writeThread == null)
            createWriteThread();
        else
            notify();
    }

    /**
     * Writes an <tt>RTCPFeedbackMessage</tt> into a destination identified by
     * a specific <tt>MediaStream</tt>.
     *
     * @param controlPayload
     * @param destination
     * @return <tt>true</tt> if the <tt>controlPayload</tt> was written
     * into the <tt>destination</tt>; otherwise, <tt>false</tt>
     */
    boolean writeControlPayload(Payload controlPayload, MediaStream destination)
    {
        // XXX The field _streams is explicitly implemented as a copy-on-write
        // storage in order to avoid synchronization.
        List<OutputDataStreamDesc> streams = _streams;

        // XXX I do NOT want to use an Iterator.
        for (int i = 0, end = streams.size(); i < end; ++i)
        {
            OutputDataStreamDesc s = streams.get(i);

            if (destination
                    == s.connectorDesc.streamRTPManagerDesc.streamRTPManager
                            .getMediaStream())
            {
                controlPayload.writeTo(s.stream);
                return true;
            }
        }
        return false;
    }
}
