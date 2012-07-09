/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import java.beans.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.*;

/**
 * Provides an abstract base implementation of <tt>Renderer</tt> which processes
 * media in <tt>AudioFormat</tt> in order to facilitate extenders.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractAudioRenderer
    extends AbstractRenderer<AudioFormat>
{
    /**
     * The <tt>AudioSystem</tt> which provides the playback deviced used by this
     * <tt>Renderer</tt>.
     */
    protected final AudioSystem audioSystem;

    /**
     * The <tt>MediaLocator</tt> which specifies the playback device to be used
     * by this <tt>Renderer</tt>.
     */
    private MediaLocator locator;

    /**
     * The <tt>PropertyChangeListener</tt> which listens to changes in the
     * values of the properties of {@link #audioSystem}.
     */
    private final PropertyChangeListener propertyChangeListener
        = new PropertyChangeListener()
        {
            public void propertyChange(PropertyChangeEvent event)
            {
                AbstractAudioRenderer.this.propertyChange(event);
            }
        };

    protected AbstractAudioRenderer(AudioSystem audioSystem)
    {
        this.audioSystem = audioSystem;
    }

    protected AbstractAudioRenderer(String locatorProtocol)
    {
        this(AudioSystem.getAudioSystem(locatorProtocol));
    }

    public void close()
    {
        if (audioSystem != null)
            audioSystem.removePropertyChangeListener(propertyChangeListener);
    }

    /**
     * Gets the <tt>MediaLocator</tt> which specifies the playback device to be
     * used by this <tt>Renderer</tt>.
     *
     * @return the <tt>MediaLocator</tt> which specifies the playback device to
     * be used by this <tt>Renderer</tt>
     */
    public MediaLocator getLocator()
    {
        MediaLocator locator = this.locator;

        if ((locator == null) && (audioSystem != null))
        {
            CaptureDeviceInfo playbackDevice
                = audioSystem.getPlaybackDevice();

            if (playbackDevice != null)
                locator = playbackDevice.getLocator();
        }
        return locator;
    }

    public void open()
        throws ResourceUnavailableException
    {
        /*
         * If this Renderer has not been forced to use a playback device with a
         * specific MediaLocator, it will use the default playback device (of
         * its associated AudioSystem). In the case of using the default
         * playback device, change the playback device used by this instance
         * upon changes of the default playback device.
         */
        if ((this.locator == null) && (audioSystem != null))
        {
            MediaLocator locator = getLocator();

            if (locator != null)
                audioSystem.addPropertyChangeListener(propertyChangeListener);
        }
    }

    private void propertyChange(PropertyChangeEvent event)
    {
        if (AudioSystem.PROP_PLAYBACK_DEVICE.equals(event.getPropertyName()))
            reset();
    }

    /**
     * Sets the <tt>MediaLocator</tt> which specifies the playback device to be
     * used by this <tt>Renderer</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> which specifies the playback
     * device to be used by this <tt>Renderer</tt>
     */
    public void setLocator(MediaLocator locator)
    {
        if (this.locator == null)
        {
            if (locator == null)
                return;
        }
        else if (this.locator.equals(locator))
            return;

        this.locator = locator;
    }
}
