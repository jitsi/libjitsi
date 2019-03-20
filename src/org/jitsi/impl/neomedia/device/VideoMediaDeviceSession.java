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
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.swing.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.codec.video.h264.*;
import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.control.KeyFrameControl;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.resources.*;
import org.jitsi.util.*;
import org.jitsi.util.event.*;
import org.jitsi.util.swing.*;
import org.jitsi.utils.*;

/**
 * Extends <tt>MediaDeviceSession</tt> to add video-specific functionality.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Hristo Terezov
 * @author Boris Grozev
 */
public class VideoMediaDeviceSession
    extends MediaDeviceSession
    implements RTCPFeedbackMessageCreateListener
{
    /**
     * The image ID of the icon which is to be displayed as the local visual
     * <tt>Component</tt> depicting the streaming of the desktop of the local
     * peer to the remote peer.
     */
    private static final String DESKTOP_STREAMING_ICON
        = "impl.media.DESKTOP_STREAMING_ICON";

    /**
     * The <tt>Logger</tt> used by the <tt>VideoMediaDeviceSession</tt> class
     * and its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(VideoMediaDeviceSession.class);

    /**
     * Gets the visual <tt>Component</tt> of a specific <tt>Player</tt> if it
     * has one and ignores the failure to access it if the specified
     * <tt>Player</tt> is unrealized.
     *
     * @param player the <tt>Player</tt> to get the visual <tt>Component</tt> of
     * if it has one
     * @return the visual <tt>Component</tt> of the specified <tt>Player</tt> if
     * it has one; <tt>null</tt> if the specified <tt>Player</tt> does not have
     * a visual <tt>Component</tt> or the <tt>Player</tt> is unrealized
     */
    private static Component getVisualComponent(Player player)
    {
        Component visualComponent = null;

        if (player.getState() >= Player.Realized)
        {
            try
            {
                visualComponent = player.getVisualComponent();
            }
            catch (NotRealizedError nre)
            {
                if (logger.isDebugEnabled())
                    logger.debug(
                            "Called Player#getVisualComponent() "
                                + "on unrealized player "
                                + player,
                            nre);
            }
        }
        return visualComponent;
    }

    /**
     * <tt>RTCPFeedbackMessageListener</tt> instance that will be passed to
     * {@link #rtpConnector} to handle RTCP PLI requests.
     */
    private RTCPFeedbackMessageListener encoder = null;

    /**
     * The <tt>KeyFrameControl</tt> used by this<tt>VideoMediaDeviceSession</tt>
     * as a means to control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * The <tt>KeyFrameRequester</tt> implemented by this
     * <tt>VideoMediaDeviceSession</tt> and provided to
     * {@link #keyFrameControl}.
     */
    private KeyFrameControl.KeyFrameRequester keyFrameRequester;

    /**
     * The <tt>Player</tt> which provides the local visual/video
     * <tt>Component</tt>.
     */
    private Player localPlayer;

    /**
     * The <tt>Object</tt> which synchronizes the access to
     * {@link #localPlayer}.
     */
    private final Object localPlayerSyncRoot = new Object();

    /**
     * Local SSRC.
     */
    private long localSSRC = -1;

    /**
     * Output size of the stream.
     *
     * It is used to specify a different size (generally lesser ones)
     * than the capture device provides. Typically one usage can be
     * in desktop streaming/sharing session when sender desktop is bigger
     * than remote ones.
     */
    private Dimension outputSize;

    /**
     * The <tt>SwScale</tt> inserted into the codec chain of the
     * <tt>Player</tt> rendering the media received from the remote peer and
     * enabling the explicit setting of the video size.
     */
    private SwScale playerScaler;

    /**
     * Remote SSRC.
     */
    private long remoteSSRC = -1;

    /**
     * The list of <tt>RTCPFeedbackMessageCreateListener</tt> which will be
     * notified when a <tt>RTCPFeedbackMessageListener</tt> is created.
     */
    private List<RTCPFeedbackMessageCreateListener>
        rtcpFeedbackMessageCreateListeners
            = new LinkedList<RTCPFeedbackMessageCreateListener>();

    /**
     * The <tt>RTPConnector</tt> with which the <tt>RTPManager</tt> of this
     * instance is to be or is already initialized.
     */
    private AbstractRTPConnector rtpConnector;

    /**
     * Use or not RTCP feedback Picture Loss Indication to request keyframes.
     * Does not affect handling of received RTCP feedback events.
     */
    private boolean useRTCPFeedbackPLI = false;

    /**
     * The facility which aids this instance in managing a list of
     * <tt>VideoListener</tt>s and firing <tt>VideoEvent</tt>s to them.
     */
    private final VideoNotifierSupport videoNotifierSupport
        = new VideoNotifierSupport(this, false);

    /**
     * Initializes a new <tt>VideoMediaDeviceSession</tt> instance which is to
     * represent the work of a <tt>MediaStream</tt> with a specific video
     * <tt>MediaDevice</tt>.
     *
     * @param device the video <tt>MediaDevice</tt> the use of which by a
     * <tt>MediaStream</tt> is to be represented by the new instance
     */
    public VideoMediaDeviceSession(AbstractMediaDevice device)
    {
        super(device);
    }

    /**
     * Adds <tt>RTCPFeedbackMessageCreateListener</tt>.
     *
     * @param listener the listener to add
     */
    public void addRTCPFeedbackMessageCreateListner(
            RTCPFeedbackMessageCreateListener listener)
    {
        synchronized (rtcpFeedbackMessageCreateListeners)
        {
            rtcpFeedbackMessageCreateListeners.add(listener);
        }

        if (encoder != null)
            listener.onRTCPFeedbackMessageCreate(encoder);
    }

    /**
     * Adds a specific <tt>VideoListener</tt> to this instance in order to
     * receive notifications when visual/video <tt>Component</tt>s are being
     * added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is
     * not added more than once and thus does not receive one and the same
     * <tt>VideoEvent</tt> multiple times.
     * </p>
     *
     * @param listener the <tt>VideoListener</tt> to be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * instance
     */
    public void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * Asserts that a specific <tt>MediaDevice</tt> is acceptable to be set as
     * the <tt>MediaDevice</tt> of this instance. Makes sure that its
     * <tt>MediaType</tt> is {@link MediaType#VIDEO}.
     *
     * @param device the <tt>MediaDevice</tt> to be checked for suitability to
     * become the <tt>MediaDevice</tt> of this instance
     * @see MediaDeviceSession#checkDevice(AbstractMediaDevice)
     */
    @Override
    protected void checkDevice(AbstractMediaDevice device)
    {
        if (!MediaType.VIDEO.equals(device.getMediaType()))
            throw new IllegalArgumentException("device");
    }

    /**
     * Gets notified about <tt>ControllerEvent</tt>s generated by
     * {@link #localPlayer}.
     *
     * @param ev the <tt>ControllerEvent</tt> specifying the <tt>Controller</tt
     *  which is the source of the event and the very type of the event
     * @param hflip <tt>true</tt> if the image displayed in the local visual
     * <tt>Component</tt> is to be horizontally flipped; otherwise,
     * <tt>false</tt>
     */
    private void controllerUpdateForCreateLocalVisualComponent(
            ControllerEvent ev,
            boolean hflip)
    {
        if (ev instanceof ConfigureCompleteEvent)
        {
            Processor player = (Processor) ev.getSourceController();

            /*
             * Use SwScale for the scaling since it produces an image with
             * better quality and add the "flip" effect to the video.
             */
            TrackControl[] trackControls = player.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0))
            {
                try
                {
                    for (TrackControl trackControl : trackControls)
                    {
                        trackControl.setCodecChain(
                                hflip
                                    ? new Codec[]
                                            { new HFlip(), new SwScale() }
                                    : new Codec[]
                                            { new SwScale() });
                        break;
                    }
                }
                catch (UnsupportedPlugInException upiex)
                {
                    logger.warn(
                            "Failed to add HFlip/SwScale Effect",
                            upiex);
                }
            }

            // Turn the Processor into a Player.
            try
            {
                player.setContentDescriptor(null);
            }
            catch (NotConfiguredError nce)
            {
                logger.error(
                    "Failed to set ContentDescriptor of Processor",
                    nce);
            }

            player.realize();
        }
        else if (ev instanceof RealizeCompleteEvent)
        {
            Player player = (Player) ev.getSourceController();
            Component visualComponent = player.getVisualComponent();
            boolean start;

            if (visualComponent == null)
                start = false;
            else
            {
                fireVideoEvent(
                        VideoEvent.VIDEO_ADDED,
                        visualComponent,
                        VideoEvent.LOCAL,
                        false);
                start = true;
            }
            if (start)
                player.start();
            else
            {
                // No listener is interested in our event so free the resources.
                synchronized (localPlayerSyncRoot)
                {
                    if (localPlayer == player)
                        localPlayer = null;
                }

                player.stop();
                player.deallocate();
                player.close();
            }
        }
        else if (ev instanceof SizeChangeEvent)
        {
            /*
             * Mostly for the sake of completeness, notify that the size of the
             * local video has changed like we do for the remote videos.
             */
            SizeChangeEvent scev = (SizeChangeEvent) ev;

            playerSizeChange(
                    scev.getSourceController(),
                    VideoEvent.LOCAL,
                    scev.getWidth(), scev.getHeight());
        }
    }

    /**
     * Creates the <tt>DataSource</tt> that this instance is to read captured
     * media from.
     *
     * @return the <tt>DataSource</tt> that this instance is to read captured
     * media from
     */
    @Override
    protected DataSource createCaptureDevice()
    {
        /*
         * Create our DataSource as SourceCloneable so we can use it to both
         * display local video and stream to remote peer.
         */
        DataSource captureDevice = super.createCaptureDevice();

        if (captureDevice != null)
        {
            MediaLocator locator = captureDevice.getLocator();
            String protocol = (locator == null) ? null : locator.getProtocol();
            float frameRate;
            DeviceConfiguration deviceConfig
                = NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration();

            // Apply the video size and the frame rate configured by the user.
            if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(protocol))
            {
                /*
                 * It is not clear at this time what the default frame rate for
                 * desktop streaming should be.
                 */
                frameRate = 10;
            }
            else
            {
                Dimension videoSize = deviceConfig.getVideoSize();

                // if we have an output size that is smaller than our current
                // settings, respect that size
                if(outputSize != null
                   && videoSize.height > outputSize.height
                   && videoSize.width > outputSize.width)
                    videoSize = outputSize;

                Dimension dim = VideoMediaStreamImpl.selectVideoSize(
                        captureDevice,
                        videoSize.width, videoSize.height);

                frameRate = deviceConfig.getFrameRate();

                // print initial video resolution, when starting video
                if(logger.isInfoEnabled() && dim != null)
                    logger.info("video send resolution: "
                            + dim.width + "x" + dim.height);
            }

            FrameRateControl frameRateControl
                = (FrameRateControl)
                    captureDevice.getControl(FrameRateControl.class.getName());

            if (frameRateControl != null)
            {
                float maxSupportedFrameRate
                    = frameRateControl.getMaxSupportedFrameRate();

                if ((maxSupportedFrameRate > 0)
                        && (frameRate > maxSupportedFrameRate))
                    frameRate = maxSupportedFrameRate;
                if(frameRate > 0)
                    frameRateControl.setFrameRate(frameRate);

                // print initial video frame rate, when starting video
                if(logger.isInfoEnabled())
                {
                    logger.info("video send FPS: " + (frameRate == -1 ?
                            "default(no restriction)" : frameRate));
                }
            }

            if (!(captureDevice instanceof SourceCloneable))
            {
                DataSource cloneableDataSource
                    = Manager.createCloneableDataSource(captureDevice);

                if (cloneableDataSource != null)
                    captureDevice = cloneableDataSource;
            }
        }
        return captureDevice;
    }

    /**
     * Initializes a new <tt>Player</tt> instance which is to provide the local
     * visual/video <tt>Component</tt>. The new instance is initialized to
     * render the media of the <tt>captureDevice</tt> of this
     * <tt>MediaDeviceSession</tt>.
     *
     * @return a new <tt>Player</tt> instance which is to provide the local
     * visual/video <tt>Component</tt>
     */
    private Player createLocalPlayer()
    {
        return createLocalPlayer(getCaptureDevice());
    }

    /**
     * Initializes a new <tt>Player</tt> instance which is to provide the local
     * visual/video <tt>Component</tt>. The new instance is initialized to
     * render the media of a specific <tt>DataSource</tt>.
     *
     * @param captureDevice the <tt>DataSource</tt> which is to have its media
     * rendered by the new instance as the local visual/video <tt>Component</tt>
     * @return a new <tt>Player</tt> instance which is to provide the local
     * visual/video <tt>Component</tt>
     */
    protected Player createLocalPlayer(DataSource captureDevice)
    {
        DataSource dataSource
            = (captureDevice instanceof SourceCloneable)
                ? ((SourceCloneable) captureDevice).createClone()
                : null;
        Processor localPlayer = null;

        if (dataSource != null)
        {
            Exception exception = null;

            try
            {
                localPlayer = Manager.createProcessor(dataSource);
            }
            catch (Exception ex)
            {
                exception = ex;
            }

            if (exception == null)
            {
                if (localPlayer != null)
                {
                    /*
                     * If a local visual Component is to be displayed for
                     * desktop sharing/streaming, do not flip it because it does
                     * not seem natural.
                     */
                    final boolean hflip
                        = (captureDevice.getControl(
                                    ImgStreamingControl.class.getName())
                                == null);

                    localPlayer.addControllerListener(
                        new ControllerListener()
                        {
                            public void controllerUpdate(ControllerEvent ev)
                            {
                                controllerUpdateForCreateLocalVisualComponent(
                                    ev,
                                    hflip);
                            }
                        });
                    localPlayer.configure();
                }
            }
            else
            {
                logger.error(
                        "Failed to connect to "
                            + MediaStreamImpl.toString(dataSource),
                        exception);
            }
        }

        return localPlayer;
    }

    /**
     * Creates the visual <tt>Component</tt> depicting the video being streamed
     * from the local peer to the remote peer.
     *
     * @return the visual <tt>Component</tt> depicting the video being streamed
     * from the local peer to the remote peer if it was immediately created or
     * <tt>null</tt> if it was not immediately created and it is to be delivered
     * to the currently registered <tt>VideoListener</tt>s in a
     * <tt>VideoEvent</tt> with type {@link VideoEvent#VIDEO_ADDED} and origin
     * {@link VideoEvent#LOCAL}
     */
    protected Component createLocalVisualComponent()
    {
        // On Android local preview is displayed directly using Surface
        // provided to the recorder. We don't want to build unused codec chain.
        if(OSUtils.IS_ANDROID)
        {
            return null;
        }

        /*
         * Displaying the currently streamed desktop is perceived as unnecessary
         * because the user sees the whole desktop anyway. Instead, a static
         * image will be presented.
         */
        DataSource captureDevice = getCaptureDevice();

        if ((captureDevice != null)
                && (captureDevice.getControl(
                            ImgStreamingControl.class.getName())
                        != null))
        {
            return createLocalVisualComponentForDesktopStreaming();
        }

        /*
         * The visual Component to depict the video being streamed from the
         * local peer to the remote peer is created by JMF and its Player so it
         * is likely to take noticeably long time. Consequently, we will deliver
         * it to the currently registered VideoListeners in a VideoEvent after
         * returning from the call.
         */
        Component localVisualComponent;

        synchronized (localPlayerSyncRoot)
        {
            if (localPlayer == null)
                localPlayer = createLocalPlayer();
            localVisualComponent
                = (localPlayer == null)
                    ? null
                    : getVisualComponent(localPlayer);
        }
        /*
         * If the local visual/video Component exists at this time, it has
         * likely been created by a previous call to this method. However, the
         * caller may still depend on a VIDEO_ADDED event being fired for it.
         */
        if (localVisualComponent != null)
        {
            fireVideoEvent(
                    VideoEvent.VIDEO_ADDED,
                    localVisualComponent,
                    VideoEvent.LOCAL,
                    false);
        }
        return localVisualComponent;
    }

    /**
     * Creates the visual <tt>Component</tt> to depict the streaming of the
     * desktop of the local peer to the remote peer.
     *
     * @return the visual <tt>Component</tt> to depict the streaming of the
     * desktop of the local peer to the remote peer
     */
    private Component createLocalVisualComponentForDesktopStreaming()
    {
        ResourceManagementService resources
            = LibJitsi.getResourceManagementService();
        ImageIcon icon
            = (resources == null)
                ? null
                : resources.getImage(DESKTOP_STREAMING_ICON);
        Canvas canvas;

        if (icon == null)
            canvas = null;
        else
        {
            final Image img = icon.getImage();

            canvas = new Canvas()
            {
                public static final long serialVersionUID = 0L;

                @Override
                public void paint(Graphics g)
                {
                    int width = getWidth();
                    int height = getHeight();

                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, width, height);

                    int imgWidth = img.getWidth(this);
                    int imgHeight = img.getHeight(this);

                    if ((imgWidth < 1) || (imgHeight < 1))
                        return;

                    boolean scale = false;
                    float scaleFactor = 1;

                    if (imgWidth > width)
                    {
                        scale = true;
                        scaleFactor = width / (float) imgWidth;
                    }
                    if (imgHeight > height)
                    {
                        scale = true;
                        scaleFactor
                            = Math.min(scaleFactor, height / (float) imgHeight);
                    }

                    int dstWidth;
                    int dstHeight;

                    if (scale)
                    {
                        dstWidth = Math.round(imgWidth * scaleFactor);
                        dstHeight = Math.round(imgHeight * scaleFactor);
                    }
                    else
                    {
                        dstWidth = imgWidth;
                        dstHeight = imgHeight;
                    }

                    int dstX = (width - dstWidth) / 2;
                    int dstY = (height - dstWidth) / 2;

                    g.drawImage(
                            img,
                            dstX, dstY, dstX + dstWidth, dstY + dstHeight,
                            0, 0, imgWidth, imgHeight,
                            this);
                }
            };

            Dimension iconSize
                = new Dimension(icon.getIconWidth(), icon.getIconHeight());

            canvas.setMaximumSize(iconSize);
            canvas.setPreferredSize(iconSize);

            /*
             * Set a clue so that we can recognize it if it gets received as an
             * argument to #disposeLocalVisualComponent().
             */
            canvas.setName(DESKTOP_STREAMING_ICON);

            fireVideoEvent(
                    VideoEvent.VIDEO_ADDED,
                    canvas,
                    VideoEvent.LOCAL,
                    false);
        }
        return canvas;
    }

    /**
     * Releases the resources allocated by a specific local <tt>Player</tt> in
     * the course of its execution and prepares it to be garbage collected. If
     * the specified <tt>Player</tt> is rendering video, notifies the
     * <tt>VideoListener</tt>s of this instance that its visual
     * <tt>Component</tt> is to no longer be used by firing a
     * {@link VideoEvent#VIDEO_REMOVED} <tt>VideoEvent</tt>.
     *
     * @param player the <tt>Player</tt> to dispose of
     * @see MediaDeviceSession#disposePlayer(Player)
     */
    protected void disposeLocalPlayer(Player player)
    {
        /*
         * The player is being disposed so let the (interested) listeners know
         * its Player#getVisualComponent() (if any) should be released.
         */
        Component visualComponent = null;

        try
        {
            visualComponent = getVisualComponent(player);

            player.stop();
            player.deallocate();
            player.close();
        }
        finally
        {
            synchronized (localPlayerSyncRoot)
            {
                if (localPlayer == player)
                    localPlayer = null;
            }

            if (visualComponent != null)
            {
                fireVideoEvent(
                        VideoEvent.VIDEO_REMOVED,
                        visualComponent,
                        VideoEvent.LOCAL,
                        false);
            }
        }
    }

    /**
     * Disposes of the local visual <tt>Component</tt> of the local peer.
     *
     * @param component the local visual <tt>Component</tt> of the local peer to
     * dispose of
     */
    protected void disposeLocalVisualComponent(Component component)
    {
        if (component != null)
        {
            /*
             * Desktop streaming does not use a Player but a Canvas with its
             * name equal to the value of DESKTOP_STREAMING_ICON.
             */
            if (DESKTOP_STREAMING_ICON.equals(component.getName()))
            {
                fireVideoEvent(
                        VideoEvent.VIDEO_REMOVED, component, VideoEvent.LOCAL,
                        false);
            }
            else
            {
                Player localPlayer;

                synchronized (localPlayerSyncRoot)
                {
                    localPlayer = this.localPlayer;
                }
                if (localPlayer != null)
                {
                    Component localPlayerVisualComponent
                        = getVisualComponent(localPlayer);

                    if ((localPlayerVisualComponent == null)
                            || (localPlayerVisualComponent == component))
                        disposeLocalPlayer(localPlayer);
                }
            }
        }
    }

    /**
     * Releases the resources allocated by a specific <tt>Player</tt> in the
     * course of its execution and prepares it to be garbage collected. If the
     * specified <tt>Player</tt> is rendering video, notifies the
     * <tt>VideoListener</tt>s of this instance that its visual
     * <tt>Component</tt> is to no longer be used by firing a
     * {@link VideoEvent#VIDEO_REMOVED} <tt>VideoEvent</tt>.
     *
     * @param player the <tt>Player</tt> to dispose of
     * @see MediaDeviceSession#disposePlayer(Player)
     */
    @Override
    protected void disposePlayer(Player player)
    {
        /*
         * The player is being disposed so let the (interested) listeners know
         * its Player#getVisualComponent() (if any) should be released.
         */
        Component visualComponent = getVisualComponent(player);

        super.disposePlayer(player);

        if (visualComponent != null)
        {
            fireVideoEvent(
                VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.REMOTE,
                false);
        }
    }

    /**
     * Notifies the <tt>VideoListener</tt>s registered with this instance about
     * a specific type of change in the availability of a specific visual
     * <tt>Component</tt> depicting video.
     *
     * @param type the type of change as defined by <tt>VideoEvent</tt> in the
     * availability of the specified visual <tt>Component</tt> depicting video
     * @param visualComponent the visual <tt>Component</tt> depicting video
     * which has been added or removed in this instance
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is
     * local (e.g. it is being locally captured); {@link VideoEvent#REMOTE} if
     * the origin of the video is remote (e.g. a remote peer is streaming it)
     * @param wait <tt>true</tt> if the call is to wait till the specified
     * <tt>VideoEvent</tt> has been delivered to the <tt>VideoListener</tt>s;
     * otherwise, <tt>false</tt>
     * @return <tt>true</tt> if this event and, more specifically, the visual
     * <tt>Component</tt> it describes have been consumed and should be
     * considered owned, referenced (which is important because
     * <tt>Component</tt>s belong to a single <tt>Container</tt> at a time);
     * otherwise, <tt>false</tt>
     */
    protected boolean fireVideoEvent(
            int type, Component visualComponent, int origin,
            boolean wait)
    {
        if (logger.isTraceEnabled())
        {
            logger.trace(
                    "Firing VideoEvent with type "
                        + VideoEvent.typeToString(type)
                        + " and origin "
                        + VideoEvent.originToString(origin));
        }

        return
            videoNotifierSupport.fireVideoEvent(
                    type, visualComponent, origin,
                    wait);
    }

    /**
     * Notifies the <tt>VideoListener</tt>s registered with this instance about
     * a specific <tt>VideoEvent</tt>.
     *
     * @param videoEvent the <tt>VideoEvent</tt> to be fired to the
     * <tt>VideoListener</tt>s registered with this instance
     * @param wait <tt>true</tt> if the call is to wait till the specified
     * <tt>VideoEvent</tt> has been delivered to the <tt>VideoListener</tt>s;
     * otherwise, <tt>false</tt>
     */
    protected void fireVideoEvent(VideoEvent videoEvent, boolean wait)
    {
        videoNotifierSupport.fireVideoEvent(videoEvent, wait);
    }

    /**
     * Gets the JMF <tt>Format</tt> of the <tt>captureDevice</tt> of this
     * <tt>MediaDeviceSession</tt>.
     *
     * @return the JMF <tt>Format</tt> of the <tt>captureDevice</tt> of this
     * <tt>MediaDeviceSession</tt>
     */
    private Format getCaptureDeviceFormat()
    {
        DataSource captureDevice = getCaptureDevice();

        if (captureDevice != null)
        {
            FormatControl[] formatControls = null;

            if (captureDevice instanceof CaptureDevice)
            {
                formatControls
                    = ((CaptureDevice) captureDevice).getFormatControls();
            }
            if ((formatControls == null) || (formatControls.length == 0))
            {
                FormatControl formatControl
                    = (FormatControl)
                        captureDevice.getControl(FormatControl.class.getName());

                if (formatControl != null)
                    formatControls = new FormatControl[] { formatControl };
            }
            if (formatControls != null)
            {
                for (FormatControl formatControl : formatControls)
                {
                    Format format = formatControl.getFormat();

                    if (format != null)
                        return format;
                }
            }
        }
        return null;
    }

    /**
     * Gets the visual <tt>Component</tt>, if any, depicting the video streamed
     * from the local peer to the remote peer.
     *
     * @return the visual <tt>Component</tt> depicting the local video if local
     * video is actually being streamed from the local peer to the remote peer;
     * otherwise, <tt>null</tt>
     */
    public Component getLocalVisualComponent()
    {
        synchronized (localPlayerSyncRoot)
        {
            return
                (localPlayer == null) ? null : getVisualComponent(localPlayer);
        }
    }

    /**
     * Returns the FMJ <tt>Format</tt> of the video we are receiving from the
     * remote peer.
     *
     * @return the FMJ <tt>Format</tt> of the video we are receiving from the
     * remote peer or <tt>null</tt> if we are not receiving any video or the FMJ
     * <tt>Format</tt> of the video we are receiving from the remote peer cannot
     * be determined
     */
    public VideoFormat getReceivedVideoFormat()
    {
        if (playerScaler != null)
        {
            Format format = playerScaler.getInputFormat();

            if (format instanceof VideoFormat)
                return (VideoFormat) format;
        }
        return null;
    }

    /**
     * Returns the format of the video we are streaming to the remote peer.
     *
     * @return The video format of the sent video. Null, if no video is sent.
     */
    public VideoFormat getSentVideoFormat()
    {
        DataSource capture = getCaptureDevice();

        if (capture instanceof PullBufferDataSource)
        {
            PullBufferStream[] streams
                = ((PullBufferDataSource) capture).getStreams();

            for (PullBufferStream stream : streams)
            {
                VideoFormat format = (VideoFormat) stream.getFormat();

                if (format != null)
                    return format;
            }
        }
        return null;
    }

    /**
     * Gets the visual <tt>Component</tt>s rendering the <tt>ReceiveStream</tt>
     * corresponding to the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual
     * <tt>Component</tt> we're looking for
     * @return the visual <tt>Component</tt> rendering the
     * <tt>ReceiveStream</tt> corresponding to the given ssrc
     */
    public Component getVisualComponent(long ssrc)
    {
        Player player = getPlayer(ssrc);

        return (player == null) ? null : getVisualComponent(player);
    }

    /**
     * Gets the visual <tt>Component</tt>s where video from the remote peer is
     * being rendered.
     *
     * @return the visual <tt>Component</tt>s where video from the remote peer
     * is being rendered
     */
    public List<Component> getVisualComponents()
    {
        List<Component> visualComponents = new LinkedList<Component>();

        /*
         * When we know (through means such as SDP) that we don't want to
         * receive, it doesn't make sense to wait for the remote peer to
         * acknowledge our desire. So we'll just stop depicting the video of the
         * remote peer regardless of whether it stops or continues its sending.
         */
        if (getStartedDirection().allowsReceiving())
        {
            for (Player player : getPlayers())
            {
                Component visualComponent = getVisualComponent(player);

                if (visualComponent != null)
                    visualComponents.add(visualComponent);
            }
        }
        return visualComponents;
    }

    /**
     * Implements {@link KeyFrameControl.KeyFrameRequester#requestKeyFrame()}
     * of {@link #keyFrameRequester}.
     *
     * @param keyFrameRequester the <tt>KeyFrameControl.KeyFrameRequester</tt>
     * on which the method is invoked
     * @return <tt>true</tt> if this <tt>KeyFrameRequester</tt> has indeed
     * requested a key frame from the remote peer of the associated
     * <tt>VideoMediaStream</tt> in response to the call; otherwise,
     * <tt>false</tt>
     */
    private boolean keyFrameRequesterRequestKeyFrame(
            KeyFrameControl.KeyFrameRequester keyFrameRequester)
    {
        boolean requested = false;

        if (VideoMediaDeviceSession.this.useRTCPFeedbackPLI)
        {
            try
            {
                OutputDataStream controlOutputStream
                    = rtpConnector.getControlOutputStream();
                if (controlOutputStream != null)
                {
                    new RTCPFeedbackMessagePacket(
                        RTCPFeedbackMessageEvent.FMT_PLI,
                        RTCPFeedbackMessageEvent.PT_PS,
                        localSSRC,
                        remoteSSRC)
                        .writeTo(controlOutputStream);
                    requested = true;
                }
            }
            catch (IOException ioe)
            {
                /*
                 * Apart from logging the IOException, there are not a lot of
                 * ways to handle it.
                 */
            }
        }
        return requested;
    }

    /**
     * Notifies this <tt>VideoMediaDeviceSession</tt> of a new
     * <tt>RTCPFeedbackListener</tt>
     *
     * @param rtcpFeedbackMessageListener the listener to be added.
     */
    @Override
    public void onRTCPFeedbackMessageCreate(
            RTCPFeedbackMessageListener rtcpFeedbackMessageListener)
    {
        if (rtpConnector != null)
        {
            try
            {
                ((ControlTransformInputStream)
                        rtpConnector.getControlInputStream())
                    .addRTCPFeedbackMessageListener(
                            rtcpFeedbackMessageListener);
            }
            catch (IOException ioe)
            {
                logger.error("Error cannot get RTCP input stream", ioe);
            }
        }
    }

    /**
     * Notifies this instance that a specific <tt>Player</tt> of remote content
     * has generated a <tt>ConfigureCompleteEvent</tt>.
     *
     * @param player the <tt>Player</tt> which is the source of a
     * <tt>ConfigureCompleteEvent</tt>
     * @see MediaDeviceSession#playerConfigureComplete(Processor)
     */
    @Override
    protected void playerConfigureComplete(Processor player)
    {
        super.playerConfigureComplete(player);

        TrackControl[] trackControls = player.getTrackControls();
        SwScale playerScaler = null;

        if ((trackControls != null) && (trackControls.length != 0)
                /* We don't add SwScale, KeyFrameControl on Android. */
                && !OSUtils.IS_ANDROID)
        {
            String fmjEncoding = getFormat().getJMFEncoding();

            try
            {
                for (TrackControl trackControl : trackControls)
                {
                    /*
                     * Since SwScale will scale any input size into the
                     * configured output size, we may never get SizeChangeEvent
                     * from the player. We'll generate it ourselves then.
                     */
                    playerScaler = new PlayerScaler(player);

                    /*
                     * For H.264, we will use RTCP feedback. For example, to
                     * tell the sender that we've missed a frame.
                     */
                    if ("h264/rtp".equalsIgnoreCase(fmjEncoding))
                    {
                        final DePacketizer depacketizer = new DePacketizer();
                        JNIDecoder decoder = new JNIDecoder();

                        if (keyFrameControl != null)
                        {
                            depacketizer.setKeyFrameControl(keyFrameControl);
                            decoder.setKeyFrameControl(
                                    new KeyFrameControlAdapter()
                                    {
                                        @Override
                                        public boolean requestKeyFrame(
                                                boolean urgent)
                                        {
                                            return
                                                depacketizer.requestKeyFrame(
                                                        urgent);
                                        }
                                    });
                        }

                        trackControl.setCodecChain(
                                new Codec[]
                                {
                                    depacketizer,
                                    decoder,
                                    playerScaler
                                });
                    }
                    else
                    {
                        trackControl.setCodecChain(
                                new Codec[] { playerScaler });
                    }
                    break;
                }
            }
            catch (UnsupportedPlugInException upiex)
            {
                logger.error(
                        "Failed to add SwScale or H.264 DePacketizer"
                            + " to codec chain",
                        upiex);
                playerScaler = null;
            }
        }
        this.playerScaler = playerScaler;
    }

    /**
     * Gets notified about <tt>ControllerEvent</tt>s generated by a specific
     * <tt>Player</tt> of remote content.
     *
     * @param ev the <tt>ControllerEvent</tt> specifying the
     * <tt>Controller</tt> which is the source of the event and the very type of
     * the event
     * @see MediaDeviceSession#playerControllerUpdate(ControllerEvent)
     */
    @Override
    protected void playerControllerUpdate(ControllerEvent ev)
    {
        super.playerControllerUpdate(ev);

        /*
         * If SwScale is in the chain and it forces a specific size of the
         * output, the SizeChangeEvents of the Player do not really notify about
         * changes in the size of the input. Besides, playerScaler will take
         * care of the events in such a case.
         */
        if ((ev instanceof SizeChangeEvent)
                && ((playerScaler == null)
                        || (playerScaler.getOutputSize() == null)))
        {
            SizeChangeEvent scev = (SizeChangeEvent) ev;

            playerSizeChange(
                    scev.getSourceController(),
                    VideoEvent.REMOTE,
                    scev.getWidth(), scev.getHeight());
        }
    }

    /**
     * Notifies this instance that a specific <tt>Player</tt> of remote content
     * has generated a <tt>RealizeCompleteEvent</tt>.
     *
     * @param player the <tt>Player</tt> which is the source of a
     * <tt>RealizeCompleteEvent</tt>.
     * @see MediaDeviceSession#playerRealizeComplete(Processor)
     */
    @Override
    protected void playerRealizeComplete(final Processor player)
    {
        super.playerRealizeComplete(player);

        Component visualComponent = getVisualComponent(player);

        if (visualComponent != null)
        {
            /*
             * SwScale seems to be very good at scaling with respect to image
             * quality so use it for the scaling in the player replacing the
             * scaling it does upon rendering.
             */
            visualComponent.addComponentListener(
                    new ComponentAdapter()
                    {
                        @Override
                        public void componentResized(ComponentEvent ev)
                        {
                            playerVisualComponentResized(player, ev);
                        }
                    });

            fireVideoEvent(
                    VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.REMOTE,
                    false);
        }
    }

    /**
     * Notifies this instance that a specific <tt>Player</tt> of local or remote
     * content/video has generated a <tt>SizeChangeEvent</tt>.
     *
     * @param sourceController the <tt>Player</tt> which is the source of the
     * event
     * @param origin {@link VideoEvent#LOCAL} or {@link VideoEvent#REMOTE} which
     * specifies the origin of the visual <tt>Component</tt> displaying video
     * which is concerned
     * @param width the width reported in the event
     * @param height the height reported in the event
     * @see SizeChangeEvent
     */
    protected void playerSizeChange(
            final Controller sourceController,
            final int origin,
            final int width, final int height)
    {
        /*
         * Invoking anything that is likely to change the UI in the Player
         * thread seems like a performance hit so bring it into the event
         * thread.
         */
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(
                    new Runnable()
                    {
                        public void run()
                        {
                            playerSizeChange(
                                    sourceController,
                                    origin,
                                    width, height);
                        }
                    });
            return;
        }

        Player player = (Player) sourceController;
        Component visualComponent = getVisualComponent(player);

        if (visualComponent != null)
        {
            /*
             * The Player will notice the new size and will notify about it
             * before it reaches the Renderer. The notification/event may as
             * well arrive before the Renderer reflects the new size onto the
             * preferredSize of the Component. In order to make sure that the
             * new size is reflected on the preferredSize of the Component
             * before the notification/event arrives to its
             * destination/listener, reflect it as soon as possible i.e. now.
             */
            try
            {
                Dimension prefSize = visualComponent.getPreferredSize();

                if ((prefSize == null)
                        || (prefSize.width < 1) || (prefSize.height < 1)
                        || !VideoLayout.areAspectRatiosEqual(
                                prefSize,
                                width, height)
                        || (prefSize.width < width)
                        || (prefSize.height < height))
                {
                    visualComponent.setPreferredSize(
                            new Dimension(width, height));
                }
            }
            finally
            {
                fireVideoEvent(
                        new SizeChangeVideoEvent(
                                this,
                                visualComponent,
                                origin,
                                width, height),
                        false);
            }
        }
    }

    /**
     * Notifies this instance that the visual <tt>Component</tt> of a
     * <tt>Player</tt> rendering remote content has been resized.
     *
     * @param player the <tt>Player</tt> rendering remote content the visual
     * <tt>Component</tt> of which has been resized
     * @param ev a <tt>ComponentEvent</tt> which specifies the resized
     * <tt>Component</tt>
     */
    private void playerVisualComponentResized(
            Processor player,
            ComponentEvent ev)
    {
        if (playerScaler == null)
            return;

        Component visualComponent = ev.getComponent();

        /*
         * When the visualComponent is not in a UI hierarchy, its size is not
         * expected to be representative of what the user is seeing.
         */
        if (visualComponent.isDisplayable())
            return;

        Dimension outputSize = visualComponent.getSize();
        float outputWidth = outputSize.width;
        float outputHeight = outputSize.height;

        if ((outputWidth < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                || (outputHeight < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH))
            return;

        /*
         * The size of the output video will be calculated so that it fits into
         * the visualComponent and the video aspect ratio is preserved. The
         * presumption here is that the inputFormat holds the video size with
         * the correct aspect ratio.
         */
        Format inputFormat = playerScaler.getInputFormat();

        if (inputFormat == null)
            return;

        Dimension inputSize = ((VideoFormat) inputFormat).getSize();

        if (inputSize == null)
            return;

        int inputWidth = inputSize.width;
        int inputHeight = inputSize.height;

        if ((inputWidth < 1) || (inputHeight < 1))
            return;

        // Preserve the aspect ratio.
        outputHeight = outputWidth * inputHeight / inputWidth;

        // Fit the output video into the visualComponent.
        boolean scale = false;
        float widthRatio;
        float heightRatio;

        if (Math.abs(outputWidth - inputWidth) < 1)
        {
            scale = true;
            widthRatio = outputWidth / inputWidth;
        }
        else
            widthRatio = 1;
        if (Math.abs(outputHeight - inputHeight) < 1)
        {
            scale = true;
            heightRatio = outputHeight / inputHeight;
        }
        else
            heightRatio = 1;
        if (scale)
        {
            float scaleFactor = Math.min(widthRatio, heightRatio);

            outputWidth = inputWidth * scaleFactor;
            outputHeight = inputHeight * scaleFactor;
        }

        outputSize.width = (int) outputWidth;
        outputSize.height = (int) outputHeight;

        Dimension playerScalerOutputSize = playerScaler.getOutputSize();

        if (playerScalerOutputSize == null)
            playerScaler.setOutputSize(outputSize);
        else
        {
            /*
             * If we are not going to make much of a change, do not even bother
             * because any scaling in the Renderer will not be noticeable
             * anyway.
             */
            int outputWidthDelta
                = outputSize.width - playerScalerOutputSize.width;
            int outputHeightDelta
                = outputSize.height - playerScalerOutputSize.height;

            if ((outputWidthDelta < -1) || (outputWidthDelta > 1)
                    || (outputHeightDelta < -1) || (outputHeightDelta > 1))
            {
                playerScaler.setOutputSize(outputSize);
            }
        }
    }

    /**
     * Removes <tt>RTCPFeedbackMessageCreateListener</tt>.
     *
     * @param listener the listener to remove
     */
    public void removeRTCPFeedbackMessageCreateListner(
            RTCPFeedbackMessageCreateListener listener)
    {
        synchronized (rtcpFeedbackMessageCreateListeners)
        {
            rtcpFeedbackMessageCreateListeners.remove(listener);
        }
    }

    /**
     * Removes a specific <tt>VideoListener</tt> from this instance in order to
     * have to no longer receive notifications when visual/video
     * <tt>Component</tt>s are being added and removed.
     *
     * @param listener the <tt>VideoListener</tt> to no longer be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * instance
     */
    public void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
    }

    /**
     * Sets the <tt>RTPConnector</tt> that will be used to
     * initialize some codec for RTCP feedback.
     *
     * @param rtpConnector the RTP connector
     */
    public void setConnector(AbstractRTPConnector rtpConnector)
    {
        this.rtpConnector = rtpConnector;
    }

    /**
     * Sets the <tt>MediaFormat</tt> in which this <tt>MediaDeviceSession</tt>
     * outputs the media captured by its <tt>MediaDevice</tt>.
     *
     * @param format the <tt>MediaFormat</tt> in which this
     * <tt>MediaDeviceSession</tt> is to output the media captured by its
     * <tt>MediaDevice</tt>
     */
    @Override
    public void setFormat(MediaFormat format)
    {
        if(format instanceof VideoMediaFormat &&
            ((VideoMediaFormat)format).getFrameRate() != -1)
        {
            FrameRateControl frameRateControl
                = (FrameRateControl)
                    getCaptureDevice().getControl(
                            FrameRateControl.class.getName());

            if (frameRateControl != null)
            {
                float frameRate = ((VideoMediaFormat)format).getFrameRate();

                float maxSupportedFrameRate
                    = frameRateControl.getMaxSupportedFrameRate();

                if ((maxSupportedFrameRate > 0)
                        && (frameRate > maxSupportedFrameRate))
                    frameRate = maxSupportedFrameRate;
                if(frameRate > 0)
                {
                    frameRateControl.setFrameRate(frameRate);

                    if(logger.isInfoEnabled())
                    {
                        logger.info("video send FPS: " + frameRate);
                    }
                }
            }
        }

        super.setFormat(format);
    }

    /**
     * Sets the <tt>KeyFrameControl</tt> to be used by this
     * <tt>VideoMediaDeviceSession</tt> as a means of control over its
     * key frame-related logic.
     *
     * @param keyFrameControl the <tt>KeyFrameControl</tt> to be used by this
     * <tt>VideoMediaDeviceSession</tt> as a means of control over its
     * key frame-related logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        if (this.keyFrameControl != keyFrameControl)
        {
            if ((this.keyFrameControl != null) && (keyFrameRequester != null))
                this.keyFrameControl.removeKeyFrameRequester(keyFrameRequester);

            this.keyFrameControl = keyFrameControl;

            if ((this.keyFrameControl != null) && (keyFrameRequester != null))
                this.keyFrameControl.addKeyFrameRequester(-1, keyFrameRequester);
        }
    }

    /**
     * Set the local SSRC.
     *
     * @param localSSRC local SSRC
     */
    public void setLocalSSRC(long localSSRC)
    {
        this.localSSRC = localSSRC;
    }

    /**
     * Sets the size of the output video.
     *
     * @param size the size of the output video
     */
    public void setOutputSize(Dimension size)
    {
        boolean equal
            = (size == null) ? (outputSize == null) : size.equals(outputSize);

        if (!equal)
        {
            outputSize = size;
            outputSizeChanged = true;
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
     * @see MediaDeviceSession#setProcessorFormat(Processor, MediaFormatImpl)
     */
    @Override
    protected void setProcessorFormat(
            Processor processor,
            MediaFormatImpl<? extends Format> mediaFormat)
    {
        Format format = mediaFormat.getFormat();

        if ("h263-1998/rtp".equalsIgnoreCase(format.getEncoding()))
        {
            /*
             * If no output size has been defined, then no SDP fmtp has been
             * found with QCIF, CIF, VGA or CUSTOM parameters. Let's default to
             * QCIF (176x144).
             */
            if (outputSize == null)
                outputSize = new Dimension(176, 144);
        }

        /*
         * Add a size in the output format. As VideoFormat has no setter, we
         * recreate the object. Also check whether capture device can output
         * such a size.
         */
        if ((outputSize != null)
                && (outputSize.width > 0)
                && (outputSize.height > 0))
        {
            Dimension deviceSize
                = ((VideoFormat) getCaptureDeviceFormat()).getSize();
            Dimension videoFormatSize;

            if ((deviceSize != null)
                    && ((deviceSize.width > outputSize.width)
                        || (deviceSize.height > outputSize.height)))
            {
                videoFormatSize = outputSize;
            }
            else
            {
                videoFormatSize = deviceSize;
                outputSize = null;
            }

            VideoFormat videoFormat = (VideoFormat) format;

            /*
             * FIXME The assignment to the local variable format makes no
             * difference because it is no longer user afterwards.
             */
            format
                = new VideoFormat(
                        videoFormat.getEncoding(),
                        videoFormatSize,
                        videoFormat.getMaxDataLength(),
                        videoFormat.getDataType(),
                        videoFormat.getFrameRate());
        }
        else
            outputSize = null;

        super.setProcessorFormat(processor, mediaFormat);
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
     * attempt to set the specified <tt>mediaFormat</tt> or <tt>null</tt> if the
     * specified <tt>format</tt> was found to be incompatible with
     * <tt>trackControl</tt>
     * @see MediaDeviceSession#setProcessorFormat(TrackControl, MediaFormatImpl,
     * Format)
     */
    @Override
    protected Format setProcessorFormat(
            TrackControl trackControl,
            MediaFormatImpl<? extends Format> mediaFormat,
            Format format)
    {
        JNIEncoder encoder = null;
        SwScale scaler = null;
        int codecCount = 0;

        /*
         * For H.264 we will monitor RTCP feedback. For example, if we receive a
         * PLI/FIR message, we will send a keyframe.
         */
        /*
         * The current Android video capture device system provided H.264 so it
         * is not possible to insert an H.264 encoder in the chain. Ideally, we
         * will want to base the decision on the format of the capture device
         * and not on the operating system. In a perfect worlds, we will
         * re-implement the functionality bellow using a Control interface and
         * we will not bother with inserting customized codecs.
         */
        if (!OSUtils.IS_ANDROID
                && "h264/rtp".equalsIgnoreCase(format.getEncoding()))
        {
            encoder = new JNIEncoder();

            // packetization-mode
            {
                Map<String, String> formatParameters
                    = mediaFormat.getFormatParameters();
                String packetizationMode
                    = (formatParameters == null)
                        ? null
                        : formatParameters.get(
                                VideoMediaFormatImpl
                                    .H264_PACKETIZATION_MODE_FMTP);

                encoder.setPacketizationMode(packetizationMode);
            }

            // additionalCodecSettings
            {
                encoder.setAdditionalCodecSettings(
                        mediaFormat.getAdditionalCodecSettings());
            }

            this.encoder = encoder;
            onRTCPFeedbackMessageCreate(encoder);
            synchronized (rtcpFeedbackMessageCreateListeners)
            {
                for (RTCPFeedbackMessageCreateListener l
                        : rtcpFeedbackMessageCreateListeners)
                    l.onRTCPFeedbackMessageCreate(encoder);
            }

            if (keyFrameControl != null)
                encoder.setKeyFrameControl(keyFrameControl);

            codecCount++;
        }

        if (outputSize != null)
        {
            /*
             * We have been explicitly told to use a specific output size so
             * insert a SwScale into the codec chain which is to take care of
             * the specified output size. However, since the video frames which
             * it will output will be streamed to a remote peer, preserve the
             * aspect ratio of the input.
             */
            scaler
                = new SwScale(
                        /* fixOddYuv420Size */ false,
                        /* preserveAspectRatio */ true);
            scaler.setOutputSize(outputSize);
            codecCount++;
        }

        Codec[] codecs = new Codec[codecCount];

        codecCount = 0;
        if(scaler != null)
            codecs[codecCount++] = scaler;
        if(encoder != null)
            codecs[codecCount++] = encoder;

        if (codecCount != 0)
        {
            /* Add our custom SwScale and possibly RTCP aware codec to the
             * codec chain so that it will be used instead of default.
             */
            try
            {
                trackControl.setCodecChain(codecs);
            }
            catch(UnsupportedPlugInException upiex)
            {
                logger.error(
                        "Failed to add SwScale/JNIEncoder to codec chain",
                        upiex);
            }
        }

        return super.setProcessorFormat(trackControl, mediaFormat, format);
    }

    /**
     * Set the remote SSRC.
     *
     * @param remoteSSRC remote SSRC
     */
    public void setRemoteSSRC(long remoteSSRC)
    {
        this.remoteSSRC = remoteSSRC;
    }

    /**
     * Sets the indicator which determines whether RTCP feedback Picture Loss
     * Indication (PLI) is to be used to request keyframes.
     *
     * @param useRTCPFeedbackPLI <tt>true</tt> to use PLI; otherwise,
     * <tt>false</tt>
     */
    public void setRTCPFeedbackPLI(boolean useRTCPFeedbackPLI)
    {
        if (this.useRTCPFeedbackPLI != useRTCPFeedbackPLI)
        {
            this.useRTCPFeedbackPLI = useRTCPFeedbackPLI;

            if (this.useRTCPFeedbackPLI)
            {
                if (keyFrameRequester == null)
                {
                    keyFrameRequester
                        = new KeyFrameControl.KeyFrameRequester()
                        {
                            @Override
                            public boolean requestKeyFrame()
                            {
                                return keyFrameRequesterRequestKeyFrame(this);
                            }
                        };
                }
                if (keyFrameControl != null)
                    keyFrameControl.addKeyFrameRequester(-1, keyFrameRequester);
            }
            else if (keyFrameRequester != null)
            {
                if (keyFrameControl != null)
                    keyFrameControl.removeKeyFrameRequester(keyFrameRequester);
                keyFrameRequester = null;
            }
        }
    }

    /**
     * Notifies this instance that the value of its <tt>startedDirection</tt>
     * property has changed from a specific <tt>oldValue</tt> to a specific
     * <tt>newValue</tt>.
     *
     * @param oldValue the <tt>MediaDirection</tt> which used to be the value of
     * the <tt>startedDirection</tt> property of this instance
     * @param newValue the <tt>MediaDirection</tt> which is the value of the
     * <tt>startedDirection</tt> property of this instance
     */
    @Override
    protected void startedDirectionChanged(
            MediaDirection oldValue,
            MediaDirection newValue)
    {
        super.startedDirectionChanged(oldValue, newValue);

        try
        {
            Player localPlayer;

            synchronized (localPlayerSyncRoot)
            {
                localPlayer = getLocalPlayer();
            }
            if (newValue.allowsSending())
            {
                if (localPlayer == null)
                    createLocalVisualComponent();
            }
            else if (localPlayer != null)
            {
                disposeLocalPlayer(localPlayer);
            }
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
            {
                logger.error(
                        "Failed to start/stop the preview of the local video",
                        t);
            }
        }

        /*
         * Translate the starting and stopping of the playback into respective
         * VideoEvents for the REMOTE origin.
         */
        for (Player player : getPlayers())
        {
            int state = player.getState();

            /*
             * The visual Component of a Player is safe to access and,
             * respectively, report through a VideoEvent only when the Player is
             * Realized.
             */
            if (state < Player.Realized)
                continue;

            if (newValue.allowsReceiving())
            {
                if (state != Player.Started)
                {
                    player.start();

                    Component visualComponent = getVisualComponent(player);

                    if (visualComponent != null)
                    {
                        fireVideoEvent(
                                VideoEvent.VIDEO_ADDED,
                                visualComponent,
                                VideoEvent.REMOTE,
                                false);
                    }
                }
            }
            else if (state > Processor.Configured)
            {
                Component visualComponent = getVisualComponent(player);

                player.stop();

                if (visualComponent != null)
                {
                    fireVideoEvent(
                            VideoEvent.VIDEO_REMOVED,
                            visualComponent,
                            VideoEvent.REMOTE,
                            false);
                }
            }
        }
    }

    /**
     * Return the <tt>Player</tt> instance which provides the local visual/video
     * <tt>Component</tt>.
     * @return the <tt>Player</tt> instance which provides the local visual/video
     * <tt>Component</tt>.
     */
    protected Player getLocalPlayer()
    {
        synchronized (localPlayerSyncRoot)
        {
            return localPlayer;
        }
    }

    /**
     * Extends <tt>SwScale</tt> in order to provide scaling with high quality
     * to a specific <tt>Player</tt> of remote video.
     */
    private class PlayerScaler
        extends SwScale
    {
        /**
         * The last size reported in the form of a <tt>SizeChangeEvent</tt>.
         */
        private Dimension lastSize;

        /**
         * The <tt>Player</tt> into the codec chain of which this
         * <tt>SwScale</tt> is set.
         */
        private final Player player;

        /**
         * Initializes a new <tt>PlayerScaler</tt> instance which is to provide
         * scaling with high quality to a specific <tt>Player</tt> of remote
         * video.
         *
         * @param player the <tt>Player</tt> of remote video into the codec
         * chain of which the new instance is to be set
         */
        public PlayerScaler(Player player)
        {
            super(true);

            this.player = player;
        }

        /**
         * Determines when the input video sizes changes and reports it as a
         * <tt>SizeChangeVideoEvent</tt> because <tt>Player</tt> is unable to
         * do it when this <tt>SwScale</tt> is scaling to a specific
         * <tt>outputSize</tt>.
         *
         * @param input input buffer
         * @param output output buffer
         * @return the native <tt>PaSampleFormat</tt>
         * @see SwScale#process(Buffer, Buffer)
         */
        @Override
        public int process(Buffer input, Buffer output)
        {
            int result = super.process(input, output);

            if (result == BUFFER_PROCESSED_OK)
            {
                Format inputFormat = getInputFormat();

                if (inputFormat != null)
                {
                    Dimension size = ((VideoFormat) inputFormat).getSize();

                    if ((size != null)
                            && (size.height >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                            && (size.width >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                            && !size.equals(lastSize))
                    {
                        lastSize = size;
                        playerSizeChange(
                                player,
                                VideoEvent.REMOTE,
                                lastSize.width, lastSize.height);
                    }
                }
            }
            return result;
        }

        /**
         * Ensures that this <tt>SwScale</tt> preserves the aspect ratio of its
         * input video when scaling.
         *
         * @param inputFormat format to set
         * @return format
         * @see SwScale#setInputFormat(Format)
         */
        @Override
        public Format setInputFormat(Format inputFormat)
        {
            inputFormat = super.setInputFormat(inputFormat);
            if (inputFormat instanceof VideoFormat)
            {
                Dimension inputSize = ((VideoFormat) inputFormat).getSize();

                if ((inputSize != null) && (inputSize.width > 0))
                {
                    Dimension outputSize = getOutputSize();
                    int outputWidth;

                    if ((outputSize != null)
                            && ((outputWidth = outputSize.width) > 0))
                    {
                        int outputHeight
                            = (int)
                                (outputWidth
                                    * inputSize.height
                                    / (float) inputSize.width);
                        int outputHeightDelta
                            = outputHeight - outputSize.height;

                        if ((outputHeightDelta < -1) || (outputHeightDelta > 1))
                        {
                             super.setOutputSize(
                                     new Dimension(outputWidth, outputHeight));
                        }
                    }
                }
            }
            return inputFormat;
        }
    }
}
