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
package org.jitsi.impl.neomedia.jmfext.media.protocol.imgstreaming;

import java.awt.*;
import java.awt.image.*;
import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.imgstreaming.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.utils.logging.*;

/**
 * The stream used by JMF for our image streaming.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class ImageStream
    extends AbstractVideoPullBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>ImageStream</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(ImageStream.class);

    /**
     * The pool of <tt>ByteBuffer</tt>s this instances is using to optimize the
     * allocations and deallocations of <tt>ByteBuffer</tt>s.
     */
    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    /**
     * Desktop interaction (screen capture, key press, ...).
     */
    private DesktopInteract desktopInteract = null;

    /**
     * Index of display that we will capture from.
     */
    private int displayIndex = -1;

    /**
     * Sequence number.
     */
    private long seqNo = 0;

    /**
     * X origin.
     */
    private int x = 0;

    /**
     * Y origin.
     */
    private int y = 0;

    /**
     * Initializes a new <tt>ImageStream</tt> instance which is to have a
     * specific <tt>FormatControl</tt>
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> of the new instance which
     * is to specify the format in which it is to provide its media data
     */
    ImageStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);
    }

    /**
     * Blocks and reads into a <tt>Buffer</tt> from this
     * <tt>PullBufferStream</tt>.
     *
     * @param buffer the <tt>Buffer</tt> this <tt>PullBufferStream</tt> is to
     * read into
     * @throws IOException if an I/O error occurs while this
     * <tt>PullBufferStream</tt> reads into the specified <tt>Buffer</tt>
     * @see AbstractVideoPullBufferStream#doRead(Buffer)
     */
    @Override
    protected void doRead(Buffer buffer)
        throws IOException
    {
        /*
         * Determine the Format in which we're expected to output. We cannot
         * rely on the Format always being specified in the Buffer because it is
         * not its responsibility, the DataSource of this ImageStream knows the
         * output Format.
         */
        Format format = buffer.getFormat();

        if (format == null)
        {
            format = getFormat();
            if (format != null)
                buffer.setFormat(format);
        }

        if(format instanceof AVFrameFormat)
        {
            Object o = buffer.getData();
            AVFrame frame;

            if (o instanceof AVFrame)
                frame = (AVFrame) o;
            else
            {
                frame = new AVFrame();
                buffer.setData(frame);
            }

            AVFrameFormat avFrameFormat = (AVFrameFormat) format;
            Dimension size = avFrameFormat.getSize();
            ByteBuffer data = readScreenNative(size);

            if(data != null)
            {
                if (frame.avpicture_fill(data, avFrameFormat) < 0)
                {
                    data.free();
                    throw new IOException("avpicture_fill");
                }
            }
            else
            {
                /*
                 * This can happen when we disconnect a monitor from computer
                 * before or during grabbing.
                 */
                throw new IOException("Failed to grab screen.");
            }
        }
        else
        {
            byte[] bytes = (byte[]) buffer.getData();
            Dimension size = ((VideoFormat) format).getSize();

            bytes = readScreen(bytes, size);

            buffer.setData(bytes);
            buffer.setOffset(0);
            buffer.setLength(bytes.length);
        }

        buffer.setHeader(null);
        buffer.setTimeStamp(System.nanoTime());
        buffer.setSequenceNumber(seqNo);
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME | Buffer.FLAG_LIVE_DATA);
        seqNo++;
    }

    /**
     * Read screen.
     *
     * @param output output buffer for screen bytes
     * @param dim dimension of the screen
     * @return raw bytes, it could be equal to output or not. Take care in the
     * caller to check if output is the returned value.
     */
    public byte[] readScreen(byte[] output, Dimension dim)
    {
        VideoFormat format = (VideoFormat) getFormat();
        Dimension formatSize = format.getSize();
        int width = formatSize.width;
        int height = formatSize.height;
        BufferedImage scaledScreen = null;
        BufferedImage screen = null;
        byte data[] = null;
        int size = width * height * 4;

        // If output is not large enough, enlarge it.
        if ((output == null) || (output.length < size))
            output = new byte[size];

        /* get desktop screen via native grabber if available */
        if(desktopInteract.captureScreen(
                displayIndex,
                x, y, dim.width, dim.height,
                output))
        {
            return output;
        }

        System.out.println("failed to grab with native! " + output.length);

        /* OK native grabber failed or is not available,
         * try with AWT Robot and convert it to the right format
         *
         * Note that it is very memory consuming since memory are allocated
         * to capture screen (via Robot) and then for converting to raw bytes
         * Moreover support for multiple display has not yet been investigated
         *
         * Normally not of our supported platform (Windows (x86, x64),
         * Linux (x86, x86-64), Mac OS X (i386, x86-64, ppc) and
         * FreeBSD (x86, x86-64) should go here.
         */
        screen = desktopInteract.captureScreen();

        if(screen != null)
        {
            /* convert to ARGB BufferedImage */
            scaledScreen
                = ImgStreamingUtils.getScaledImage(
                        screen,
                        width, height,
                        BufferedImage.TYPE_INT_ARGB);
            /* get raw bytes */
            data = ImgStreamingUtils.getImageBytes(scaledScreen, output);
        }

        screen = null;
        scaledScreen = null;
        return data;
    }

    /**
     * Read screen and store result in native buffer.
     *
     * @param dim dimension of the video
     * @return true if success, false otherwise
     */
    private ByteBuffer readScreenNative(Dimension dim)
    {
        int size
            = dim.width * dim.height * 4 + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE;
        ByteBuffer data = byteBufferPool.getBuffer(size);

        data.setLength(size);

        /* get desktop screen via native grabber */
        boolean b;

        try
        {
            b
                = desktopInteract.captureScreen(
                        displayIndex,
                        x, y, dim.width, dim.height,
                        data.getPtr(), data.getLength());
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
            {
                throw (ThreadDeath) t;
            }
            else
            {
                b = false;
//                logger.error("Failed to grab screen!", t);
            }
        }
        if (!b)
        {
            data.free();
            data = null;
        }
        return data;
    }

    /**
     * Sets the index of the display to be used by this <tt>ImageStream</tt>.
     *
     * @param displayIndex the index of the display to be used by this
     * <tt>ImageStream</tt>
     */
    public void setDisplayIndex(int displayIndex)
    {
        this.displayIndex = displayIndex;
    }

    /**
     * Sets the origin to be captured by this <tt>ImageStream</tt>.
     *
     * @param x the x coordinate of the origin to be set on this instance
     * @param y the y coordinate of the origin to be set on this instance
     */
    public void setOrigin(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    /**
     * Start desktop capture stream.
     *
     * @see AbstractPullBufferStream#start()
     */
    @Override
    public void start()
        throws IOException
    {
        super.start();

        if(desktopInteract == null)
        {
            try
            {
                desktopInteract = new DesktopInteractImpl();
            }
            catch(Exception e)
            {
                logger.warn("Cannot create DesktopInteract object!");
            }
        }
    }

    /**
     * Stop desktop capture stream.
     *
     * @see AbstractPullBufferStream#stop()
     */
    @Override
    public void stop()
        throws IOException
    {
        try
        {
            if (logger.isInfoEnabled())
                logger.info("Stop stream");
        }
        finally
        {
            super.stop();

            byteBufferPool.drain();
        }
    }
}
