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

import java.beans.*;
import java.io.*;
import java.net.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.audiolevel.*;
import org.jitsi.impl.neomedia.conference.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>MediaDevice</tt> which performs audio mixing using
 * {@link AudioMixer}.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 */
public class AudioMixerMediaDevice
    extends AbstractMediaDevice
    implements MediaDeviceWrapper
{
    /**
     * The <tt>Logger</tt> used by <tt>AudioMixerMediaDevice</tt> and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioMixerMediaDevice.class);

    /**
     * The <tt>AudioMixer</tt> which performs audio mixing in this
     * <tt>MediaDevice</tt> (and rather the session that it represents).
     */
    private AudioMixer audioMixer;

    /**
     * The actual <tt>AudioMediaDeviceImpl</tt> wrapped by this instance for the
     * purposes of audio mixing and used by {@link #audioMixer} as its
     * <tt>CaptureDevice</tt>.
     */
    private final AudioMediaDeviceImpl device;

    /**
     * The <tt>MediaDeviceSession</tt> of this <tt>AudioMixer</tt> with
     * {@link #device}.
     */
    private AudioMixerMediaDeviceSession deviceSession;

    /**
     * The <tt>SimpleAudioLevelListener</tt> which is registered (or is to be
     * registered) with {@link #localUserAudioLevelDispatcher} and which
     * delivers each of the audio level changes to
     * {@link #localUserAudioLevelListeners}.
     */
    private final SimpleAudioLevelListener localUserAudioLevelDelegate
        = new SimpleAudioLevelListener()
        {
            public void audioLevelChanged(int level)
            {
                lastMeasuredLocalUserAudioLevel = level;
                fireLocalUserAudioLevelChanged(level);
            }
        };

    /**
     * The dispatcher that delivers to listeners calculations of the local
     * audio level.
     */
    private final AudioLevelEventDispatcher localUserAudioLevelDispatcher
        = new AudioLevelEventDispatcher(
                "Local User Audio Level Dispatcher (Mixer Edition)");

    /**
     * The <tt>List</tt> where we store all listeners interested in changes of
     * the local audio level and the number of times each one of them has been
     * added. We wrap listeners because we may have multiple subscriptions with
     * the same listener and we would only store it once. If one of the multiple
     * subscriptions of a particular listener is removed, however, we wouldn't
     * want to reset the listener to <tt>null</tt> as there are others still
     * interested, and hence the <tt>referenceCount</tt> in the wrapper.
     * <p>
     * <b>Note</b>: <tt>localUserAudioLevelListeners</tt> is a copy-on-write
     * storage and access to it is synchronized by
     * {@link #localUserAudioLevelListenersSyncRoot}.
     * </p>
     */
    private List<SimpleAudioLevelListenerWrapper>
        localUserAudioLevelListeners
            = new ArrayList<SimpleAudioLevelListenerWrapper>();

    /**
     * The <tt>Object</tt> which synchronizes the access to
     * {@link #localUserAudioLevelListeners}.
     */
    private final Object localUserAudioLevelListenersSyncRoot = new Object();

    /**
     * The levels map that we use to cache last measured audio levels for all
     * streams associated with this mixer.
     */
    private final AudioLevelMap audioLevelCache = new AudioLevelMap();

    /**
     * The most recently measured level of the locally captured audio stream.
     */
    private int lastMeasuredLocalUserAudioLevel = 0;

    /**
     * The <tt>List</tt> of RTP extensions supported by this device (at the time
     * of writing this list is only filled for audio devices and is
     * <tt>null</tt> otherwise).
     */
    private List<RTPExtension> rtpExtensions = null;

    /**
     * The <tt>Map</tt> where we store audio level dispatchers and the
     * streams they are interested in.
     */
    private final Map<ReceiveStream, AudioLevelEventDispatcher>
        streamAudioLevelListeners
            = new HashMap<ReceiveStream, AudioLevelEventDispatcher>();

    /**
     * The <tt>ReceiveStreamBufferListener</tt> which gets notified when this
     * <tt>MediaDevice</tt> reads from the <tt>CaptureDevice</tt> to the
     * <tt>AudioMixer</tt>
     */
    private ReceiveStreamBufferListener receiveStreamBufferListener;

    /**
     * Initializes a new <tt>AudioMixerMediaDevice</tt> instance which is to
     * enable audio mixing on a specific <tt>AudioMediaDeviceImpl</tt>.
     *
     * @param device the <tt>AudioMediaDeviceImpl</tt> which the new instance is
     * to enable audio mixing on
     */
    public AudioMixerMediaDevice(AudioMediaDeviceImpl device)
    {
        /*
         * AudioMixer is initialized with a CaptureDevice so we have to be sure
         * that the wrapped device can provide one.
         */
        if (!device.getDirection().allowsSending())
            throw
                new IllegalArgumentException("device must be able to capture");

        this.device = device;
    }

    /**
     * Connects to a specific <tt>CaptureDevice</tt> given in the form of a
     * <tt>DataSource</tt>.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> to be connected to
     * @throws IOException if anything wrong happens while connecting to the
     * specified <tt>captureDevice</tt>
     * @see AbstractMediaDevice#connect(DataSource)
     */
    @Override
    public void connect(DataSource captureDevice)
        throws IOException
    {
        DataSource effectiveCaptureDevice = captureDevice;

        /*
         * Unwrap wrappers of the captureDevice until
         * AudioMixingPushBufferDataSource is found.
         */
        if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
            captureDevice
                = ((PushBufferDataSourceDelegate<?>) captureDevice)
                    .getDataSource();

        /*
         * AudioMixingPushBufferDataSource is definitely not a CaptureDevice
         * and does not need the special connecting defined by
         * AbstractMediaDevice and MediaDeviceImpl.
         */
        if (captureDevice instanceof AudioMixingPushBufferDataSource)
            effectiveCaptureDevice.connect();
        else
            device.connect(effectiveCaptureDevice);
    }

    /**
     * Creates a <tt>DataSource</tt> instance for this <tt>MediaDevice</tt>
     * which gives access to the captured media.
     *
     * @return a <tt>DataSource</tt> instance which gives access to the media
     * captured by this <tt>MediaDevice</tt>
     * @see AbstractMediaDevice#createOutputDataSource()
     */
    @Override
    public AudioMixingPushBufferDataSource createOutputDataSource()
    {
        return getAudioMixer().createOutDataSource();
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to the {@link AbstractMediaDevice#createPlayer(DataSource)}
     * implementation of the <tt>MediaDevice</tt> on which this instance enables
     * mixing i.e. {@link #getWrappedDevice()}.
     */
    @Override
    protected Processor createPlayer(DataSource dataSource)
        throws Exception
    {
        return device.createPlayer(dataSource);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to the {@link AbstractMediaDevice#createRenderer()}
     * implementation of the <tt>MediaDevice</tt> on which this instance enables
     * mixing i.e. {@link #getWrappedDevice()}.
     */
    @Override
    protected Renderer createRenderer()
    {
        return device.createRenderer();
    }

    /**
     * Creates a new <tt>MediaDeviceSession</tt> instance which is to represent
     * the use of this <tt>MediaDevice</tt> by a <tt>MediaStream</tt>.
     *
     * @return a new <tt>MediaDeviceSession</tt> instance which is to represent
     * the use of this <tt>MediaDevice</tt> by a <tt>MediaStream</tt>
     * @see AbstractMediaDevice#createSession()
     */
    @Override
    public synchronized MediaDeviceSession createSession()
    {
        if (deviceSession == null)
            deviceSession = new AudioMixerMediaDeviceSession();
        return new MediaStreamMediaDeviceSession(deviceSession);
    }

    /**
     * Notifies the <tt>SimpleAudioLevelListener</tt>s registered with this
     * instance about the new/current audio level of the local media stream.
     *
     * @param level the new/current audio level of the local media stream.
     */
    private void fireLocalUserAudioLevelChanged(int level)
    {
        List<SimpleAudioLevelListenerWrapper> localUserAudioLevelListeners;

        synchronized(localUserAudioLevelListenersSyncRoot)
        {
            /*
             * It is safe to not copy the localUserAudioLevelListeners of this
             * instance here because it is a copy-on-write storage.
             */
            localUserAudioLevelListeners = this.localUserAudioLevelListeners;
        }

        /*
         * XXX These events are going to happen veeery often (~50 times per sec)
         * and we'd like to avoid creating an iterator every time.
         */
        int localUserAudioLevelListenerCount
            = localUserAudioLevelListeners.size();

        for(int i = 0; i < localUserAudioLevelListenerCount; i++)
        {
            localUserAudioLevelListeners.get(i).listener.audioLevelChanged(
                    level);
        }
    }

    /**
     * Gets the <tt>AudioMixer</tt> which performs audio mixing in this
     * <tt>MediaDevice</tt> (and rather the session it represents). If it still
     * does not exist, it is created.
     *
     * @return the <tt>AudioMixer</tt> which performs audio mixing in this
     * <tt>MediaDevice</tt> (and rather the session it represents)
     */
    private synchronized AudioMixer getAudioMixer()
    {
        if (audioMixer == null)
        {
            audioMixer
                = new AudioMixer(device.createCaptureDevice())
                {
                    @Override
                    protected void connect(
                            DataSource dataSource,
                            DataSource inputDataSource)
                        throws IOException
                    {
                        /*
                         * CaptureDevice needs special connecting as defined by
                         * AbstractMediaDevice and, especially, MediaDeviceImpl.
                         */
                        if (inputDataSource == captureDevice)
                            AudioMixerMediaDevice.this.connect(dataSource);
                        else
                            super.connect(dataSource, inputDataSource);
                    }

                    @Override
                    protected void read(
                            PushBufferStream stream,
                            Buffer buffer,
                            DataSource dataSource)
                        throws IOException
                    {
                        super.read(stream, buffer, dataSource);

                        /*
                         * XXX The audio read from the specified stream has not
                         * been made available to the mixing yet. Slow code here
                         * is likely to degrade the performance of the whole
                         * mixer.
                         */

                        if (dataSource == captureDevice)
                        {
                            /*
                             * The audio of the very CaptureDevice to be
                             * contributed to the mix.
                             */
                            synchronized(localUserAudioLevelListenersSyncRoot)
                            {
                                if (localUserAudioLevelListeners.isEmpty())
                                    return;
                            }
                            localUserAudioLevelDispatcher.addData(buffer);

                        }
                        else if (dataSource
                                instanceof ReceiveStreamPushBufferDataSource)
                        {
                            /*
                             * The audio of a ReceiveStream to be contributed to
                             * the mix.
                             */
                            ReceiveStream receiveStream
                                = ((ReceiveStreamPushBufferDataSource)
                                        dataSource)
                                    .getReceiveStream();
                            AudioLevelEventDispatcher streamEventDispatcher;

                            synchronized (streamAudioLevelListeners)
                            {
                                streamEventDispatcher
                                    = streamAudioLevelListeners.get(
                                            receiveStream);
                            }
                            if ((streamEventDispatcher != null)
                                    && !buffer.isDiscard()
                                    && (buffer.getLength() > 0)
                                    && (buffer.getData() != null))
                            {
                                streamEventDispatcher.addData(buffer);
                            }

                            ReceiveStreamBufferListener receiveStreamBufferListener =
                                AudioMixerMediaDevice.this.receiveStreamBufferListener;

                            if ((receiveStreamBufferListener != null)
                                && !buffer.isDiscard()
                                && (buffer.getLength() > 0)
                                && (buffer.getData() != null))
                            {
                                receiveStreamBufferListener.bufferReceived(
                                    receiveStream, buffer);
                            }
                        }
                    }
                };
        }
        return audioMixer;
    }

    /**
     * Returns the <tt>MediaDirection</tt> supported by this device.
     *
     * @return {@link MediaDirection#SENDONLY} if this is a read-only device,
     * {@link MediaDirection#RECVONLY} if this is a write-only device or
     * {@link MediaDirection#SENDRECV} if this <tt>MediaDevice</tt> can both
     * capture and render media
     * @see MediaDevice#getDirection()
     */
    public MediaDirection getDirection()
    {
        return device.getDirection();
    }

    /**
     * Gets the <tt>MediaFormat</tt> in which this <t>MediaDevice</tt> captures
     * media.
     *
     * @return the <tt>MediaFormat</tt> in which this <tt>MediaDevice</tt>
     * captures media
     * @see MediaDevice#getFormat()
     */
    public MediaFormat getFormat()
    {
        return device.getFormat();
    }

    /**
     * Gets the <tt>MediaType</tt> that this device supports.
     *
     * @return {@link MediaType#AUDIO} if this is an audio device or
     * {@link MediaType#VIDEO} if this is a video device
     * @see MediaDevice#getMediaType()
     */
    public MediaType getMediaType()
    {
        return device.getMediaType();
    }

    /**
     * Returns a <tt>List</tt> containing (at the time of writing) a single
     * extension descriptor indicating <tt>SENDRECV</tt> for mixer-to-client
     * audio levels.
     *
     * @return a <tt>List</tt> containing the <tt>CSRC_AUDIO_LEVEL_URN</tt>
     * extension descriptor.
     */
    @Override
    public List<RTPExtension> getSupportedExtensions()
    {
        if (rtpExtensions == null)
        {
            rtpExtensions = new ArrayList<RTPExtension>(2);

            URI csrcAudioLevelURN;
            URI ssrcAudioLevelURN;
            try
            {
                csrcAudioLevelURN = new URI(RTPExtension.CSRC_AUDIO_LEVEL_URN);
                ssrcAudioLevelURN = new URI(RTPExtension.SSRC_AUDIO_LEVEL_URN);
            }
            catch (URISyntaxException e)
            {
                // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and
                // never changes.
                csrcAudioLevelURN = null;
                ssrcAudioLevelURN = null;
                if (logger.isInfoEnabled())
                    logger.info("Aha! Someone messed with the source!", e);
            }

            if (csrcAudioLevelURN != null)
            {
                rtpExtensions.add(
                        new RTPExtension(
                                csrcAudioLevelURN,
                                MediaDirection.SENDRECV));
            }
            if (ssrcAudioLevelURN != null)
            {
                rtpExtensions.add(
                        new RTPExtension(
                                ssrcAudioLevelURN,
                                MediaDirection.SENDRECV));
            }
        }

        return rtpExtensions;
    }

    /**
     * Gets the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt>.
     *
     * @param sendPreset not used
     * @param receivePreset not used
     * @return the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt>
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(
            QualityPreset sendPreset,
            QualityPreset receivePreset)
    {
        return device.getSupportedFormats();
    }

    /**
     * Set the listener which gets notified when this <tt>MediaDevice</tt>
     * reads data from a <tt>ReceiveStream</tt>
     *
     * @param listener the <tt>ReceiveStreamBufferListener</tt> which gets notified
     */
    public void setReceiveStreamBufferListener(ReceiveStreamBufferListener listener)
    {
        this.receiveStreamBufferListener = listener;
    }

    /**
     * Gets the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt> and enabled in <tt>encodingConfiguration</tt>.
     *
     * @param sendPreset not used
     * @param receivePreset not used
     * @param encodingConfiguration the <tt>EncodingConfiguration</tt> instance
     * to use
     * @return the list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt> and enabled in <tt>encodingConfiguration</tt>.
     * @see MediaDevice#getSupportedFormats(QualityPreset, QualityPreset,
     * EncodingConfiguration)
     */
    public List<MediaFormat> getSupportedFormats(
            QualityPreset sendPreset,
            QualityPreset receivePreset,
            EncodingConfiguration encodingConfiguration)
    {
        return device.getSupportedFormats(encodingConfiguration);
    }

    /**
     * Gets the actual <tt>MediaDevice</tt> which this <tt>MediaDevice</tt> is
     * effectively built on top of and forwarding to.
     *
     * @return the actual <tt>MediaDevice</tt> which this <tt>MediaDevice</tt>
     * is effectively built on top of and forwarding to
     * @see MediaDeviceWrapper#getWrappedDevice()
     */
    public MediaDevice getWrappedDevice()
    {
        return device;
    }

    /**
     * Removes the <tt>DataSource</tt> accepted by a specific
     * <tt>DataSourceFilter</tt> from the list of input <tt>DataSource</tt> of
     * the <tt>AudioMixer</tt> of this <tt>AudioMixerMediaDevice</tt> from
     * which it reads audio to be mixed.
     *
     * @param dataSourceFilter the <tt>DataSourceFilter</tt> which selects the
     * <tt>DataSource</tt>s to be removed
     */
    void removeInputDataSources(DataSourceFilter dataSourceFilter)
    {
        AudioMixer audioMixer = this.audioMixer;

        if (audioMixer != null)
            audioMixer.removeInDataSources(dataSourceFilter);
    }

    /**
     * Represents the one and only <tt>MediaDeviceSession</tt> with the
     * <tt>MediaDevice</tt> of this <tt>AudioMixer</tt>
     */
    private class AudioMixerMediaDeviceSession
        extends MediaDeviceSession
    {
        /**
         * The list of <tt>MediaDeviceSession</tt>s of <tt>MediaStream</tt>s
         * which use this <tt>AudioMixer</tt>.
         */
        private final List<MediaStreamMediaDeviceSession>
            mediaStreamMediaDeviceSessions
                = new LinkedList<MediaStreamMediaDeviceSession>();

        /**
         * The <tt>VolumeControl</tt> which is to control the volume (level) of
         * the audio (to be) played back by this instance.
         */
        private VolumeControl outputVolumeControl;

        /**
         * Initializes a new <tt>AudioMixingMediaDeviceSession</tt> which is to
         * represent the <tt>MediaDeviceSession</tt> of this <tt>AudioMixer</tt>
         * with its <tt>MediaDevice</tt>
         */
        public AudioMixerMediaDeviceSession()
        {
            super(AudioMixerMediaDevice.this);
        }

        /**
         * Adds <tt>l</tt> to the list of listeners that are being notified of
         * new local audio levels as they change. If <tt>l</tt> is added
         * multiple times it would only be registered once.
         *
         * @param l the listener we'd like to add.
         */
        void addLocalUserAudioLevelListener(SimpleAudioLevelListener l)
        {
            // If the listener is null, we have nothing more to do here.
            if (l == null)
                return;

            synchronized(localUserAudioLevelListenersSyncRoot)
            {
                //if this is the first listener that we are seeing then we also
                //need to create the dispatcher.
                if (localUserAudioLevelListeners.isEmpty())
                {
                    localUserAudioLevelDispatcher.setAudioLevelListener(
                            localUserAudioLevelDelegate);
                }

                //check if this listener has already been added.
                SimpleAudioLevelListenerWrapper wrapper
                    = new SimpleAudioLevelListenerWrapper(l);
                int index = localUserAudioLevelListeners.indexOf(wrapper);

                if( index != -1)
                {
                    wrapper = localUserAudioLevelListeners.get(index);
                    wrapper.referenceCount++;
                }
                else
                {
                    /*
                     * XXX localUserAudioLevelListeners must be a copy-on-write
                     * storage so that firing events to its
                     * SimpleAudioLevelListeners can happen outside a block
                     * synchronized by localUserAudioLevelListenersSyncRoot and
                     * thus reduce the chances for a deadlock (which was,
                     * otherwise, observed in practice).
                     */
                    localUserAudioLevelListeners
                        = new ArrayList<SimpleAudioLevelListenerWrapper>(
                                localUserAudioLevelListeners);
                    localUserAudioLevelListeners.add(wrapper);
                }
            }
        }

        /**
         * Adds a specific <tt>MediaStreamMediaDeviceSession</tt> to the mix
         * represented by this instance so that it knows when it is in use.
         *
         * @param mediaStreamMediaDeviceSession the
         * <tt>MediaStreamMediaDeviceSession</tt> to be added to the mix
         * represented by this instance
         */
        void addMediaStreamMediaDeviceSession(
                MediaStreamMediaDeviceSession mediaStreamMediaDeviceSession)
        {
            if (mediaStreamMediaDeviceSession == null)
                throw new NullPointerException("mediaStreamMediaDeviceSession");

            synchronized (mediaStreamMediaDeviceSessions)
            {
                if (!mediaStreamMediaDeviceSessions
                        .contains(mediaStreamMediaDeviceSession))
                    mediaStreamMediaDeviceSessions
                        .add(mediaStreamMediaDeviceSession);
            }
        }

        /**
         * Adds a specific <tt>DataSource</tt> providing remote audio to the mix
         * produced by the associated <tt>MediaDevice</tt>.
         *
         * @param playbackDataSource the <tt>DataSource</tt> providing remote
         * audio to be added to the mix produced by the associated
         * <tt>MediaDevice</tt>
         */
        @Override
        public void addPlaybackDataSource(DataSource playbackDataSource)
        {
            /*
             * We don't play back the contributions of the conference members
             * separately, we have a single playback of the mix of all
             * contributions but ours.
             */
            super.addPlaybackDataSource(getCaptureDevice());
        }

        /**
         * Adds a specific <tt>ReceiveStream</tt> to the list of
         * <tt>ReceiveStream</tt>s known to this instance to be contributing
         * audio to the mix produced by its associated <tt>AudioMixer</tt>.
         *
         * @param receiveStream the <tt>ReceiveStream</tt> to be added to the
         * list of <tt>ReceiveStream</tt>s known to this instance to be
         * contributing audio to the mix produced by its associated
         * <tt>AudioMixer</tt>
         */
        @Override
        public void addReceiveStream(ReceiveStream receiveStream)
        {
            addSSRC(0xFFFFFFFFL & receiveStream.getSSRC());
        }

        /**
         * Creates the <tt>DataSource</tt> that this instance is to read
         * captured media from. Since this is the <tt>MediaDeviceSession</tt> of
         * this <tt>AudioMixer</tt> with its <tt>MediaDevice</tt>, returns the
         * <tt>localOutputDataSource</tt> of the <tt>AudioMixer</tt> i.e. the
         * <tt>DataSource</tt> which represents the mix of all
         * <tt>ReceiveStream</tt>s and excludes the captured data from the
         * <tt>MediaDevice</tt> of the <tt>AudioMixer</tt>.
         *
         * @return the <tt>DataSource</tt> that this instance is to read
         * captured media from
         * @see MediaDeviceSession#createCaptureDevice()
         */
        @Override
        protected DataSource createCaptureDevice()
        {
            return getAudioMixer().getLocalOutDataSource();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Player createPlayer(DataSource dataSource)
        {
            /*
             * TODO AudioMixerMediaDevice wraps a MediaDevice so
             * AudioMixerMediaDeviceSession should wrap a MediaDeviceSession of
             * that same wrapped MediaDevice.
             */
            return super.createPlayer(dataSource);
        }

        /**
         * Sets the <tt>VolumeControl</tt> which is to control the volume
         * (level) of the audio (to be) played back by this instance.
         *
         * @param outputVolumeControl the <tt>VolumeControl</tt> which is to be
         * control the volume (level) of the audio (to be) played back by this
         * instance
         */
        void setOutputVolumeControl(VolumeControl outputVolumeControl)
        {
            this.outputVolumeControl = outputVolumeControl;
        }

        /**
         * Sets <tt>listener</tt> as the list of listeners that will receive
         * notifications of audio level event changes in the data arriving from
         * <tt>stream</tt>.
         *
         * @param stream the stream that <tt>l</tt> would like to register as
         * an audio level listener for.
         * @param listener the listener we'd like to register for notifications
         * from <tt>stream</tt>.
         */
        void setStreamAudioLevelListener(
                ReceiveStream stream,
                SimpleAudioLevelListener listener)
        {
            synchronized(streamAudioLevelListeners)
            {
                AudioLevelEventDispatcher dispatcher
                    = streamAudioLevelListeners.get(stream);

                if (listener == null)
                {
                    if (dispatcher != null)
                    {
                        try
                        {
                            dispatcher.setAudioLevelListener(null);
                            dispatcher.setAudioLevelCache(null, -1);
                        }
                        finally
                        {
                            streamAudioLevelListeners.remove(stream);
                        }
                    }
                }
                else
                {
                    if (dispatcher == null)
                    {
                        dispatcher
                            = new AudioLevelEventDispatcher(
                                    "Stream Audio Level Dispatcher"
                                        + " (Mixer Edition)");
                        dispatcher.setAudioLevelCache(
                                audioLevelCache,
                                0xFFFFFFFFL & stream.getSSRC());
                        streamAudioLevelListeners.put(stream, dispatcher);
                    }
                    dispatcher.setAudioLevelListener(listener);
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         * Overrides the super implementation in order to configure the
         * <tt>VolumeControl</tt> of the returned <tt>Renderer</tt> for the
         * purposes of having call/telephony conference-specific volume
         * (levels).
         */
        @Override
        protected Renderer createRenderer(
                Player player,
                TrackControl trackControl)
        {
            Renderer renderer = super.createRenderer(player, trackControl);

            if (renderer != null)
            {
                AudioMediaDeviceSession.setVolumeControl(
                        renderer,
                        outputVolumeControl);
            }

            return renderer;
        }

        /**
         * Removes <tt>l</tt> from the list of listeners that are being
         * notified of local audio levels.If <tt>l</tt> is not in the list,
         * the method has no effect.
         *
         * @param l the listener we'd like to remove.
         */
        void removeLocalUserAudioLevelListener(
                SimpleAudioLevelListener l)
        {
            synchronized(localUserAudioLevelListenersSyncRoot)
            {
                //check if this listener has already been added.
                int index
                    = localUserAudioLevelListeners.indexOf(
                            new SimpleAudioLevelListenerWrapper(l));

                if( index != -1)
                {
                    SimpleAudioLevelListenerWrapper wrapper
                        = localUserAudioLevelListeners.get(index);

                    if(wrapper.referenceCount > 1)
                        wrapper.referenceCount--;
                    else
                    {
                        /*
                         * XXX localUserAudioLevelListeners must be a
                         * copy-on-write storage so that firing events to its
                         * SimpleAudioLevelListeners can happen outside a block
                         * synchronized by localUserAudioLevelListenersSyncRoot
                         * and thus reduce the chances for a deadlock (whic
                         * was, otherwise, observed in practice).
                         */
                        localUserAudioLevelListeners
                            = new ArrayList<SimpleAudioLevelListenerWrapper>(
                                    localUserAudioLevelListeners);
                        localUserAudioLevelListeners.remove(wrapper);
                    }
                }

                //if this was the last listener then we also need to remove the
                //dispatcher
                if (localUserAudioLevelListeners.isEmpty())
                    localUserAudioLevelDispatcher.setAudioLevelListener(null);
            }
        }

        /**
         * Removes a specific <tt>MediaStreamMediaDeviceSession</tt> from the
         * mix represented by this instance. When the last
         * <tt>MediaStreamMediaDeviceSession</tt> is removed from this instance,
         * it is no longer in use and closes itself thus signaling to its
         * <tt>MediaDevice</tt> that it is no longer in use.
         *
         * @param mediaStreamMediaDeviceSession the
         * <tt>MediaStreamMediaDeviceSession</tt> to be removed from the mix
         * represented by this instance
         */
        void removeMediaStreamMediaDeviceSession(
                MediaStreamMediaDeviceSession mediaStreamMediaDeviceSession)
        {
            if (mediaStreamMediaDeviceSession != null)
            {
                synchronized (mediaStreamMediaDeviceSessions)
                {
                    if (mediaStreamMediaDeviceSessions
                                .remove(mediaStreamMediaDeviceSession)
                            && mediaStreamMediaDeviceSessions.isEmpty())
                        close();
                }
            }
        }

        /**
         * Removes a specific <tt>DataSource</tt> providing remote audio from
         * the mix produced by the associated <tt>AudioMixer</tt>.
         *
         * @param playbackDataSource the <tt>DataSource</tt> providing remote
         * audio to be removed from the mix produced by the associated
         * <tt>AudioMixer</tt>
         */
        @Override
        public void removePlaybackDataSource(
                final DataSource playbackDataSource)
        {
            removeInputDataSources(
                    new DataSourceFilter()
                            {
                                @Override
                                public boolean accept(DataSource dataSource)
                                {
                                    return
                                        dataSource.equals(playbackDataSource);
                                }
                            });
        }

        /**
         * Removes a specific <tt>ReceiveStream</tt> from the list of
         * <tt>ReceiveStream</tt>s known to this instance to be contributing
         * audio to the mix produced by its associated <tt>AudioMixer</tt>.
         *
         * @param receiveStream the <tt>ReceiveStream</tt> to be removed from
         * the list of <tt>ReceiveStream</tt>s known to this instance to be
         * contributing audio to the mix produced by its associated
         * <tt>AudioMixer</tt>
         */
        @Override
        public void removeReceiveStream(ReceiveStream receiveStream)
        {
            long ssrc = 0xFFFFFFFFL & receiveStream.getSSRC();

            removeSSRC(ssrc);

            //make sure we no longer cache levels for that stream.
            audioLevelCache.removeLevel(ssrc);
        }
    }

    /**
     * Represents the work of a <tt>MediaStream</tt> with the
     * <tt>MediaDevice</tt> of an <tt>AudioMixer</tt> and the contribution of
     * that <tt>MediaStream</tt> to the mix.
     */
    private static class MediaStreamMediaDeviceSession
        extends AudioMediaDeviceSession
        implements PropertyChangeListener
    {
        /**
         * The <tt>MediaDeviceSession</tt> of the <tt>AudioMixer</tt> that this
         * instance exposes to a <tt>MediaStream</tt>. While there are multiple
         * <tt>MediaStreamMediaDeviceSession<tt>s each servicing a specific
         * <tt>MediaStream</tt>, they all share and delegate to one and the same
         * <tt>AudioMixerMediaDeviceSession</tt> so that they all contribute to
         * the mix.
         */
        private final AudioMixerMediaDeviceSession audioMixerMediaDeviceSession;

        /**
         * We use this field to keep a reference to the listener that we've
         * registered with the audio mixer for local audio level notifications.
         * We use this reference so that we could unregister it if someone
         * resets it or sets it to <tt>null</tt>.
         */
        private SimpleAudioLevelListener localUserAudioLevelListener = null;

        /**
         * We use this field to keep a reference to the listener that we've
         * registered with the audio mixer for stream audio level notifications.
         * We use this reference so because at the time we get it from the
         * <tt>MediaStream</tt> it might be too early to register it with the
         * mixer as it is like that we don't have a receive stream yet. If
         * that's the case, we hold on to the listener and register it only
         * when we get the <tt>ReceiveStream</tt>.
         */
        private SimpleAudioLevelListener streamAudioLevelListener = null;

        /**
         * The <tt>Object</tt> that we use to lock operations on
         * <tt>streamAudioLevelListener</tt>.
         */
        private final Object streamAudioLevelListenerLock = new Object();

        /**
         * Initializes a new <tt>MediaStreamMediaDeviceSession</tt> which is to
         * represent the work of a <tt>MediaStream</tt> with the
         * <tt>MediaDevice</tt> of this <tt>AudioMixer</tt> and its contribution
         * to the mix.
         *
         * @param audioMixerMediaDeviceSession the <tt>MediaDeviceSession</tt>
         * of the <tt>AudioMixer</tt> with its <tt>MediaDevice</tt> which the
         * new instance is to delegate to in order to contribute to the mix
         */
        public MediaStreamMediaDeviceSession(
                AudioMixerMediaDeviceSession audioMixerMediaDeviceSession)
        {
            super(audioMixerMediaDeviceSession.getDevice());

            this.audioMixerMediaDeviceSession = audioMixerMediaDeviceSession;
            this.audioMixerMediaDeviceSession
                    .addMediaStreamMediaDeviceSession(this);

            this.audioMixerMediaDeviceSession.addPropertyChangeListener(this);
        }

        /**
         * Releases the resources allocated by this instance in the course of
         * its execution and prepares it to be garbage collected.
         *
         * @see MediaDeviceSession#close()
         */
        @Override
        public void close()
        {
            try
            {
                super.close();
            }
            finally
            {
                audioMixerMediaDeviceSession
                    .removeMediaStreamMediaDeviceSession(this);
            }
        }

        /**
         * Creates a new <tt>Player</tt> for a specific <tt>DataSource</tt> so
         * that it is played back on the <tt>MediaDevice</tt> represented by
         * this instance.
         *
         * @param dataSource the <tt>DataSource</tt> to create a new
         * <tt>Player</tt> for
         * @return a new <tt>Player</tt> for the specified <tt>dataSource</tt>
         * @see MediaDeviceSession#createPlayer(DataSource)
         */
        @Override
        protected Player createPlayer(DataSource dataSource)
        {
            /*
             * We don't want the contribution of each conference member played
             * back separately, we want the one and only mix of all
             * contributions but ours to be played back once for all of them.
             */
            return null;
        }

        /**
         * Returns the list of SSRC identifiers that are directly contributing
         * to the media flows that we are sending out. Note that since this is
         * a pseudo device we would simply be delegating the call to the
         * corresponding method of the master mixer device session.
         *
         * @return a <tt>long[]</tt> array of SSRC identifiers that are
         * currently contributing to the mixer encapsulated by this device
         * session.
         */
        @Override
        public long[] getRemoteSSRCList()
        {
            return audioMixerMediaDeviceSession.getRemoteSSRCList();
        }

        /**
         * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt>
         * has been added for playback on the represented <tt>MediaDevice</tt>.
         *
         * @param playbackDataSource the <tt>DataSource</tt> which has been
         * added for playback on the represented <tt>MediaDevice</tt>
         * @see MediaDeviceSession#playbackDataSourceAdded(DataSource)
         */
        @Override
        protected void playbackDataSourceAdded(DataSource playbackDataSource)
        {
            super.playbackDataSourceAdded(playbackDataSource);

            DataSource captureDevice = getCaptureDevice();

            /*
             * Unwrap wrappers of the captureDevice until
             * AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
                captureDevice
                    = ((PushBufferDataSourceDelegate<?>) captureDevice)
                        .getDataSource();
            if (captureDevice instanceof AudioMixingPushBufferDataSource)
                ((AudioMixingPushBufferDataSource) captureDevice)
                    .addInDataSource(playbackDataSource);

            audioMixerMediaDeviceSession.addPlaybackDataSource(
                    playbackDataSource);
        }

        /**
         * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt>
         * has been removed from playback on the represented
         * <tt>MediaDevice</tt>.
         *
         * @param playbackDataSource the <tt>DataSource</tt> which has been
         * removed from playback on the represented <tt>MediaDevice</tt>
         * @see MediaDeviceSession#playbackDataSourceRemoved(DataSource)
         */
        @Override
        protected void playbackDataSourceRemoved(DataSource playbackDataSource)
        {
            super.playbackDataSourceRemoved(playbackDataSource);

            audioMixerMediaDeviceSession.removePlaybackDataSource(
                    playbackDataSource);
        }

        /**
         * Notifies this <tt>MediaDeviceSession</tt> that a <tt>DataSource</tt>
         * has been updated.
         *
         * @param playbackDataSource the <tt>DataSource</tt> which has been
         * updated.
         * @see MediaDeviceSession#playbackDataSourceUpdated(DataSource)
         */
        @Override
        protected void playbackDataSourceUpdated(DataSource playbackDataSource)
        {
            super.playbackDataSourceUpdated(playbackDataSource);

            DataSource captureDevice = getCaptureDevice();

            /*
             * Unwrap wrappers of the captureDevice until
             * AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
                captureDevice
                    = ((PushBufferDataSourceDelegate<?>) captureDevice)
                        .getDataSource();
            if (captureDevice instanceof AudioMixingPushBufferDataSource)
            {
                ((AudioMixingPushBufferDataSource) captureDevice)
                    .updateInDataSource(playbackDataSource);
            }
        }

        /**
         * The method relays <tt>PropertyChangeEvent</tt>s indicating a change
         * in the SSRC_LIST in the encapsulated mixer device so that the
         * <tt>MediaStream</tt> that uses this device session can update its
         * CSRC list.
         *
         * @param evt that <tt>PropertyChangeEvent</tt> whose old and new value
         * we will be relaying to the stream.
         */
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (MediaDeviceSession.SSRC_LIST.equals(evt.getPropertyName()))
            {
                firePropertyChange(
                        MediaDeviceSession.SSRC_LIST,
                        evt.getOldValue(),
                        evt.getNewValue());
            }
        }

        /**
         * Notifies this instance that a specific <tt>ReceiveStream</tt> has
         * been added to the list of playbacks of <tt>ReceiveStream</tt>s and/or
         * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
         * <tt>MediaDevice</tt> represented by this instance.
         *
         * @param receiveStream the <tt>ReceiveStream</tt> which has been added
         * to the list of playbacks of <tt>ReceiveStream</tt>s and/or
         * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
         * <tt>MediaDevice</tt> represented by this instance
         */
        @Override
        protected void receiveStreamAdded(ReceiveStream receiveStream)
        {
            super.receiveStreamAdded(receiveStream);

            /*
             * If someone registered a stream level listener, we can now add it
             * since we have the stream that it's supposed to listen to.
             */
            synchronized (streamAudioLevelListenerLock)
            {
                if (streamAudioLevelListener != null)
                    audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                            receiveStream,
                            streamAudioLevelListener);
            }

            audioMixerMediaDeviceSession.addReceiveStream(receiveStream);
        }

        /**
         * Notifies this instance that a specific <tt>ReceiveStream</tt> has
         * been removed from the list of playbacks of <tt>ReceiveStream</tt>s
         * and/or <tt>DataSource</tt>s performed by respective <tt>Player</tt>s
         * on the <tt>MediaDevice</tt> represented by this instance.
         *
         * @param receiveStream the <tt>ReceiveStream</tt> which has been
         * removed from the list of playbacks of <tt>ReceiveStream</tt>s and/or
         * <tt>DataSource</tt>s performed by respective <tt>Player</tt>s on the
         * <tt>MediaDevice</tt> represented by this instance
         */
        @Override
        protected void receiveStreamRemoved(ReceiveStream receiveStream)
        {
            super.receiveStreamRemoved(receiveStream);

            audioMixerMediaDeviceSession.removeReceiveStream(receiveStream);
        }

        /**
         * Override it here cause we won't register effects to that stream
         * cause we already have one.
         *
         * @param processor the processor.
         */
        @Override
        protected void registerLocalUserAudioLevelEffect(Processor processor)
        {
        }

        /**
         * Adds a specific <tt>SoundLevelListener</tt> to the list of listeners
         * interested in and notified about changes in local sound level related
         * information.
         * @param l the <tt>SoundLevelListener</tt> to add
         */
        @Override
        public void setLocalUserAudioLevelListener(SimpleAudioLevelListener l)
        {
            if (localUserAudioLevelListener != null)
            {
                audioMixerMediaDeviceSession.removeLocalUserAudioLevelListener(
                        localUserAudioLevelListener);
                localUserAudioLevelListener = null;
            }

            if (l != null)
            {
                localUserAudioLevelListener = l;

                // add the listener only if we are not muted
                // this happens when holding a conversation, stream is muted
                // and when recreated listener is again set
                if(!isMute())
                {
                    audioMixerMediaDeviceSession.addLocalUserAudioLevelListener(
                            l);
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         * Overrides the super implementation to redirect/delegate the
         * invocation to the master/audioMixerMediaDeviceSession because
         * <tt>MediaStreamMediaDeviceSession</tt> does not perform
         * playback/rendering.
         */
        @Override
        public void setOutputVolumeControl(VolumeControl outputVolumeControl)
        {
            audioMixerMediaDeviceSession.setOutputVolumeControl(
                    outputVolumeControl);
        }

        /**
         * Adds <tt>listener</tt> to the list of
         * <tt>SimpleAudioLevelListener</tt>s registered with the mixer session
         * that this "slave session" encapsulates. This class does not keep a
         * reference to <tt>listener</tt>.
         *
         * @param listener the <tt>SimpleAudioLevelListener</tt> that we are to
         * pass to the mixer device session or <tt>null</tt> if we are trying
         * to unregister it.
         */
        @Override
        public void setStreamAudioLevelListener(
                SimpleAudioLevelListener listener)
        {
            synchronized(streamAudioLevelListenerLock)
            {
                streamAudioLevelListener = listener;

                for (ReceiveStream receiveStream : getReceiveStreams())
                {
                    /*
                     * If we already have a ReceiveStream, register the listener
                     * with the mixer; otherwise, wait till we get one.
                     */
                    audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                            receiveStream,
                            streamAudioLevelListener);
                }
            }
        }

        /**
         * Returns the last audio level that was measured by the underlying
         * mixer for the specified <tt>csrc</tt>.
         *
         * @param csrc the CSRC ID whose last measured audio level we'd like to
         * retrieve.
         *
         * @return the audio level that was last measured by the underlying
         * mixer for the specified <tt>csrc</tt> or <tt>-1</tt> if the
         * <tt>csrc</tt> does not belong to neither of the conference
         * participants.
         */
        @Override
        public int getLastMeasuredAudioLevel(long csrc)
        {
            return
                ((AudioMixerMediaDevice) getDevice()).audioLevelCache.getLevel(
                        csrc);
        }

        /**
         * Returns the last audio level that was measured by the underlying
         * mixer for local user.
         *
         * @return the audio level that was last measured for the local user.
         */
        @Override
        public int getLastMeasuredLocalUserAudioLevel()
        {
            return
                ((AudioMixerMediaDevice) getDevice())
                    .lastMeasuredLocalUserAudioLevel;
        }

        /**
         * Sets the indicator which determines whether this
         * <tt>MediaDeviceSession</tt> is set to output "silence" instead of the
         * actual media fed from its <tt>CaptureDevice</tt>.
         * If we are muted we just remove the local level listener from the
         * session.
         *
         * @param mute <tt>true</tt> to set this <tt>MediaDeviceSession</tt> to
         *             output "silence" instead of the actual media fed from its
         *             <tt>CaptureDevice</tt>; otherwise, <tt>false</tt>
         */
        @Override
        public void setMute(boolean mute)
        {
            boolean oldValue = isMute();

            super.setMute(mute);

            boolean newValue = isMute();

            if (oldValue != newValue)
            {
                if (newValue)
                {
                    audioMixerMediaDeviceSession
                        .removeLocalUserAudioLevelListener(
                            localUserAudioLevelListener);
                }
                else
                {
                    audioMixerMediaDeviceSession
                        .addLocalUserAudioLevelListener(
                            localUserAudioLevelListener);
                }
            }
        }
    }

    /**
     * A very lightweight wrapper that allows us to track the number of times
     * that a particular listener was added.
     */
    private static class SimpleAudioLevelListenerWrapper
    {
        /** The listener being wrapped by this wrapper. */
        public final SimpleAudioLevelListener listener;

        /** The number of times this listener has been added. */
        int referenceCount;

        /**
         * Creates a wrapper of the <tt>l</tt> listener.
         *
         * @param l the listener we'd like to wrap;
         */
        public SimpleAudioLevelListenerWrapper(SimpleAudioLevelListener l)
        {
            this.listener = l;
            this.referenceCount = 1;
        }

        /**
         * Returns <tt>true</tt> if <tt>obj</tt> is a wrapping the same listener
         * as ours.
         *
         * @param obj the wrapper we'd like to compare to this instance
         *
         * @return <tt>true</tt> if <tt>obj</tt> is a wrapping the same listener
         * as ours.
         */
        @Override
        public boolean equals(Object obj)
        {
            return (obj instanceof SimpleAudioLevelListenerWrapper)
                && ((SimpleAudioLevelListenerWrapper)obj).listener == listener;
        }

        /**
         * Returns a hash code value for this instance for the benefit of
         * hashtables.
         *
         * @return a hash code value for this instance for the benefit of
         * hashtables
         */
        @Override
        public int hashCode()
        {
            /*
             * Equality is based on the listener field only so its hashCode is
             * enough. Besides, it's the only immutable of this instance i.e.
             * the only field appropriate for the calculation of the hashCode.
             */
            return listener.hashCode();
        }
    }

    /**
     * Returns the <tt>TranscodingDataSource</tt> associated with
     * <tt>inputDataSource</tt> in this object's <tt>AudioMixer</tt>.
     *
     * @param inputDataSource the <tt>DataSource</tt> to search for
     *
     * @return Returns the <tt>TranscodingDataSource</tt> associated with
     * <tt>inputDataSource</tt> in this object's <tt>AudioMixer</tt>
     *
     * @see AudioMixer#getTranscodingDataSource(javax.media.protocol.DataSource)
     */
    public TranscodingDataSource getTranscodingDataSource(
            DataSource inputDataSource)
    {
            return getAudioMixer().getTranscodingDataSource(inputDataSource);
    }

}
