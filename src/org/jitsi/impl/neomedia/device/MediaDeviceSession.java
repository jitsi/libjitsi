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
package org.jitsi.impl.neomedia.device;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.jitsi.utils.*;

/**
 * Represents the use of a specific <tt>MediaDevice</tt> by a
 * <tt>MediaStream</tt>.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Boris Grozev
 */
public class MediaDeviceSession
    extends PropertyChangeNotifier
{
    /**
     * The <tt>Logger</tt> used by the <tt>MediaDeviceSession</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MediaDeviceSession.class);

    /**
     * The name of the <tt>MediaDeviceSession</tt> instance property the value
     * of which represents the output <tt>DataSource</tt> of the
     * <tt>MediaDeviceSession</tt> instance which provides the captured (RTP)
     * data to be sent by <tt>MediaStream</tt> to <tt>MediaStreamTarget</tt>.
     */
    public static final String OUTPUT_DATA_SOURCE = "OUTPUT_DATA_SOURCE";

    /**
     * The name of the property that corresponds to the array of SSRC
     * identifiers that we store in this <tt>MediaDeviceSession</tt> instance
     * and that we update upon adding and removing <tt>ReceiveStream</tt>
     */
    public static final String SSRC_LIST = "SSRC_LIST";

    /**
     * The JMF <tt>DataSource</tt> of {@link #device} through which this
     * instance accesses the media captured by it.
     */
    private DataSource captureDevice;

    /**
     * The indicator which determines whether {@link DataSource#connect()} has
     * been successfully executed on {@link #captureDevice}.
     */
    private boolean captureDeviceIsConnected;

    /**
     * The <tt>ContentDescriptor</tt> which specifies the content type in which
     * this <tt>MediaDeviceSession</tt> is to output the media captured by its
     * <tt>MediaDevice</tt>.
     */
    private ContentDescriptor contentDescriptor;

    /**
     * The <tt>MediaDevice</tt> used by this instance to capture and play back
     * media.
     */
    private final AbstractMediaDevice device;

    /**
     * The last JMF <tt>Format</tt> set to this instance by a call to its
     * {@link #setFormat(MediaFormat)} and to be set as the output format of
     * {@link #processor}.
     */
    private MediaFormatImpl<? extends Format> format;

    /**
     * The indicator which determines whether this <tt>MediaDeviceSession</tt>
     * is set to output "silence" instead of the actual media captured from
     * {@link #captureDevice}.
     */
    private boolean mute = false;

    /**
     * The list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance. The (read and write)
     * accesses to the field are to be synchronized using
     * {@link #playbacksLock}.
     */
    private final List<Playback> playbacks = new LinkedList<Playback>();

    /**
     * The <tt>ReadWriteLock</tt> which is used to synchronize the (read and
     * write) accesses to {@link #playbacks}.
     */
    private final ReadWriteLock playbacksLock = new ReentrantReadWriteLock();

    /**
     * The <tt>ControllerListener</tt> which listens to the <tt>Player</tt>s of
     * {@link #playbacks} for <tt>ControllerEvent</tt>s.
     */
    private final ControllerListener playerControllerListener
        = new ControllerListener()
                {
                    /**
                     * Notifies this <tt>ControllerListener</tt> that the
                     * <tt>Controller</tt> which it is registered with has
                     * generated an event.
                     *
                     * @param ev the <tt>ControllerEvent</tt> specifying the
                     * <tt>Controller</tt> which is the source of the event and
                     * the very type of the event
                     * @see ControllerListener#controllerUpdate(ControllerEvent)
                     */
                    public void controllerUpdate(ControllerEvent ev)
                    {
                        playerControllerUpdate(ev);
                    }
                };

    /**
     * The JMF <tt>Processor</tt> which transcodes {@link #captureDevice} into
     * the format of this instance.
     */
    private Processor processor;

    /**
     * The <tt>ControllerListener</tt> which listens to {@link #processor} for
     * <tt>ControllerEvent</tt>s.
     */
    private ControllerListener processorControllerListener;

    /**
     * The indicator which determines whether {@link #processor} has received
     * a <tt>ControllerClosedEvent</tt> at an unexpected time in its execution.
     * A value of <tt>false</tt> does not mean that <tt>processor</tt> exists
     * or that it is not closed, it just means that if <tt>processor</tt> failed
     * to be initialized or it received a <tt>ControllerClosedEvent</tt>, it was
     * at an expected time of its execution and that the fact in question was
     * reflected, for example, by setting <tt>processor</tt> to <tt>null</tt>.
     * If there is no <tt>processorIsPrematurelyClosed</tt> field and
     * <tt>processor</tt> is set to <tt>null</tt> or left existing after the
     * receipt of <tt>ControllerClosedEvent</tt>, it will either lead to not
     * firing a <tt>PropertyChangeEvent</tt> for <tt>OUTPUT_DATA_SOURCE</tt>
     * when it has actually changed and, consequently, cause the
     * <tt>SendStream</tt>s of <tt>MediaStreamImpl</tt> to not be recreated or
     * it will be impossible to detect that <tt>processor</tt> cannot have its
     * format set and will thus be left broken even for subsequent calls to
     * {@link #setFormat(MediaFormat)}.
     */
    private boolean processorIsPrematurelyClosed;

    /**
     * The list of SSRC identifiers representing the parties that we are
     * currently handling receive streams from.
     */
    private long[] ssrcList = null;

    /**
     * The <tt>MediaDirection</tt> in which this <tt>MediaDeviceSession</tt> has
     * been started.
     */
    private MediaDirection startedDirection = MediaDirection.INACTIVE;

    /**
     * If the player have to be disposed when we {@link #close()} this instance.
     */
    private boolean disposePlayerOnClose = true;

    /**
     * Whether output size has changed after latest processor config.
     * Used for video streams.
     */
    protected boolean outputSizeChanged = false;

    /**
     * Whether this device session is used by a stream which uses a translator.
     */
    public boolean useTranslator = false;

    /**
     * Initializes a new <tt>MediaDeviceSession</tt> instance which is to
     * represent the use of a specific <tt>MediaDevice</tt> by a
     * <tt>MediaStream</tt>.
     *
     * @param device the <tt>MediaDevice</tt> the use of which by a
     * <tt>MediaStream</tt> is to be represented by the new instance
     */
    protected MediaDeviceSession(AbstractMediaDevice device)
    {
        checkDevice(device);

        this.device = device;
    }

    /**
     * Sets the indicator which determines whether this instance is to dispose
     * of its associated player upon closing.
     *
     * @param disposePlayerOnClose <tt>true</tt> to have this instance dispose
     * of its associated player upon closing; otherwise, <tt>false</tt>
     */
    public void setDisposePlayerOnClose(boolean disposePlayerOnClose)
    {
        this.disposePlayerOnClose = disposePlayerOnClose;
    }

    /**
     * Adds <tt>ssrc</tt> to the array of SSRC identifiers representing parties
     * that this <tt>MediaDeviceSession</tt> is currently receiving streams
     * from. We use this method mostly as a way of to caching SSRC identifiers
     * during a conference call so that the streams that are sending CSRC lists
     * could have them ready for use rather than have to construct them for
     * every RTP packet.
     *
     * @param ssrc the new SSRC identifier that we'd like to add to the array of
     * <tt>ssrc</tt> identifiers stored by this session.
     */
    protected void addSSRC(long ssrc)
    {
        //init if necessary
        if (ssrcList == null)
        {
            setSsrcList(new long[] { ssrc });
            return;
        }

        //check whether we already have this ssrc
        for (int i = 0; i < ssrcList.length; i++)
        {
            if (ssrc == ssrcList[i])
                return;
        }

        //resize the array and add the new ssrc to the end.
        long[] newSsrcList = new long[ssrcList.length + 1];

        System.arraycopy(ssrcList, 0, newSsrcList, 0, ssrcList.length);
        newSsrcList[newSsrcList.length - 1] = ssrc;

        setSsrcList(newSsrcList);
    }

    /**
     * For JPEG and H263, we know that they only work for particular sizes.  So
     * we'll perform extra checking here to make sure they are of the right
     * sizes.
     *
     * @param sourceFormat the original format to check the size of
     * @return the modified <tt>VideoFormat</tt> set to the size we support
     */
    private static VideoFormat assertSize(VideoFormat sourceFormat)
    {
        int width, height;

        // JPEG
        if (sourceFormat.matches(new Format(VideoFormat.JPEG_RTP)))
        {
            Dimension size = sourceFormat.getSize();

            // For JPEG, make sure width and height are divisible by 8.
            width = (size.width % 8 == 0)
                    ? size.width
                    : ((size.width / 8) * 8);
            height = (size.height % 8 == 0)
                    ? size.height
                    : ((size.height / 8) * 8);
        }
        // H.263
        else if (sourceFormat.matches(new Format(VideoFormat.H263_RTP)))
        {
            // For H.263, we only support some specific sizes.
//            if (size.width < 128)
//            {
//                width = 128;
//                height = 96;
//            }
//            else if (size.width < 176)
//            {
//                width = 176;
//                height = 144;
//            }
//            else
//            {
                width = 352;
                height = 288;
//            }
        }
        else
        {
            // We don't know this particular format. We'll just leave it alone
            // then.
            return sourceFormat;
        }

        VideoFormat result
            = new VideoFormat(
                    null,
                    new Dimension(width, height),
                    Format.NOT_SPECIFIED,
                    null,
                    Format.NOT_SPECIFIED);
        return (VideoFormat) result.intersects(sourceFormat);
    }

    /**
     * Asserts that a specific <tt>MediaDevice</tt> is acceptable to be set as
     * the <tt>MediaDevice</tt> of this instance. Allows extenders to override
     * and customize the check.
     *
     * @param device the <tt>MediaDevice</tt> to be checked for suitability to
     * become the <tt>MediaDevice</tt> of this instance
     */
    protected void checkDevice(AbstractMediaDevice device)
    {
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    public void close()
    {
        try
        {
            stop(MediaDirection.SENDRECV);
        }
        finally
        {
            /*
             * XXX The order of stopping the playback and capture is important
             * here because when we use echo cancellation the capture accesses
             * data from the playback and thus there is synchronization to avoid
             * segfaults but this synchronization can sometimes lead to a slow
             * stop of the playback. That is why we stop the capture first.
             */

            // capture
            disconnectCaptureDevice();
            closeProcessor();

            // playback
            if (disposePlayerOnClose)
                disposePlayer();

            processor = null;
            captureDevice = null;
        }
    }

    /**
     * Makes sure {@link #processor} is closed.
     */
    private void closeProcessor()
    {
        if (processor != null)
        {
            if (processorControllerListener != null)
                processor.removeControllerListener(processorControllerListener);

            processor.stop();
            if (logger.isTraceEnabled())
                logger
                    .trace(
                        "Stopped Processor with hashCode "
                            + processor.hashCode());

            if (processor.getState() == Processor.Realized)
            {
                DataSource dataOutput;

                try
                {
                    dataOutput = processor.getDataOutput();
                }
                catch (NotRealizedError nre)
                {
                    dataOutput = null;
                }
                if (dataOutput != null)
                    dataOutput.disconnect();
            }

            processor.deallocate();
            processor.close();
            processorIsPrematurelyClosed = false;

            /*
             * Once the processor uses the captureDevice, the captureDevice has
             * to be reconnected on its next use.
             */
            disconnectCaptureDevice();
        }
    }

    /**
     * Creates the <tt>DataSource</tt> that this instance is to read captured
     * media from.
     *
     * @return the <tt>DataSource</tt> that this instance is to read captured
     * media from
     */
    protected DataSource createCaptureDevice()
    {
        DataSource captureDevice = getDevice().createOutputDataSource();

        if (captureDevice != null)
        {
            MuteDataSource muteDataSource
                = AbstractControls.queryInterface(
                        captureDevice,
                        MuteDataSource.class);

            if (muteDataSource == null)
            {
                // Try to enable muting.
                if (captureDevice instanceof PushBufferDataSource)
                {
                    captureDevice
                        = new RewritablePushBufferDataSource(
                                (PushBufferDataSource) captureDevice);
                }
                else if (captureDevice instanceof PullBufferDataSource)
                {
                    captureDevice
                        = new RewritablePullBufferDataSource(
                                (PullBufferDataSource) captureDevice);
                }
                muteDataSource
                    = AbstractControls.queryInterface(
                            captureDevice,
                            MuteDataSource.class);
            }
            if (muteDataSource != null)
                muteDataSource.setMute(mute);
        }

        return captureDevice;
    }

    /**
     * Creates a new <tt>Player</tt> for a specific <tt>DataSource</tt> so that
     * it is played back on the <tt>MediaDevice</tt> represented by this
     * instance.
     *
     * @param dataSource the <tt>DataSource</tt> to create a new <tt>Player</tt>
     * for
     * @return a new <tt>Player</tt> for the specified <tt>dataSource</tt>
     */
    protected Player createPlayer(DataSource dataSource)
    {
        Processor player = null;
        Throwable exception = null;

        try
        {
            player = getDevice().createPlayer(dataSource);
        }
        catch (Exception ex)
        {
            exception = ex;
        }
        if (exception != null)
        {
            logger.error(
                    "Failed to create Player for "
                        + MediaStreamImpl.toString(dataSource),
                    exception);
        }
        else if (player != null)
        {
            /*
             * We cannot wait for the Player to get configured (e.g. with
             * waitForState) because it will stay in the Configuring state until
             * it reads some media. In the case of a ReceiveStream not sending
             * media (e.g. abnormally stopped), it will leave us blocked.
             */
            player.addControllerListener(playerControllerListener);

            player.configure();
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Created Player with hashCode "
                            + player.hashCode()
                            + " for "
                            + MediaStreamImpl.toString(dataSource));
            }
        }

        return player;
    }

    /**
     * Initializes a new FMJ <tt>Processor</tt> which is to transcode
     * {@link #captureDevice} into the format of this instance.
     *
     * @return a new FMJ <tt>Processor</tt> which is to transcode
     * <tt>captureDevice</tt> into the format of this instance
     */
    protected Processor createProcessor()
    {
        DataSource captureDevice = getConnectedCaptureDevice();

        if (captureDevice != null)
        {
            Processor processor = null;
            Throwable exception = null;

            try
            {
                processor = Manager.createProcessor(captureDevice);
            }
            catch (IOException ioe)
            {
                // TODO
                exception = ioe;
            }
            catch (NoProcessorException npe)
            {
                // TODO
                exception = npe;
            }

            if (exception != null)
                logger
                    .error(
                        "Failed to create Processor for " + captureDevice,
                        exception);
            else
            {
                if (processorControllerListener == null)
                    processorControllerListener = new ControllerListener()
                    {

                        /**
                         * Notifies this <tt>ControllerListener</tt> that
                         * the <tt>Controller</tt> which it is registered
                         * with has generated an event.
                         *
                         * @param event the <tt>ControllerEvent</tt>
                         * specifying the <tt>Controller</tt> which is the
                         * source of the event and the very type of the
                         * event
                         * @see ControllerListener#controllerUpdate(
                         * ControllerEvent)
                         */
                        public void controllerUpdate(ControllerEvent event)
                        {
                            processorControllerUpdate(event);
                        }
                    };
                processor
                    .addControllerListener(processorControllerListener);

                if (waitForState(processor, Processor.Configured))
                {
                    this.processor = processor;
                    processorIsPrematurelyClosed = false;
                }
                else
                {
                    if (processorControllerListener != null)
                        processor
                            .removeControllerListener(
                                processorControllerListener);
                    processor = null;
                }
            }
        }

        return this.processor;
    }

    /**
     * Creates a <tt>ContentDescriptor</tt> to be set on a specific
     * <tt>Processor</tt> of captured media to be sent to the remote peer.
     * Allows extenders to override. The default implementation returns
     * {@link ContentDescriptor#RAW_RTP}.
     *
     * @param processor the <tt>Processor</tt> of captured media to be sent to
     * the remote peer which is to have its <tt>contentDescriptor</tt> set to
     * the returned <tt>ContentDescriptor</tt>
     * @return a <tt>ContentDescriptor</tt> to be set on the specified
     * <tt>processor</tt> of captured media to be sent to the remote peer
     */
    protected ContentDescriptor createProcessorContentDescriptor(
            Processor processor)
    {
        return
            (contentDescriptor == null)
                ? new ContentDescriptor(ContentDescriptor.RAW_RTP)
                : contentDescriptor;
    }

    /**
     * Initializes a <tt>Renderer</tt> instance which is to be utilized by a
     * specific <tt>Player</tt> in order to play back the media represented by
     * a specific <tt>TrackControl</tt>. Allows extenders to override and,
     * optionally, perform additional configuration of the returned
     * <tt>Renderer</tt>.
     *
     * @param player the <tt>Player</tt> which is to utilize the
     * initialized/returned <tt>Renderer</tt>
     * @param trackControl the <tt>TrackControl</tt> which represents the media
     * to be played back (and, technically, on which the initialized/returned
     * <tt>Renderer</tt> is to be set)
     * @return the <tt>Renderer</tt> which is to be set on the specified
     * <tt>trackControl</tt>. If <tt>null</tt>,
     * {@link TrackControl#setRenderer(Renderer)} is not invoked on the
     * specified <tt>trackControl</tt>.
     */
    protected Renderer createRenderer(Player player, TrackControl trackControl)
    {
        return getDevice().createRenderer();
    }

    /**
     * Makes sure {@link #captureDevice} is disconnected.
     */
    private void disconnectCaptureDevice()
    {
        if (captureDevice != null)
        {
            /*
             * As reported by Carlos Alexandre, stopping before disconnecting
             * resolves a slow disconnect on Linux.
             */
            try
            {
                captureDevice.stop();
            }
            catch (IOException ioe)
            {
                /*
                 * We cannot do much about the exception because we're not
                 * really interested in the stopping but rather in calling
                 * DataSource#disconnect() anyway.
                 */
                logger
                    .error(
                        "Failed to properly stop captureDevice "
                            + captureDevice,
                        ioe);
            }

            captureDevice.disconnect();
            captureDeviceIsConnected = false;
        }
    }

    /**
     * Releases the resources allocated by the <tt>Player</tt>s of
     * {@link #playbacks} in the course of their execution and prepares them to
     * be garbage collected.
     */
    private void disposePlayer()
    {
        Lock writeLock = playbacksLock.writeLock();

        writeLock.lock();
        try
        {
            for (Playback playback : playbacks)
            {
                if (playback.player != null)
                {
                    disposePlayer(playback.player);
                    playback.player = null;
                }
            }
        }
        finally
        {
            writeLock.unlock();
        }
    }

    /**
     * Releases the resources allocated by a specific <tt>Player</tt> in the
     * course of its execution and prepares it to be garbage collected.
     *
     * @param player the <tt>Player</tt> to dispose of
     */
    protected void disposePlayer(Player player)
    {
        if (playerControllerListener != null)
            player.removeControllerListener(playerControllerListener);
        player.stop();
        player.deallocate();
        player.close();
    }

    /**
     * Finds the first <tt>Format</tt> instance in a specific list of
     * <tt>Format</tt>s which matches a specific <tt>Format</tt>. The
     * implementation considers a pair of <tt>Format</tt>s matching if they have
     * the same encoding.
     *
     * @param formats the array of <tt>Format</tt>s to be searched for a match
     * to the specified <tt>format</tt>
     * @param format the <tt>Format</tt> to search for a match in the specified
     * <tt>formats</tt>
     * @return the first element of <tt>formats</tt> which matches
     * <tt>format</tt> i.e. is of the same encoding
     */
    private static Format findFirstMatchingFormat(
            Format[] formats,
            Format format)
    {
        double formatSampleRate
            = (format instanceof AudioFormat)
                ? ((AudioFormat) format).getSampleRate()
                : Format.NOT_SPECIFIED;
        ParameterizedVideoFormat parameterizedVideoFormat
            = (format instanceof ParameterizedVideoFormat)
                ? (ParameterizedVideoFormat) format
                : null;

        for (Format match : formats)
        {
            if (match.isSameEncoding(format))
            {
                /*
                 * The encoding alone is, of course, not enough. For example,
                 * AudioFormats may have different sample rates (i.e. clock
                 * rates as we call them in MediaFormat).
                 */
                if (match instanceof AudioFormat)
                {
                    if (formatSampleRate != Format.NOT_SPECIFIED)
                    {
                        double matchSampleRate
                            = ((AudioFormat) match).getSampleRate();

                        if ((matchSampleRate != Format.NOT_SPECIFIED)
                                && (matchSampleRate != formatSampleRate))
                            continue;
                    }
                }
                else if (match instanceof ParameterizedVideoFormat)
                {
                    if (!((ParameterizedVideoFormat) match)
                            .formatParametersMatch(format))
                        continue;
                }
                else if (parameterizedVideoFormat != null)
                {
                    if (!parameterizedVideoFormat.formatParametersMatch(match))
                        continue;
                }
                return match;
            }
        }
        return null;
    }

    /**
     * Gets the <tt>DataSource</tt> that this instance uses to read captured
     * media from. If it does not exist yet, it is created.
     *
     * @return the <tt>DataSource</tt> that this instance uses to read captured
     * media from
     */
    public synchronized DataSource getCaptureDevice()
    {
        if (captureDevice == null)
            captureDevice = createCaptureDevice();
        return captureDevice;
    }

    /**
     * Gets {@link #captureDevice} in a connected state. If this instance is not
     * connected to <tt>captureDevice</tt> yet, first tries to connect to it.
     * Returns <tt>null</tt> if this instance fails to create
     * <tt>captureDevice</tt> or to connect to it.
     *
     * @return {@link #captureDevice} in a connected state; <tt>null</tt> if
     * this instance fails to create <tt>captureDevice</tt> or to connect to it
     */
    protected DataSource getConnectedCaptureDevice()
    {
        DataSource captureDevice = getCaptureDevice();

        if ((captureDevice != null) && !captureDeviceIsConnected)
        {
            /*
             * Give this instance a chance to set up an optimized media codec
             * chain by setting the output Format on the input CaptureDevice.
             */
            try
            {
                if (this.format != null)
                    setCaptureDeviceFormat(captureDevice, this.format);
            }
            catch (Throwable t)
            {
                logger.warn(
                        "Failed to setup an optimized media codec chain"
                            + " by setting the output Format"
                            + " on the input CaptureDevice",
                        t);
            }

            Throwable exception = null;

            try
            {
                getDevice().connect(captureDevice);
            }
            catch (IOException ioex)
            {
                exception = ioex;
            }

            if (exception == null)
                captureDeviceIsConnected = true;
            else
            {
                logger.error(
                        "Failed to connect to "
                            + MediaStreamImpl.toString(captureDevice),
                        exception);
                captureDevice = null;
            }
        }
        return captureDevice;
    }

    /**
     * Gets the <tt>MediaDevice</tt> associated with this instance and the work
     * of a <tt>MediaStream</tt> with which is represented by it.
     *
     * @return the <tt>MediaDevice</tt> associated with this instance and the
     * work of a <tt>MediaStream</tt> with which is represented by it
     */
    public AbstractMediaDevice getDevice()
    {
        return device;
    }

    /**
     * Gets the JMF <tt>Format</tt> in which this instance captures media.
     *
     * @return the JMF <tt>Format</tt> in which this instance captures media.
     */
    public Format getProcessorFormat()
    {
        Processor processor = getProcessor();

        if ((processor != null)
                && (this.processor == processor)
                && !processorIsPrematurelyClosed)
        {
            MediaType mediaType = getMediaType();

            for (TrackControl trackControl : processor.getTrackControls())
            {
                if (!trackControl.isEnabled())
                    continue;

                Format jmfFormat = trackControl.getFormat();
                MediaType type
                    = (jmfFormat instanceof VideoFormat)
                        ? MediaType.VIDEO
                        : MediaType.AUDIO;

                if (mediaType.equals(type))
                    return jmfFormat;
            }
        }
        return null;
    }

    /**
     * Gets the <tt>MediaFormat</tt> in which this instance captures media from
     * its associated <tt>MediaDevice</tt>.
     *
     * @return the <tt>MediaFormat</tt> in which this instance captures media
     * from its associated <tt>MediaDevice</tt>
     */
    public MediaFormatImpl<? extends Format> getFormat()
    {
        /*
         * If the Format of the processor is different than the format of this
         * MediaDeviceSession, we'll likely run into unexpected issues so debug
         * whether there are such cases.
         */
        if (logger.isDebugEnabled() && (processor != null))
        {
            Format processorFormat = getProcessorFormat();
            Format format
                = (this.format == null) ? null : this.format.getFormat();
            boolean processorFormatMatchesFormat
                = (processorFormat == null)
                    ? (format == null)
                    : processorFormat.matches(format);

            if (!processorFormatMatchesFormat)
            {
                logger.debug(
                        "processorFormat != format; processorFormat= `"
                            + processorFormat + "`; format= `" + format + "`");
            }
        }

        return format;
    }

    /**
     * Gets the <tt>MediaType</tt> of the media captured and played back by this
     * instance. It is the same as the <tt>MediaType</tt> of its associated
     * <tt>MediaDevice</tt>.
     *
     * @return the <tt>MediaType</tt> of the media captured and played back by
     * this instance as reported by {@link MediaDevice#getMediaType()} of its
     * associated <tt>MediaDevice</tt>
     */
    private MediaType getMediaType()
    {
        return getDevice().getMediaType();
    }

    /**
     * Gets the output <tt>DataSource</tt> of this instance which provides the
     * captured (RTP) data to be sent by <tt>MediaStream</tt> to
     * <tt>MediaStreamTarget</tt>.
     *
     * @return the output <tt>DataSource</tt> of this instance which provides
     * the captured (RTP) data to be sent by <tt>MediaStream</tt> to
     * <tt>MediaStreamTarget</tt>
     */
    public DataSource getOutputDataSource()
    {
        Processor processor = getProcessor();
        DataSource outputDataSource;

        if ((processor == null)
                || ((processor.getState() < Processor.Realized)
                        && !waitForState(processor, Processor.Realized)))
            outputDataSource = null;
        else
        {
            outputDataSource = processor.getDataOutput();
            if (logger.isTraceEnabled() && (outputDataSource != null))
            {
                logger.trace(
                        "Processor with hashCode "
                            + processor.hashCode()
                            + " provided "
                            + MediaStreamImpl.toString(outputDataSource));
            }

            /*
             * Whoever wants the outputDataSource, they expect it to be started
             * in accord with the previously-set direction.
             */
            startProcessorInAccordWithDirection(processor);
        }
        return outputDataSource;
    }

    /**
     * Gets the information related to the playback of a specific
     * <tt>DataSource</tt> on the <tt>MediaDevice</tt> represented by this
     * <tt>MediaDeviceSession</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> to get the information related
     * to the playback of
     * @return the information related to the playback of the specified
     * <tt>DataSource</tt> on the <tt>MediaDevice</tt> represented by this
     * <tt>MediaDeviceSession</tt>
     */
    private Playback getPlayback(DataSource dataSource)
    {
        Lock readLock = playbacksLock.readLock();

        readLock.lock();
        try
        {
            for (Playback playback : playbacks)
            {
                if (playback.dataSource == dataSource)
                    return playback;
            }
        }
        finally
        {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the information related to the playback of a specific
     * <tt>ReceiveStream</tt> on the <tt>MediaDevice</tt> represented by this
     * <tt>MediaDeviceSession</tt>.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> to get the information
     * related to the playback of
     * @return the information related to the playback of the specified
     * <tt>ReceiveStream</tt> on the <tt>MediaDevice</tt> represented by this
     * <tt>MediaDeviceSession</tt>
     */
    private Playback getPlayback(ReceiveStream receiveStream)
    {
        Lock readLock = playbacksLock.readLock();

        readLock.lock();
        try
        {
            for (Playback playback : playbacks)
            {
                if (playback.receiveStream == receiveStream)
                    return playback;
            }
        }
        finally
        {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the <tt>Player</tt> rendering the <tt>ReceiveStream</tt> with a
     * specific SSRC.
     *
     * @param ssrc the SSRC of the <tt>ReceiveStream</tt> to get the rendering
     * the <tt>Player</tt> of
     * @return the <tt>Player</tt> rendering the <tt>ReceiveStream</tt> with the
     * specified <tt>ssrc</tt>
     */
    protected Player getPlayer(long ssrc)
    {
        Lock readLock = playbacksLock.readLock();

        readLock.lock();
        try
        {
            for (Playback playback : playbacks)
            {
                long playbackSSRC
                    = 0xFFFFFFFFL & playback.receiveStream.getSSRC();

                if (playbackSSRC == ssrc)
                    return playback.player;
            }
        }
        finally
        {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the <tt>Player</tt>s rendering the <tt>ReceiveStream</tt>s of this
     * instance on its associated <tt>MediaDevice</tt>.
     *
     * @return the <tt>Player</tt>s rendering the <tt>ReceiveStream</tt>s of
     * this instance on its associated <tt>MediaDevice</tt>
     */
    protected List<Player> getPlayers()
    {
        Lock readLock = playbacksLock.readLock();
        List<Player> players;

        readLock.lock();
        try
        {
            players = new ArrayList<Player>(playbacks.size());
            for (Playback playback : playbacks)
            {
                if (playback.player != null)
                    players.add(playback.player);
            }
        }
        finally
        {
            readLock.unlock();
        }
        return players;
    }

    /**
     * Gets the JMF <tt>Processor</tt> which transcodes the <tt>MediaDevice</tt>
     * of this instance into the format of this instance. If the
     * <tt>Processor</tt> in question does not exist, the method will create it.
     * <p>
     * <b>Warning</b>: Because the method will unconditionally create the
     * <tt>Processor</tt> if it does not exist and because the creation of the
     * <tt>Processor</tt> will connect to the <tt>CaptureDevice</tt> of this
     * instance, extreme care is to be taken when invoking the method in order
     * to ensure that the existence of the <tt>Processor</tt> is really in
     * accord with the rest of the state of this instance. Overall, the method
     * is to be considered private and is to not be invoked outside the
     * <tt>MediaDeviceSession</tt> class.
     * </p>
     *
     * @return the JMF <tt>Processor</tt> which transcodes the
     * <tt>MediaDevice</tt> of this instance into the format of this instance
     */
    private Processor getProcessor()
    {
        if (processor == null)
            processor = createProcessor();
        return processor;
    }

    /**
     * Gets a list of the <tt>ReceiveStream</tt>s being played back on the
     * <tt>MediaDevice</tt> represented by this instance.
     *
     * @return a list of <tt>ReceiveStream</tt>s being played back on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    public List<ReceiveStream> getReceiveStreams()
    {
        Lock readLock = playbacksLock.readLock();
        List<ReceiveStream> receiveStreams;

        readLock.lock();
        try
        {
            receiveStreams = new ArrayList<ReceiveStream>(playbacks.size());
            for (Playback playback : playbacks)
            {
                if (playback.receiveStream != null)
                    receiveStreams.add(playback.receiveStream);
            }
        }
        finally
        {
            readLock.unlock();
        }
        return receiveStreams;
    }

    /**
     * Returns the list of SSRC identifiers that this device session is handling
     * streams from. In this case (i.e. the case of a device session handling
     * a single remote party) we would rarely (if ever) have more than a single
     * SSRC identifier returned. However, we would also be using the same method
     * to query a device session operating over a mixer in which case we would
     * have the SSRC IDs of all parties currently contributing to the mixing.
     *
     * @return a <tt>long[]</tt> array of SSRC identifiers that this device
     * session is handling streams from.
     */
    public long[] getRemoteSSRCList()
    {
        return ssrcList;
    }

    /**
     * Gets the <tt>MediaDirection</tt> in which this instance has been started.
     * For example, a <tt>MediaDirection</tt> which returns <tt>true</tt> for
     * <tt>allowsSending()</tt> signals that this instance is capturing media
     * from its <tt>MediaDevice</tt>.
     *
     * @return the <tt>MediaDirection</tt> in which this instance has been
     * started
     */
    public MediaDirection getStartedDirection()
    {
        return startedDirection;
    }

    /**
     * Gets a list of the <tt>MediaFormat</tt>s in which this instance is
     * capable of capturing media from its associated <tt>MediaDevice</tt>.
     *
     * @return a new list of <tt>MediaFormat</tt>s in which this instance is
     * capable of capturing media from its associated <tt>MediaDevice</tt>
     */
    public List<MediaFormat> getSupportedFormats()
    {
        Processor processor = getProcessor();
        Set<Format> supportedFormats = new HashSet<Format>();

        if ((processor != null)
                && (this.processor == processor)
                && !processorIsPrematurelyClosed)
        {
            MediaType mediaType = getMediaType();

            for (TrackControl trackControl : processor.getTrackControls())
            {
                if (!trackControl.isEnabled())
                    continue;

                for (Format supportedFormat
                        : trackControl.getSupportedFormats())
                {
                    switch (mediaType)
                    {
                    case AUDIO:
                        if (supportedFormat instanceof AudioFormat)
                            supportedFormats.add(supportedFormat);
                        break;
                    case VIDEO:
                        if (supportedFormat instanceof VideoFormat)
                            supportedFormats.add(supportedFormat);
                        break;
                    default:
                        // FMJ and LibJitsi handle audio and video only and it
                        // seems unlikely that support for any other types of
                        // media will be added here.
                        break;
                    }
                }
            }
        }

        List<MediaFormat> supportedMediaFormats
            = new ArrayList<MediaFormat>(supportedFormats.size());

        for (Format format : supportedFormats)
            supportedMediaFormats.add(MediaFormatImpl.createInstance(format));
        return supportedMediaFormats;
    }

    /**
     * Determines whether this <tt>MediaDeviceSession</tt> is set to output
     * "silence" instead of the actual media fed from its
     * <tt>CaptureDevice</tt>.
     *
     * @return <tt>true</tt> if this <tt>MediaDeviceSession</tt> is set to
     * output "silence" instead of the actual media fed from its
     * <tt>CaptureDevice</tt>; otherwise, <tt>false</tt>
     */
    public boolean isMute()
    {
        DataSource captureDevice = this.captureDevice;

        if (captureDevice == null)
            return mute;

        MuteDataSource muteDataSource
            = AbstractControls.queryInterface(
                    captureDevice,
                    MuteDataSource.class);

        return (muteDataSource == null) ? false : muteDataSource.isMute();
    }

    /**
     * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt> has
     * been added for playback on the represented <tt>MediaDevice</tt>.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which has been added
     * for playback on the represented <tt>MediaDevice</tt>
     */
    protected void playbackDataSourceAdded(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt> has
     * been removed from playback on the represented <tt>MediaDevice</tt>.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which has been removed
     * from playback on the represented <tt>MediaDevice</tt>
     */
    protected void playbackDataSourceRemoved(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt> has
     * been changed on the represented <tt>MediaDevice</tt>.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which has been added
     * for playback on the represented <tt>MediaDevice</tt>
     */
    protected void playbackDataSourceUpdated(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt> has
     * been changed on the represented <tt>MediaDevice</tt>.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which has been added
     * for playback on the represented <tt>MediaDevice</tt>
     */
    public void playbackDataSourceChanged(DataSource playbackDataSource)
    {
        playbackDataSourceUpdated(playbackDataSource);
    }

    /**
     * Notifies this instance that a specific <tt>Player</tt> of remote content
     * has generated a <tt>ConfigureCompleteEvent</tt>. Allows extenders to
     * carry out additional processing on the <tt>Player</tt>.
     *
     * @param player the <tt>Player</tt> which is the source of a
     * <tt>ConfigureCompleteEvent</tt>
     */
    protected void playerConfigureComplete(Processor player)
    {
        TrackControl[] tcs = player.getTrackControls();

        if ((tcs != null) && (tcs.length != 0))
        {
            for (int i = 0; i < tcs.length; i++)
            {
                TrackControl tc = tcs[i];
                Renderer renderer = createRenderer(player, tc);

                if (renderer != null)
                {
                    try
                    {
                        tc.setRenderer(renderer);
                    }
                    catch (UnsupportedPlugInException upie)
                    {
                        logger.warn(
                                "Failed to set " + renderer.getClass().getName()
                                    + " renderer on track " + i,
                                upie);
                    }
                }
            }
        }
    }

    /**
     * Gets notified about <tt>ControllerEvent</tt>s generated by a specific
     * <tt>Player</tt> of remote content.
     * <p>
     * Extenders who choose to override are advised to override more specialized
     * methods such as {@link #playerConfigureComplete(Processor)} and
     * {@link #playerRealizeComplete(Processor)}. In any case, extenders
     * overriding this method should call the super implementation.
     * </p>
     *
     * @param ev the <tt>ControllerEvent</tt> specifying the
     * <tt>Controller</tt> which is the source of the event and the very type of
     * the event
     */
    protected void playerControllerUpdate(ControllerEvent ev)
    {
        if (ev instanceof ConfigureCompleteEvent)
        {
            Processor player = (Processor) ev.getSourceController();

            if (player != null)
            {
                playerConfigureComplete(player);

                /*
                 * To use the processor as a Player we must set its
                 * ContentDescriptor to null.
                 */
                try
                {
                    player.setContentDescriptor(null);
                }
                catch (NotConfiguredError nce)
                {
                    logger.error(
                            "Failed to set ContentDescriptor to Player.",
                            nce);
                    return;
                }

                player.realize();
            }
        }
        else if (ev instanceof RealizeCompleteEvent)
        {
            Processor player = (Processor) ev.getSourceController();

            if (player != null)
            {
                playerRealizeComplete(player);

                player.start();
            }
        }
    }

    /**
     * Notifies this instance that a specific <tt>Player</tt> of remote content
     * has generated a <tt>RealizeCompleteEvent</tt>. Allows extenders to carry
     * out additional processing on the <tt>Player</tt>.
     *
     * @param player the <tt>Player</tt> which is the source of a
     * <tt>RealizeCompleteEvent</tt>
     */
    protected void playerRealizeComplete(Processor player)
    {
    }

    /**
     * Gets notified about <tt>ControllerEvent</tt>s generated by
     * {@link #processor}.
     *
     * @param ev the <tt>ControllerEvent</tt> specifying the
     * <tt>Controller</tt> which is the source of the event and the very type of
     * the event
     */
    protected void processorControllerUpdate(ControllerEvent ev)
    {
        if (ev instanceof ConfigureCompleteEvent)
        {
            Processor processor = (Processor) ev.getSourceController();

            if (processor != null)
            {
                try
                {
                    processor.setContentDescriptor(
                            createProcessorContentDescriptor(processor));
                }
                catch (NotConfiguredError nce)
                {
                    logger.error(
                            "Failed to set ContentDescriptor to Processor.",
                            nce);
                }

                if (format != null)
                    setProcessorFormat(processor, format);
            }
        }
        else if (ev instanceof ControllerClosedEvent)
        {
            Processor processor = (Processor) ev.getSourceController();

            /*
             * If everything goes according to plan, we should've removed the
             * ControllerListener from the processor by now.
             */
            logger.warn(ev);

            // TODO Should the access to processor be synchronized?
            if ((processor != null) && (this.processor == processor))
                processorIsPrematurelyClosed = true;
        }
        else if (ev instanceof RealizeCompleteEvent)
        {
            Processor processor = (Processor) ev.getSourceController();

            for (FormatParametersAwareCodec fpac
                    : getAllTrackControls(
                            FormatParametersAwareCodec.class,
                            processor))
            {
                Map<String, String> formatParameters
                        = format == null
                        ? null
                        : format.getFormatParameters();
                if (formatParameters != null)
                    fpac.setFormatParameters(formatParameters);
            }
            for (AdvancedAttributesAwareCodec aaac
                    : getAllTrackControls(
                            AdvancedAttributesAwareCodec.class,
                            processor))
            {
                Map<String, String> advanceAttrs
                        = format == null
                        ? null
                        : format.getAdvancedAttributes();
                if (advanceAttrs != null)
                    aaac.setAdvancedAttributes(advanceAttrs);
            }
        }
    }

    /**
     * Removes <tt>ssrc</tt> from the array of SSRC identifiers representing
     * parties that this <tt>MediaDeviceSession</tt> is currently receiving
     * streams from.
     *
     * @param ssrc the SSRC identifier that we'd like to remove from the array
     * of <tt>ssrc</tt> identifiers stored by this session.
     */
    protected void removeSSRC(long ssrc)
    {
        //find the ssrc
        int index = -1;

        if (ssrcList == null || ssrcList.length == 0)
        {
            //list is already empty so there's nothing to do.
            return;
        }

        for (int i = 0; i < ssrcList.length; i++)
        {
            if (ssrcList[i] == ssrc)
            {
                index = i;
                break;
            }
        }

        if (index < 0 || index >= ssrcList.length)
        {
            //the ssrc we are trying to remove is not in the list so there's
            //nothing to do.
            return;
        }

        //if we get here and the list has a single element this would mean we
        //simply need to empty it as the only element is the one we are removing
        if (ssrcList.length == 1)
        {
            setSsrcList(null);
            return;
        }

        long[] newSsrcList = new long[ssrcList.length - 1];

        System.arraycopy(ssrcList, 0, newSsrcList, 0, index);
        if (index < ssrcList.length - 1)
        {
            System.arraycopy(ssrcList,    index + 1,
                             newSsrcList, index,
                             ssrcList.length - index - 1);
        }

        setSsrcList(newSsrcList);
    }

    /**
     * Notifies this instance that a specific <tt>ReceiveStream</tt> has been
     * added to the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which has been added to
     * the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    protected void receiveStreamAdded(ReceiveStream receiveStream)
    {
    }

    /**
     * Notifies this instance that a specific <tt>ReceiveStream</tt> has been
     * removed from the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which has been removed
     * from the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    protected void receiveStreamRemoved(ReceiveStream receiveStream)
    {
    }

    protected void setCaptureDeviceFormat(
            DataSource captureDevice,
            MediaFormatImpl<? extends Format> mediaFormat)
    {
        Format format = mediaFormat.getFormat();

        if (format instanceof AudioFormat)
        {
            AudioFormat audioFormat = (AudioFormat) format;
            int channels = audioFormat.getChannels();
            double sampleRate
                = OSUtils.IS_ANDROID
                    ? audioFormat.getSampleRate()
                    : Format.NOT_SPECIFIED;

            if ((channels != Format.NOT_SPECIFIED)
                    || (sampleRate != Format.NOT_SPECIFIED))
            {
                FormatControl formatControl
                    = (FormatControl)
                        captureDevice.getControl(
                                FormatControl.class.getName());

                if (formatControl != null)
                {
                    Format[] supportedFormats
                        = formatControl.getSupportedFormats();

                    if ((supportedFormats != null)
                            && (supportedFormats.length != 0))
                    {
                        if (sampleRate != Format.NOT_SPECIFIED)
                        {
                            /*
                             * As per RFC 3551.4.5.2, because of a mistake in
                             * RFC 1890 and for backward compatibility, G.722
                             * should always be announced as 8000 even though it
                             * is wideband.
                             */
                            String encoding = audioFormat.getEncoding();

                            if ((Constants.G722.equalsIgnoreCase(encoding)
                                        || Constants.G722_RTP.equalsIgnoreCase(
                                                encoding))
                                    && (sampleRate == 8000))
                            {
                                sampleRate = 16000;
                            }
                        }

                        Format supportedAudioFormat = null;

                        for (int i = 0; i < supportedFormats.length; i++)
                        {
                            Format sf = supportedFormats[i];

                            if (sf instanceof AudioFormat)
                            {
                                AudioFormat saf = (AudioFormat) sf;

                                if ((Format.NOT_SPECIFIED != channels)
                                        && (saf.getChannels() != channels))
                                    continue;
                                if ((Format.NOT_SPECIFIED != sampleRate)
                                        && (saf.getSampleRate() != sampleRate))
                                    continue;

                                supportedAudioFormat = saf;
                                break;
                            }
                        }

                        if (supportedAudioFormat != null)
                            formatControl.setFormat(supportedAudioFormat);
                    }
                }
            }
        }
    }

    /**
     * Sets the <tt>ContentDescriptor</tt> which specifies the content type in
     * which this <tt>MediaDeviceSession</tt> is to output the media captured by
     * its <tt>MediaDevice</tt>. The default content type in which
     * <tt>MediaDeviceSession</tt> outputs the media captured by its
     * <tt>MediaDevice</tt> is {@link ContentDescriptor#RAW_RTP}.
     *
     * @param contentDescriptor the <tt>ContentDescriptor</tt> which specifies
     * the content type in which this <tt>MediaDeviceSession</tt> is to output
     * the media captured by its <tt>MediaDevice</tt>
     */
    public void setContentDescriptor(ContentDescriptor contentDescriptor)
    {
        if (contentDescriptor == null)
            throw new NullPointerException("contentDescriptor");

        this.contentDescriptor = contentDescriptor;
    }

    /**
     * Sets the <tt>MediaFormat</tt> in which this <tt>MediaDeviceSession</tt>
     * outputs the media captured by its <tt>MediaDevice</tt>.
     *
     * @param format the <tt>MediaFormat</tt> in which this
     * <tt>MediaDeviceSession</tt> is to output the media captured by its
     * <tt>MediaDevice</tt>
     */
    public void setFormat(MediaFormat format)
    {
        if (!getMediaType().equals(format.getMediaType()))
            throw new IllegalArgumentException("format");

        /*
         * We need javax.media.Format and we know how to convert MediaFormat to
         * it only for MediaFormatImpl so assert early.
         */
        @SuppressWarnings("unchecked")
        MediaFormatImpl<? extends Format> mediaFormatImpl
            = (MediaFormatImpl<? extends Format>) format;

        this.format = mediaFormatImpl;
        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Set format " + this.format
                        + " on "
                        + getClass().getSimpleName() + " " + hashCode());
        }

        /*
         * If the processor is after Configured, setting a different format will
         * silently fail. Recreate the processor in order to be able to set the
         * different format.
         */
        if (processor != null)
        {
            int processorState = processor.getState();

            if (processorState == Processor.Configured)
                setProcessorFormat(processor, this.format);
            else if (processorIsPrematurelyClosed
                        || ((processorState > Processor.Configured)
                                && !this.format.getFormat().equals(
                                        getProcessorFormat()))
                        || outputSizeChanged)
            {
                outputSizeChanged = false;
                setProcessor(null);
            }
        }
    }

    /**
     * Sets the <tt>MediaFormatImpl</tt> in which a specific <tt>Processor</tt>
     * producing media to be streamed to the remote peer is to output.
     *
     * @param processor the <tt>Processor</tt> to set the output
     * <tt>MediaFormatImpl</tt> of
     * @param mediaFormat the <tt>MediaFormatImpl</tt> to set on
     * <tt>processor</tt>
     */
    protected void setProcessorFormat(
            Processor processor,
            MediaFormatImpl<? extends Format> mediaFormat)
    {
        TrackControl[] trackControls = processor.getTrackControls();
        MediaType mediaType = getMediaType();
        Format format = mediaFormat.getFormat();

        for (int trackIndex = 0;
                trackIndex < trackControls.length;
                trackIndex++)
        {
            TrackControl trackControl = trackControls[trackIndex];

            if (!trackControl.isEnabled())
                continue;

            Format[] supportedFormats = trackControl.getSupportedFormats();

            if ((supportedFormats == null) || (supportedFormats.length < 1))
            {
                trackControl.setEnabled(false);
                continue;
            }

            Format supportedFormat = null;

            switch (mediaType)
            {
            case AUDIO:
                if (supportedFormats[0] instanceof AudioFormat)
                {
                    supportedFormat
                        = findFirstMatchingFormat(supportedFormats, format);

                    /*
                     * We've failed to find a supported format so try to use
                     * whatever we've been told and, if it fails, the caller
                     * will at least know why.
                     */
                    if (supportedFormat == null)
                        supportedFormat = format;
                }
                break;
            case VIDEO:
                if (supportedFormats[0] instanceof VideoFormat)
                {
                    supportedFormat
                        = findFirstMatchingFormat(supportedFormats, format);

                    /*
                     * We've failed to find a supported format so try to use
                     * whatever we've been told and, if it fails, the caller
                     * will at least know why.
                     */
                    if (supportedFormat == null)
                        supportedFormat = format;
                    if (supportedFormat != null)
                        supportedFormat
                            = assertSize((VideoFormat) supportedFormat);
                }
                break;
            default:
                // FMJ and LibJitsi handle audio and video only and it seems
                // unlikely that support for any other types of media will be
                // added here.
                break;
            }

            if (supportedFormat == null)
                trackControl.setEnabled(false);
            else if (!supportedFormat.equals(trackControl.getFormat()))
            {
                Format setFormat
                    = setProcessorFormat(
                            trackControl,
                            mediaFormat, supportedFormat);

                if (setFormat == null)
                    logger.error(
                            "Failed to set format of track "
                                + trackIndex
                                + " to "
                                + supportedFormat
                                + ". Processor is in state "
                                + processor.getState());
                else if (setFormat != supportedFormat)
                    logger.warn(
                            "Failed to change format of track "
                                + trackIndex
                                + " from "
                                + setFormat
                                + " to "
                                + supportedFormat
                                + ". Processor is in state "
                                + processor.getState());
                else if (logger.isTraceEnabled())
                    logger.trace(
                            "Set format of track "
                                + trackIndex
                                + " to "
                                + setFormat);
            }
        }
    }

    /**
     * Sets the <tt>MediaFormatImpl</tt> of a specific <tt>TrackControl</tt> of
     * the <tt>Processor</tt> which produces the media to be streamed by this
     * <tt>MediaDeviceSession</tt> to the remote peer. Allows extenders to
     * override the set procedure and to detect when the JMF <tt>Format</tt> of
     * the specified <tt>TrackControl</tt> changes.
     *
     * @param trackControl the <tt>TrackControl</tt> to set the JMF
     * <tt>Format</tt> of
     * @param mediaFormat the <tt>MediaFormatImpl</tt> to be set on the
     * specified <tt>TrackControl</tt>. Though <tt>mediaFormat</tt> encapsulates
     * a JMF <tt>Format</tt>, <tt>format</tt> is to be set on the specified
     * <tt>trackControl</tt> because it may be more specific. In any case, the
     * two JMF <tt>Format</tt>s match. The <tt>MediaFormatImpl</tt> is provided
     * anyway because it carries additional information such as format
     * parameters.
     * @param format the JMF <tt>Format</tt> to be set on the specified
     * <tt>TrackControl</tt>. Though <tt>mediaFormat</tt> encapsulates a JMF
     * <tt>Format</tt>, the specified <tt>format</tt> is to be set on the
     * specified <tt>trackControl</tt> because it may be more specific than the
     * JMF <tt>Format</tt> of the <tt>mediaFormat</tt>
     * @return the JMF <tt>Format</tt> set on <tt>TrackControl</tt> after the
     * attempt to set the specified <tt>format</tt> or <tt>null</tt> if the
     * specified <tt>format</tt> was found to be incompatible with
     * <tt>trackControl</tt>
     */
    protected Format setProcessorFormat(
            TrackControl trackControl,
            MediaFormatImpl<? extends Format> mediaFormat,
            Format format)
    {
        return trackControl.setFormat(format);
    }

    /**
     * Sets the indicator which determines whether this
     * <tt>MediaDeviceSession</tt> is set to output "silence" instead of the
     * actual media fed from its <tt>CaptureDevice</tt>.
     *
     * @param mute <tt>true</tt> to set this <tt>MediaDeviceSession</tt> to
     * output "silence" instead of the actual media fed from its
     * <tt>CaptureDevice</tt>; otherwise, <tt>false</tt>
     */
    public void setMute(boolean mute)
    {
        if (this.mute != mute)
        {
            this.mute = mute;

            MuteDataSource muteDataSource
                = AbstractControls.queryInterface(
                        captureDevice,
                        MuteDataSource.class);

            if (muteDataSource != null)
                muteDataSource.setMute(this.mute);
        }
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone)
    {
        InbandDTMFDataSource inbandDTMFDataSource
            = AbstractControls.queryInterface(
                    captureDevice,
                    InbandDTMFDataSource.class);

        if (inbandDTMFDataSource != null)
            inbandDTMFDataSource.addDTMF(tone);
    }

    /**
     * Adds a specific <tt>DataSource</tt> to the list of playbacks of
     * <tt>ReceiveStream</tt>s and/or <tt>DataSource</tt>s performed by
     * respective <tt>Player</tt>s on the <tt>MediaDevice</tt> represented by
     * this instance.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which to be added to
     * the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    public void addPlaybackDataSource(DataSource playbackDataSource)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean added = false;

        writeLock.lock();
        try
        {
            Playback playback = getPlayback(playbackDataSource);

            if (playback == null)
            {
                if (playbackDataSource
                        instanceof ReceiveStreamPushBufferDataSource)
                {
                    ReceiveStream receiveStream
                        = ((ReceiveStreamPushBufferDataSource)
                                playbackDataSource)
                            .getReceiveStream();

                    playback = getPlayback(receiveStream);
                }
                if (playback == null)
                {
                    playback = new Playback(playbackDataSource);
                    playbacks.add(playback);
                }
                else
                    playback.dataSource = playbackDataSource;

                playback.player = createPlayer(playbackDataSource);

                readLock.lock();
                added = true;
            }
        }
        finally
        {
            writeLock.unlock();
        }
        if (added)
        {
            try
            {
                playbackDataSourceAdded(playbackDataSource);
            }
            finally
            {
                readLock.unlock();
            }
        }
    }

    /**
     * Removes a specific <tt>DataSource</tt> from the list of playbacks of
     * <tt>ReceiveStream</tt>s and/or <tt>DataSource</tt>s performed by
     * respective <tt>Player</tt>s on the <tt>MediaDevice</tt> represented by
     * this instance.
     *
     * @param playbackDataSource the <tt>DataSource</tt> which to be removed
     * from the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    public void removePlaybackDataSource(DataSource playbackDataSource)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean removed = false;

        writeLock.lock();
        try
        {
            Playback playback = getPlayback(playbackDataSource);

            if (playback != null)
            {
                if (playback.player != null)
                {
                    disposePlayer(playback.player);
                    playback.player = null;
                }

                playback.dataSource = null;
                if (playback.receiveStream == null)
                    playbacks.remove(playback);

                readLock.lock();
                removed = true;
            }
        }
        finally
        {
            writeLock.unlock();
        }
        if (removed)
        {
            try
            {
                playbackDataSourceRemoved(playbackDataSource);
            }
            finally
            {
                readLock.unlock();
            }
        }
    }

    /**
     * Sets the JMF <tt>Processor</tt> which is to transcode
     * {@link #captureDevice} into the format of this instance.
     *
     * @param processor the JMF <tt>Processor</tt> which is to transcode
     * {@link #captureDevice} into the format of this instance
     */
    private void setProcessor(Processor processor)
    {
        if (this.processor != processor)
        {
            closeProcessor();

            this.processor = processor;

            /*
             * Since the processor has changed, its output DataSource known to
             * the public has also changed.
             */
            firePropertyChange(OUTPUT_DATA_SOURCE, null, null);
        }
    }

    /**
     * Adds a specific <tt>ReceiveStream</tt> to the list of playbacks of
     * <tt>ReceiveStream</tt>s and/or <tt>DataSource</tt>s performed by
     * respective <tt>Player</tt>s on the <tt>MediaDevice</tt> represented by
     * this instance.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which to be added to the
     * list of playbacks of <tt>ReceiveStream</tt>s and/or <tt>DataSource</tt>s
     * performed by respective <tt>Player</tt>s on the <tt>MediaDevice</tt>
     * represented by this instance
     */
    public void addReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean added = false;

        writeLock.lock();
        try
        {
            if (getPlayback(receiveStream) == null)
            {
                playbacks.add(new Playback(receiveStream));

                addSSRC(0xFFFFFFFFL & receiveStream.getSSRC());

                // playbackDataSource
                DataSource receiveStreamDataSource
                    = receiveStream.getDataSource();

                if (receiveStreamDataSource != null)
                {
                    if (receiveStreamDataSource instanceof PushBufferDataSource)
                    {
                        receiveStreamDataSource
                            = new ReceiveStreamPushBufferDataSource(
                                    receiveStream,
                                    (PushBufferDataSource)
                                        receiveStreamDataSource,
                                    true);
                    }
                    else
                    {
                        logger.warn(
                                "Adding ReceiveStream with DataSource not of"
                                    + " type PushBufferDataSource but "
                                    + receiveStreamDataSource.getClass()
                                            .getSimpleName()
                                    + " which may prevent the ReceiveStream"
                                    + " from properly transferring to another"
                                    + " MediaDevice if such a need arises.");
                    }
                    addPlaybackDataSource(receiveStreamDataSource);
                }

                readLock.lock();
                added = true;
            }
        }
        finally
        {
            writeLock.unlock();
        }
        if (added)
        {
            try
            {
                receiveStreamAdded(receiveStream);
            }
            finally
            {
                readLock.unlock();
            }
        }
    }

    /**
     * Removes a specific <tt>ReceiveStream</tt> from the list of playbacks of
     * <tt>ReceiveStream</tt>s and/or <tt>DataSource</tt>s performed by
     * respective <tt>Player</tt>s on the <tt>MediaDevice</tt> represented by
     * this instance.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which to be removed from
     * the list of playbacks of <tt>ReceiveStream</tt>s and/or
     * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
     * <tt>MediaDevice</tt> represented by this instance
     */
    public void removeReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean removed = false;

        writeLock.lock();
        try
        {
            Playback playback = getPlayback(receiveStream);

            if (playback != null)
            {
                removeSSRC(0xFFFFFFFFL & receiveStream.getSSRC());
                if (playback.dataSource != null)
                    removePlaybackDataSource(playback.dataSource);

                if (playback.dataSource != null)
                {
                    logger.warn(
                            "Removing ReceiveStream with an associated"
                                + " DataSource.");
                }
                playbacks.remove(playback);

                readLock.lock();
                removed = true;
            }
        }
        finally
        {
            writeLock.unlock();
        }
        if (removed)
        {
            try
            {
                receiveStreamRemoved(receiveStream);
            }
            finally
            {
                readLock.unlock();
            }
        }
    }

    /**
     * Sets the list of SSRC identifiers that this device stores to
     * <tt>newSsrcList</tt> and fires a <tt>PropertyChangeEvent</tt> for the
     * <tt>SSRC_LIST</tt> property.
     *
     * @param newSsrcList that SSRC array that we'd like to replace the existing
     * SSRC list with.
     */
    private void setSsrcList(long[] newSsrcList)
    {
        // use getRemoteSSRCList() instead of direct access to ssrcList
        // as the extender may override it
        long[] oldSsrcList = getRemoteSSRCList();

        ssrcList = newSsrcList;

        firePropertyChange(SSRC_LIST, oldSsrcList, getRemoteSSRCList());
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be started. For example,
     * {@link MediaDirection#SENDRECV} to start both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only start
     * the capture of media in this instance
     */
    public void start(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        MediaDirection oldValue = startedDirection;

        startedDirection = startedDirection.or(direction);
        if (!oldValue.equals(startedDirection))
            startedDirectionChanged(oldValue, startedDirection);
    }

    /**
     * Notifies this instance that the value of its <tt>startedDirection</tt>
     * property has changed from a specific <tt>oldValue</tt> to a specific
     * <tt>newValue</tt>. Allows extenders to override and perform additional
     * processing of the change. Overriding implementations must call this
     * implementation in order to ensure the proper execution of this
     * <tt>MediaDeviceSession</tt>.
     *
     * @param oldValue the <tt>MediaDirection</tt> which used to be the value of
     * the <tt>startedDirection</tt> property of this instance
     * @param newValue the <tt>MediaDirection</tt> which is the value of the
     * <tt>startedDirection</tt> property of this instance
     */
    protected void startedDirectionChanged(
            MediaDirection oldValue,
            MediaDirection newValue)
    {
        if (newValue.allowsSending())
        {
            Processor processor = getProcessor();

            if (processor != null)
                startProcessorInAccordWithDirection(processor);
        }
        else if ((processor != null)
                    && (processor.getState() > Processor.Configured))
        {
            processor.stop();
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Stopped Processor with hashCode "
                            + processor.hashCode());
            }
        }
    }

    /**
     * Starts a specific <tt>Processor</tt> if this <tt>MediaDeviceSession</tt>
     * has been started and the specified <tt>Processor</tt> is not started.
     *
     * @param processor the <tt>Processor</tt> to start
     */
    protected void startProcessorInAccordWithDirection(Processor processor)
    {
        if (startedDirection.allowsSending()
                && (processor.getState() != Processor.Started))
        {
            processor.start();
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Started Processor with hashCode "
                            + processor.hashCode());
            }
        }
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a <tt>MediaDirection</tt> value which represents the
     * direction of the processing of media to be stopped. For example,
     * {@link MediaDirection#SENDRECV} to stop both capture and playback of
     * media in this instance or {@link MediaDirection#SENDONLY} to only stop
     * the capture of media in this instance
     */
    public void stop(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        MediaDirection oldValue = startedDirection;

        switch (startedDirection)
        {
        case SENDRECV:
            if (direction.allowsReceiving())
                startedDirection
                    = direction.allowsSending()
                        ? MediaDirection.INACTIVE
                        : MediaDirection.SENDONLY;
            else if (direction.allowsSending())
                startedDirection = MediaDirection.RECVONLY;
            break;
        case SENDONLY:
            if (direction.allowsSending())
                startedDirection = MediaDirection.INACTIVE;
            break;
        case RECVONLY:
            if (direction.allowsReceiving())
                startedDirection = MediaDirection.INACTIVE;
            break;
        case INACTIVE:
            /*
             * This MediaDeviceSession is already inactive so there's nothing to
             * stop.
             */
            break;
        default:
            throw new IllegalArgumentException("direction");
        }

        if (!oldValue.equals(startedDirection))
            startedDirectionChanged(oldValue, startedDirection);
    }

    /**
     * Waits for the specified JMF <tt>Processor</tt> to enter the specified
     * <tt>state</tt> and returns <tt>true</tt> if <tt>processor</tt> has
     * successfully entered <tt>state</tt> or <tt>false</tt> if <tt>process</tt>
     * has failed to enter <tt>state</tt>.
     *
     * @param processor the JMF <tt>Processor</tt> to wait on
     * @param state the state as defined by the respective <tt>Processor</tt>
     * state constants to wait <tt>processor</tt> to enter
     * @return <tt>true</tt> if <tt>processor</tt> has successfully entered
     * <tt>state</tt>; otherwise, <tt>false</tt>
     */
    private static boolean waitForState(Processor processor, int state)
    {
        return new ProcessorUtility().waitForState(processor, state);
    }

    /**
     * Copies the playback part of a specific <tt>MediaDeviceSession</tt> into
     * this instance.
     *
     * @param deviceSession the <tt>MediaDeviceSession</tt> to copy the playback
     * part of into this instance
     */
    public void copyPlayback(MediaDeviceSession deviceSession)
    {
        if (deviceSession.disposePlayerOnClose)
        {
            logger.error(
                    "Cannot copy playback if MediaDeviceSession has closed it");
        }
        else
        {
            /*
             * TODO Technically, we should be synchronizing the (read and write)
             * accesses to the playbacks fields. In practice, we are not doing
             * it because it likely was the easiest to not bother with it at the
             * time of its writing.
             */
            playbacks.addAll(deviceSession.playbacks);
            setSsrcList(deviceSession.ssrcList);
        }
    }

    /**
     * Represents the information related to the playback of a
     * <tt>DataSource</tt> on the <tt>MediaDevice</tt> represented by a
     * <tt>MediaDeviceSession</tt>. The <tt>DataSource</tt> may have an
     * associated <tt>ReceiveStream</tt>.
     */
    private static class Playback
    {
        /**
         * The <tt>DataSource</tt> the information related to the playback of
         * which is represented by this instance and which is associated with
         * {@link #receiveStream}.
         */
        public DataSource dataSource;

        /**
         * The <tt>ReceiveStream</tt> the information related to the playback of
         * which is represented by this instance and which is associated with
         * {@link #dataSource}.
         */
        public ReceiveStream receiveStream;

        /**
         * The <tt>Player</tt> which performs the actual playback.
         */
        public Player player;

        /**
         * Initializes a new <tt>Playback</tt> instance which is to represent
         * the information related to the playback of a specific
         * <tt>DataSource</tt>.
         *
         * @param dataSource the <tt>DataSource</tt> the information related to
         * the playback of which is to be represented by the new instance
         */
        public Playback(DataSource dataSource)
        {
            this.dataSource = dataSource;
        }

        /**
         * Initializes a new <tt>Playback</tt> instance which is to represent
         * the information related to the playback of a specific
         * <tt>ReceiveStream</tt>.
         *
         * @param receiveStream the <tt>ReceiveStream</tt> the information
         * related to the playback of which is to be represented by the new
         * instance
         */
        public Playback(ReceiveStream receiveStream)
        {
            this.receiveStream = receiveStream;
        }
    }

    /**
     * Returns the <tt>TranscodingDataSource</tt> associated with
     * <tt>receiveStream</tt>.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> to use
     *
     * @return the <tt>TranscodingDataSource</tt> associated with
     * <tt>receiveStream</tt>.
     */
    public TranscodingDataSource getTranscodingDataSource(
                ReceiveStream receiveStream)
    {
        TranscodingDataSource transcodingDataSource = null;
        if(device instanceof AudioMixerMediaDevice)
        {
            transcodingDataSource = ((AudioMixerMediaDevice) device)
                    .getTranscodingDataSource(receiveStream.getDataSource());
        }
        return transcodingDataSource;
    }

    /**
     * Searches for controls of type <tt>controlType</tt> in the
     * <tt>TrackControl</tt>s of the <tt>Processor</tt> used to transcode the
     * <tt>MediaDevice</tt> of this instance into the format of this instance.
     * Returns a <tt>Set</tt> of instances of class <tt>controlType</tt>,
     * always non-null.
     *
     * @param controlType the name of the class to search for.
     * @return A non-null <tt>Set</tt> of all <tt>controlType</tt>s found.
     */
    public <T> Set<T> getEncoderControls(Class<T> controlType)
    {
        return getAllTrackControls(controlType, this.processor);
    }

    /**
     * Searches for controls of type <tt>controlType</tt> in the
     * <tt>TrackControl</tt>s of the <tt>Processor</tt> used to decode
     * <tt>receiveStream</tt>. Returns a <tt>Set</tt> of instances of class
     * <tt>controlType</tt>, always non-null.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> whose <tt>Processor</tt>'s
     * <tt>TrackControl</tt>s are to be searched.
     * @param controlType the name of the class to search for.
     * @return A non-null <tt>Set</tt> of all <tt>controlType</tt>s found.
     */
    public <T> Set<T> getDecoderControls(
            ReceiveStream receiveStream,
            Class<T> controlType)
    {
        TranscodingDataSource transcodingDataSource
            = getTranscodingDataSource(receiveStream);

        if (transcodingDataSource == null)
            return Collections.emptySet();
        else
        {
            return
                getAllTrackControls(
                        controlType,
                        transcodingDataSource.getTranscodingProcessor());
        }
    }

    /**
     * Returns the <tt>Set</tt> of controls of type <tt>controlType</tt>, which
     * are controls for some of <tt>processor</tt>'s <tt>TrackControl</tt>s.
     *
     * @param controlType the name of the class to search for.
     * @param processor the <tt>Processor</tt> whose <tt>TrackControls</tt>s
     * will be searched.
     * @return A non-null <tt>Set</tt> of all <tt>controlType</tt>s found.
     */
    private <T> Set<T> getAllTrackControls(
            Class<T> controlType,
            Processor processor)
    {
        Set<T> controls = null;

        if ((processor != null) && (processor.getState() >= Processor.Realized))
        {
            TrackControl[] trackControls = processor.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0))
            {
                String className = controlType.getName();

                for (TrackControl trackControl : trackControls)
                {
                    Object o = trackControl.getControl(className);

                    if (controlType.isInstance(o))
                    {
                        @SuppressWarnings("unchecked")
                        T t = (T) o;

                        if (controls == null)
                            controls = new HashSet<T>();
                        controls.add(t);
                    }
                }
            }
        }
        if (controls == null)
            controls = Collections.emptySet();
        return controls;
    }

    /**
     * Updates the value of <tt>useTranslator</tt>.
     * @param useTranslator whether this device session is used by
     * a <tt>MediaStream</tt> that is having a translator.
     */
    public void setUseTranslator(boolean useTranslator)
    {
        this.useTranslator = useTranslator;
    }
}
