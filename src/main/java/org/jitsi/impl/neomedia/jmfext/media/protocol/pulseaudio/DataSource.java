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
package org.jitsi.impl.neomedia.jmfext.media.protocol.pulseaudio;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.impl.neomedia.pulseaudio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> using the native
 * PulseAudio API/library.
 *
 * @author Lyubomir Marinov
 */
public class DataSource
    extends AbstractPullBufferCaptureDevice
{
    /**
     * The <tt>Logger</tt> used by the <tt>DataSource</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(DataSource.class);

    private static final int BUFFER_IN_TENS_OF_MILLIS = 10;

    /**
     * The indicator which determines whether debug-level logging is enabled for
     * the <tt>DataSource</tt> class and its instances.
     */
    private static final boolean DEBUG = logger.isDebugEnabled();

    private static final int FRAGSIZE_IN_TENS_OF_MILLIS = 2;

    /**
     * The indicator which determines whether <tt>DataSource</tt> instances
     * apply audio volume levels on the audio data to be renderer or leave the
     * task to PulseAudio.
     */
    private static final boolean SOFTWARE_GAIN;

    static
    {
        boolean softwareGain = true;

        try
        {
            String libraryVersion = PA.get_library_version();

            if (libraryVersion != null)
            {
                StringTokenizer st = new StringTokenizer(libraryVersion, ".");

                if (/* major */ Integer.parseInt(st.nextToken()) >= 1
                        && /* minor */ Integer.parseInt(st.nextToken()) >= 0)
                {
                    // FIXME The control of the volume through the native
                    // PulseAudio API has been reported to maximize the
                    // system-wide volume of the source with flat volumes i.e.
                    // https://java.net/jira/browse/JITSI-1050 (Pulseaudio
                    // changes volume to maximum values).
//                    softwareGain = false;
//                    if (logger.isDebugEnabled())
//                    {
//                        logger.debug(
//                                "Will control the volume"
//                                    + " through the native PulseAudio API.");
//                    }
                }
            }
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        SOFTWARE_GAIN = softwareGain;
    }

    /**
     * Implements a <tt>PullBufferStream</tt> using the native PulseAudio
     * API/library.
     */
    private class PulseAudioStream
        extends AbstractPullBufferStream<DataSource>
    {
        /**
         * The <tt>PulseAudioSystem</tt> instance which provides the capture
         * device and allows creating {@link #stream}.
         */
        private final PulseAudioSystem audioSystem;

        private byte[] buffer;

        /**
         * The number of channels of audio data this <tt>PulseAudioStream</tt>
         * is configured to input.
         */
        private int channels;

        /**
         * The indicator which determines whether {@link #stream}'s input is
         * paused or resumed.
         */
        private boolean corked = true;

        /**
         * The <tt>pa_cvolume</tt> (structure) instance used by this
         * <tt>PulseAudioRenderer</tt> to set the per-channel volume of
         * {@link #stream}.
         */
        private long cvolume;

        private int fragsize;

        /**
         * The <tt>GainControl</tt> which specifies the volume level to be
         * applied to the audio data input through this
         * <tt>PulseAudioStream</tt>.
         */
        private final GainControl gainControl;

        /**
         * The volume level specified by {@link #gainControl} which has been
         * set on {@link #stream}.
         */
        private float gainControlLevel;

        /**
         * The number of bytes in {@link #buffer} starting at {@link #offset}.
         */
        private int length;

        /**
         * The offset in {@link #buffer}.
         */
        private int offset;

        /**
         * The PulseAudio callback which notifies this <tt>PulseAudioStream</tt>
         * that {@link #stream} has audio data available to input.
         */
        private final PA.stream_request_cb_t readCb
            = new PA.stream_request_cb_t()
            {
                @Override
                public void callback(long s, int nbytes)
                {
                    readCb(s, nbytes);
                }
            };

        /**
         * The PulseAudio stream which inputs audio data from the PulseAudio
         * source.
         */
        private long stream;

        /**
         * Initializes a new <tt>PulseAudioStream</tt> which is to have its
         * <tt>Format</tt>-related information abstracted by a specific
         * <tt>FormatControl</tt>.
         *
         * @param formatControl the <tt>FormatControl</tt> which is to abstract
         * the <tt>Format</tt>-related information of the new instance
         */
        public PulseAudioStream(FormatControl formatControl)
        {
            super(DataSource.this, formatControl);

            audioSystem = PulseAudioSystem.getPulseAudioSystem();
            if (audioSystem == null)
                throw new IllegalStateException("audioSystem");

            MediaServiceImpl mediaServiceImpl
                = NeomediaServiceUtils.getMediaServiceImpl();

            gainControl
                = (mediaServiceImpl == null)
                    ? null
                    : (GainControl) mediaServiceImpl.getInputVolumeControl();
        }

        /**
         * Connects this <tt>PulseAudioStream</tt> to the configured source and
         * prepares it to input audio data in the configured FMJ
         * <tt>Format</tt>.
         *
         * @throws IOException if this <tt>PulseAudioStream</tt> fails to
         * connect to the configured source
         */
        @SuppressWarnings("unused")
        public void connect()
            throws IOException
        {
            audioSystem.lockMainloop();
            try
            {
                connectWithMainloopLock();
            }
            finally
            {
                audioSystem.unlockMainloop();
            }
        }

        /**
         * Connects this <tt>PulseAudioStream</tt> to the configured source and
         * prepares it to input audio data in the configured FMJ
         * <tt>Format</tt>. The method executes with the assumption that the
         * PulseAudio event loop object is locked by the executing thread.
         *
         * @throws IOException if this <tt>PulseAudioStream</tt> fails to
         * connect to the configured source
         */
        private void connectWithMainloopLock()
            throws IOException
        {
            if (stream != 0)
                return;

            AudioFormat format = (AudioFormat) getFormat();
            int sampleRate = (int) format.getSampleRate();
            int channels = format.getChannels();
            int sampleSizeInBits = format.getSampleSizeInBits();

            if ((sampleRate == Format.NOT_SPECIFIED)
                    && (MediaUtils.MAX_AUDIO_SAMPLE_RATE
                            != Format.NOT_SPECIFIED))
                sampleRate = (int) MediaUtils.MAX_AUDIO_SAMPLE_RATE;
            if (channels == Format.NOT_SPECIFIED)
                channels = 1;
            if (sampleSizeInBits == Format.NOT_SPECIFIED)
                sampleSizeInBits = 16;

            long stream = 0;
            Throwable exception = null;

            try
            {
                stream
                    = audioSystem.createStream(
                            sampleRate,
                            channels,
                            getClass().getName(),
                            PulseAudioSystem.MEDIA_ROLE_PHONE);
                this.channels = channels;
            }
            catch (IllegalStateException ise)
            {
                exception = ise;
            }
            catch (RuntimeException re)
            {
                exception = re;
            }
            if (exception != null)
            {
                IOException ioe = new IOException();

                ioe.initCause(exception);
                throw ioe;
            }
            if (stream == 0)
                throw new IOException("stream");

            try
            {
                int bytesPerTenMillis
                    = (sampleRate / 100) * channels * (sampleSizeInBits / 8);

                fragsize = FRAGSIZE_IN_TENS_OF_MILLIS * bytesPerTenMillis;
                buffer = new byte[BUFFER_IN_TENS_OF_MILLIS * bytesPerTenMillis];

                long attr
                    = PA.buffer_attr_new(
                            -1,
                            -1,
                            -1,
                            -1,
                            fragsize);

                if (attr == 0)
                    throw new IOException("pa_buffer_attr_new");

                try
                {
                    Runnable stateCallback
                        = new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                audioSystem.signalMainloop(false);
                            }
                        };

                    PA.stream_set_state_callback(
                            stream,
                            stateCallback);
                    PA.stream_connect_record(
                            stream,
                            getLocatorDev(),
                            attr,
                            PA.STREAM_ADJUST_LATENCY
                                | PA.STREAM_START_CORKED);

                    try
                    {
                        if (attr != 0)
                        {
                            PA.buffer_attr_free(attr);
                            attr = 0;
                        }

                        int state
                            = audioSystem.waitForStreamState(
                                    stream,
                                    PA.STREAM_READY);

                        if (state != PA.STREAM_READY)
                            throw new IOException("stream.state");

                        PA.stream_set_read_callback(stream, readCb);

                        if (!SOFTWARE_GAIN && (gainControl != null))
                        {
                            cvolume = PA.cvolume_new();

                            boolean freeCvolume = true;

                            try
                            {
                                float gainControlLevel = gainControl.getLevel();

                                setStreamVolume(stream, gainControlLevel);
                                this.gainControlLevel = gainControlLevel;
                                freeCvolume = false;
                            }
                            finally
                            {
                                if (freeCvolume)
                                {
                                    PA.cvolume_free(cvolume);
                                    cvolume = 0;
                                }
                            }
                        }

                        this.stream = stream;
                    }
                    finally
                    {
                        if (this.stream == 0)
                            PA.stream_disconnect(stream);
                    }
                }
                finally
                {
                    if (attr != 0)
                        PA.buffer_attr_free(attr);
                }
            }
            finally
            {
                if (this.stream == 0)
                    PA.stream_unref(stream);
            }
        }

        /**
         * Pauses or resumes the input of audio data through {@link #stream}.
         *
         * @param b <tt>true</tt> to pause the input of audio data or
         * <tt>false</tt> to resume it
         */
        private void cork(boolean b)
            throws IOException
        {
            try
            {
                PulseAudioSystem.corkStream(stream, b);
                corked = b;
            }
            finally
            {
                audioSystem.signalMainloop(false);
            }
        }

        /**
         * Disconnects this <tt>PulseAudioStream</tt> and its
         * <tt>DataSource</tt> from the connected capture device.
         */
        public void disconnect()
            throws IOException
        {
            audioSystem.lockMainloop();
            try
            {
                long stream = this.stream;

                if (stream != 0)
                {
                    try
                    {
                        stopWithMainloopLock();
                    }
                    finally
                    {
                        long cvolume = this.cvolume;

                        this.cvolume = 0;
                        this.stream = 0;

                        buffer = null;
                        corked = true;
                        fragsize = 0;
                        length = 0;
                        offset = 0;

                        audioSystem.signalMainloop(false);

                        if (cvolume != 0)
                            PA.cvolume_free(cvolume);
                        PA.stream_disconnect(stream);
                        PA.stream_unref(stream);
                    }
                }
            }
            finally
            {
                audioSystem.unlockMainloop();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void read(Buffer buffer)
            throws IOException
        {
            audioSystem.lockMainloop();
            try
            {
                if (stream == 0)
                    throw new IOException("stream");

                byte[] data
                    = AbstractCodec2.validateByteArraySize(
                            buffer,
                            fragsize,
                            false);
                int toRead = fragsize;
                int offset = 0;
                int length = 0;

                while (toRead > 0)
                {
                    if (corked)
                        break;

                    if (this.length <= 0)
                    {
                        audioSystem.waitMainloop();
                        continue;
                    }

                    int toCopy = (toRead < this.length) ? toRead : this.length;

                    System.arraycopy(
                            this.buffer, this.offset,
                            data, offset,
                            toCopy);

                    this.offset += toCopy;
                    this.length -= toCopy;
                    if (this.length <= 0)
                    {
                        this.offset = 0;
                        this.length = 0;
                    }

                    toRead -= toCopy;
                    offset += toCopy;
                    length += toCopy;
                }

                buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
                buffer.setLength(length);
                buffer.setOffset(0);
                buffer.setTimeStamp(System.nanoTime());

                if (gainControl != null)
                {
                    if (SOFTWARE_GAIN || (cvolume == 0))
                    {
                        if (length > 0)
                        {
                            BasicVolumeControl.applyGain(
                                    gainControl,
                                    data, 0, length);
                        }
                    }
                    else
                    {
                        float gainControlLevel = gainControl.getLevel();

                        if (this.gainControlLevel != gainControlLevel)
                        {
                            this.gainControlLevel = gainControlLevel;
                            setStreamVolume(stream, gainControlLevel);
                        }
                    }
                }
            }
            finally
            {
                audioSystem.unlockMainloop();
            }
        }

        private void readCb(long stream, int length)
        {
            try
            {
                int peeked;

                if (corked)
                {
                    peeked = 0;
                }
                else
                {
                    int offset;

                    if ((buffer == null) || (buffer.length < length))
                    {
                        buffer = new byte[length];
                        this.offset = 0;
                        this.length = 0;
                        offset = 0;
                    }
                    else
                    {
                        offset = this.offset + this.length;
                        if (offset + length > buffer.length)
                        {
                            int overflow = this.length + length - buffer.length;

                            if (overflow > 0)
                            {
                                if (overflow >= this.length)
                                {
                                    if (DEBUG && logger.isDebugEnabled())
                                    {
                                        logger.debug(
                                                "Dropping "
                                                    + this.length
                                                    + " bytes!");
                                    }
                                    this.offset = 0;
                                    this.length = 0;
                                    offset = 0;
                                }
                                else
                                {
                                    if (DEBUG && logger.isDebugEnabled())
                                    {
                                        logger.debug(
                                                "Dropping "
                                                    + overflow
                                                    + " bytes!");
                                    }
                                    this.offset += overflow;
                                    this.length -= overflow;
                                }
                            }
                            if (this.length > 0)
                            {
                                for (int i = 0;
                                        i < this.length;
                                        i++, this.offset++)
                                {
                                    buffer[i] = buffer[this.offset];
                                }
                                this.offset = 0;
                                offset = this.length;
                            }
                        }
                    }

                    peeked = PA.stream_peek(stream, buffer, offset);
                }

                PA.stream_drop(stream);
                this.length += peeked;
            }
            finally
            {
                audioSystem.signalMainloop(false);
            }
        }

        /**
         * Sets the volume of a specific PulseAudio <tt>stream</tt> to a
         * specific <tt>level</tt>.
         *
         * @param stream the PulseAudio stream to set the volume of
         * @param level the volume to set on <tt>stream</tt>
         */
        private void setStreamVolume(long stream, float level)
        {
            int volume
                = PA.sw_volume_from_linear(
                        level * (BasicVolumeControl.MAX_VOLUME_PERCENT / 100));

            PA.cvolume_set(cvolume, channels, volume);

            long o
                = PA.context_set_source_output_volume(
                        audioSystem.getContext(),
                        PA.stream_get_index(stream),
                        cvolume,
                        null);

            if (o != 0)
                PA.operation_unref(o);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void start()
            throws IOException
        {
            audioSystem.lockMainloop();
            try
            {
                if (stream == 0)
                    connectWithMainloopLock();

                cork(false);
            }
            finally
            {
                audioSystem.unlockMainloop();
            }

            super.start();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void stop()
            throws IOException
        {
            audioSystem.lockMainloop();
            try
            {
                stopWithMainloopLock();
            }
            finally
            {
                audioSystem.unlockMainloop();
            }
        }

        /**
         * Pauses the input of audio data performed by {@link #stream}. The
         * method executes with the assumption that the PulseAudio event loop
         * object is locked by the executing thread.
         */
        private void stopWithMainloopLock()
            throws IOException
        {
            if (stream != 0)
                cork(true);

            super.stop();
        }
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PulseAudioStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new PulseAudioStream(formatControl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doDisconnect()
    {
        synchronized (getStreamSyncRoot())
        {
            Object[] streams = streams();

            if ((streams != null) && (streams.length != 0))
            {
                for (Object stream : streams)
                {
                    if (stream instanceof PulseAudioStream)
                    {
                        try
                        {
                            ((PulseAudioStream) stream).disconnect();
                        }
                        catch (IOException ioe)
                        {
                            // Well, what can we do?
                        }
                    }
                }
            }
        }

        super.doDisconnect();
    }

    /**
     * Returns the name of the PulseAudio source that this <tt>DataSource</tt>
     * is configured to input audio data from.
     *
     * @return the name of the PulseAudio source that this <tt>DataSource</tt>
     * is configured to input audio data from
     */
    private String getLocatorDev()
    {
        MediaLocator locator = getLocator();
        String locatorDev;

        if (locator == null)
        {
            locatorDev = null;
        }
        else
        {
            locatorDev = locator.getRemainder();
            if ((locatorDev != null) && (locatorDev.length() <= 0))
                locatorDev = null;
        }
        return locatorDev;
    }
}
