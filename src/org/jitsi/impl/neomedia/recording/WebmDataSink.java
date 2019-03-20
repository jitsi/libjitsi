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
package org.jitsi.impl.neomedia.recording;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

import javax.media.*;
import javax.media.datasink.*;
import javax.media.format.*;
import javax.media.protocol.*;
import java.io.*;

/**
 * A <tt>DataSink</tt> implementation which writes output in webm format.
 *
 * @author Boris Grozev
 */
public class WebmDataSink
    implements DataSink,
               BufferTransferHandler
{
    /**
     * The <tt>Logger</tt> used by the <tt>WebmDataSink</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(WebmDataSink.class);

    /**
     * Whether to generate a RECORDING_ENDED event when closing.
     */
    private static final boolean USE_RECORDING_ENDED_EVENTS = false;

    /**
     * The <tt>WebmWriter</tt> which we use to write the frames to a file.
     */
    private WebmWriter writer = null;

    private RecorderEventHandler eventHandler;
    private long ssrc = -1;

    /**
     * Whether this <tt>DataSink</tt> is open and should write to its
     * <tt>WebmWriter</tt>.
     */
    private boolean open = false;
    private final Object openCloseSyncRoot = new Object();

    /**
     * Whether we are in a state of waiting for a keyframe and discarding
     * non-key frames.
     */
    private boolean waitingForKeyframe = true;

    /**
     * The height of the video. Initialized on the first received keyframe.
     */
    private int height = 0;

    /**
     * The height of the video. Initialized on the first received keyframe.
     */
    private int width = 0;

    /**
     * A <tt>Buffer</tt> used to transfer frames.
     */
    private Buffer buffer = new Buffer();

    private WebmWriter.FrameDescriptor fd = new WebmWriter.FrameDescriptor();

    /**
     * Our <tt>DataSource</tt>.
     */
    private DataSource dataSource = null;

    /**
     * The name of the file into which we will write.
     */
    private String filename;

    /**
     * The RTP time stamp of the first frame written to the output webm file.
     */
    private long firstFrameRtpTimestamp = -1;

    /**
     * The time as returned by <tt>System.currentTimeMillis()</tt> of the first
     * frame written to the output webm file.
     */
    private long firstFrameTime = -1;

    /**
     * The PTS (presentation timestamp) of the last frame written to the output
     * file. In milliseconds.
     */
    private long lastFramePts = -1;

    /**
     * The <tt>KeyFrameControl</tt> which we will use to request a keyframe.
     */
    private KeyFrameControl keyFrameControl = null;

    /**
     * Whether we have already requested a keyframe.
     */
    private boolean keyframeRequested = false;

    private int framesSinceLastKeyframeRequest = 0;
    private static int REREQUEST_KEYFRAME_INTERVAL = 100;

    /**
     * Property name to control auto requesting keyframes periodically
     * to improve seeking speed without re-encoding the file
     */
    private static String AUTO_REQUEST_KEYFRAME_PNAME =
            WebmDataSink.class.getCanonicalName() + ".AUTOKEYFRAME";
    private int autoKeyframeRequestInterval = 0;


    /**
     * Initialize a new <tt>WebmDataSink</tt> instance.
     * @param filename the name of the file into which to write.
     * @param dataSource the <tt>DataSource</tt> to use.
     */
    public WebmDataSink(String filename, DataSource dataSource)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        this.autoKeyframeRequestInterval =
                cfg.getInt(AUTO_REQUEST_KEYFRAME_PNAME, this.autoKeyframeRequestInterval);
        if (this.autoKeyframeRequestInterval > 0 && logger.isInfoEnabled()) {
            logger.info("Auto keyframe request is initialized for every " + this.autoKeyframeRequestInterval + " frames.");
        }
        this.filename = filename;
        this.dataSource = dataSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDataSinkListener(DataSinkListener dataSinkListener)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        synchronized (openCloseSyncRoot)
        {
            if (!open)
            {
                if (logger.isDebugEnabled())
                    logger.debug("Not closing WebmDataSink: already closed.");
                return;
            }
            if (writer != null)
                writer.close();
            if (USE_RECORDING_ENDED_EVENTS
                    && eventHandler != null
                    && firstFrameTime != -1
                    && lastFramePts != -1)
            {
                RecorderEvent event = new RecorderEvent();
                event.setType(RecorderEvent.Type.RECORDING_ENDED);
                event.setSsrc(ssrc);
                event.setFilename(filename);

                // make sure that the difference in the 'instant'-s of the
                // STARTED and ENDED events matches the duration of the file
                event.setDuration(lastFramePts);

                event.setMediaType(MediaType.VIDEO);
                eventHandler.handleEvent(event);
            }
            open = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaLocator getOutputLocator()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() throws IOException, SecurityException
    {
        synchronized (openCloseSyncRoot)
        {
            if (dataSource instanceof PushBufferDataSource)
            {
                PushBufferDataSource pbds = (PushBufferDataSource) dataSource;
                PushBufferStream[] streams = pbds.getStreams();

                //XXX: should we allow for multiple streams in the data source?
                for (PushBufferStream stream : streams)
                {
                    //XXX whats the proper way to check for this? and handle?
                    if (!stream.getFormat().matches(new VideoFormat("VP8")))
                        throw new IOException("Unsupported stream format");

                    stream.setTransferHandler(this);
                }
            }
            dataSource.connect();

            open = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeDataSinkListener(DataSinkListener dataSinkListener)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOutputLocator(MediaLocator mediaLocator)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws IOException
    {
        writer = new WebmWriter(filename);
        dataSource.start();
        if (logger.isInfoEnabled())
            logger.info("Created WebmWriter on " + filename);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() throws IOException
    {
        //XXX: should we do something here? reset waitingForKeyframe?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getControl(String s)
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] getControls()
    {
        return new Object[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSource(DataSource dataSource)
            throws IOException, IncompatibleSourceException
    {
        //maybe we should throw an exception here, since we don't support
        //changing the data source?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transferData(PushBufferStream stream)
    {
        synchronized (openCloseSyncRoot)
        {
        if (!open)
            return;
        try
        {
            stream.read(buffer);
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }

        byte[] data = (byte[])buffer.getData();
        int offset = buffer.getOffset();
        int len = buffer.getLength();

        /*
         * Until an SDES packet is received by FMJ, it doesn't correctly set
         * the packets' timestamps. To avoid waiting, we use the RTP time stamps
         * directly. We can do this because VP8 always uses a rate of 90000.
         */
        long rtpTimeStamp = buffer.getRtpTimeStamp();

        boolean key = isKeyFrame(data, offset);
        boolean valid = isKeyFrameValid(data, offset);
        if (waitingForKeyframe && key)
        {
            if (valid)
            {
                waitingForKeyframe = false;
                width = getWidth(data, offset);
                height = getHeight(data, offset);
                firstFrameRtpTimestamp = rtpTimeStamp;
                firstFrameTime = System.currentTimeMillis();

                writer.writeWebmFileHeader(width, height);

                if (logger.isInfoEnabled())
                    logger.info("Received the first keyframe (width="
                        + width + "; height=" + height + ")"+" ssrc="+ssrc);

                if (eventHandler != null)
                {
                    RecorderEvent event = new RecorderEvent();
                    event.setType(RecorderEvent.Type.RECORDING_STARTED);
                    event.setSsrc(ssrc);
                    if (height*4 == width*3)
                        event.setAspectRatio(
                                RecorderEvent.AspectRatio.ASPECT_RATIO_4_3);
                    else if (height*16 == width*9)
                        event.setAspectRatio(
                                RecorderEvent.AspectRatio.ASPECT_RATIO_16_9);

                    event.setFilename(filename);
                    event.setInstant(firstFrameTime);
                    event.setRtpTimestamp(rtpTimeStamp);
                    event.setMediaType(MediaType.VIDEO);
                    eventHandler.handleEvent(event);
                }
            }
            else
            {
                keyframeRequested = false;
                if (logger.isInfoEnabled())
                    logger.info("Received an invalid first keyframe. " +
                                "Requesting a new one."+ssrc);
            }
        }

        framesSinceLastKeyframeRequest++;
        if (framesSinceLastKeyframeRequest > REREQUEST_KEYFRAME_INTERVAL)
            keyframeRequested = false;

        if (!keyframeRequested &&
                // recording not started yet
                (waitingForKeyframe ||
                        // auto keyframe request
                        (this.autoKeyframeRequestInterval > 0 &&
                                framesSinceLastKeyframeRequest > this.autoKeyframeRequestInterval))
                )
        {
            if (logger.isInfoEnabled())
                logger.info("Requesting keyframe. " + ssrc);
            if (keyFrameControl != null)
                keyframeRequested = keyFrameControl.requestKeyFrame(true);
            framesSinceLastKeyframeRequest = 0;
        }

        //that's temporary, aimed at debugging a specific issue
        if (key && logger.isInfoEnabled())
        {
            String s = "";
            for (int i = 0; i<10 && i<len; i++)
                s += String.format("%02x", data[offset+i]);
            logger.info("Keyframe. First 10 bytes: "+s);
        }

        if (!waitingForKeyframe)
        {
            if (key)
            {
                if (!valid)
                {
                    if (logger.isInfoEnabled())
                        logger.info("Dropping an invalid VP8 keyframe.");
                    return;
                }

                int oldWidth = width;
                width = getWidth(data, offset);
                int oldHeight = height;
                height = getHeight(data, offset);
                // TODO generate an event? start writing in a new file?
                if (width != oldWidth || height != oldHeight)
                {
                    if (logger.isInfoEnabled())
                    {
                        logger.info("VP8 stream width/height changed. Old: "
                            + oldWidth + "/" + oldHeight
                            + ". New: " + width + "/" + height + ".");
                    }
                }
            }
            fd.buffer = data;
            fd.offset = offset;
            fd.length = len;
            fd.flags = key ? WebmWriter.FLAG_FRAME_IS_KEY : 0;
            if (!isShowFrame(data, offset))
                fd.flags |= WebmWriter.FLAG_FRAME_IS_INVISIBLE;

            long diff = rtpTimeStamp - firstFrameRtpTimestamp;
            if (diff < -(1L<<31))
                diff += 1L<<32;
            //pts is in milliseconds, the VP8 rtp clock rate is 90000
            fd.pts = diff / 90;
            writer.writeFrame(fd);

            lastFramePts = fd.pts;
        }
        } //synchronized
    }

    /**
     * Returns <tt>true</tt> if the VP8 compressed frame contained in
     * <tt>buf</tt> at offset <tt>offset</tt> is a keyframe.
     * TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in <tt>buf</tt> where the VP8 compressed frame
     * starts.
     *
     * @return <tt>true</tt>if the VP8 compressed frame contained in
     * <tt>buf</tt> at offset <tt>offset</tt> is a keyframe.
     */
    private boolean isKeyFrame(byte[] buf, int offset)
    {
        return (buf[offset] & 0x01) == 0;
    }

    /**
     * Returns <tt>true</tt> if the VP8 compressed keyframe contained in
     * <tt>buf</tt> at offset <tt>offset</tt> is valid.
     * TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in <tt>buf</tt> where the VP8 compressed frame
     * starts.
     *
     * @return <tt>true</tt>if the VP8 compressed keyframe contained in
     * <tt>buf</tt> at offset <tt>offset</tt> is valid.
     */
    private boolean isKeyFrameValid(byte[] buf, int offset)
    {
        return (buf[offset + 3] == (byte) 0x9d) &&
               (buf[offset + 4] == (byte) 0x01) &&
               (buf[offset + 5] == (byte) 0x2a);
    }

    /**
     * Returns the width of the VP8 compressed frame contained in <tt>buf</tt>
     * at offset <tt>offset</tt>. See the format defined in RFC6386.
     * TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in <tt>buf</tt> where the VP8 compressed frame
     * starts.
     *
     * @return the width of the VP8 compressed frame contained in <tt>buf</tt>
     * at offset <tt>offset</tt>.
     */
    private int getWidth(byte[] buf, int offset)
    {
        return (((buf[offset+7] & 0xff) << 8) | (buf[offset+6] & 0xff)) & 0x3fff;
    }

    /**
     * Returns the height of the VP8 compressed frame contained in <tt>buf</tt>
     * at offset <tt>offset</tt>. See the format defined in RFC6386.
     * TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in <tt>buf</tt> where the VP8 compressed frame
     * starts.
     *
     * @return the height of the VP8 compressed frame contained in <tt>buf</tt>
     * at offset <tt>offset</tt>.
     */
     private int getHeight(byte[] buf, int offset)
    {
        return (((buf[offset+9] & 0xff) << 8) | (buf[offset+8] & 0xff)) & 0x3fff;
    }


    /**
     * Returns the value of the <tt>show_frame</tt> field from the
     * "uncompressed data chunk" in the VP8 compressed frame contained in
     * <tt>buf</tt> at offset <tt>offset</tt>.
     * RFC6386 isn't clear about the format, so the interpretation of
     * @{link http://tools.ietf.org/html/draft-ietf-payload-vp8-11} is used.
     * TODO: move it to a more general class?
     *
     * @param buf the buffer containing a compressed VP8 frame.
     * @param offset the offset in <tt>buf</tt> where the VP8 compressed frame
     * starts.
     *
     * @return the value of the <tt>show_frame</tt> field from the
     * "uncompressed data chunk" in the VP8 compressed frame contained in
     * <tt>buf</tt> at offset <tt>offset</tt>.
     */
    private boolean isShowFrame(byte[] buf, int offset)
    {
        return (buf[offset] & 0x10) == 0;
    }

    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        this.keyFrameControl = keyFrameControl;
    }

    public RecorderEventHandler getEventHandler()
    {
        return eventHandler;
    }

    public void setEventHandler(RecorderEventHandler eventHandler)
    {
        this.eventHandler = eventHandler;
    }

    public void setSsrc(long ssrc)
    {
       this.ssrc = ssrc;
    }

}
