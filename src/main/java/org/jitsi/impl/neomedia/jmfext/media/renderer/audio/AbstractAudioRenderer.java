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
package org.jitsi.impl.neomedia.jmfext.media.renderer.audio;

import java.beans.*;
import java.nio.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.*;
import org.jitsi.service.neomedia.*;

/**
 * Provides an abstract base implementation of <tt>Renderer</tt> which processes
 * media in <tt>AudioFormat</tt> in order to facilitate extenders.
 *
 * @param <T> the runtime type of the <tt>AudioSystem</tt> which provides the
 * playback device used by the <tt>AbstractAudioRenderer</tt>
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractAudioRenderer<T extends AudioSystem>
    extends AbstractRenderer<AudioFormat>
{
    /**
     * The byte order of the running Java virtual machine expressed in the
     * <tt>endian</tt> term of {@link AudioFormat}.
     */
    public static final int JAVA_AUDIO_FORMAT_ENDIAN = AudioFormat.BIG_ENDIAN;

    /**
     * The native byte order of the hardware upon which this Java virtual
     * machine is running expressed in the <tt>endian</tt> term of
     * {@link AudioFormat}.
     */
    public static final int NATIVE_AUDIO_FORMAT_ENDIAN
        = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
            ? AudioFormat.BIG_ENDIAN
            : AudioFormat.LITTLE_ENDIAN;

    /**
     * The <tt>AudioSystem</tt> which provides the playback device used by this
     * <tt>Renderer</tt>.
     */
    protected final T audioSystem;

    /**
     * The flow of the media data (to be) implemented by this instance which is
     * either {@link AudioSystem.DataFlow#NOTIFY} or
     * {@link AudioSystem.DataFlow#PLAYBACK}.
     */
    protected final AudioSystem.DataFlow dataFlow;

    /**
     * The <tt>GainControl</tt> through which the volume/gain of the media
     * rendered by this instance is (to be) controlled.
     */
    private GainControl gainControl;

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
            public void propertyChange(PropertyChangeEvent ev)
            {
                AbstractAudioRenderer.this.propertyChange(ev);
            }
        };

    /**
     * The <tt>VolumeControl</tt> through which the volume/gain of the media
     * rendered by this instance is (to be) controlled. If non-<tt>null</tt>,
     * overrides {@link #gainControl}.
     */
    private VolumeControl volumeControl;

    /**
     * Initializes a new <tt>AbstractAudioRenderer</tt> instance which is to use
     * playback devices provided by a specific <tt>AudioSystem</tt>.
     *
     * @param audioSystem the <tt>AudioSystem</tt> which is to provide the
     * playback devices to be used by the new instance
     */
    protected AbstractAudioRenderer(T audioSystem)
    {
        this(audioSystem, AudioSystem.DataFlow.PLAYBACK);
    }

    /**
     * Initializes a new <tt>AbstractAudioRenderer</tt> instance which is to use
     * notification or playback (as determined by <tt>dataFlow</tt>) devices
     * provided by a specific <tt>AudioSystem</tt>.
     *
     * @param audioSystem the <tt>AudioSystem</tt> which is to provide the
     * notification or playback devices to be used by the new instance
     * @param dataFlow the flow of the media data to be implemented by the new
     * instance i.e. whether notification or playback devices provided by the
     * specified <tt>audioSystem</tt> are to be used by the new instance. Must
     * be either {@link AudioSystem.DataFlow#NOTIFY} or
     * {@link AudioSystem.DataFlow#PLAYBACK}.
     * @throws IllegalArgumentException if the specified <tt>dataFlow</tt> is
     * neither <tt>AudioSystem.DataFlow.NOTIFY</tt> nor
     * <tt>AudioSystem.DataFlow.PLAYBACK</tt>
     */
    protected AbstractAudioRenderer(
            T audioSystem,
            AudioSystem.DataFlow dataFlow)
    {
        if ((dataFlow != AudioSystem.DataFlow.NOTIFY)
                && (dataFlow != AudioSystem.DataFlow.PLAYBACK))
            throw new IllegalArgumentException("dataFlow");

        this.audioSystem = audioSystem;
        this.dataFlow = dataFlow;

        if (AudioSystem.DataFlow.PLAYBACK.equals(dataFlow))
        {
            /*
             * XXX The Renderer implementations are probed for their
             * supportedInputFormats during the initialization of
             * MediaServiceImpl so the latter may not be available at this time.
             * Which is not much of a problem given than the GainControl is of
             * no interest during the probing of the supportedInputFormats.
             */
            MediaServiceImpl mediaServiceImpl
                = NeomediaServiceUtils.getMediaServiceImpl();

            gainControl
                = (mediaServiceImpl == null)
                    ? null
                    : (GainControl) mediaServiceImpl.getOutputVolumeControl();
        }
        else
            gainControl = null;
    }

    /**
     * Initializes a new <tt>AbstractAudioRenderer</tt> instance which is to use
     * playback devices provided by an <tt>AudioSystem</tt> specified by the
     * protocol of the <tt>MediaLocator</tt>s of the <tt>CaptureDeviceInfo</tt>s
     * registered by the <tt>AudioSystem</tt>.
     *
     * @param locatorProtocol the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt> registered by the <tt>AudioSystem</tt> which
     * is to provide the playback devices to be used by the new instance
     */
    protected AbstractAudioRenderer(String locatorProtocol)
    {
        this(locatorProtocol, AudioSystem.DataFlow.PLAYBACK);
    }

    /**
     * Initializes a new <tt>AbstractAudioRenderer</tt> instance which is to use
     * notification or playback devices provided by an <tt>AudioSystem</tt>
     * specified by the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt>s registered by the <tt>AudioSystem</tt>.
     *
     * @param locatorProtocol the protocol of the <tt>MediaLocator</tt>s of the
     * <tt>CaptureDeviceInfo</tt> registered by the <tt>AudioSystem</tt> which
     * is to provide the notification or playback devices to be used by the new
     * instance
     * @param dataFlow the flow of the media data to be implemented by the new
     * instance i.e. whether notification or playback devices provided by the
     * specified <tt>audioSystem</tt> are to be used by the new instance. Must
     * be either {@link AudioSystem.DataFlow#NOTIFY} or
     * {@link AudioSystem.DataFlow#PLAYBACK}.
     * @throws IllegalArgumentException if the specified <tt>dataFlow</tt> is
     * neither <tt>AudioSystem.DataFlow.NOTIFY</tt> nor
     * <tt>AudioSystem.DataFlow.PLAYBACK</tt>
     */
    @SuppressWarnings("unchecked")
    protected AbstractAudioRenderer(
            String locatorProtocol,
            AudioSystem.DataFlow dataFlow)
    {
        this((T) AudioSystem.getAudioSystem(locatorProtocol), dataFlow);
    }

    /**
     * {@inheritDoc}
     */
    public void close()
    {
        if (audioSystem != null)
            audioSystem.removePropertyChangeListener(propertyChangeListener);
    }

    /**
     * Implements {@link javax.media.Controls#getControls()}. Gets the available
     * controls over this instance. <tt>AbstractAudioRenderer</tt> returns a
     * {@link GainControl} if available.
     *
     * @return an array of <tt>Object</tt>s which represent the available
     * controls over this instance
     */
    @Override
    public Object[] getControls()
    {
        GainControl gainControl = getGainControl();

        return
            (gainControl == null)
                ? super.getControls()
                : new Object[] { gainControl };
    }

    /**
     * Gets the <tt>GainControl</tt>, if any, which controls the volume level of
     * the audio (to be) played back by this <tt>Renderer</tt>.
     *
     * @return the <tt>GainControl</tt>, if any, which controls the volume level
     * of the audio (to be) played back by this <tt>Renderer</tt>
     */
    protected GainControl getGainControl()
    {
        VolumeControl volumeControl = this.volumeControl;
        GainControl gainControl = this.gainControl;

        if (volumeControl instanceof GainControl)
            gainControl = (GainControl) volumeControl;

        return gainControl;
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
            CaptureDeviceInfo device = audioSystem.getSelectedDevice(dataFlow);

            if (device != null)
                locator = device.getLocator();
        }
        return locator;
    }

    /**
     * {@inheritDoc}
     */
    public Format[] getSupportedInputFormats()
    {
        /*
         * XXX If the AudioSystem (class) associated with this Renderer (class
         * and its instances) fails to initialize, the following may throw a
         * NullPointerException. Such a throw should be considered appropriate.
         */
        return audioSystem.getDevice(dataFlow, getLocator()).getFormats();
    }

    /**
     * {@inheritDoc}
     */
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
            /*
             * We actually want to allow the user to switch the playback and/or
             * notify device to none mid-stream in order to disable the
             * playback. If an extender does not want to support that behavior,
             * they will throw an exception and/or not call this implementation
             * anyway.
             */
            audioSystem.addPropertyChangeListener(propertyChangeListener);
        }
    }

    /**
     * Notifies this instance that the value of the property of
     * {@link #audioSystem} which identifies the default notification or
     * playback (as determined by {@link #dataFlow}) device has changed. The
     * default implementation does nothing so extenders may safely not call back
     * to their <tt>AbstractAudioRenderer</tt> super.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which specifies details about
     * the change such as the name of the property and its old and new values
     */
    protected void playbackDevicePropertyChange(PropertyChangeEvent ev)
    {
    }

    /**
     * Notifies this instance about a specific <tt>PropertyChangeEvent</tt>.
     * <tt>AbstractAudioRenderer</tt> listens to changes in the values of the
     * properties of {@link #audioSystem}.
     *
     * @param ev the <tt>PropertyChangeEvent</tt> to notify this instance about
     */
    private void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName;

        switch (dataFlow)
        {
        case NOTIFY:
            propertyName = NotifyDevices.PROP_DEVICE;
            break;
        case PLAYBACK:
            propertyName = PlaybackDevices.PROP_DEVICE;
            break;
        default:
            // The value of the field dataFlow is either NOTIFY or PLAYBACK.
            return;
        }
        if (propertyName.equals(ev.getPropertyName()))
            playbackDevicePropertyChange(ev);
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

    /**
     * Sets the <tt>VolumeControl</tt> which is to control the volume (level) of
     * the audio (to be) played back by this <tt>Renderer</tt>.
     *
     * @param volumeControl the <tt>VolumeControl</tt> which is to control the
     * volume (level) of the audio (to be) played back by this <tt>Renderer</tt>
     */
    public void setVolumeControl(VolumeControl volumeControl)
    {
        this.volumeControl = volumeControl;
    }

    /**
     * Changes the priority of the current thread to a value which is considered
     * appropriate for the purposes of audio processing.
     */
    public static void useAudioThreadPriority()
    {
        useThreadPriority(MediaThread.getAudioPriority());
    }
}
