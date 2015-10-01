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
package org.jitsi.impl.neomedia.jmfext.media.protocol.quicktime;

import java.awt.*;
import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.quicktime.*;

/**
 * Implements a <tt>PushBufferStream</tt> using QuickTime/QTKit.
 *
 * @author Lyubomir Marinov
 */
public class QuickTimeStream
    extends AbstractPushBufferStream<DataSource>
{

    /**
     * The indicator which determines whether {@link #captureOutput}
     * automatically drops late frames. If <tt>false</tt>, we have to drop them
     * ourselves because QuickTime/QTKit will buffer them all and the video will
     * be late.
     */
    private final boolean automaticallyDropsLateVideoFrames;

    /**
     * The pool of <tt>ByteBuffer</tt>s this instances is using to transfer the
     * media data captured by {@link #captureOutput} out of this instance
     * through the <tt>Buffer</tt>s specified in its {@link #read(Buffer)}.
     */
    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    /**
     * The <tt>QTCaptureOutput</tt> represented by this <tt>SourceStream</tt>.
     */
    final QTCaptureDecompressedVideoOutput captureOutput
        = new QTCaptureDecompressedVideoOutput();

    /**
     * The <tt>VideoFormat</tt> which has been successfully set on
     * {@link #captureOutput}.
     */
    private VideoFormat captureOutputFormat;

    /**
     * The captured media data to be returned in {@link #read(Buffer)}.
     */
    private ByteBuffer data;

    /**
     * The <tt>Format</tt> of {@link #data} if known. If possible, determined by
     * the <tt>CVPixelBuffer</tt> video frame from which <tt>data</tt> is
     * acquired.
     */
    private Format dataFormat;

    /**
     * The <tt>Object</tt> which synchronizes the access to the
     * {@link #data}-related fields of this instance.
     */
    private final Object dataSyncRoot = new Object();

    /**
     * The time stamp in nanoseconds of {@link #data}.
     */
    private long dataTimeStamp;

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt>.
     */
    private Format format;

    /**
     * The captured media data to become the value of {@link #data} as soon as
     * the latter becomes is consumed. Thus prepares this
     * <tt>QuickTimeStream</tt> to provide the latest available frame and not
     * wait for QuickTime/QTKit to capture a new one.
     */
    private ByteBuffer nextData;

    /**
     * The <tt>Format</tt> of {@link #nextData} if known.
     */
    private Format nextDataFormat;

    /**
     * The time stamp in nanoseconds of {@link #nextData}.
     */
    private long nextDataTimeStamp;

    /**
     * The <tt>Thread</tt> which is to call
     * {@link BufferTransferHandler#transferData(PushBufferStream)} for this
     * <tt>QuickTimeStream</tt> so that the call is not made in QuickTime/QTKit
     * and we can drop late frames when
     * {@link #automaticallyDropsLateVideoFrames} is <tt>false</tt>.
     */
    private Thread transferDataThread;

    /**
     * Initializes a new <tt>QuickTimeStream</tt> instance which is to have its
     * <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    QuickTimeStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);

        if (formatControl != null)
        {
            Format format = formatControl.getFormat();

            if (format != null)
                setCaptureOutputFormat(format);
        }

        automaticallyDropsLateVideoFrames
            = captureOutput.setAutomaticallyDropsLateVideoFrames(true);
        captureOutput.setDelegate(
                new QTCaptureDecompressedVideoOutput.Delegate()
                {

                    /**
                     * Notifies this <tt>Delegate</tt> that the
                     * <tt>QTCaptureOutput</tt> to which it is set has output a
                     * specific <tt>CVImageBuffer</tt> representing a video
                     * frame with a specific <tt>QTSampleBuffer</tt>.
                     *
                     * @param videoFrame the <tt>CVImageBuffer</tt> which
                     * represents the output video frame
                     * @param sampleBuffer the <tt>QTSampleBuffer</tt> which
                     * represents additional details about the output video
                     * samples
                     */
                    @Override
                    public void outputVideoFrameWithSampleBuffer(
                            CVImageBuffer videoFrame,
                            QTSampleBuffer sampleBuffer)
                    {
                        captureOutputDidOutputVideoFrameWithSampleBuffer(
                                captureOutput,
                                videoFrame,
                                sampleBuffer);
                    }
                });

        FrameRateControl frameRateControl
            = (FrameRateControl)
                dataSource.getControl(FrameRateControl.class.getName());

        if (frameRateControl != null)
        {
            float frameRate = frameRateControl.getFrameRate();

            if (frameRate > 0)
                setFrameRate(frameRate);
        }
    }

    /**
     * Notifies this instance that its <tt>QTCaptureOutput</tt> has output a
     * specific <tt>CVImageBuffer</tt> representing a video frame with a
     * specific <tt>QTSampleBuffer</tt>.
     *
     * @param captureOutput the <tt>QTCaptureOutput</tt> which has output a
     * video frame
     * @param videoFrame the <tt>CVImageBuffer</tt> which represents the output
     * video frame
     * @param sampleBuffer the <tt>QTSampleBuffer</tt> which represents
     * additional details about the output video samples
     */
    private void captureOutputDidOutputVideoFrameWithSampleBuffer(
            QTCaptureOutput captureOutput,
            CVImageBuffer videoFrame,
            QTSampleBuffer sampleBuffer)
    {
        CVPixelBuffer pixelBuffer = (CVPixelBuffer) videoFrame;
        boolean transferData;
        Format videoFrameFormat = getVideoFrameFormat(pixelBuffer);

        synchronized (dataSyncRoot)
        {
            if (!automaticallyDropsLateVideoFrames && (data != null))
            {
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }
                nextData = byteBufferPool.getBuffer(pixelBuffer.getByteCount());
                if (nextData != null)
                {
                    nextData.setLength(
                            pixelBuffer.getBytes(
                                    nextData.getPtr(),
                                    nextData.getCapacity()));
                    nextDataTimeStamp = System.nanoTime();
                    if (nextDataFormat == null)
                        nextDataFormat = videoFrameFormat;
                }
                return;
            }

            if (data != null)
            {
                data.free();
                data = null;
            }
            data = byteBufferPool.getBuffer(pixelBuffer.getByteCount());
            if (data != null)
            {
                data.setLength(
                        pixelBuffer.getBytes(
                                data.getPtr(),
                                data.getCapacity()));
                dataTimeStamp = System.nanoTime();
                if (dataFormat == null)
                    dataFormat = videoFrameFormat;
            }

            if (nextData != null)
            {
                nextData.free();
                nextData = null;
            }

            if (automaticallyDropsLateVideoFrames)
                transferData = (data != null);
            else
            {
                transferData = false;
                dataSyncRoot.notifyAll();
            }
        }

        if (transferData)
        {
            BufferTransferHandler transferHandler = this.transferHandler;

            if (transferHandler != null)
                transferHandler.transferData(this);
        }
    }

    /**
     * Releases the resources used by this instance throughout its existence and
     * makes it available for garbage collection. This instance is considered
     * unusable after closing.
     *
     * @see AbstractPushBufferStream#close()
     */
    @Override
    public void close()
    {
        super.close();

        captureOutput.setDelegate(null);
        byteBufferPool.drain();
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PushBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PushBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     */
    @Override
    protected Format doGetFormat()
    {
        Format format;

        if (this.format == null)
        {
            format = getCaptureOutputFormat();
            if (format == null)
                format = super.doGetFormat();
            else
            {
                VideoFormat videoFormat = (VideoFormat) format;

                if (videoFormat.getSize() != null)
                    this.format = format;
                else
                {
                    Dimension defaultSize
                        = NeomediaServiceUtils
                            .getMediaServiceImpl()
                                .getDeviceConfiguration()
                                    .getVideoSize();

                    format
                        = videoFormat.intersects(
                                new VideoFormat(
                                        /* encoding */ null,
                                        new Dimension(
                                                defaultSize.width,
                                                defaultSize.height),
                                        /* maxDataLength */ Format.NOT_SPECIFIED,
                                        /* dataType */ null,
                                        /* frameRate */ Format.NOT_SPECIFIED));
                }
            }
        }
        else
            format = this.format;
        return format;
    }

    /**
     * Gets the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt> as indicated by {@link #captureOutput}.
     *
     * @return the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt> as indicated by {@link #captureOutput}
     */
    private Format getCaptureOutputFormat()
    {
        NSDictionary pixelBufferAttributes
            = captureOutput.pixelBufferAttributes();

        if (pixelBufferAttributes != null)
        {
            int pixelFormatType
                = pixelBufferAttributes
                    .intForKey(
                        CVPixelBufferAttributeKey
                            .kCVPixelBufferPixelFormatTypeKey);
            int width
                = pixelBufferAttributes.intForKey(
                        CVPixelBufferAttributeKey.kCVPixelBufferWidthKey);
            int height
                = pixelBufferAttributes.intForKey(
                        CVPixelBufferAttributeKey.kCVPixelBufferHeightKey);

            switch (pixelFormatType)
            {
            case CVPixelFormatType.kCVPixelFormatType_32ARGB:
                if (captureOutputFormat instanceof AVFrameFormat)
                {
                    return
                        new AVFrameFormat(
                                ((width == 0) && (height == 0)
                                    ? null
                                    : new Dimension(width, height)),
                                /* frameRate */ Format.NOT_SPECIFIED,
                                FFmpeg.PIX_FMT_ARGB,
                                CVPixelFormatType.kCVPixelFormatType_32ARGB);
                }
                else
                {
                    return
                        new RGBFormat(
                                ((width == 0) && (height == 0)
                                    ? null
                                    : new Dimension(width, height)),
                                /* maxDataLength */ Format.NOT_SPECIFIED,
                                Format.byteArray,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                32,
                                2, 3, 4);
                }
            case CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar:
                if ((width == 0) && (height == 0))
                {
                    if (captureOutputFormat instanceof AVFrameFormat)
                    {
                        return
                            new AVFrameFormat(
                                    FFmpeg.PIX_FMT_YUV420P,
                                    CVPixelFormatType
                                        .kCVPixelFormatType_420YpCbCr8Planar);
                    }
                    else
                        return new YUVFormat(YUVFormat.YUV_420);
                }
                else if (captureOutputFormat instanceof AVFrameFormat)
                {
                    return
                        new AVFrameFormat(
                                new Dimension(width, height),
                                /* frameRate */ Format.NOT_SPECIFIED,
                                FFmpeg.PIX_FMT_YUV420P,
                                CVPixelFormatType
                                    .kCVPixelFormatType_420YpCbCr8Planar);
                }
                else
                {
                    int strideY = width;
                    int strideUV = strideY / 2;
                    int offsetY = 0;
                    int offsetU = strideY * height;
                    int offsetV = offsetU + strideUV * height / 2;

                    return
                        new YUVFormat(
                                new Dimension(width, height),
                                /* maxDataLength */ Format.NOT_SPECIFIED,
                                Format.byteArray,
                                /* frameRate */ Format.NOT_SPECIFIED,
                                YUVFormat.YUV_420,
                                strideY, strideUV,
                                offsetY, offsetU, offsetV);
                }
            }
        }
        return null;
    }

    /**
     * Gets the output frame rate of the
     * <tt>QTCaptureDecompressedVideoOutput</tt> represented by this
     * <tt>QuickTimeStream</tt>.
     *
     * @return the output frame rate of the
     * <tt>QTCaptureDecompressedVideoOutput</tt> represented by this
     * <tt>QuickTimeStream</tt>
     */
    public float getFrameRate()
    {
        return (float) (1.0d / captureOutput.minimumVideoFrameInterval());
    }

    /**
     * Gets the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt> as indicated by a specific
     * <tt>CVPixelBuffer</tt>.
     *
     * @param videoFrame the <tt>CVPixelBuffer</tt> which provides details about
     * the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt>
     * @return the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt> as indicated by the specified
     * <tt>CVPixelBuffer</tt>
     */
    private Format getVideoFrameFormat(CVPixelBuffer videoFrame)
    {
        Format format = getFormat();
        Dimension size = ((VideoFormat) format).getSize();

        if ((size == null) || ((size.width == 0) && (size.height == 0)))
        {
            format
                = format.intersects(
                        new VideoFormat(
                                /* encoding */ null,
                                new Dimension(
                                        videoFrame.getWidth(),
                                        videoFrame.getHeight()),
                                /* maxDataLength */ Format.NOT_SPECIFIED,
                                /* dataType */ null,
                                /* frameRate */ Format.NOT_SPECIFIED));
        }
        return format;
    }

    /**
     * Reads media data from this <tt>PushBufferStream</tt> into a specific
     * <tt>Buffer</tt> without blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PushBufferStream</tt>
     * @throws IOException if anything goes wrong while reading media data from
     * this <tt>PushBufferStream</tt> into the specified <tt>buffer</tt>
     */
    public void read(Buffer buffer)
        throws IOException
    {
        synchronized (dataSyncRoot)
        {
            if (data == null)
            {
                buffer.setLength(0);
                return;
            }

            if (dataFormat != null)
                buffer.setFormat(dataFormat);

            Format format = buffer.getFormat();

            if (format == null)
            {
                format = getFormat();
                if (format != null)
                    buffer.setFormat(format);
            }
            if (format instanceof AVFrameFormat)
            {
                if (AVFrame.read(buffer, format, data) < 0)
                    data.free();
                /*
                 * XXX For the sake of safety, make sure that this instance does
                 * not reference the data instance as soon as it is set on the
                 * AVFrame.
                 */
                data = null;
            }
            else
            {
                Object o = buffer.getData();
                byte[] bytes;
                int length = data.getLength();

                if (o instanceof byte[])
                {
                    bytes = (byte[]) o;
                    if (bytes.length < length)
                        bytes = null;
                }
                else
                    bytes = null;
                if (bytes == null)
                {
                    bytes = new byte[length];
                    buffer.setData(bytes);
                }

                CVPixelBuffer.memcpy(bytes, 0, length, data.getPtr());
                data.free();
                data = null;

                buffer.setLength(length);
                buffer.setOffset(0);
            }

            buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_SYSTEM_TIME);
            buffer.setTimeStamp(dataTimeStamp);

            if (!automaticallyDropsLateVideoFrames)
                dataSyncRoot.notifyAll();
        }
    }

    /**
     * Calls {@link BufferTransferHandler#transferData(PushBufferStream)} from
     * inside {@link #transferDataThread} so that the call is not made in
     * QuickTime/QTKit and we can drop late frames in the meantime.
     */
    private void runInTransferDataThread()
    {
        boolean transferData = false;

        while (Thread.currentThread().equals(transferDataThread))
        {
            if (transferData)
            {
                BufferTransferHandler transferHandler = this.transferHandler;

                if (transferHandler != null)
                    transferHandler.transferData(this);

                synchronized (dataSyncRoot)
                {
                    if (data != null)
                        data.free();
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    if (dataFormat == null)
                        dataFormat = nextDataFormat;
                    nextData = null;
                }
            }

            synchronized (dataSyncRoot)
            {
                if (data == null)
                {
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    if (dataFormat == null)
                        dataFormat = nextDataFormat;
                    nextData = null;
                }
                if (data == null)
                {
                    boolean interrupted = false;

                    try
                    {
                        dataSyncRoot.wait();
                    }
                    catch (InterruptedException iex)
                    {
                        interrupted = true;
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();

                    transferData = (data != null);
                }
                else
                    transferData = true;
            }
        }
    }

    /**
     * Sets the <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt> to {@link #captureOutput}.
     *
     * @param format the <tt>Format</tt> of the media data made available by
     * this <tt>PushBufferStream</tt> to be set to {@link #captureOutput}
     */
    private void setCaptureOutputFormat(Format format)
    {
        VideoFormat videoFormat = (VideoFormat) format;
        Dimension size = videoFormat.getSize();
        int width;
        int height;

        /*
         * FIXME Mac OS X Leopard does not seem to report the size of the
         * QTCaptureDevice in its formatDescriptions early in its creation.
         * The workaround presented here is to just force a specific size.
         */
        if (size == null)
        {
            Dimension defaultSize
                = NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration()
                            .getVideoSize();

            width = defaultSize.width;
            height = defaultSize.height;
        }
        else
        {
            width = size.width;
            height = size.height;
        }

        NSMutableDictionary pixelBufferAttributes = null;

        if ((width > 0) && (height > 0))
        {
            if (pixelBufferAttributes == null)
                pixelBufferAttributes = new NSMutableDictionary();
            pixelBufferAttributes.setIntForKey(
                    width,
                    CVPixelBufferAttributeKey.kCVPixelBufferWidthKey);
            pixelBufferAttributes.setIntForKey(
                    height,
                    CVPixelBufferAttributeKey.kCVPixelBufferHeightKey);
        }

        String encoding;

        if (format instanceof AVFrameFormat)
        {
            switch (((AVFrameFormat) format).getPixFmt())
            {
            case FFmpeg.PIX_FMT_ARGB:
                encoding = VideoFormat.RGB;
                break;
            case FFmpeg.PIX_FMT_YUV420P:
                encoding = VideoFormat.YUV;
                break;
            default:
                encoding = null;
                break;
            }
        }
        else if (format.isSameEncoding(VideoFormat.RGB))
            encoding = VideoFormat.RGB;
        else if (format.isSameEncoding(VideoFormat.YUV))
            encoding = VideoFormat.YUV;
        else
            encoding = null;

        if (VideoFormat.RGB.equalsIgnoreCase(encoding))
        {
            if (pixelBufferAttributes == null)
                pixelBufferAttributes = new NSMutableDictionary();
            pixelBufferAttributes.setIntForKey(
                    CVPixelFormatType.kCVPixelFormatType_32ARGB,
                    CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey);
        }
        else if (VideoFormat.YUV.equalsIgnoreCase(encoding))
        {
            if (pixelBufferAttributes == null)
                pixelBufferAttributes = new NSMutableDictionary();
            pixelBufferAttributes.setIntForKey(
                    CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar,
                    CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey);
        }
        else
            throw new IllegalArgumentException("format");

        if (pixelBufferAttributes != null)
        {
            captureOutput.setPixelBufferAttributes(pixelBufferAttributes);
            captureOutputFormat = videoFormat;
        }
    }

    /**
     * Sets the output frame rate of the
     * <tt>QTCaptureDecompressedVideoOutput</tt> represented by this
     * <tt>QuickTimeStream</tt>.
     *
     * @param frameRate the output frame rate to be set on the
     * <tt>QTCaptureDecompressedVideoOutput</tt> represented by this
     * <tt>QuickTimeStream</tt>
     * @return the output frame rate of the
     * <tt>QTCaptureDecompressedVideoOutput</tt> represented by this
     * <tt>QuickTimeStream</tt>
     */
    public float setFrameRate(float frameRate)
    {
        captureOutput.setMinimumVideoFrameInterval(1.0d / frameRate);
        return getFrameRate();
    }

    /**
     * Starts the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void start()
        throws IOException
    {
        super.start();

        if (!automaticallyDropsLateVideoFrames)
        {
            transferDataThread
                = new Thread(getClass().getSimpleName())
                {
                    @Override
                    public void run()
                    {
                        runInTransferDataThread();
                    }
                };
            transferDataThread.start();
        }
    }

    /**
     * Stops the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void stop()
        throws IOException
    {
        try
        {
            transferDataThread = null;

            synchronized (dataSyncRoot)
            {
                if (data != null)
                {
                    data.free();
                    data = null;
                }
                dataFormat = null;
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }
                nextDataFormat = null;

                if (!automaticallyDropsLateVideoFrames)
                    dataSyncRoot.notifyAll();
            }
        }
        finally
        {
            super.stop();

            byteBufferPool.drain();
        }
    }
}
