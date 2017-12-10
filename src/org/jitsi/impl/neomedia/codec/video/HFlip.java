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
package org.jitsi.impl.neomedia.codec.video;

import java.awt.*;

import javax.media.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.util.*;

/**
 * Implements a video <tt>Effect</tt> which horizontally flips
 * <tt>AVFrame</tt>s.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class HFlip
    extends AbstractCodec2
    implements Effect
{
    /**
     * The <tt>Logger</tt> used by the <tt>HFlip</tt> class and its instances
     * for logging output.
     */
    private static final Logger logger = Logger.getLogger(HFlip.class);

    /**
     * The list of <tt>Format</tt>s supported by <tt>HFlip</tt> instances as
     * input and output.
     */
    private static final Format[] SUPPORTED_FORMATS
        = new Format[] { new AVFrameFormat() };

    /**
     * The name of the FFmpeg ffsink video source <tt>AVFilter</tt> used by
     * <tt>HFlip</tt>.
     */
    private static final String VSINK_FFSINK_NAME = "buffersink";

    /**
     * The name of the FFmpeg buffer video source <tt>AVFilter</tt> used by
     * <tt>HFlip</tt>.
     */
    private static final String VSRC_BUFFER_NAME = "buffer";

    /**
     * The pointer to the <tt>AVFilterContext</tt> in {@link #graph} of the
     * FFmpeg video source with the name {@link #VSRC_BUFFER_NAME}.
     */
    private long buffer;

    /**
     * The pointer to the <tt>AVFilterContext</tt> in {@link #graph} of the
     * FFmpeg video sink with the name {@link #VSINK_FFSINK_NAME}.
     */
    private long ffsink;

    /**
     * The pointer to the <tt>AVFilterGraph</tt> instance which contains the
     * FFmpeg hflip filter represented by this <tt>Effect</tt>.
     */
    private long graph = 0;

    /**
     * The indicator which determines whether the fact that {@link #graph} is
     * equal to zero means that an attempt to initialize it is to be made. If
     * <tt>false</tt>, indicates that such an attempt has already been made and
     * has failed. In other words, prevents multiple initialization attempts
     * with the same parameters.
     */
    private boolean graphIsPending = true;

    /**
     * The height of {@link #graph}.
     */
    private int height;

    /**
     * The pointer to the <tt>AVFrame</tt> instance which is the output (data)
     * of this <tt>Effect</tt>.
     */
    private long outputFrame;

    /**
     * The FFmpeg pixel format of {@link #graph}.
     */
    private int pixFmt = FFmpeg.PIX_FMT_NONE;

    /**
     * The width of {@link #graph}.
     */
    private int width;

    /**
     * Initializes a new <tt>HFlip</tt> instance.
     */
    public HFlip()
    {
        super("FFmpeg HFlip Filter", AVFrameFormat.class, SUPPORTED_FORMATS);
    }

    /**
     * Closes this <tt>Effect</tt>.
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected synchronized void doClose()
    {
        try
        {
            if (outputFrame != 0)
            {
                FFmpeg.avcodec_free_frame(outputFrame);
                outputFrame = 0;
            }
        }
        finally
        {
            reset();
        }
    }

    /**
     * Opens this <tt>Effect</tt>.
     *
     * @throws ResourceUnavailableException if any of the required resource
     * cannot be allocated
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected synchronized void doOpen()
        throws ResourceUnavailableException
    {
        outputFrame = FFmpeg.avcodec_alloc_frame();
        if (outputFrame == 0)
        {
            String reason = "avcodec_alloc_frame: " + outputFrame;

            logger.error(reason);
            throw new ResourceUnavailableException(reason);
        }
    }

    /**
     * Performs the media processing defined by this <tt>Effect</tt>.
     *
     * @param inputBuffer the <tt>Buffer</tt> that contains the media data to be
     * processed
     * @param outputBuffer the <tt>Buffer</tt> in which to store the processed
     * media data
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the processing is successful
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected synchronized int doProcess(
            Buffer inputBuffer,
            Buffer outputBuffer)
    {
        // Make sure the graph is configured with the current Format i.e. size
        // and pixFmt.
        AVFrameFormat format = (AVFrameFormat) inputBuffer.getFormat();
        Dimension size = format.getSize();
        int pixFmt = format.getPixFmt();

        if ((this.width != size.width)
                || (this.height != size.height)
                || (this.pixFmt != pixFmt))
            reset();

        if (!allocateFfmpegGraph(format, size, pixFmt))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        // The graph is configured for the current Format, apply its filters to
        // the inputFrame.
        long inputFrame = ((AVFrame) inputBuffer.getData()).getPtr();

        long filterResult
            = FFmpeg.get_filtered_video_frame(
                    inputFrame, this.width, this.height, this.pixFmt,
                    buffer,
                    ffsink,
                    outputFrame);
        if(filterResult < 0)
        {
            // If get_filtered_video_frame fails, it is likely to fail for any
            // frame. Consequently, printing that it has failed will result in a
            // lot of repeating logging output. Since the failure in question
            // will be visible in the UI anyway, just debug it.
            if (logger.isTraceEnabled())
                logger.trace("get_filtered_video_frame: "
                    + FFmpeg.av_strerror((int)filterResult));
            return BUFFER_PROCESSED_FAILED;
        }

        Object out = outputBuffer.getData();

        if (!(out instanceof AVFrame)
                || (((AVFrame) out).getPtr() != outputFrame))
        {
            outputBuffer.setData(new AVFrame(outputFrame));
        }

        outputBuffer.setDiscard(inputBuffer.isDiscard());
        outputBuffer.setDuration(inputBuffer.getDuration());
        outputBuffer.setEOM(inputBuffer.isEOM());
        outputBuffer.setFlags(inputBuffer.getFlags());
        outputBuffer.setFormat(format);
        outputBuffer.setHeader(inputBuffer.getHeader());
        outputBuffer.setLength(inputBuffer.getLength());
        outputBuffer.setSequenceNumber(inputBuffer.getSequenceNumber());
        outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
        return BUFFER_PROCESSED_OK;
    }

    private boolean allocateFfmpegGraph(AVFrameFormat format, Dimension size,
        int pixFmt)
    {
        if (graph != 0)
        {
            return true;
        }

        String errorReason = null;
        int error = 0;
        long buffer = 0;
        long ffsink = 0;

        if (graphIsPending)
        {
            graphIsPending = false;

            graph = FFmpeg.avfilter_graph_alloc();
            if (graph == 0)
                errorReason = "avfilter_graph_alloc";
            else
            {
                String filters
                    = VSRC_BUFFER_NAME + "=" + size.width + ":" + size.height
                        + ":" + pixFmt + ":1:1000000:1:1,"
                        + "hflip,"
                        + "format=pix_fmts=" + pixFmt + ","
                        + VSINK_FFSINK_NAME;
                long log_ctx = 0;

                error
                    = FFmpeg.avfilter_graph_parse(
                            graph,
                            filters,
                            0, 0,
                            log_ctx);
                if (error == 0)
                {
                    // Unfortunately, the name of an AVFilterContext created by
                    // avfilter_graph_parse is not the name of the AVFilter.
                    String parsedFilterNameFormat = "Parsed_%2$s_%1$d";
                    String parsedFilterName
                        = String.format(
                                parsedFilterNameFormat,
                                0, VSRC_BUFFER_NAME);

                    buffer
                        = FFmpeg.avfilter_graph_get_filter(
                                graph,
                                parsedFilterName);
                    if (buffer == 0)
                    {
                        errorReason
                            = "avfilter_graph_get_filter: "
                                + VSRC_BUFFER_NAME
                                + "/"
                                + parsedFilterName;
                    }
                    else
                    {
                        parsedFilterName
                            = String.format(
                                    parsedFilterNameFormat,
                                    3,
                                    VSINK_FFSINK_NAME);
                        ffsink
                            = FFmpeg.avfilter_graph_get_filter(
                                    graph,
                                    parsedFilterName);
                        if (ffsink == 0)
                        {
                            errorReason
                                = "avfilter_graph_get_filter: "
                                    + VSINK_FFSINK_NAME
                                    + "/"
                                    + parsedFilterName;
                        }
                        else
                        {
                            error
                                = FFmpeg.avfilter_graph_config(graph, log_ctx);
                            if (error != 0)
                                errorReason = "avfilter_graph_config";
                        }
                    }
                }
                else
                {
                    errorReason = "avfilter_graph_parse";
                }

                if (errorReason != null)
                {
                    FFmpeg.avfilter_graph_free(graph);
                    graph = 0;
                }
            }
        }

        if (graph == 0)
        {
            if (errorReason != null)
            {
                StringBuilder msg = new StringBuilder(errorReason);
                if (error != 0)
                {
                    msg.append(": ").append(error);
                }

                msg.append(", format ").append(format);
                logger.error(msg);
            }

            return false;
        }
        else
        {
            this.width = size.width;
            this.height = size.height;
            this.pixFmt = pixFmt;
            this.buffer = buffer;
            this.ffsink = ffsink;
        }

        return true;
    }

    /**
     * Resets the state of this <tt>PlugIn</tt>.
     */
    @Override
    public synchronized void reset()
    {
        if (graph != 0)
        {
            FFmpeg.avfilter_graph_free(graph);
            graph = 0;
            graphIsPending = true;

            width = 0;
            height = 0;
            pixFmt = FFmpeg.PIX_FMT_NONE;
            buffer = 0;
            ffsink = 0;
        }
    }
}
