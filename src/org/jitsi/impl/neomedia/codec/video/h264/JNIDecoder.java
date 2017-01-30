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
package org.jitsi.impl.neomedia.codec.video.h264;

import java.awt.*;
import java.io.*;

import javax.media.*;
import javax.media.format.*;

import net.iharder.*;
import net.sf.fmj.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.*;

/**
 * Decodes H.264 NAL units and returns the resulting frames as FFmpeg
 * <tt>AVFrame</tt>s (i.e. in YUV format).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class JNIDecoder
    extends AbstractCodec
{
    /**
     * The default output <tt>VideoFormat</tt>.
     */
    private static final VideoFormat[] DEFAULT_OUTPUT_FORMATS
        = new VideoFormat[] { new AVFrameFormat(FFmpeg.PIX_FMT_YUV420P) };

    /**
     * The <tt>Logger</tt> used by the <tt>JNIDecoder</tt> class and its
     * instances to print debug-related information.
     */
    private static final Logger logger = Logger.getLogger(JNIDecoder.class);

    /**
     * Plugin name.
     */
    private static final String PLUGIN_NAME = "H.264 Decoder";

    /**
     *  The codec context native pointer we will use.
     */
    private long avctx;

    /**
     * The <tt>AVFrame</tt> in which the video frame decoded from the encoded
     * media data is stored.
     */
    private AVFrame avframe;

    /**
     * If decoder has got a picture.
     */
    private final boolean[] got_picture = new boolean[1];

    private boolean gotPictureAtLeastOnce;

    /**
     * The last known height of {@link #avctx} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int height;

    /**
     * The <tt>KeyFrameControl</tt> used by this <tt>JNIDecoder</tt> to
     * control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * Array of output <tt>VideoFormat</tt>s.
     */
    private final VideoFormat[] outputFormats;

    /**
     * The last known width of {@link #avctx} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int width;

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance which is to decode H.264
     * NAL units into frames in YUV format.
     */
    public JNIDecoder()
    {
        inputFormats
            = new VideoFormat[]
                    {
                        /*
                         * Explicitly state both ParameterizedVideoFormat (to
                         * receive any format parameters which may be of concern
                         * to this JNIDecoder) and VideoFormat (to make sure
                         * that nothing breaks because of equality and/or
                         * matching tests involving ParameterizedVideoFormat).
                         */
                        new ParameterizedVideoFormat(Constants.H264),
                        new VideoFormat(Constants.H264)
                    };
        outputFormats = DEFAULT_OUTPUT_FORMATS;
    }

    /**
     * Check <tt>Format</tt>.
     *
     * @param format <tt>Format</tt> to check
     * @return true if <tt>Format</tt> is H264_RTP
     */
    public boolean checkFormat(Format format)
    {
        return format.getEncoding().equals(Constants.H264_RTP);
    }

    /**
     * Close <tt>Codec</tt>.
     */
    @Override
    public synchronized void close()
    {
        if (opened)
        {
            opened = false;
            super.close();

            FFmpeg.avcodec_close(avctx);
            FFmpeg.av_free(avctx);
            avctx = 0;

            if (avframe != null)
            {
                avframe.free();
                avframe = null;
            }

            gotPictureAtLeastOnce = false;
        }
    }

    /**
     * Ensure frame rate.
     *
     * @param frameRate frame rate
     * @return frame rate
     */
    private float ensureFrameRate(float frameRate)
    {
        return frameRate;
    }

    /**
     * Get matching outputs for a specified input <tt>Format</tt>.
     *
     * @param inputFormat input <tt>Format</tt>
     * @return array of matching outputs or null if there are no matching
     * outputs.
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return
            new Format[]
            {
                new AVFrameFormat(
                        inputVideoFormat.getSize(),
                        ensureFrameRate(inputVideoFormat.getFrameRate()),
                        FFmpeg.PIX_FMT_YUV420P)
            };
    }

    /**
     * Get plugin name.
     *
     * @return "H.264 Decoder"
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Get all supported output <tt>Format</tt>s.
     *
     * @param inputFormat input <tt>Format</tt> to determine corresponding
     * output <tt>Format/tt>s
     * @return an array of supported output <tt>Format</tt>s
     */
    @Override
    public Format[] getSupportedOutputFormats(Format inputFormat)
    {
        Format[] supportedOutputFormats;

        if (inputFormat == null)
        {
            supportedOutputFormats = outputFormats;
        }
        else
        {
            // mismatch input format
            if (!(inputFormat instanceof VideoFormat)
                    || (AbstractCodec2.matches(inputFormat, inputFormats)
                            == null))
            {
                supportedOutputFormats = AbstractCodec2.EMPTY_FORMATS;
            }
            else
            {
                // match input format
                supportedOutputFormats = getMatchingOutputFormats(inputFormat);
            }
        }
        return supportedOutputFormats;
    }

    /**
     * Handles any format parameters of the input and/or output <tt>Format</tt>s
     * with which this <tt>JNIDecoder</tt> has been configured. For example,
     * takes into account the format parameter <tt>sprop-parameter-sets</tt> if
     * it is specified by the input <tt>Format</tt>.
     */
    private void handleFmtps()
    {
        try
        {

        Format f = getInputFormat();

        if (f instanceof ParameterizedVideoFormat)
        {
            ParameterizedVideoFormat pvf = (ParameterizedVideoFormat) f;
            String spropParameterSets
                = pvf.getFormatParameter(
                        VideoMediaFormatImpl.H264_SPROP_PARAMETER_SETS_FMTP);

            if (spropParameterSets != null)
            {
                ByteArrayOutputStream nals = new ByteArrayOutputStream();

                for (String s : spropParameterSets.split(","))
                {
                    if ((s != null) && (s.length() != 0))
                    {
                        byte[] nal = Base64.decode(s);

                        if ((nal != null) && (nal.length != 0))
                        {
                            nals.write(H264.NAL_PREFIX);
                            nals.write(nal);
                        }
                    }
                }
                if (nals.size() != 0)
                {
                    // Add padding because it seems to be required by FFmpeg.
                    for (int i = 0;
                            i < FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE;
                            i++)
                    {
                        nals.write(0);
                    }

                    /*
                     * In accord with RFC 6184 "RTP Payload Format for H.264
                     * Video", place the NAL units conveyed by
                     * sprop-parameter-sets in the NAL unit stream to precede
                     * any other NAL units in decoding order.
                     */
                    FFmpeg.avcodec_decode_video(
                            avctx,
                            avframe.getPtr(),
                            got_picture,
                            nals.toByteArray(), nals.size());
                }
            }
        }

        /*
         * Because the handling of format parameter is new at the time of this
         * writing and it currently handles only the format parameter
         * sprop-parameter-sets the failed handling of which will be made
         * visible later on anyway, do not let it kill this JNIDecoder.
         */
        }
        catch (Throwable t)
        {
            if (t instanceof InterruptedException)
                Thread.currentThread().interrupt();
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                logger.error("Failed to handle format parameters", t);
        }
    }

    /**
     * Inits the codec instances.
     *
     * @throws ResourceUnavailableException if codec initialization failed
     */
    @Override
    public synchronized void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        if (avframe != null)
        {
            avframe.free();
            avframe = null;
        }
        avframe = new AVFrame();

        long avcodec = FFmpeg.avcodec_find_decoder(FFmpeg.CODEC_ID_H264);

        if (avcodec == 0)
        {
            throw new ResourceUnavailableException(
                    "Could not find H.264 decoder.");
        }

        avctx = FFmpeg.avcodec_alloc_context3(avcodec);
        FFmpeg.avcodeccontext_set_workaround_bugs(avctx,
                FFmpeg.FF_BUG_AUTODETECT);

        /* allow to pass incomplete frame to decoder */
        FFmpeg.avcodeccontext_add_flags2(avctx,
                FFmpeg.CODEC_FLAG2_CHUNKS);

        if (FFmpeg.avcodec_open2(avctx, avcodec) < 0)
            throw new RuntimeException("Could not open H.264 decoder.");

        gotPictureAtLeastOnce = false;

        opened = true;
        super.open();

        /*
         * After this JNIDecoder has been opened, handle format parameters such
         * as sprop-parameter-sets which require this JNIDecoder to be in the
         * opened state.
         */
        handleFmtps();
    }

    /**
     * Decodes H.264 media data read from a specific input <tt>Buffer</tt> into
     * a specific output <tt>Buffer</tt>.
     *
     * @param in input <tt>Buffer</tt>
     * @param out output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>in</tt> has been successfully
     * processed
     */
    @Override
    public synchronized int process(Buffer in, Buffer out)
    {
        if (!checkInputBuffer(in))
            return BUFFER_PROCESSED_FAILED;
        if (isEOM(in) || !opened)
        {
            propagateEOM(out);
            return BUFFER_PROCESSED_OK;
        }
        if (in.isDiscard())
        {
            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        // Ask FFmpeg to decode.
        got_picture[0] = false;
        // TODO Take into account the offset of the input Buffer.
        FFmpeg.avcodec_decode_video(
                avctx,
                avframe.getPtr(),
                got_picture,
                (byte[]) in.getData(), in.getLength());

        if (!got_picture[0])
        {
            if ((in.getFlags() & Buffer.FLAG_RTP_MARKER) != 0)
            {
                if (keyFrameControl != null)
                    keyFrameControl.requestKeyFrame(!gotPictureAtLeastOnce);
            }

            out.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        gotPictureAtLeastOnce = true;

        // format
        int width = FFmpeg.avcodeccontext_get_width(avctx);
        int height = FFmpeg.avcodeccontext_get_height(avctx);

        if ((width > 0)
                && (height > 0)
                && ((this.width != width) || (this.height != height)))
        {
            this.width = width;
            this.height = height;

            // Output in same size and frame rate as input.
            Dimension outSize = new Dimension(this.width, this.height);
            VideoFormat inFormat = (VideoFormat) in.getFormat();
            float outFrameRate = ensureFrameRate(inFormat.getFrameRate());

            outputFormat
                = new AVFrameFormat(
                        outSize,
                        outFrameRate,
                        FFmpeg.PIX_FMT_YUV420P);
        }
        out.setFormat(outputFormat);

        // data
        if (out.getData() != avframe)
            out.setData(avframe);

        // timeStamp
        long pts = FFmpeg.AV_NOPTS_VALUE; // TODO avframe_get_pts(avframe);

        if (pts == FFmpeg.AV_NOPTS_VALUE)
        {
            out.setTimeStamp(in.getTimeStamp());
        }
        else
        {
            out.setTimeStamp(pts);

            int outFlags = out.getFlags();

            outFlags |= Buffer.FLAG_RELATIVE_TIME;
            outFlags &= ~(Buffer.FLAG_RTP_TIME | Buffer.FLAG_SYSTEM_TIME);
            out.setFlags(outFlags);
        }

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be input for processing in
     * this <tt>Codec</tt>.
     *
     * @param format the <tt>Format</tt> of the media data to be input for
     * processing in this <tt>Codec</tt>
     * @return the <tt>Format</tt> of the media data to be input for processing
     * in this <tt>Codec</tt> if <tt>format</tt> is compatible with this
     * <tt>Codec</tt>; otherwise, <tt>null</tt>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format setFormat = super.setInputFormat(format);

        if (setFormat != null)
            reset();
        return setFormat;
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this
     * <tt>DePacketizer</tt> as a means of control over its key frame-related
     * logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this
     * <tt>DePacketizer</tt> as a means of control over its key frame-related
     * logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        this.keyFrameControl = keyFrameControl;
    }
}
