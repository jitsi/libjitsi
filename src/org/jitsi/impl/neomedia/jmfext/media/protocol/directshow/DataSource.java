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
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.media.*;
import javax.media.control.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.utils.logging.*;

/**
 * Implements a <tt>CaptureDevice</tt> and a <tt>DataSource</tt> using
 * DirectShow.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class DataSource
    extends AbstractVideoPushBufferCaptureDevice
{
    /**
     * The map of DirectShow pixel formats to FFmpeg pixel formats which allows
     * converting between the two.
     */
    private static final int[] DS_TO_FFMPEG_PIX_FMTS
        = new int[]
                {
                    DSFormat.RGB24,
                    FFmpeg.PIX_FMT_RGB24,
                    DSFormat.RGB32,
                    FFmpeg.PIX_FMT_RGB32,
                    DSFormat.ARGB32,
                    FFmpeg.PIX_FMT_ARGB,
                    DSFormat.YUY2,
                    FFmpeg.PIX_FMT_YUYV422,
                    DSFormat.MJPG,
                    FFmpeg.PIX_FMT_YUVJ422P,
                    DSFormat.UYVY,
                    FFmpeg.PIX_FMT_UYVY422,
                    DSFormat.Y411,
                    FFmpeg.PIX_FMT_UYYVYY411,
                    DSFormat.Y41P,
                    FFmpeg.PIX_FMT_YUV411P,
                    DSFormat.NV12,
                    FFmpeg.PIX_FMT_NV12,
                    DSFormat.I420,
                    FFmpeg.PIX_FMT_YUV420P
                };

    /**
     * The <tt>Logger</tt> used by the <tt>DataSource</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DataSource.class);

    /**
     * Gets the FFmpeg pixel format matching a specific DirectShow
     * Specification pixel format.
     *
     * @param ffmpegPixFmt FFmpeg format
     * @return the DirectShow pixel format matching the specified FFmpeg format
     */
    public static int getDSPixFmt(int ffmpegPixFmt)
    {
        for (int i = 0; i < DS_TO_FFMPEG_PIX_FMTS.length; i += 2)
            if (DS_TO_FFMPEG_PIX_FMTS[i + 1] == ffmpegPixFmt)
                return DS_TO_FFMPEG_PIX_FMTS[i];
        return -1;
    }

    /**
     * Gets the DirectShow pixel format matching a specific FFmpeg pixel
     * format.
     *
     * @param dsPixFmt the DirectShow pixel format to get the matching
     * FFmpeg pixel format of
     * @return the FFmpeg pixel format matching the specified DirectShow pixel
     */
    public static int getFFmpegPixFmt(int dsPixFmt)
    {
        for (int i = 0; i < DS_TO_FFMPEG_PIX_FMTS.length; i += 2)
            if (DS_TO_FFMPEG_PIX_FMTS[i] == dsPixFmt)
                return DS_TO_FFMPEG_PIX_FMTS[i + 1];
        return FFmpeg.PIX_FMT_NONE;
    }

    /**
     * DirectShow capture device.
     */
    private DSCaptureDevice device;

    /**
     * DirectShow manager.
     */
    private DSManager manager;

    /**
     * Constructor.
     */
    public DataSource()
    {
        this(null);
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance from a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);
    }

    /**
     * Creates a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPushBufferCaptureDevice</tt>.
     *
     * @return a new <tt>FrameRateControl</tt> instance which is to allow the
     * getting and setting of the frame rate of this
     * <tt>AbstractVideoPushBufferCaptureDevice</tt>
     * @see AbstractPushBufferCaptureDevice#createFrameRateControl()
     */
    @Override
    protected FrameRateControl createFrameRateControl()
    {
        return
            new FrameRateControlAdapter()
            {
                /**
                 * The output frame rate of this
                 * <tt>AbstractVideoPullBufferCaptureDevice</tt>.
                 */
                private float frameRate = -1;

                @Override
                public float getFrameRate()
                {
                    return frameRate;
                }

                @Override
                public float setFrameRate(float frameRate)
                {
                    this.frameRate = frameRate;
                    return this.frameRate;
                }
            };
    }

    /**
     * Create a new <tt>PushBufferStream</tt> which is to be at a specific
     * zero-based index in the list of streams of this
     * <tt>PushBufferDataSource</tt>. The <tt>Format</tt>-related information of
     * the new instance is to be abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt>
     * in the list of streams of this <tt>PushBufferDataSource</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @return a new <tt>PushBufferStream</tt> which is to be at the specified
     * <tt>streamIndex</tt> in the list of streams of this
     * <tt>PushBufferDataSource</tt> and which has its <tt>Format</tt>-related
     * information abstracted by the specified <tt>formatControl</tt>
     * @see AbstractPushBufferCaptureDevice#createStream(int, FormatControl)
     */
    @Override
    protected DirectShowStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        DirectShowStream stream = new DirectShowStream(this, formatControl);

        if (logger.isTraceEnabled())
        {
            DSCaptureDevice device = this.device;

            if (device != null)
            {
                DSFormat supportedFormats[] = device.getSupportedFormats();

                for (DSFormat supportedFormat : supportedFormats)
                {
                    logger.trace(
                            "width= " + supportedFormat.getWidth()
                                + ", height= " + supportedFormat.getHeight()
                                + ", pixelFormat= "
                                + supportedFormat.getPixelFormat());
                }
            }
        }

        return stream;
    }

    /**
     * Opens a connection to the media source specified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while opening the connection
     * to the media source specified by the <tt>MediaLocator</tt> of this
     * <tt>DataSource</tt>
     * @see AbstractPushBufferCaptureDevice#doConnect()
     */
    @Override
    protected void doConnect()
        throws IOException
    {
        super.doConnect();

        boolean connected = false;

        try
        {
            DSCaptureDevice device = getDevice();

            device.connect();

            synchronized (getStreamSyncRoot())
            {
                for (Object stream : getStreams())
                    ((DirectShowStream) stream).setDevice(device);
            }

            connected = true;
        }
        finally
        {
            if (!connected)
            {
                /*
                 * The connect attempt has failed but it may have been
                 * successful up to the point of failure thus partially
                 * modifying the state. The disconnect procedure is prepared to
                 * deal with a partially modified state and will restore it to
                 * its pristine form.
                 */
                doDisconnect();
            }
        }
    }

    /**
     * Closes the connection to the media source specified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>.
     *
     * @see AbstractPushBufferCaptureDevice#doDisconnect()
     */
    @Override
    protected void doDisconnect()
    {
        try
        {
            synchronized (getStreamSyncRoot())
            {
                for (Object stream : getStreams())
                {
                    try
                    {
                        ((DirectShowStream) stream).setDevice(null);
                    }
                    catch (IOException ioe)
                    {
                        logger.error(
                                "Failed to disconnect "
                                    + stream.getClass().getName(),
                                ioe);
                    }
                }
            }
        }
        finally
        {
            if (device != null)
            {
                device.disconnect();
                device = null;
            }
            if (manager != null)
            {
                manager.dispose();
                manager = null;
            }

            super.doDisconnect();
        }
    }

    private DSCaptureDevice getDevice()
    {
        DSCaptureDevice device = this.device;

        if (device == null)
        {
            MediaLocator locator = getLocator();

            if (locator == null)
                throw new IllegalStateException("locator");
            if (!locator.getProtocol().equalsIgnoreCase(
                    DeviceSystem.LOCATOR_PROTOCOL_DIRECTSHOW))
                throw new IllegalStateException("locator.protocol");

            String remainder = locator.getRemainder();

            if (remainder == null)
                throw new IllegalStateException("locator.remainder");

            if (manager == null)
                manager = new DSManager();
            try
            {
                /*
                 * Find the device specified by the locator using matching by
                 * name.
                 */
                for (DSCaptureDevice d : manager.getCaptureDevices())
                {
                    if (remainder.equals(d.getName()))
                    {
                        device = d;
                        break;
                    }
                }

                if (device != null)
                    this.device = device;
            }
            finally
            {
                if (this.device == null)
                {
                    manager.dispose();
                    manager = null;
                }
            }
        }

        return device;
    }

    /**
     * Gets the <tt>Format</tt>s which are to be reported by a
     * <tt>FormatControl</tt> as supported formats for a
     * <tt>PushBufferStream</tt> at a specific zero-based index in the list of
     * streams of this <tt>PushBufferDataSource</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt>
     * for which the specified <tt>FormatControl</tt> is to report the list of
     * supported <tt>Format</tt>s
     * @return an array of <tt>Format</tt>s to be reported by a
     * <tt>FormatControl</tt> as the supported formats for the
     * <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt> in the
     * list of streams of this <tt>PushBufferDataSource</tt>
     * @see AbstractPushBufferCaptureDevice#getSupportedFormats(int)
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        DSCaptureDevice device = this.device;

        if (device == null)
            return super.getSupportedFormats(streamIndex);

        DSFormat[] deviceFmts = device.getSupportedFormats();
        List<Format> fmts = new ArrayList<Format>(deviceFmts.length);

        for (DSFormat deviceFmt : deviceFmts)
        {
            Dimension size
                = new Dimension(deviceFmt.getWidth(), deviceFmt.getHeight());
            int devicePixFmt = deviceFmt.getPixelFormat();
            int pixFmt = getFFmpegPixFmt(devicePixFmt);

            if (pixFmt != FFmpeg.PIX_FMT_NONE)
            {
                fmts.add(
                        new AVFrameFormat(
                                size,
                                Format.NOT_SPECIFIED,
                                pixFmt, devicePixFmt));
            }
        }
        return fmts.toArray(new Format[fmts.size()]);
    }

    /**
     * Attempts to set the <tt>Format</tt> to be reported by the
     * <tt>FormatControl</tt> of a <tt>PushBufferStream</tt> at a specific
     * zero-based index in the list of streams of this
     * <tt>PushBufferDataSource</tt>. The <tt>PushBufferStream</tt> does not
     * exist at the time of the attempt to set its <tt>Format</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt>
     * the <tt>Format</tt> of which is to be set
     * @param oldValue the last-known <tt>Format</tt> for the
     * <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt>
     * @param newValue the <tt>Format</tt> which is to be set
     * @return the <tt>Format</tt> to be reported by the <tt>FormatControl</tt>
     * of the <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt>
     * in the list of streams of this <tt>PushBufferStream</tt> or <tt>null</tt>
     * if the attempt to set the <tt>Format</tt> did not success and any
     * last-known <tt>Format</tt> is to be left in effect
     * @see AbstractPushBufferCaptureDevice#setFormat(int, Format, Format)
     */
    @Override
    protected Format setFormat(
            int streamIndex,
            Format oldValue, Format newValue)
    {
        // This DataSource supports setFormat.
        return
            DirectShowStream.isSupportedFormat(newValue)
                ? newValue
                : super.setFormat(streamIndex, oldValue, newValue);
    }
}
