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

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.impl.neomedia.pulseaudio.*;
import org.jitsi.utils.version.*;

/**
 * Implements an <tt>AudioSystem</tt> using the native PulseAudio API/library.
 *
 * @author Lyubomir Marinov
 */
public class PulseAudioSystem
    extends AudioSystem
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying PulseAudio
     * <tt>CaptureDevice</tt>s.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_PULSEAUDIO;

    /**
     * The PulseAudio logic role of media which represents an event,
     * notification.
     */
    public static final String MEDIA_ROLE_EVENT = "event";

    /**
     * The PulseAudio logic role of media which represents telephony call audio.
     */
    public static final String MEDIA_ROLE_PHONE = "phone";

    /**
     * The human-readable name of the <tt>CaptureDeviceInfo</tt> which is to
     * represent the automatic, default PulseAudio device to be used in the
     * absence of a specification of a PulseAudio device.
     */
    private static final String NULL_DEV_CAPTURE_DEVICE_INFO_NAME = "Default";

    /**
     * Pause or resume the playback of a specific PulseAudio stream temporarily.
     *
     * @param stream the PulseAudio stream to pause or resume the playback of
     * @param b <tt>true</tt> to pause or <tt>false</tt> to resume the playback
     * of the specified <tt>stream</tt>
     */
    public static void corkStream(long stream, boolean b)
        throws IOException
    {
        if (stream == 0)
            throw new IOException("stream");

        long o = PA.stream_cork(stream, b, null);

        if (o == 0)
            throw new IOException("pa_stream_cork");

        PA.operation_unref(o);
    }

    /**
     * Returns the one and only instance of <tt>PulseAudioSystem</tt> known to
     * the <tt>AudioSystem</tt> framework.
     *
     * @return the one and only instance of <tt>PulseAudioSystem</tt> known to
     * the <tt>AudioSystem</tt> framework or <tt>null</tt> if no such system is
     * known to the <tt>AudioSystem</tt> framework
     */
    public static PulseAudioSystem getPulseAudioSystem()
    {
        AudioSystem audioSystem
            = AudioSystem.getAudioSystem(PulseAudioSystem.LOCATOR_PROTOCOL);

        return
            (audioSystem instanceof PulseAudioSystem)
                ? (PulseAudioSystem) audioSystem
                : null;
    }

    /**
     * The indicator which specifies whether the method {@link #createContext()}
     * has been executed. Used to enforce a single execution of the method in
     * question.
     */
    private boolean createContext;

    /**
     * The connection context for asynchronous communication with the PulseAudio
     * server.
     */
    private long context;

    /**
     * The PulseAudio main loop associated with this <tt>PulseAudioSystem</tt>.
     */
    private long mainloop;

    /**
     * Initializes a new <tt>PulseAudioSystem</tt> instance.
     *
     * @throws Exception if anything goes wrong while initializing the new
     * instance
     */
    public PulseAudioSystem()
        throws Exception
    {
        super(LOCATOR_PROTOCOL, FEATURE_NOTIFY_AND_PLAYBACK_DEVICES);
    }

    /**
     * Initializes the connection context for asynchronous communication with
     * the PulseAudio server i.e. creates {@link #context}.
     */
    private void createContext()
    {
        if (this.context != 0)
            throw new IllegalStateException("context");

        startMainloop();
        try
        {
            long proplist = PA.proplist_new();

            if (proplist == 0)
                throw new RuntimeException("pa_proplist_new");

            try
            {
                populateContextProplist(proplist);

                long context
                    = PA.context_new_with_proplist(
                            PA.threaded_mainloop_get_api(mainloop),
                            null /* PA_PROP_APPLICATION_NAME */,
                            proplist);

                if (context == 0)
                    throw new RuntimeException("pa_context_new_with_proplist");

                try
                {
                    PA.proplist_free(proplist);
                    proplist = 0;

                    Runnable stateCallback
                        = new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                signalMainloop(false);
                            }
                        };

                    lockMainloop();
                    try
                    {
                        PA.context_set_state_callback(context, stateCallback);
                        PA.context_connect(
                                context,
                                null,
                                PA.CONTEXT_NOFLAGS,
                                0);

                        try
                        {
                            int state
                                = waitForContextState(
                                        context,
                                        PA.CONTEXT_READY);

                            if (state == PA.CONTEXT_READY)
                            {
                                this.context = context;
                            }
                            else
                            {
                                throw new IllegalStateException(
                                        "context.state");
                            }
                        }
                        finally
                        {
                            if (this.context == 0)
                                PA.context_disconnect(context);
                        }
                    }
                    finally
                    {
                        unlockMainloop();
                    }
                }
                finally
                {
                    if (this.context == 0)
                        PA.context_unref(context);
                }
            }
            finally
            {
                if (proplist != 0)
                    PA.proplist_free(proplist);
            }
        }
        finally
        {
            if (this.context == 0)
                stopMainloop();
        }
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the implementation provided by <tt>AudioSystem</tt> because the
     * PulseAudio <tt>Renderer</tt> implementation does not follow the
     * convention of <tt>AudioSystem</tt>.
     */
    @Override
    public Renderer createRenderer(boolean playback)
    {
        MediaLocator locator;

        if (playback)
        {
            locator = null;
        }
        else
        {
            CaptureDeviceInfo device = getSelectedDevice(DataFlow.NOTIFY);

            if (device == null)
            {
                // As AudioSystem does, no notification is to be sounded unless
                // there is a device with notify data flow.
                return null;
            }
            else
            {
                locator = device.getLocator();
            }
        }

        PulseAudioRenderer renderer
            = new PulseAudioRenderer(
                    playback ? MEDIA_ROLE_PHONE : MEDIA_ROLE_EVENT);

        if (locator != null)
            renderer.setLocator(locator);

        return renderer;
    }

    /**
     * Initializes a new PulseAudio stream which is to input or output audio at
     * a specific sample rate and with a specific number of channels. The new
     * audio stream is to be associated with a specific human-readable name and
     * is to have a specific PulseAudio logic role.
     *
     * @param sampleRate the sample rate at which the new PulseAudio stream is
     * to input or output
     * @param channels the number of channels of the audio to be input or output
     * by the new PulseAudio stream
     * @param mediaName the human-readable name of the new PulseAudio stream
     * @param mediaRole the PulseAudio logic role of the new stream
     * @return a new PulseAudio stream which is to input or output audio at the
     * specified <tt>sampleRate</tt>, with the specified number of
     * <tt>channels</tt>, to be associated with the specified human-readable
     * <tt>mediaName</tt>, and have the specified PulseAudio logic
     * <tt>mediaRole</tt>
     */
    public long createStream(
            int sampleRate,
            int channels,
            String mediaName,
            String mediaRole)
        throws RuntimeException
    {
        long context = getContext();

        if (context == 0)
            throw new IllegalStateException("context");

        long sampleSpec
            = PA.sample_spec_new(PA.SAMPLE_S16LE, sampleRate, channels);

        if (sampleSpec == 0)
            throw new RuntimeException("pa_sample_spec_new");

        try
        {
            long proplist = PA.proplist_new();

            if (proplist == 0)
                throw new RuntimeException("pa_proplist_new");

            try
            {
                PA.proplist_sets(proplist, PA.PROP_MEDIA_NAME, mediaRole);
                PA.proplist_sets(proplist, PA.PROP_MEDIA_ROLE, mediaRole);

                long stream
                    = PA.stream_new_with_proplist(
                            context,
                            null,
                            sampleSpec,
                            0,
                            proplist);

                if (stream == 0)
                {
                    throw new RuntimeException(
                            "pa_stream_new_with_proplist");
                }

                return stream;
            }
            finally
            {
                PA.proplist_free(proplist);
            }
        }
        finally
        {
            PA.sample_spec_free(sampleSpec);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doInitialize()
        throws Exception
    {
        long context = getContext();

        final List<CaptureDeviceInfo2> captureDevices
            = new LinkedList<CaptureDeviceInfo2>();
        final List<Format> captureDeviceFormats = new LinkedList<Format>();
        PA.source_info_cb_t sourceInfoListCb
            = new PA.source_info_cb_t()
            {
                @Override
                public void callback(long c, long i, int eol)
                {
                    try
                    {
                        if (eol == 0 && i != 0)
                        {
                            sourceInfoListCb(
                                    c,
                                    i,
                                    captureDevices,
                                    captureDeviceFormats);
                        }
                    }
                    finally
                    {
                        signalMainloop(false);
                    }
                }
            };

        final List<CaptureDeviceInfo2> playbackDevices
            = new LinkedList<CaptureDeviceInfo2>();
        final List<Format> playbackDeviceFormats = new LinkedList<Format>();
        PA.sink_info_cb_t sinkInfoListCb
            = new PA.sink_info_cb_t()
            {
                @Override
                public void callback(long c, long i, int eol)
                {
                    try
                    {
                        if (eol == 0 && i != 0)
                        {
                            sinkInfoListCb(
                                    c,
                                    i,
                                    playbackDevices,
                                    playbackDeviceFormats);
                        }
                    }
                    finally
                    {
                        signalMainloop(false);
                    }
                }
            };

        lockMainloop();
        try
        {
            long o;

            o = PA.context_get_source_info_list(context, sourceInfoListCb);
            if (o == 0)
                throw new RuntimeException("pa_context_get_source_info_list");
            try
            {
                while (PA.operation_get_state(o) == PA.OPERATION_RUNNING)
                    waitMainloop();
            }
            finally
            {
                PA.operation_unref(o);
            }

            o = PA.context_get_sink_info_list(context, sinkInfoListCb);
            if (o == 0)
                throw new RuntimeException("pa_context_get_sink_info_list");
            try
            {
                while (PA.operation_get_state(o) == PA.OPERATION_RUNNING)
                    waitMainloop();
            }
            finally
            {
                PA.operation_unref(o);
            }
        }
        finally
        {
            unlockMainloop();
        }

        if (!captureDeviceFormats.isEmpty())
        {
            captureDevices.add(
                    0,
                    new CaptureDeviceInfo2(
                            NULL_DEV_CAPTURE_DEVICE_INFO_NAME,
                            new MediaLocator(LOCATOR_PROTOCOL + ":"),
                            captureDeviceFormats.toArray(
                                    new Format[captureDeviceFormats.size()]),
                            null,
                            null,
                            null));
        }
        if (!playbackDevices.isEmpty())
        {
            playbackDevices.add(
                    0,
                    new CaptureDeviceInfo2(
                            NULL_DEV_CAPTURE_DEVICE_INFO_NAME,
                            new MediaLocator(LOCATOR_PROTOCOL + ":"),
                            null,
                            null,
                            null,
                            null));
        }

        setCaptureDevices(captureDevices);
        setPlaybackDevices(playbackDevices);
    }

    /**
     * Returns the connection context for asynchronous communication with the
     * PulseAudio server. If such a context does not exist, it is created.
     *
     * @return the connection context for asynchronous communication with the
     * PulseAudio server
     */
    public synchronized long getContext()
    {
        if (context == 0)
        {
            if (!createContext)
            {
                createContext = true;
                createContext();
            }
            if (context == 0)
                throw new IllegalStateException("context");
        }
        return context;
    }

    /**
     * Locks the PulseAudio event loop object associated with this
     * <tt>PulseAudioSystem</tt>, effectively blocking the PulseAudio event loop
     * thread from processing events. May be used to enforce exclusive access to
     * all PulseAudio objects attached to the PulseAudio event loop. The lock is
     * recursive. The method may not be called inside the PulseAudio event loop
     * thread. Events that are dispatched from the PulseAudio event loop thread
     * are executed with the lock held.
     */
    public void lockMainloop()
    {
        PA.threaded_mainloop_lock(mainloop);
    }

    /**
     * Populates a specific <tt>pa_proplist</tt> which is to be used with a
     * <tt>pa_context</tt> with properties such as the application name and
     * version.
     *
     * @param proplist the <tt>pa_proplist</tt> which is to be populated with
     * <tt>pa_context</tt>-related properties such as the application name and
     * version
     */
    private void populateContextProplist(long proplist)
    {
        // XXX For the sake of simplicity while working on libjitsi, get the
        // version information in the form of System property values instead of
        // going through the VersionService.
        // TODO: use the VersionService
        String name = "libjitsi";
        String version = null;

        if (name != null)
            PA.proplist_sets(proplist, PA.PROP_APPLICATION_NAME, name);
        if (version != null)
            PA.proplist_sets(proplist, PA.PROP_APPLICATION_VERSION, version);
    }

    /**
     * Signals all threads waiting for a signalling event in
     * {@link #waitMainloop()}.
     *
     * @param waitForAccept <tt>true</tt> to not return before the signal is
     * accepted by a <tt>pa_threaded_mainloop_accept()<tt>; otherwise,
     * <tt>false</tt>
     */
    public void signalMainloop(boolean waitForAccept)
    {
        PA.threaded_mainloop_signal(mainloop, waitForAccept);
    }

    /**
     * Called back from <tt>pa_context_get_sink_info_list()</tt> to report
     * information about a specific sink.
     *
     * @param context the connection context for asynchronous communication with
     * the PulseAudio server which is reporting the <tt>sinkInfo</tt>
     * @param sinkInfo the information about the sink being reported
     * @param deviceList the list of <tt>CaptureDeviceInfo</tt>s which
     * reperesents existing devices and into which the callback is represent the
     * <tt>sinkInfo</tt>
     * @param formatList the list of <tt>Format</tt>s supported by the various
     * <tt>CaptureDeviceInfo</tt>s in <tt>deviceList</tt> and into which the
     * callback is to represent the <tt>sinkInfo</tt>
     */
    private void sinkInfoListCb(
            long context,
            long sinkInfo,
            List<CaptureDeviceInfo2> deviceList,
            List<Format> formatList)
    {
        // PulseAudio should supposedly automatically convert between sample
        // formats so we do not have to insist on PA_SAMPLE_S16LE.
        int sampleSpecFormat = PA.sink_info_get_sample_spec_format(sinkInfo);

        if (sampleSpecFormat == PA.SAMPLE_INVALID)
            return;

        String description = PA.sink_info_get_description(sinkInfo);
        String name = PA.sink_info_get_name(sinkInfo);

        deviceList.add(
                new CaptureDeviceInfo2(
                        (description == null) ? name : description,
                        new MediaLocator(LOCATOR_PROTOCOL + ":" + name),
                        null,
                        null,
                        null,
                        null));
    }

    /**
     * Called back from <tt>pa_context_get_source_info_list()</tt> to report
     * information about a specific source.
     *
     * @param context the connection context for asynchronous communication with
     * the PulseAudio server which is reporting the <tt>sourceInfo</tt>
     * @param sourceInfo the information about the source being reported
     * @param deviceList the list of <tt>CaptureDeviceInfo</tt>s which
     * reperesents existing devices and into which the callback is represent the
     * <tt>sourceInfo</tt>
     * @param formatList the list of <tt>Format</tt>s supported by the various
     * <tt>CaptureDeviceInfo</tt>s in <tt>deviceList</tt> and into which the
     * callback is to represent the <tt>sourceInfo</tt>
     */
    private void sourceInfoListCb(
            long context,
            long sourceInfo,
            List<CaptureDeviceInfo2> deviceList,
            List<Format> formatList)
    {
        int monitorOfSink = PA.source_info_get_monitor_of_sink(sourceInfo);

        if (monitorOfSink != PA.INVALID_INDEX)
            return;

        // PulseAudio should supposedly automatically convert between sample
        // formats so we do not have to insist on PA_SAMPLE_S16LE.
        int sampleSpecFormat
            = PA.source_info_get_sample_spec_format(sourceInfo);

        if (sampleSpecFormat == PA.SAMPLE_INVALID)
            return;

        int channels = PA.source_info_get_sample_spec_channels(sourceInfo);
        int rate = PA.source_info_get_sample_spec_rate(sourceInfo);

        if ((MediaUtils.MAX_AUDIO_CHANNELS != Format.NOT_SPECIFIED)
                && (MediaUtils.MAX_AUDIO_CHANNELS < channels))
            channels = MediaUtils.MAX_AUDIO_CHANNELS;
        if ((MediaUtils.MAX_AUDIO_SAMPLE_RATE != Format.NOT_SPECIFIED)
                && (MediaUtils.MAX_AUDIO_SAMPLE_RATE < rate))
            rate = (int) MediaUtils.MAX_AUDIO_SAMPLE_RATE;

        AudioFormat audioFormat
            = new AudioFormat(
                    AudioFormat.LINEAR,
                    rate,
                    16,
                    channels,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray);

        if (!formatList.contains(audioFormat))
            formatList.add(audioFormat);

        String description = PA.source_info_get_description(sourceInfo);
        String name = PA.source_info_get_name(sourceInfo);

        deviceList.add(
                new CaptureDeviceInfo2(
                        (description == null) ? name : description,
                        new MediaLocator(LOCATOR_PROTOCOL + ":" + name),
                        new Format[] { audioFormat },
                        null,
                        null,
                        null));
    }

    /**
     * Starts a new PulseAudio event loop thread and associates it with this
     * <tt>PulseAudioSystem</tt>.
     *
     * @throws RuntimeException if a PulseAudio event loop thread exists and is
     * associated with this <tt>PulseAudioSystem</tt> or a new PulseAudio event
     * loop thread initialized for the purposes of association with this
     * <tt>PulseAudioSystem</tt> failed to start
     */
    private void startMainloop()
    {
        if (this.mainloop != 0)
            throw new IllegalStateException("mainloop");

        long mainloop = PA.threaded_mainloop_new();

        if (mainloop == 0)
            throw new RuntimeException("pa_threaded_mainloop_new");
        try
        {
            if (PA.threaded_mainloop_start(mainloop) < 0)
                throw new RuntimeException("pa_threaded_mainloop_start");

            this.mainloop = mainloop;
        }
        finally
        {
            if (this.mainloop == 0)
                PA.threaded_mainloop_free(mainloop);
        }
    }

    /**
     * Terminates the PulseAudio event loop thread associated with this
     * <tt>PulseAudioSystem</tt> cleanly. Make sure to unlock the PulseAudio
     * main loop object before calling the method.
     */
    private void stopMainloop()
    {
        long mainloop = this.mainloop;

        if (mainloop == 0)
            throw new IllegalStateException("mainloop");

        this.mainloop = 0;
        PA.threaded_mainloop_stop(mainloop);
        PA.threaded_mainloop_free(mainloop);
    }

    /**
     * Returns a human-readable <tt>String</tt> representation of this
     * <tt>PulseAudioSystem</tt>. Always returns &quot;PulseAudio&quot;.
     *
     * @return &quot;PulseAudio&quot; as a human-readable <tt>String</tt>
     * representation of this <tt>PulseAudioSystem</tt>
     */
    @Override
    public String toString()
    {
        return "PulseAudio";
    }

    /**
     * Unlocks the PulseAudio event look object associated with this
     * <tt>PulseAudioSystem</tt>, inverse of {@link #lockMainloop()}.
     */
    public void unlockMainloop()
    {
        PA.threaded_mainloop_unlock(mainloop);
    }

    /**
     * Waits for a specific PulseAudio context to get into a specific state,
     * <tt>PA_CONTEXT_FAILED</tt>, or <tt>PA_CONTEXT_TERMINATED</tt>.
     *
     * @param context the PulseAudio context to wait for
     * @param stateToWaitFor the PulseAudio state of the specified
     * <tt>context</tt> to wait for
     * @return the state of the specified <tt>context</tt> which caused the
     * method to return
     */
    private int waitForContextState(long context, int stateToWaitFor)
    {
        int state;

        do
        {
            state = PA.context_get_state(context);
            if ((PA.CONTEXT_FAILED == state)
                    || (stateToWaitFor == state)
                    || (PA.CONTEXT_TERMINATED == state))
                break;

            waitMainloop();
        }
        while (true);

        return state;
    }

    /**
     * Waits for a specific PulseAudio stream to get into a specific state,
     * <tt>PA_STREAM_FAILED</tt>, or <tt>PA_STREAM_TERMINATED</tt>.
     *
     * @param stream the PulseAudio stream to wait for
     * @param stateToWaitFor the PulseAudio state of the specified
     * <tt>stream</tt> to wait for
     * @return the state of the specified <tt>stream</tt> which caused the
     * method to return
     */
    public int waitForStreamState(long stream, int stateToWaitFor)
    {
        int state;

        do
        {
            state = PA.stream_get_state(stream);
            if ((stateToWaitFor == state)
                    || (PA.STREAM_FAILED == state)
                    || (PA.STREAM_TERMINATED == state))
                break;

            waitMainloop();
        }
        while (true);

        return state;
    }

    /**
     * Wait for an event to be signalled by the PulseAudio event loop thread
     * associated with this <tt>PulseAudioSystem</tt>.
     */
    public void waitMainloop()
    {
        PA.threaded_mainloop_wait(mainloop);
    }
}
