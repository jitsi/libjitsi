/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.awt.*;
import java.util.*;
import java.util.List;

import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.event.*;

/**
 * Extends the <tt>MediaStream</tt> interface and adds methods specific to
 * video streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface VideoMediaStream
    extends MediaStream
{
    /**
     * Gets the visual <tt>Component</tt>, if any, depicting the video streamed
     * from the local peer to the remote peer.
     *
     * @return the visual <tt>Component</tt> depicting the local video if local
     * video is actually being streamed from the local peer to the remote peer;
     * otherwise, <tt>null</tt>
     */
    public Component getLocalVisualComponent();

    /**
     * Gets the visual <tt>Component</tt> where video from the remote peer is
     * being rendered or <tt>null</tt> if no video is currently being rendered.
     *
     * @return the visual <tt>Component</tt> where video from the remote peer is
     * being rendered or <tt>null</tt> if no video is currently being rendered
     * @deprecated Since multiple videos may be received from the remote peer
     * and rendered, it is not clear which one of them is to be singled out as
     * the return value. Thus {@link #getVisualComponent(long)} and
     * {@link #getVisualComponents()} are to be used instead.
     */
    @Deprecated
    public Component getVisualComponent();

    /**
     * Gets the visual <tt>Component</tt> rendering the <tt>ReceiveStream</tt>
     * with a specific SSRC.
     *
     * @param ssrc the SSRC of the <tt>ReceiveStream</tt> to get the associated
     * rendering visual <tt>Component</tt> of
     * @return the visual <tt>Component</tt> rendering the
     * <tt>ReceiveStream</tt> with the specified <tt>ssrc</tt> if any;
     * otherwise, <tt>null</tt>
     */
    public Component getVisualComponent(long ssrc);

    /**
     * Gets a list of the visual <tt>Component</tt>s where video from the remote
     * peer is being rendered.
     *
     * @return a list of the visual <tt>Component</tt>s where video from the
     * remote peer is being rendered
     */
    public List<Component> getVisualComponents();

    /**
     * Adds a specific <tt>VideoListener</tt> to this <tt>VideoMediaStream</tt>
     * in order to receive notifications when visual/video <tt>Component</tt>s
     * are being added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is
     * not added more than once and thus does not receive one and the same
     * <tt>VideoEvent</tt> multiple times
     * </p>
     *
     * @param listener the <tt>VideoListener</tt> to be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * <tt>VideoMediaStream</tt>
     */
    public void addVideoListener(VideoListener listener);

    /**
     * Removes a specific <tt>VideoListener</tt> from this
     * <tt>VideoMediaStream</tt> in order to have to no longer receive
     * notifications when visual/video <tt>Component</tt>s are being added and
     * removed.
     *
     * @param listener the <tt>VideoListener</tt> to no longer be notified when
     * visual/video <tt>Component</tt>s are being added or removed in this
     * <tt>VideoMediaStream</tt>
     */
    public void removeVideoListener(VideoListener listener);

    /**
     * Gets the <tt>KeyFrameControl</tt> of this <tt>VideoMediaStream</tt>.
     *
     * @return the <tt>KeyFrameControl</tt> of this <tt>VideoMediaStream</tt>
     */
    public KeyFrameControl getKeyFrameControl();

    /**
     * Gets the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>.
     *
     * @return the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>
     */
    public QualityControl getQualityControl();

    /**
     * Updates the <tt>QualityControl</tt> of this <tt>VideoMediaStream</tt>.
     *
     * @param advancedParams parameters of advanced attributes that may affect
     * quality control
     */
    public void updateQualityControl(Map<String, String> advancedParams);

    /**
     * Move origin of a partial desktop streaming <tt>MediaDevice</tt>.
     *
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    public void movePartialDesktopStreaming(int x, int y);
}
