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

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.logging.*;

/**
 * Implements a VP8 encoder.
 *
 * @author Boris Grozev
 */
public class VPXEncoder
    extends AbstractCodec2
{
    /**
     * VPX interface to use
     */
    private static final int INTERFACE = VPX.INTERFACE_VP8_ENC;

    /**
     * Default width
     */
    private static final int DEFAULT_WIDTH = 640;

    /**
     * Default height.
     */
    private static final int DEFAULT_HEIGHT = 480;

    /**
     * The <tt>Logger</tt> used by the <tt>VPXEncoder</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(VPXEncoder.class);

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
            = new VideoFormat[] { new VideoFormat(Constants.VP8) };

    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing
     * encoder configuration
     */
    private long cfg = 0;

    /**
     * Pointer to the libvpx codec context to be used
     */
    private long context = 0;

    /**
     * Flags passed when (re-)initializing the encoder context
     */
    private long flags = 0;

    /**
     * Number of encoder frames so far. Used as pst (presentation time stamp)
     */
    private long frameCount = 0;


    /**
     * Pointer to a native vpx_image instance used to feed frames to the encoder
     */
    private long img = 0;

    /**
     * Iterator for the compressed frames in the encoder context. Can be
     * re-initialized by setting its only element to 0.
     */
    private long[] iter = new long[1];

    /**
     * Whether there are unprocessed packets left from a previous call to
     * VP8.codec_encode()
     */
    private boolean leftoverPackets = false;

    /**
     * Pointer to a vpx_codec_cx_pkt_t
     */
    private long pkt = 0;

    /**
     * Current width of the input and output frames
     */
    private int width = DEFAULT_WIDTH;

    /**
     * Current height of the input and output frames
     */
    private int height = DEFAULT_HEIGHT;

    /**
     * Initializes a new <tt>VPXEncoder</tt> instance.
     */
    public VPXEncoder()
    {
        super("VP8 Encoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats
            = new VideoFormat[]
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
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        if(logger.isDebugEnabled())
            logger.debug("Closing encoder");
        if(context != 0)
        {
            VPX.codec_destroy(context);
            VPX.free(context);
            context = 0;
        }
        if(img != 0)
        {
            VPX.free(img);
            img = 0;
        }
        if(cfg != 0)
        {
            VPX.free(cfg);
            cfg = 0;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException
    {
        img = VPX.img_malloc();
        if(img == 0)
        {
            throw new RuntimeException("Could not img_malloc()");
        }
        VPX.img_set_fmt(img, VPX.IMG_FMT_I420);
        VPX.img_set_bps(img, 12);
        VPX.img_set_w(img, width);
        VPX.img_set_d_w(img, width);
        VPX.img_set_h(img, height);
        VPX.img_set_d_h(img, height);

        cfg = VPX.codec_enc_cfg_malloc();
        if(cfg == 0)
        {
            throw new RuntimeException("Could not codec_enc_cfg_malloc()");
        }
        VPX.codec_enc_config_default(INTERFACE, cfg, 0);

        int bitRate
            = NeomediaServiceUtils
                .getMediaServiceImpl()
                    .getDeviceConfiguration()
                        .getVideoBitrate();

        //set some settings
        VPX.codec_enc_cfg_set_rc_target_bitrate(cfg, bitRate);
        VPX.codec_enc_cfg_set_rc_resize_allowed(cfg, 1);
        VPX.codec_enc_cfg_set_rc_end_usage(cfg, VPX.RC_MODE_CBR);
        VPX.codec_enc_cfg_set_kf_mode(cfg, VPX.KF_MODE_AUTO);
        VPX.codec_enc_cfg_set_w(cfg, width);
        VPX.codec_enc_cfg_set_h(cfg, height);
        VPX.codec_enc_cfg_set_error_resilient(cfg,
            VPX.ERROR_RESILIENT_DEFAULT | VPX.ERROR_RESILIENT_PARTITIONS);

        context = VPX.codec_ctx_malloc();
        int ret = VPX.codec_enc_init(context, INTERFACE, cfg, flags);

        if(ret != VPX.CODEC_OK)
            throw new RuntimeException("Failed to initialize encoder, libvpx"
                    + " error:\n"
                    + VPX.codec_err_to_string(ret));

        if (inputFormat == null)
            throw new ResourceUnavailableException("No input format selected");
        if (outputFormat == null)
            throw new ResourceUnavailableException("No output format selected");

        if(logger.isDebugEnabled())
            logger.debug("VP8 encoder opened succesfully");
    }

    /**
     * Updates the input width and height the encoder should expect.
     *
     * @param w new width
     * @param h new height
     */
    private void updateSize(int w, int h)
    {
        if(logger.isInfoEnabled())
            logger.info("Setting new width/height: "+w + "/"+ h);

        this.width = w;
        this.height = h;
        if(img != 0)
        {
            VPX.img_set_w(img, w);
            VPX.img_set_d_w(img, w);
            VPX.img_set_h(img, h);
            VPX.img_set_d_h(img, h);
        }
        if(cfg != 0)
        {
            VPX.codec_enc_cfg_set_w(cfg, w);
            VPX.codec_enc_cfg_set_h(cfg, h);
            reinit();
        }
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
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        if(inputBuffer.isDiscard())
        {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        int ret = BUFFER_PROCESSED_OK;
        byte[] output;
        if(leftoverPackets)
        {
            if(VPX.codec_cx_pkt_get_kind(pkt) == VPX.CODEC_CX_FRAME_PKT)
            {
                int size = VPX.codec_cx_pkt_get_size(pkt);
                output = validateByteArraySize(inputBuffer, size, false);
                VPX.memcpy(output,
                           VPX.codec_cx_pkt_get_data(pkt),
                           size);
                outputBuffer.setOffset(0);
                outputBuffer.setLength(size);
                outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
            }
            else
            {
                //not a compressed frame, skip this packet
                ret |= OUTPUT_BUFFER_NOT_FILLED;
            }
        }
        else
        {
            frameCount++;

            YUVFormat format = (YUVFormat) inputBuffer.getFormat();
            Dimension formatSize = format.getSize();
            int width = formatSize.width;
            int height = formatSize.height;

            if (width > 0 && height > 0 &&
                    (width != this.width || height != this.height))
                updateSize(width, height);

            //setup img
            int strideY = format.getStrideY();
            if (strideY == Format.NOT_SPECIFIED)
                strideY = width;
            int strideUV = format.getStrideUV();
            if (strideUV == Format.NOT_SPECIFIED)
                strideUV = width/2;
            VPX.img_set_stride0(img, strideY);
            VPX.img_set_stride1(img, strideUV);
            VPX.img_set_stride2(img, strideUV);
            VPX.img_set_stride3(img, 0);

            int offsetY = format.getOffsetY();
            if (offsetY == Format.NOT_SPECIFIED)
                offsetY = 0;
            int offsetU = format.getOffsetU();
            if (offsetU == Format.NOT_SPECIFIED)
                offsetU = offsetY + width * height;
            int offsetV = format.getOffsetV();
            if (offsetV == Format.NOT_SPECIFIED)
                offsetV = offsetU + (width * height) / 4;

            int result = VPX.codec_encode(
                    context,
                    img,
                    (byte[]) inputBuffer.getData(),
                    offsetY,
                    offsetU,
                    offsetV,
                    frameCount, //pts
                    1, //duration
                    0, //flags
                    VPX.DL_REALTIME);
            if(result != VPX.CODEC_OK)
            {
                logger.warn("Failed to encode a frame: "
                        + VPX.codec_err_to_string(result));
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            iter[0] = 0;
            pkt = VPX.codec_get_cx_data(context, iter);
            if(pkt != 0 &&
                    VPX.codec_cx_pkt_get_kind(pkt) == VPX.CODEC_CX_FRAME_PKT)
            {
                int size = VPX.codec_cx_pkt_get_size(pkt);
                long data = VPX.codec_cx_pkt_get_data(pkt);
                output = validateByteArraySize(outputBuffer, size, false);
                VPX.memcpy(output, data, size);
                outputBuffer.setOffset(0);
                outputBuffer.setLength(size);
                outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
            }
            else
            {
                //not a compressed frame, skip this packet
                ret |= OUTPUT_BUFFER_NOT_FILLED;
            }
        }

        pkt = VPX.codec_get_cx_data(context, iter);
        leftoverPackets = pkt != 0;

        if(leftoverPackets)
            return ret | INPUT_BUFFER_NOT_CONSUMED;
        else
            return ret;

    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array of formats matching input format
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return
            new VideoFormat[]
                    {
                        new VideoFormat(
                                Constants.VP8,
                                inputVideoFormat.getSize(),
                                /* maxDataLength */ Format.NOT_SPECIFIED,
                                Format.byteArray,
                                inputVideoFormat.getFrameRate())
                    };
    }

    /**
     * Reinitializes the encoder context. Needed when the input size changes.
     */
    private void reinit()
    {
        if(context != 0)
            VPX.codec_destroy(context);

        int ret = VPX.codec_enc_init(context, INTERFACE, cfg, flags);

        if(ret != VPX.CODEC_OK)
            throw new RuntimeException("Failed to re-initialize encoder, libvpx"
                    + " error:\n"
                    + VPX.codec_err_to_string(ret));
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
     */
    @Override
    public Format setInputFormat(Format format)
    {
        if(!(format instanceof VideoFormat)
                || (matches(format, inputFormats) == null))
            return null;

        YUVFormat yuvFormat = (YUVFormat) format;

        if (yuvFormat.getOffsetU() > yuvFormat.getOffsetV())
            return null;

        inputFormat = specialize(yuvFormat, Format.byteArray);

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
        if(!(format instanceof VideoFormat)
                || (matches(format, getMatchingOutputFormats(inputFormat))
                        == null))
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
