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
package org.jitsi.impl.neomedia.codec.video.h263p;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements a H.263+ encoder.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class JNIEncoder
    extends AbstractCodec
{
    /**
     * The frame rate to be assumed by <tt>JNIEncoder</tt> instance in the
     * absence of any other frame rate indication.
     */
    private static final int DEFAULT_FRAME_RATE = 30;

    /**
     * Default output formats.
     */
    private static final Format[] DEFAULT_OUTPUT_FORMATS
        = { new VideoFormat(Constants.H263P) };

    /**
     * Key frame every 300 frames.
     */
    private static final int IFRAME_INTERVAL = 300;

    /**
     * Name of the code.
     */
    private static final String PLUGIN_NAME = "H.263+ Encoder";

    /**
     * The codec we will use.
     */
    private long avcontext = 0;

    /**
     * The encoded data is stored in avpicture.
     */
    private long avFrame = 0;

    /**
     * The supplied data length.
     */
    private int encFrameLen = 0;

    /**
     * Next interval for an automatic keyframe.
     */
    private int framesSinceLastIFrame = IFRAME_INTERVAL + 1;

    /**
     * The raw frame buffer.
     */
    private long rawFrameBuffer = 0;

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        inputFormats
            = new Format[]
            {
                new YUVFormat(
                        /* size */ null,
                        /* maxDataLength */ Format.NOT_SPECIFIED,
                        Format.byteArray,
                        /* frameRate */ Format.NOT_SPECIFIED,
                        YUVFormat.YUV_420,
                        /* strideY */ Format.NOT_SPECIFIED,
                        /* strideUV */ Format.NOT_SPECIFIED,
                        /* offsetY */ Format.NOT_SPECIFIED,
                        /* offsetU */ Format.NOT_SPECIFIED,
                        /* offsetV */ Format.NOT_SPECIFIED)
            };

        inputFormat = null;
        outputFormat = null;
    }

    /**
     * Closes this <tt>Codec</tt>.
     */
    @Override
    public synchronized void close()
    {
        if (opened)
        {
            opened = false;
            super.close();

            FFmpeg.avcodec_close(avcontext);
            FFmpeg.av_free(avcontext);
            avcontext = 0;

            FFmpeg.avcodec_free_frame(avFrame);
            avFrame = 0;
            FFmpeg.av_free(rawFrameBuffer);
            rawFrameBuffer = 0;
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param in input format
     * @return array for formats matching input format
     */
    private Format[] getMatchingOutputFormats(Format in)
    {
        VideoFormat videoIn = (VideoFormat) in;

        return
            new VideoFormat[]
            {
                new VideoFormat(
                        Constants.H263P,
                        videoIn.getSize(),
                        Format.NOT_SPECIFIED,
                        Format.byteArray,
                        videoIn.getFrameRate())
            };
    }

    /**
     * Gets the name of this <tt>Codec</tt>.
     *
     * @return codec name
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Returns the list of formats supported at the output.
     *
     * @param in input <tt>Format</tt> to determine corresponding output
     * <tt>Format/tt>s
     * @return array of formats supported at output
     */
    @Override
    public Format[] getSupportedOutputFormats(Format in)
    {
        // null input format
        if (in == null)
            return DEFAULT_OUTPUT_FORMATS;

        // mismatch input format
        if (!(in instanceof VideoFormat)
                || (null == AbstractCodec2.matches(in, inputFormats)))
            return new Format[0];

        return getMatchingOutputFormats(in);
    }

    /**
     * Opens this <tt>Codec</tt>.
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        if (inputFormat == null)
            throw new ResourceUnavailableException("No input format selected");
        if (outputFormat == null)
            throw new ResourceUnavailableException("No output format selected");

        VideoFormat outputVideoFormat = (VideoFormat) outputFormat;
        Dimension size = outputVideoFormat.getSize();
        int width = size.width;
        int height = size.height;

        long avcodec = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_H263P);

        avcontext = FFmpeg.avcodec_alloc_context3(avcodec);

        FFmpeg.avcodeccontext_set_pix_fmt(avcontext, FFmpeg.PIX_FMT_YUV420P);
        FFmpeg.avcodeccontext_set_size(avcontext, width, height);
        FFmpeg.avcodeccontext_set_qcompress(avcontext, 0.6f);

        int bitRate = NeomediaServiceUtils
                .getMediaServiceImpl()
                    .getDeviceConfiguration()
                        .getVideoBitrate() * 1000;
        int frameRate = (int) outputVideoFormat.getFrameRate();

        if (frameRate == Format.NOT_SPECIFIED)
            frameRate = DEFAULT_FRAME_RATE;

        // average bit rate
        FFmpeg.avcodeccontext_set_bit_rate(avcontext, bitRate);
        FFmpeg.avcodeccontext_set_bit_rate_tolerance(avcontext,
                bitRate / (frameRate - 1));
        //FFmpeg.avcodeccontext_set_rc_max_rate(avcontext, bitRate);
        //FFmpeg.avcodeccontext_set_sample_aspect_ratio(avcontext, 0, 0);

        // time_base should be 1 / frame rate
        FFmpeg.avcodeccontext_set_time_base(avcontext, 1, frameRate);
        //FFmpeg.avcodeccontext_set_quantizer(avcontext, 10, 51, 4);

        FFmpeg.avcodeccontext_set_mb_decision(avcontext,
            FFmpeg.FF_MB_DECISION_SIMPLE);

        //FFmpeg.avcodeccontext_set_rc_eq(avcontext, "blurCplx^(1-qComp)");

        FFmpeg.avcodeccontext_add_flags(avcontext,
            FFmpeg.CODEC_FLAG_LOOP_FILTER);
        FFmpeg.avcodeccontext_add_flags(avcontext,
                FFmpeg.CODEC_FLAG_AC_PRED);
        FFmpeg.avcodeccontext_add_flags(avcontext,
                FFmpeg.CODEC_FLAG_H263P_UMV);
        FFmpeg.avcodeccontext_add_flags(avcontext,
                FFmpeg.CODEC_FLAG_H263P_SLICE_STRUCT);

        FFmpeg.avcodeccontext_set_me_method(avcontext, 6);
        FFmpeg.avcodeccontext_set_me_subpel_quality(avcontext, 2);
        FFmpeg.avcodeccontext_set_me_range(avcontext, 18);
        FFmpeg.avcodeccontext_set_me_cmp(avcontext, FFmpeg.FF_CMP_CHROMA);
        FFmpeg.avcodeccontext_set_scenechange_threshold(avcontext, 40);

        // Constant quality mode (also known as constant ratefactor)
        //FFmpeg.avcodeccontext_set_crf(avcontext, 0);
        //FFmpeg.avcodeccontext_set_rc_buffer_size(avcontext, 0);
        FFmpeg.avcodeccontext_set_gop_size(avcontext, IFRAME_INTERVAL);
        //FFmpeg.avcodeccontext_set_i_quant_factor(avcontext, 1f / 1.4f);

        //FFmpeg.avcodeccontext_set_refs(avcontext, 2);
        //FFmpeg.avcodeccontext_set_trellis(avcontext, 2);

        if (FFmpeg.avcodec_open2(avcontext, avcodec) < 0)
        {
            throw
                new ResourceUnavailableException(
                        "Could not open codec. (size= "
                            + width + "x" + height
                            + ")");
        }

        encFrameLen = (width * height * 3) / 2;

        rawFrameBuffer = FFmpeg.av_malloc(encFrameLen);

        avFrame = FFmpeg.avcodec_alloc_frame();

        int sizeInBytes = width * height;

        FFmpeg.avframe_set_data(
                avFrame,
                rawFrameBuffer,
                sizeInBytes,
                sizeInBytes / 4);
        FFmpeg.avframe_set_linesize(avFrame, width, width / 2, width / 2);

        opened = true;

        super.open();
    }

    /**
     * Processes/encodes a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     * @return <tt>BUFFER_PROCESSED_OK</tt> if buffer has been successfully
     * processed
     */
    @Override
    public synchronized int process(Buffer inBuffer, Buffer outBuffer)
    {
        if (isEOM(inBuffer))
        {
            propagateEOM(outBuffer);
            reset();
            return BUFFER_PROCESSED_OK;
        }
        if (inBuffer.isDiscard())
        {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        Format inFormat = inBuffer.getFormat();

        if ((inFormat != inputFormat) && !inFormat.matches(inputFormat))
            setInputFormat(inFormat);

        if (inBuffer.getLength() < 3)
        {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        // copy data to avframe
        FFmpeg.memcpy(
                rawFrameBuffer,
                (byte[]) inBuffer.getData(), inBuffer.getOffset(),
                encFrameLen);

        if (framesSinceLastIFrame >= IFRAME_INTERVAL)
        {
            FFmpeg.avframe_set_key_frame(avFrame, true);
            framesSinceLastIFrame = 0;
        }
        else
        {
            framesSinceLastIFrame++;
            FFmpeg.avframe_set_key_frame(avFrame, false);
        }

        /*
         * Do not always allocate a new data array for outBuffer, try to reuse
         * the existing one if it is suitable.
         */
        Object outData = outBuffer.getData();
        byte[] out;

        if (outData instanceof byte[])
        {
            out = (byte[]) outData;
            if (out.length < encFrameLen)
                out = null;
        }
        else
            out = null;
        if (out == null)
            out = new byte[encFrameLen];

        // encode data
        int outputLength
            = FFmpeg.avcodec_encode_video(avcontext, out, out.length, avFrame);

        outBuffer.setData(out);
        outBuffer.setLength(outputLength);
        outBuffer.setOffset(0);
        outBuffer.setTimeStamp(inBuffer.getTimeStamp());

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input to this
     * <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of media data to set on this
     * <tt>Codec</tt>
     * @return the <tt>Format</tt> of media data set on this <tt>Codec</tt> or
     * <tt>null</tt> if the specified <tt>format</tt> is not supported by this
     * <tt>Codec</tt>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        // mismatch input format
        if (!(format instanceof VideoFormat)
                || (null == AbstractCodec2.matches(format, inputFormats)))
            return null;

        YUVFormat yuvFormat = (YUVFormat) format;

        if (yuvFormat.getOffsetU() > yuvFormat.getOffsetV())
            return null;

        inputFormat = AbstractCodec2.specialize(yuvFormat, Format.byteArray);

        // Return the selected inputFormat
        return inputFormat;
    }

    /**
     * Sets the <tt>Format</tt> in which this <tt>Codec</tt> is to output media
     * data.
     *
     * @param format the <tt>Format</tt> in which this <tt>Codec</tt> is to
     * output media data
     * @return the <tt>Format</tt> in which this <tt>Codec</tt> is currently
     * configured to output media data or <tt>null</tt> if <tt>format</tt> was
     * found to be incompatible with this <tt>Codec</tt>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        // mismatch output format
        if (!(format instanceof VideoFormat)
                || (null
                        == AbstractCodec2.matches(
                                format,
                                getMatchingOutputFormats(inputFormat))))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the
         * input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        outputFormat
            = new VideoFormat(
                    videoFormat.getEncoding(),
                    size,
                    /* maxDataLength */ Format.NOT_SPECIFIED,
                    Format.byteArray,
                    videoFormat.getFrameRate());

        // Return the selected outputFormat
        return outputFormat;
    }
}
