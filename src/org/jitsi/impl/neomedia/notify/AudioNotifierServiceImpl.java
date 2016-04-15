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
package org.jitsi.impl.neomedia.notify;

import java.beans.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.audionotifier.*;

/**
 * The implementation of <tt>AudioNotifierService</tt>.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public class AudioNotifierServiceImpl
    implements AudioNotifierService,
               PropertyChangeListener
{
    /**
     * The cache of <tt>SCAudioClip</tt> instances which we may reuse. The reuse
     * is complex because a <tt>SCAudioClip</tt> may be used by a single user at
     * a time.
     */
    private Map<AudioKey, SCAudioClip> audios;

    /**
     * The <tt>Object</tt> which synchronizes the access to {@link #audios}.
     */
    private final Object audiosSyncRoot = new Object();

    /**
     * The <tt>DeviceConfiguration</tt> which provides information about the
     * notify and playback devices on which this instance plays
     * <tt>SCAudioClip</tt>s.
     */
    private final DeviceConfiguration deviceConfiguration;

    /**
     * The indicator which determined whether <tt>SCAudioClip</tt>s are to be
     * played by this instance.
     */
    private boolean mute;

    /**
     * Initializes a new <tt>AudioNotifierServiceImpl</tt> instance.
     */
    public AudioNotifierServiceImpl()
    {
        this.deviceConfiguration
            = NeomediaServiceUtils
                .getMediaServiceImpl()
                    .getDeviceConfiguration();

        this.deviceConfiguration.addPropertyChangeListener(this);
    }

    /**
     * Checks whether the playback and notification configuration share the same
     * device.
     *
     * @return are audio out and notifications using the same device.
     */
    public boolean audioOutAndNotificationsShareSameDevice()
    {
        AudioSystem audioSystem = getDeviceConfiguration().getAudioSystem();
        CaptureDeviceInfo notify
            = audioSystem.getSelectedDevice(AudioSystem.DataFlow.NOTIFY);
        CaptureDeviceInfo playback
            = audioSystem.getSelectedDevice(AudioSystem.DataFlow.PLAYBACK);

        if (notify == null)
            return (playback == null);
        else
        {
            if (playback == null)
                return false;
            else
                return notify.getLocator().equals(playback.getLocator());
        }
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of
     * available audio-s. Uses notification device if any.
     *
     * @param uri the path where the audio file could be found
     * @return a newly created <tt>SCAudioClip</tt> from <tt>uri</tt>
     */
    public SCAudioClip createAudio(String uri)
    {
        return createAudio(uri, false);
    }

    /**
     * Creates an SCAudioClip from the given URI and adds it to the list of
     * available audio-s.
     *
     * @param uri the path where the audio file could be found
     * @param playback use or not the playback device.
     * @return a newly created <tt>SCAudioClip</tt> from <tt>uri</tt>
     */
    public SCAudioClip createAudio(String uri, boolean playback)
    {
        SCAudioClip audio;

        synchronized (audiosSyncRoot)
        {
            final AudioKey key = new AudioKey(uri, playback);

            /*
             * While we want to reuse the SCAudioClip instances, they may be
             * used by a single user at a time. That's why we'll forget about
             * them while they are in use and we'll reclaim them when they are
             * no longer in use.
             */
            audio = (audios == null) ? null : audios.remove(key);

            if (audio == null)
            {
                try
                {
                    AudioSystem audioSystem
                        = getDeviceConfiguration().getAudioSystem();

                    if (audioSystem == null)
                    {
                        audio = new JavaSoundClipImpl(uri, this);
                    }
                    else if (NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(
                            audioSystem.getLocatorProtocol()))
                    {
                        audio = null;
                    }
                    else
                    {
                        audio
                            = new AudioSystemClipImpl(
                                    uri,
                                    this,
                                    audioSystem,
                                    playback);
                    }
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                    {
                        /*
                         * Could not initialize a new SCAudioClip instance to be
                         * played.
                         */
                        return null;
                    }
                }
            }

            /*
             * Make sure that the SCAudioClip will be reclaimed for reuse when
             * it is no longer in use.
             */
            if (audio != null)
            {
                if (audios == null)
                    audios = new HashMap<AudioKey, SCAudioClip>();

                /*
                 * We have to return in the Map which was active at the time the
                 * SCAudioClip was initialized because it may have become
                 * invalid if the playback or notify audio device changed.
                 */
                final Map<AudioKey, SCAudioClip> finalAudios = audios;
                final SCAudioClip finalAudio = audio;

                audio
                    = new SCAudioClip()
                    {
                        /**
                         * Evaluates a specific <tt>loopCondition</tt> as
                         * defined by
                         * {@link SCAudioClip#play(int,Callable)}.
                         *
                         * @param loopCondition the
                         * <tt>Callable&lt;Boolean&gt;</tt> which represents the
                         * <tt>loopCondition</tt> to be evaluated
                         * @return {@link Boolean#FALSE} if
                         * <tt>loopCondition</tt> is <tt>null</tt>; otherwise,
                         * the value returned by invoking
                         * {@link Callable#call()} on the specified
                         * <tt>loopCondition</tt>
                         * @throws Exception if the specified
                         * <tt>loopCondition</tt> throws an <tt>Exception</tt>
                         */
                        private Boolean evaluateLoopCondition(
                                Callable<Boolean> loopCondition)
                            throws Exception
                        {
                            /*
                             * SCAudioClip.play(int,Callable<Boolean>) is
                             * documented to play the SCAudioClip once only if
                             * the loopCondition is null. The same will be
                             * accomplished by returning Boolean.FALSE.
                             */
                            return
                                (loopCondition == null)
                                    ? Boolean.FALSE
                                    : loopCondition.call();
                        }

                        /**
                         * {@inheritDoc}
                         *
                         * Returns the wrapped <tt>SCAudioClip</tt> into the
                         * cache from it has earlier been retrieved in order to
                         * allow its reuse. 
                         */
                        @Override
                        protected void finalize()
                            throws Throwable
                        {
                            try
                            {
                                synchronized (audios)
                                {
                                    finalAudios.put(key, finalAudio);
                                }
                            }
                            finally
                            {
                                super.finalize();
                            }
                        }

                        public void play()
                        {
                            /*
                             * SCAudioClip.play() is documented to behave as if
                             * loopInterval is negative and/or loopCondition is
                             * null. We have to take care that this instance
                             * does not get garbage collected until the
                             * finalAudio finishes playing so we will delegate
                             * to this instance's implementation of
                             * SCAudioClip.play(int,Callable<Boolean>) instead
                             * of to the finalAudio's.
                             */
                            play(-1, null);
                        }

                        public void play(
                                int loopInterval,
                                final Callable<Boolean> finalLoopCondition)
                        {
                            /*
                             * We have to make sure that this instance does not
                             * get garbage collected before the finalAudio
                             * finishes playing. The argument loopCondition of
                             * the method
                             * SCAudioClip.play(int,Callable<Boolean>) will
                             * live/be referenced during that time so we will
                             * use it to hold on to this instance.
                             */
                            Callable<Boolean> loopCondition
                                = new Callable<Boolean>()
                                {
                                    public Boolean call()
                                        throws Exception
                                    {
                                        return
                                            evaluateLoopCondition(
                                                    finalLoopCondition);
                                    }
                                };

                            finalAudio.play(loopInterval, loopCondition);
                        }

                        public void stop()
                        {
                            finalAudio.stop();
                        }

                        /**
                         * Determines whether this audio is started i.e. a
                         * <tt>play</tt> method was invoked and no subsequent
                         * <tt>stop</tt> has been invoked yet.
                         *
                         * @return <tt>true</tt> if this audio is started;
                         * otherwise, <tt>false</tt>
                         */
                        public boolean isStarted()
                        {
                            return finalAudio.isStarted();
                        }
                    };
            }
        }

        return audio;
    }

    /**
     * The device configuration.
     *
     * @return the deviceConfiguration
     */
    public DeviceConfiguration getDeviceConfiguration()
    {
        return deviceConfiguration;
    }

    /**
     * Returns <tt>true</tt> if the sound is currently disabled; <tt>false</tt>,
     * otherwise.
     *
     * @return <tt>true</tt> if the sound is currently disabled; <tt>false</tt>,
     * otherwise
     */
    public boolean isMute()
    {
        return mute;
    }

    /**
     * Listens for changes in notify device.
     *
     * @param ev the event that notify device has changed.
     */
    public void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if (DeviceConfiguration.AUDIO_NOTIFY_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_PLAYBACK_DEVICE.equals(
                        propertyName))
        {
            synchronized (audiosSyncRoot)
            {
                /*
                 * Make sure that the currently referenced SCAudioClips will not
                 * be reclaimed.
                 */
                audios = null;
            }
        }
    }

    /**
     * Enables or disables the sound in the application. If <tt>false</tt>, we
     * try to restore all looping sounds if any.
     *
     * @param mute when <tt>true</tt> disables the sound; otherwise, enables the
     * sound.
     */
    public void setMute(boolean mute)
    {
        // TODO Auto-generated method stub
        this.mute = mute;
    }

    /**
     * Implements the key of {@link AudioNotifierServiceImpl#audios}. Combines
     * the <tt>uri</tt> of the <tt>SCAudioClip</tt> with the indicator which
     * determines whether the <tt>SCAudioClip</tt> in question uses the playback
     * or the notify audio device.
     */
    private static class AudioKey
    {
        /**
         * Is it playback?
         */
        private final boolean playback;

        /**
         * The uri.
         */
        final String uri;

        /**
         * Initializes a new <tt>AudioKey</tt> instance.
         *
         * @param uri
         * @param playback
         */
        private AudioKey(String uri, boolean playback)
        {
            this.uri = uri;
            this.playback = playback;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o)
        {
            if (o == this)
                return true;
            if (!(o instanceof AudioKey))
                return false;

            AudioKey that = (AudioKey) o;

            return
                (playback == that.playback)
                    && ((uri == null)
                            ? (that.uri == null)
                            : uri.equals(that.uri));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode()
        {
            return ((uri == null) ? 0 : uri.hashCode()) + (playback ? 1 : 0);
        }
    }
}
