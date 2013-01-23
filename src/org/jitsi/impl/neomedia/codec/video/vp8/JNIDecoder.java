/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.video.vp8;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.util.Logger;

import javax.media.*;
import javax.media.format.*;
import java.awt.*;

/**
 * Implements a VP8 decoder.
 *
 * Heavily based on the h264 decoder from
 * org.jitsi.impl.neomedia.codec.video.h264.JNIDecoder
 *
 * @author Boris Grozev
 */
public class JNIDecoder
        extends AbstractCodecExt
{
    /**
     * The <tt>Logger</tt> used by the <tt>JNIDecoder</tt> class
     * for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(JNIDecoder.class);

    /**
     * Pointer to the ffmpeg codec context to be used.
     */
    private long avcontext = 0;

    /**
     * The decoded data is stored in an avframe in native ffmpeg format (YUV).
     */
    private long avframe = 0;

    /**
     * If decoder has got a picture.
     */
    private final boolean[] got_picture = new boolean[1];

    /**
     * The last known height of {@link #avcontext} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int width = Constants.VIDEO_WIDTH;

    /**
     * The last known width of {@link #avcontext} i.e. the video output by this
     * <tt>JNIDecoder</tt>. Used to detect changes in the output size.
     */
    private int height = Constants.VIDEO_HEIGHT;

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
            = new VideoFormat[] { new AVFrameFormat(
                                        new Dimension(
                                            Constants.VIDEO_WIDTH,
                                            Constants.VIDEO_HEIGHT),
                                        Format.NOT_SPECIFIED,
                                        FFmpeg.PIX_FMT_YUV420P,
                                        Format.NOT_SPECIFIED) };

    /**
     * Initializes a new <tt>JNIDecoder</tt> instance.
     */
    public JNIDecoder()
    {
        super("VP8 JNI Decoder",
                VideoFormat.class,
                SUPPORTED_OUTPUT_FORMATS);

        inputFormat = null;
        outputFormat = null;
        inputFormats = new VideoFormat[] {new VideoFormat(Constants.VP8)};
    }

    /**
     * {@inheritDoc}
     */
    protected void doClose()
    {
        if(logger.isDebugEnabled())
            logger.debug("Closing decoder");

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
    }

    /**
     * {@inheritDoc}
     *
     * Initializes an FFmpeg context (in <tt>avcontext</tt>) and an FFmpeg
     * frame (in <tt>avframe</tt>)
     * @throws ResourceUnavailableException if initialization failed
     */
    protected void doOpen() throws ResourceUnavailableException
    {
        long avcodec = FFmpeg.avcodec_find_decoder(FFmpeg.CODEC_ID_VP8);
        if(avcodec == 0)
            throw new RuntimeException("Failed to avcodec_find_decoder()");


        avcontext = FFmpeg.avcodec_alloc_context3(avcodec);

        if (FFmpeg.avcodec_open2(avcontext, avcodec) < 0)
            throw new RuntimeException("Could not open codec");

        avframe = FFmpeg.avcodec_alloc_frame();

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
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        // Ask FFmpeg to decode.
        got_picture[0] = false;

        // TODO Take into account the offset of inputBuffer.
        if(inputBuffer.getOffset() != 0)
        {
            logger.warn("Input buffer offset is not 0");
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        FFmpeg.avcodec_decode_video(
                avcontext,
                avframe,
                got_picture,
                (byte[]) inputBuffer.getData(),
                //inputBuffer.getOffset(),
                inputBuffer.getLength());

        if (!got_picture[0])
        {
            outputBuffer.setDiscard(true);
            if(logger.isTraceEnabled())
                logger.trace("Discarding a packet: "
                        + inputBuffer.getSequenceNumber());
            return BUFFER_PROCESSED_OK;
        }

        // format
        int width = FFmpeg.avcodeccontext_get_width(avcontext);
        int height = FFmpeg.avcodeccontext_get_height(avcontext);

        if ((width > 0)
                && (height > 0)
                && ((this.width != width) || (this.height != height)))
        {
            //update the output dimentions if they've changed
            if(logger.isDebugEnabled())
                logger.debug("New dimentions: width="+width+", height="+height);
            this.width = width;
            this.height = height;

            // Output in same size and frame rate as input.
            Dimension outSize = new Dimension(this.width, this.height);
            VideoFormat inFormat = (VideoFormat) inputBuffer.getFormat();
            float outFrameRate = inFormat.getFrameRate();

            outputFormat
                    = new AVFrameFormat(
                        outSize,
                        outFrameRate,
                        FFmpeg.PIX_FMT_YUV420P,
                        Format.NOT_SPECIFIED);
        }
        outputBuffer.setFormat(outputFormat);

        // data
        Object out = outputBuffer.getData();
        if (!(out instanceof AVFrame) || (((AVFrame) out).getPtr() != avframe))
            outputBuffer.setData(new AVFrame(avframe));

        // timeStamp
        long pts = FFmpeg.AV_NOPTS_VALUE; // TODO avframe_get_pts(avframe);

        if (pts == FFmpeg.AV_NOPTS_VALUE)
            outputBuffer.setTimeStamp(Buffer.TIME_UNKNOWN);
        else
        {
            outputBuffer.setTimeStamp(pts);

            int outFlags = outputBuffer.getFlags();

            outFlags |= Buffer.FLAG_RELATIVE_TIME;
            outFlags &= ~(Buffer.FLAG_RTP_TIME | Buffer.FLAG_SYSTEM_TIME);
            outputBuffer.setFlags(outFlags);
        }

        return BUFFER_PROCESSED_OK;
    }


    /**
     * Get matching outputs for a specified input <tt>Format</tt>.
     *
     * @param in input <tt>Format</tt>
     * @return array of matching outputs or null if there are no matching
     * outputs.
     */
    protected Format[] getMatchingOutputFormats(Format in)
    {
        VideoFormat ivf = (VideoFormat) in;
        Dimension inSize = ivf.getSize();
        Dimension outSize;

        // return the default size/currently decoder and encoder
        // set to transmit/receive at this size
        if (inSize == null)
        {
            VideoFormat ovf = SUPPORTED_OUTPUT_FORMATS[0];

            if (ovf == null)
                return null;
            else
                outSize = ovf.getSize();
        }
        else
            outSize = inSize; // Output in same size as input.

        return
                new Format[]
                        {
                                new AVFrameFormat(
                                        outSize,
                                        ivf.getFrameRate(),
                                        FFmpeg.PIX_FMT_YUV420P,
                                        Format.NOT_SPECIFIED)
                        };
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
}