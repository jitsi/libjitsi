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
package org.jitsi.impl.neomedia.codec.video.vp8;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.logging.*;

/**
 * Implements a VP8 decoder.
 *
 * @author Boris Grozev
 */
public class VPXDecoder
    extends AbstractCodec2
{
    /**
     * The decoder interface to use
     */
    private static final int INTERFACE = VPX.INTEFACE_VP8_DEC;

    /**
     * The <tt>Logger</tt> used by the <tt>VPXDecoder</tt> class
     * for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(VPXDecoder.class);

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
        = new VideoFormat[] { new AVFrameFormat(FFmpeg.PIX_FMT_YUV420P) };

    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing
     * the decoder configuration
     */
    private long cfg = 0;

    /**
     * Pointer to the libvpx codec context to be used
     */
    private long context = 0;

    /**
     * The last known width of the video output by this
     * <tt>VPXDecoder</tt>. Used to detect changes in the output size.
     */
    private int height;

    /**
     * Pointer to a native vpx_image structure, containing a decoded frame.
     * When doProcess() is called, this is either 0 or it has the address of
     * the next unprocessed image from the decoder
     */
    private long img = 0;

    /**
     * Iterator for the frames in the decoder context. Can be re-initialized by
     * setting its only element to 0.
     */
    private long[] iter = new long[1];

    /**
     * Whether there are unprocessed frames left from a previous call to
     * VP8.codec_decode()
     */
    private boolean leftoverFrames = false;

    /**
     * The last known height of the video output by this
     * <tt>VPXDecoder</tt>. Used to detect changes in the output size.
     */
    private int width;

    /**
     * Initializes a new <tt>VPXDecoder</tt> instance.
     */
    public VPXDecoder()
    {
        super("VP8 VPX Decoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);

        inputFormat = null;
        outputFormat = null;
        inputFormats = new VideoFormat[] { new VideoFormat(Constants.VP8) };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        if(logger.isDebugEnabled())
            logger.debug("Closing decoder");

        if(context != 0)
        {
            VPX.codec_destroy(context);
            VPX.free(context);
        }
        if(cfg != 0)
            VPX.free(cfg);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException if initialization failed
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException
    {
        context = VPX.codec_ctx_malloc();
        //cfg = VPX.codec_dec_cfg_malloc();
        long flags = 0; //VPX.CODEC_USE_XMA;

        int ret = VPX.codec_dec_init(context, INTERFACE, 0, flags);
        if(ret != VPX.CODEC_OK)
            throw new RuntimeException("Failed to initialize decoder, libvpx"
                    + " error:\n"
                    + VPX.codec_err_to_string(ret));

        if(logger.isDebugEnabled())
            logger.debug("VP8 decoder opened succesfully");
    }

    /**
     * {@inheritDoc}
     *
     * Decodes a VP8 frame contained in <tt>inputBuffer</tt> into
     * <tt>outputBuffer</tt> (in <tt>AVFrameFormat</tt>)
     * @param inputBuffer input <tt>Buffer</tt>
     * @param outputBuffer output <tt>Buffer</tt>
     * @return <tt>BUFFER_PROCESSED_OK</tt> if <tt>inBuffer</tt> has been
     * successfully processed
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        if(leftoverFrames)
        {
            /*
             * There are more decoded frames available in the context. Fill
             * outputBuffer with the next frame.
             */
            updateOutputFormat(
                    VPX.img_get_d_w(img),
                    VPX.img_get_d_h(img),
                    ((VideoFormat) inputBuffer.getFormat()).getFrameRate());
            outputBuffer.setFormat(outputFormat);


            AVFrame avframe = makeAVFrame(img);
            outputBuffer.setData(avframe);

            //YUV420p format , 12 bits per pixel
            outputBuffer.setLength(width * height * 3 / 2);
            outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
        }
        else
        {
            /*
             * All frames from the decoder context have been processed. Decode
             * the next VP8 frame, and fill outputBuffer with the first decoded
             * frame.
             */

            byte[] buf = (byte[])inputBuffer.getData();
            int buf_offset = inputBuffer.getOffset();
            int buf_size = inputBuffer.getLength();

            int ret = VPX.codec_decode(context,
                    buf,
                    buf_offset,
                    buf_size,
                    0, 0);
            if(ret != VPX.CODEC_OK)
            {
                if(logger.isDebugEnabled())
                    logger.debug("Discarding a frame, codec_decode() error: "
                            + VPX.codec_err_to_string(ret));
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            leftoverFrames = false;
            iter[0] = 0;  //decode has just been called, reset iterator
            img = VPX.codec_get_frame(context, iter);

            if(img == 0)
            {
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            updateOutputFormat(
                    VPX.img_get_d_w(img),
                    VPX.img_get_d_h(img),
                    ((VideoFormat) inputBuffer.getFormat()).getFrameRate());
            outputBuffer.setFormat(outputFormat);


            AVFrame avframe = makeAVFrame(img);
            outputBuffer.setData(avframe);

            //YUV420p format , 12 bits per pixel
            outputBuffer.setLength(width * height * 3 / 2);
            outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
        }


        /*
         * outputBuffer is all setup now. Check the decoder context for more
         * decoded frames.
         */
        img = VPX.codec_get_frame(context, iter);
        if(img == 0) //no more frames
        {
            leftoverFrames = false;
            return BUFFER_PROCESSED_OK;
        }
        else
        {
            leftoverFrames = true;
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }

    /**
     * Get matching outputs for a specified input <tt>Format</tt>.
     *
     * @param inputFormat input <tt>Format</tt>
     * @return array of matching outputs or null if there are no matching
     * outputs.
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return
            new Format[]
                    {
                        new AVFrameFormat(
                                inputVideoFormat.getSize(),
                                inputVideoFormat.getFrameRate(),
                                FFmpeg.PIX_FMT_YUV420P)
                    };
    }

    /**
     * Allocates a new AVFrame and set its data fields to the data fields
     * from the <tt>vpx_image_t</tt> pointed to by <tt>img</tt>. Also set its
     * 'linesize' according to <tt>img</tt>.
     *
     * @param img pointer to a <tt>vpx_image_t</tt> whose data will be used
     *
     * @return an AVFrame instance with its data fields set to the fields from
     * <tt>img</tt>
     */
    private AVFrame makeAVFrame(long img)
    {
        AVFrame avframe = new AVFrame();
        long p0 = VPX.img_get_plane0(img);
        long p1 = VPX.img_get_plane1(img);
        long p2 = VPX.img_get_plane2(img);

        //p0, p1, p2 are pointers, while avframe_set_data uses offsets
        FFmpeg.avframe_set_data(avframe.getPtr(),
                                p0,
                                p1-p0,
                                p2-p1);

        FFmpeg.avframe_set_linesize(avframe.getPtr(),
                VPX.img_get_stride0(img),
                VPX.img_get_stride1(img),
                VPX.img_get_stride2(img));

        return avframe;
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
     * Changes the output format, if necessary, according to the new dimentions
     * given via <tt>width</tt> and <tt>height</tt>.
     * @param width new width
     * @param height new height
     * @param frameRate frame rate
     */
    private void updateOutputFormat(int width, int height, float frameRate)
    {
        if ((width > 0) && (height > 0)
                && ((this.width != width) || (this.height != height)))
        {
            this.width = width;
            this.height = height;
            outputFormat
                = new AVFrameFormat(
                        new Dimension(width, height),
                        frameRate,
                        FFmpeg.PIX_FMT_YUV420P);
        }
    }
}
