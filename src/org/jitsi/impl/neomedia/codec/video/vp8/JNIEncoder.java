/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.vp8;


import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.*;

import javax.media.*;
import javax.media.format.*;
import java.awt.*;

/**
 * Implements a VP8 encoder.
 *
 * Heavily based on the h264 encoder from
 * org.jitsi.impl.neomedia.codec.video.h264.JNIEncoder
 *
 * @author Boris Grozev
 */
public class JNIEncoder
    extends AbstractCodecExt
{
    /**
     * The <tt>Logger</tt> used by the <tt>JNIEncoder</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(JNIEncoder.class);

    /**
     * Key frame every 50 frames.
     */
    private static final int IFRAME_INTERVAL = 50;

    /**
     * Next interval for an automatic keyframe.
     */
    private int framesSinceLastIFrame = IFRAME_INTERVAL + 1;

    /**
     * Pointer to the ffmpeg codec context to be used.
     */
    private long avcontext = 0;

    /**
     * FFmpeg avframe
     */
    private long avframe = 0;

    /**
     * Raw frame buffer (pointer to ffmpeg memory)
     */
    private long rawFrameBuffer = 0;

    /**
     * Length of the raw frame buffer. Once the dimensions are known, this is
     * set to 3/2 * (height*width), which is the size needed for a YUV420 frame.
     */
    private int rawFrameLen = 0;

    /**
     * Buffer used to store the encoded frame.
     */
    private byte[] encFrameBuffer = null;

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
            = new VideoFormat[] { new VideoFormat(Constants.VP8) };

    /**
     * The frame rate to be assumed by <tt>JNIEncoder</tt> instances in the
     * absence of any other frame rate indication.
     */
    static final int DEFAULT_FRAME_RATE = 30;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        super("VP8 Encoder",
                VideoFormat.class,
                SUPPORTED_OUTPUT_FORMATS);
        inputFormats
                = new VideoFormat[]
                {
                        new YUVFormat(
                                null,
                                Format.NOT_SPECIFIED,
                                Format.byteArray,
                                DEFAULT_FRAME_RATE,
                                YUVFormat.YUV_420,
                                Format.NOT_SPECIFIED, Format.NOT_SPECIFIED,
                                0, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED)
                };
        inputFormat = null;
        outputFormat = null;
    }

    /**
     * {@inheritDoc}
     */
    protected void doClose()
    {
        if(logger.isDebugEnabled())
            logger.debug("Closing encoder");
        if(avcontext != 0)
        {
            FFmpeg.avcodec_close(avcontext);
            FFmpeg.av_free(avcontext);
            avcontext = 0;
        }

        if(avframe != 0)
        {
            FFmpeg.avcodec_free_frame(avframe);
            avframe = 0;
        }

        if(rawFrameBuffer != 0)
        {
            FFmpeg.av_free(rawFrameBuffer);
            rawFrameBuffer = 0;
        }

        encFrameBuffer = null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException
     */
    protected void doOpen() throws ResourceUnavailableException
    {
        if (inputFormat == null)
            throw new ResourceUnavailableException("No input format selected");
        if (outputFormat == null)
            throw new ResourceUnavailableException("No output format selected");

        VideoFormat outputVideoFormat = (VideoFormat) outputFormat;
        Dimension size = outputVideoFormat.getSize();
        int width = size.width;
        int height = size.height;

        long avcodec = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_VP8);
        avcontext = FFmpeg.avcodec_alloc_context3(avcodec);

        FFmpeg.avcodeccontext_set_pix_fmt(avcontext, FFmpeg.PIX_FMT_YUV420P);
        FFmpeg.avcodeccontext_set_size(avcontext, width, height);
        FFmpeg.avcodeccontext_set_qcompress(avcontext, 0.6f);

        int frameRate = (int) outputVideoFormat.getFrameRate();
        if (frameRate == Format.NOT_SPECIFIED)
            frameRate = DEFAULT_FRAME_RATE;

        // average bit rate
        int bitRate = 256000;
        FFmpeg.avcodeccontext_set_bit_rate(avcontext, bitRate);
        FFmpeg.avcodeccontext_set_bit_rate_tolerance(avcontext,
                bitRate / frameRate);
        // time_base should be 1 / frame rate
        FFmpeg.avcodeccontext_set_time_base(avcontext, 1, frameRate);

        if (FFmpeg.avcodec_open2(avcontext, avcodec) < 0)
        {
            throw
                    new ResourceUnavailableException(
                            "Could not open codec. (size= "
                                    + width + "x" + height
                                    + ")");
        }

        //YUV420 encodes 4 pixels in 6 bytes
        rawFrameLen = (width * height * 3) / 2;
        encFrameBuffer = new byte[rawFrameLen];
        rawFrameBuffer = FFmpeg.av_malloc(rawFrameLen);
        avframe = FFmpeg.avcodec_alloc_frame();

        int sizeInBytes = width * height;
        FFmpeg.avframe_set_data(
                avframe,
                rawFrameBuffer,
                sizeInBytes,
                sizeInBytes / 4);
        FFmpeg.avframe_set_linesize(avframe, width, width / 2, width / 2);

        if(logger.isDebugEnabled())
            logger.debug("VP8 encoder opened succesfully");
    }

    /**
     * {@inheritDoc}
     *
     * Encodes the frame in <tt>inputBuffer</tt> (in <tt>YUVFormat</tt>) into
     * a VP8 frame (in <tt>outputBuffer</tt>)
     *
     * @param inputBuffer input <tt>Buffer</tt>
     * @param outputBuffer output <tt>Buffer</tt>
     *
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>inBuffer</tt> has been
     * successfully processed
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        Format inFormat = inputBuffer.getFormat();

        if ((inFormat != inputFormat) && !inFormat.matches(inputFormat))
            setInputFormat(inFormat);

        if (inputBuffer.getLength() < 10)
        {
            outputBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        // copy data to avframe
        FFmpeg.memcpy(
                rawFrameBuffer,
                (byte[]) inputBuffer.getData(), inputBuffer.getOffset(),
                rawFrameLen);

        if (framesSinceLastIFrame >= IFRAME_INTERVAL)
        {
            FFmpeg.avframe_set_key_frame(avframe, true);
            framesSinceLastIFrame = 0;
            if(logger.isTraceEnabled())
                logger.trace("Encoding a key frame");
        }
        else
        {
            framesSinceLastIFrame++;
            FFmpeg.avframe_set_key_frame(avframe, false);
        }

        // encode data
        int encLen
                = FFmpeg.avcodec_encode_video(
                    avcontext,
                    encFrameBuffer, encFrameBuffer.length,
                    avframe);

        // set up the output buffer
        byte[] out = validateByteArraySize(outputBuffer, encLen);
        System.arraycopy(encFrameBuffer, 0, out, 0, encLen);
        outputBuffer.setLength(encLen);
        outputBuffer.setOffset(0);
        outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Sets the input format.
     *
     * @param in format to set
     * @return format
     */
    @Override
    public Format setInputFormat(Format in)
    {
        if(!(in instanceof VideoFormat) || (matches(in, inputFormats) == null))
            return null;

        YUVFormat yuv = (YUVFormat) in;

        if (yuv.getOffsetU() > yuv.getOffsetV())
            return null;
        Dimension size = yuv.getSize();

        if (size == null)
            size = new Dimension(Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT);

        int strideY = size.width;
        int strideUV = strideY / 2;
        int offsetU = strideY * size.height;
        int offsetV = offsetU + strideUV * size.height / 2;

        int yuvMaxDataLength = (strideY + strideUV) * size.height;

        inputFormat
                = new YUVFormat(
                size,
                yuvMaxDataLength + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE,
                Format.byteArray,
                yuv.getFrameRate(),
                YUVFormat.YUV_420,
                strideY, strideUV,
                0, offsetU, offsetV);

        // Return the selected inputFormat
        return inputFormat;
    }

    /**
     * Sets the <tt>Format</tt> in which this <tt>Codec</tt> is to output media
     * data.
     *
     * @param out the <tt>Format</tt> in which this <tt>Codec</tt> is to
     * output media data
     * @return the <tt>Format</tt> in which this <tt>Codec</tt> is currently
     * configured to output media data or <tt>null</tt> if <tt>format</tt> was
     * found to be incompatible with this <tt>Codec</tt>
     */
    @Override
    public Format setOutputFormat(Format out)
    {
        if(!(out instanceof VideoFormat) ||
                (matches(out, getMatchingOutputFormats(inputFormat)) == null))
            return null;

        VideoFormat videoOut = (VideoFormat) out;
        Dimension outSize = videoOut.getSize();

        if (outSize == null)
        {
            Dimension inSize = ((VideoFormat) inputFormat).getSize();

            outSize
                    = (inSize == null)
                    ? new Dimension(
                    Constants.VIDEO_WIDTH,
                    Constants.VIDEO_HEIGHT)
                    : inSize;
        }

        outputFormat = new VideoFormat(
                videoOut.getEncoding(),
                outSize,
                Format.NOT_SPECIFIED,
                Format.byteArray,
                videoOut.getFrameRate());

        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param in input format
     * @return array of formats matching input format
     */
    protected Format[] getMatchingOutputFormats(Format in)
    {
        VideoFormat videoIn = (VideoFormat) in;

        return
                new VideoFormat[]
                        {
                                new VideoFormat(
                                        Constants.VP8,
                                        videoIn.getSize(),
                                        Format.NOT_SPECIFIED,
                                        Format.byteArray,
                                        videoIn.getFrameRate())
                        };
    }
}
