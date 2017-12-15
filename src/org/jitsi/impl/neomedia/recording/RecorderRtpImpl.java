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

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.audiolevel.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.fec.*;
import org.jitsi.impl.neomedia.transform.rtcp.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.MediaException;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;

import com.sun.media.util.*;

/**
 * A <tt>Recorder</tt> implementation which attaches to an <tt>RTPTranslator</tt>.
 *
 * @author Vladimir Marinov
 * @author Boris Grozev
 */
public class RecorderRtpImpl
        implements Recorder,
                   ReceiveStreamListener,
                   ActiveSpeakerChangedListener,
                   ControllerListener
{
    /**
     * The <tt>Logger</tt> used by the <tt>RecorderRtpImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(RecorderRtpImpl.class);

    /**
     * The <tt>ConfigurationService</tt> used to load recorder configuration.
     */
    private static final ConfigurationService cfg
            = LibJitsi.getConfigurationService();

    //values hard-coded to match chrome
    //TODO: allow to set them dynamically
    private static final byte redPayloadType = 116;
    private static final byte ulpfecPayloadType = 117;
    private static final byte vp8PayloadType = 100;
    private static final byte opusPayloadType = 111;
    private static final Format redFormat = new VideoFormat(Constants.RED);
    private static final Format ulpfecFormat = new VideoFormat(Constants.ULPFEC);
    private static final Format vp8RtpFormat = new VideoFormat(Constants.VP8_RTP);
    private static final Format vp8Format = new VideoFormat(Constants.VP8);
    private static final Format opusFormat
            = new AudioFormat(Constants.OPUS_RTP,
                              48000,
                              Format.NOT_SPECIFIED,
                              Format.NOT_SPECIFIED);

    /**
     * Config parameter for FMJ video jitter size
     */
    private static final String FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE_PNAME =
            RecorderRtpImpl.class.getCanonicalName() +
                    ".FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE";

    private static final int FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE =
            cfg.getInt(FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE_PNAME, 300);

    /**
     * Config parameter for FMJ audio jitter size
     */
    private static final String FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME =
            RecorderRtpImpl.class.getCanonicalName() +
                    ".FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME";

    private static final int FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE =
            cfg.getInt(FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE_PNAME, 16);

    /**
     * The name of the property which controls whether the recorder should
     * perform active speaker detection.
     */
    private static String PERFORM_ASD_PNAME =
            RecorderRtpImpl.class.getCanonicalName() + ".PERFORM_ASD";

    /**
     * The name of the property which sets a custom output audio codec.
     * Currently only WAV is supported
     */
    private static String AUDIO_CODEC_PNAME =
            RecorderRtpImpl.class.getCanonicalName() + ".AUDIO_CODEC";

    /**
     * The <tt>ContentDescriptor</tt> to use when saving audio.
     */
    private static ContentDescriptor AUDIO_CONTENT_DESCRIPTOR
            = new ContentDescriptor(FileTypeDescriptor.MPEG_AUDIO);

    /**
     * The suffix for audio file names.
     */
    private static String AUDIO_FILENAME_SUFFIX = ".mp3";

    /**
     * The suffix for video file names.
     */
    private static final String VIDEO_FILENAME_SUFFIX = ".webm";

    static
    {
        Registry.set(
                "video_jitter_buffer_MIN_SIZE",
                FMJ_VIDEO_JITTER_BUFFER_MIN_SIZE);
        Registry.set(
                "adaptive_jitter_buffer_MIN_SIZE",
                FMJ_AUDIO_JITTER_BUFFER_MIN_SIZE);
    }

    /**
     * The <tt>RTPTranslator</tt> that this recorder is/will be attached
     * to.
     */
    private RTPTranslatorImpl translator;

    /**
     * The custom <tt>RTPConnector</tt> that this instance uses to read from
     * {@link #translator} and write to {@link #rtpManager}.
     */
    private RTPConnectorImpl rtpConnector;

    /**
     * Path to the directory where the output files will be stored.
     */
    private String path;

    /**
     * The <tt>RTCPFeedbackMessageSender</tt> that we use to send RTCP FIR
     * messages.
     */
    private RTCPFeedbackMessageSender rtcpFeedbackSender;

    /**
     * The {@link RTPManager} instance we use to handle the packets coming
     * from <tt>RTPTranslator</tt>.
     */
    private RTPManager rtpManager;

    /**
     * The instance which should be notified when events related to recordings
     * (such as the start or end of a recording) occur.
     */
    private RecorderEventHandlerImpl eventHandler;

    /**
     * Holds the <tt>ReceiveStreams</tt> added to this instance by
     * {@link #rtpManager} and additional information associated with each one
     * (e.g. the <tt>Processor</tt>, if any, used for it).
     */
    private final HashSet<ReceiveStreamDesc> receiveStreams
            = new HashSet<ReceiveStreamDesc>();

    private final Set<Long> activeVideoSsrcs = new HashSet<Long>();

    /**
     * The <tt>ActiveSpeakerDetector</tt> which will listen to the audio receive
     * streams of this <tt>RecorderRtpImpl</tt> and notify it about changes to
     * the active speaker via calls to {@link #activeSpeakerChanged(long)}
     */
    private ActiveSpeakerDetector activeSpeakerDetector = null;

    /**
     * Controls whether this <tt>RecorderRtpImpl</tt> should perform active
     * speaker detection and fire <tt>SPEAKER_CHANGED</tt> recorder events.
     */
    private final boolean performActiveSpeakerDetection;

    StreamRTPManager streamRTPManager;

    private SynchronizerImpl synchronizer;
    private boolean started = false;
    private MediaStream mediaStream;

    /**
     * Constructor.
     *
     * @param translator the <tt>RTPTranslator</tt> to which this instance will
     * attach in order to record media.
     */
    public RecorderRtpImpl(RTPTranslator translator)
    {
        this.translator = (RTPTranslatorImpl) translator;

        boolean performActiveSpeakerDetection = false;

        if (cfg != null)
        {
            performActiveSpeakerDetection
                = cfg.getBoolean(
                        PERFORM_ASD_PNAME,
                        performActiveSpeakerDetection);

            // setting custom audio codec
            String audioCodec = cfg.getString(AUDIO_CODEC_PNAME);
            if ("wav".equalsIgnoreCase(audioCodec)) {
                AUDIO_FILENAME_SUFFIX = ".wav";
                AUDIO_CONTENT_DESCRIPTOR
                        = new ContentDescriptor(FileTypeDescriptor.WAVE);
            }
        }
        this.performActiveSpeakerDetection = performActiveSpeakerDetection;
    }

    /**
     * Implements {@link Recorder#addListener(Recorder.Listener)}.
     */
    @Override
    public void addListener(Listener listener)
    {
    }

    /**
     * Implements {@link Recorder#removeListener(Recorder.Listener)}.
     */
    @Override
    public void removeListener(Listener listener)
    {
    }

    /**
     * Implements {@link Recorder#getSupportedFormats()}.
     */
    @Override
    public List<String> getSupportedFormats()
    {
        return null;
    }

    /**
     * Implements {@link Recorder#setMute(boolean)}.
     */
    @Override
    public void setMute(boolean mute)
    {
    }

    /**
     * Implements {@link Recorder#getFilename()}. Returns null, since we don't
     * have a (single) associated filename.
     */
    @Override
    public String getFilename()
    {
        return null;
    }

    /**
     * Sets the instance which should be notified when events related to
     * recordings (such as the start or end of a recording) occur.
     */
    public void setEventHandler(RecorderEventHandler eventHandler)
    {
        if (this.eventHandler == null
                || (this.eventHandler != eventHandler
                      && this.eventHandler.handler != eventHandler))
        {
            if (this.eventHandler == null)
                this.eventHandler = new RecorderEventHandlerImpl(eventHandler);
            else
                this.eventHandler.handler = eventHandler;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param format unused, since this implementation records multiple streams
     * using potentially different formats.
     * @param dirname the path to the directory into which this <tt>Recorder</tt>
     * will store the recorded media files.
     */
    @Override
    public void start(String format, String dirname)
            throws IOException,
                   MediaException
    {
        if (logger.isInfoEnabled())
            logger.info("Starting, format=" + format + " " + hashCode());
        path = dirname;

        MediaService mediaService = LibJitsi.getMediaService();

        if (performActiveSpeakerDetection)
        {
            activeSpeakerDetector = new ActiveSpeakerDetectorImpl();
            activeSpeakerDetector.addActiveSpeakerChangedListener(this);
        }

        /*
         * Note that we use only one RTPConnector for both the RTPTranslator
         * and the RTPManager instances. The this.translator will write to its
         * output streams, and this.rtpManager will read from its input streams.
         */
        rtpConnector = new RTPConnectorImpl(redPayloadType, ulpfecPayloadType);

        rtpManager = RTPManager.newInstance();

        /*
         * Add the formats that we know about.
         */
        rtpManager.addFormat(vp8RtpFormat, vp8PayloadType);
        rtpManager.addFormat(opusFormat, opusPayloadType);
        rtpManager.addReceiveStreamListener(this);

        /*
         * Note: When this.rtpManager sends RTCP sender/receiver reports, they
         * will end up being written to its own input stream. This is not
         * expected to cause problems, but might be something to keep an eye on.
         */
        rtpManager.initialize(rtpConnector);

        /*
         * Register a fake call participant.
         * TODO: can we use a more generic MediaStream here?
         */
        mediaStream
            = mediaService.createMediaStream(new MediaDeviceImpl(
                        new CaptureDeviceInfo(), MediaType.VIDEO));
        streamRTPManager = new StreamRTPManager(mediaStream, translator);

        streamRTPManager.initialize(rtpConnector);

        rtcpFeedbackSender = translator.getRtcpFeedbackMessageSender();

        translator.addFormat(streamRTPManager,
                             opusFormat,
                             opusPayloadType);

        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, redFormat, redPayloadType);
        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, ulpfecFormat, ulpfecPayloadType);
        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, mediaFormatImpl.getFormat(), vp8PayloadType);

        started = true;
    }

    /**
     * {@inheritDoc}
     */
    public MediaStream getMediaStream()
    {
        return mediaStream;
    }

    @Override
    public void stop()
    {
        if (started)
        {
        if (logger.isInfoEnabled())
            logger.info("Stopping " + hashCode());

        // remove the recorder from the translator (e.g. stop new packets from
        // being written to rtpConnector
        if (streamRTPManager != null)
            streamRTPManager.dispose();

        HashSet<ReceiveStreamDesc> streamsToRemove
                = new HashSet<ReceiveStreamDesc>();
        synchronized (receiveStreams)
        {
            streamsToRemove.addAll(receiveStreams);
        }

        for(ReceiveStreamDesc r : streamsToRemove)
            removeReceiveStream(r, false);

        rtpConnector.rtcpPacketTransformer.close();
        rtpConnector.rtpPacketTransformer.close();
        rtpManager.dispose();

        if (activeSpeakerDetector != null)
            activeSpeakerDetector.removeActiveSpeakerChangedListener(this);

        started=false;
        }

    }

    /**
     * Implements {@link ReceiveStreamListener#update(ReceiveStreamEvent)}.
     *
     * {@link #rtpManager} will use this to notify us of
     * <tt>ReceiveStreamEvent</tt>s.
     */
    @Override
    public void update(ReceiveStreamEvent event)
    {
        if (event == null)
            return;
        ReceiveStream receiveStream = event.getReceiveStream();

        if (event instanceof NewReceiveStreamEvent)
        {
            if (receiveStream == null)
            {
                logger.warn("NewReceiveStreamEvent: null");
                return;
            }

            final long ssrc = getReceiveStreamSSRC(receiveStream);

            ReceiveStreamDesc receiveStreamDesc = findReceiveStream(ssrc);

            if (receiveStreamDesc != null)
            {
                String s = "NewReceiveStreamEvent for an existing SSRC. ";
                if (receiveStream != receiveStreamDesc.receiveStream)
                    s += "(but different ReceiveStream object)";
                logger.warn(s);
                return;
            }
            else
                receiveStreamDesc = new ReceiveStreamDesc(receiveStream);

            if (logger.isInfoEnabled())
                logger.info("New ReceiveStream, ssrc=" + ssrc);

            // Find the format of the ReceiveStream
            DataSource dataSource = receiveStream.getDataSource();
            if (dataSource instanceof PushBufferDataSource)
            {
                Format format = null;
                PushBufferDataSource pbds = (PushBufferDataSource) dataSource;
                for (PushBufferStream pbs : pbds.getStreams())
                {
                    if ((format = pbs.getFormat()) != null)
                        break;
                }

                if (format == null)
                {
                    logger.error("Failed to handle new ReceiveStream: "
                                         + "Failed to determine format");
                    return;
                }

                receiveStreamDesc.format = format;
            }
            else
            {
                logger.error("Failed to handle new ReceiveStream: "
                                     + "Unsupported DataSource");
                return;
            }

            int rtpClockRate = -1;
            if (receiveStreamDesc.format instanceof AudioFormat)
                rtpClockRate = (int) ((AudioFormat) receiveStreamDesc.format)
                        .getSampleRate();
            else if (receiveStreamDesc.format instanceof VideoFormat)
                rtpClockRate = 90000;
            getSynchronizer().setRtpClockRate(ssrc, rtpClockRate);

            //create a Processor and configure it
            Processor processor = null;
            try
            {
                processor
                        = Manager.createProcessor(receiveStream.getDataSource());
            }
            catch (NoProcessorException npe)
            {
                logger.error("Failed to create Processor: ", npe);
                return;
            }
            catch (IOException ioe)
            {
                logger.error("Failed to create Processor: ", ioe);
                return;
            }

            if (logger.isInfoEnabled())
                logger.info("Created processor for SSRC="+ssrc);

            processor.addControllerListener(this);
            receiveStreamDesc.processor = processor;

            final int streamCount;
            synchronized (receiveStreams)
            {
                receiveStreams.add(receiveStreamDesc);
                streamCount = receiveStreams.size();
            }

            /*
             * XXX TODO IRBABOON
             * This is a terrible hack which works around a failure to realize()
             * some of the Processor-s for audio streams, when multiple streams
             * start nearly simultaneously. The cause of the problem is currently
             * unknown (and synchronizing all FMJ calls in RecorderRtpImpl
             * does not help).
             * XXX TODO NOOBABRI
             */
            if (receiveStreamDesc.format instanceof AudioFormat)
            {
                final Processor p = processor;
                new Thread(){
                    @Override
                    public void run()
                    {
                        // delay configuring the processors for the different
                        // audio streams to decrease the probability that they
                        // run together.
                        try
                        {
                            int ms = 450 * (streamCount - 1);
                            logger.warn("Sleeping for " + ms + "ms before"
                                        + " configuring processor for SSRC="
                                        + ssrc+ " "+System.currentTimeMillis());
                            Thread.sleep(ms);
                        }
                        catch (Exception e) {}

                        p.configure();
                    }
                }.run();
            }
            else
            {
                processor.configure();
            }
        }
        else if (event instanceof TimeoutEvent)
        {
            if (receiveStream == null)
            {
                // TODO: we might want to get the list of ReceiveStream-s from
                // rtpManager and compare it to our list, to see if we should
                // remove a stream.
                logger.warn("TimeoutEvent: null.");
                return;
            }

            // FMJ silently creates new ReceiveStream instances, so we have to
            // recognize them by the SSRC.
            ReceiveStreamDesc receiveStreamDesc
                    = findReceiveStream(getReceiveStreamSSRC(receiveStream));
            if (receiveStreamDesc != null)
            {
                if (logger.isInfoEnabled())
                {
                    logger.info("ReceiveStream timeout, ssrc="
                                        + receiveStreamDesc.ssrc);
                }

                removeReceiveStream(receiveStreamDesc, true);
            }
            else
            {
                if (logger.isInfoEnabled())
                {
                    logger.info("ReceiveStream timeout for an unknown stream"
                                    + " (already removed?) "
                                    + getReceiveStreamSSRC(receiveStream));
                }
            }
        }
        else if (event != null && logger.isInfoEnabled())
        {
            logger.info("Unhandled ReceiveStreamEvent ("
                                + event.getClass().getName()
                                + "): " + event);
        }
    }

    private void removeReceiveStream(ReceiveStreamDesc receiveStream,
                                     boolean emptyJB)
    {
        long ssrc = receiveStream.ssrc;
        if (receiveStream.format instanceof VideoFormat)
        {
            // Don't accept packets with this SSRC
            rtpConnector.packetBuffer.disable(ssrc);
            emptyPacketBuffer(ssrc);

            /*
             * Workaround an issue with Chrome resetting the RTP timestamps
             * after a stream's direction changes: if the stream with the same
             * SSRC starts again later, we will obtain new mappings based on
             * the new Sender Reports.
             * See https://code.google.com/p/webrtc/issues/detail?id=3597
             */
            getSynchronizer().removeMapping(ssrc);

            // Continue accepting packets with this SSRC
            rtpConnector.packetBuffer.reset(ssrc);
        }

        if (receiveStream.dataSink != null)
        {
            try
            {
                receiveStream.dataSink.stop();
            }
            catch (IOException e)
            {
                logger.error("Failed to stop DataSink " + e);
            }

            receiveStream.dataSink.close();
        }

        if (receiveStream.processor != null)
        {
            receiveStream.processor.stop();
            receiveStream.processor.close();
        }

        DataSource dataSource = receiveStream.receiveStream.getDataSource();
        if (dataSource != null)
        {
            try
            {
                dataSource.stop();
            }
            catch (IOException ioe)
            {
                logger.warn("Failed to stop DataSource");
            }
            dataSource.disconnect();
        }

        synchronized(receiveStreams)
        {
            receiveStreams.remove(receiveStream);
        }

        synchronized (activeVideoSsrcs)
        {
            if (activeVideoSsrcs.contains(ssrc))
                activeVideoSsrcs.remove(ssrc);
        }
    }

    /**
     * Implements {@link ControllerListener#controllerUpdate(ControllerEvent)}.
     * Handles events from the <tt>Processor</tt>s that this instance uses to
     * transcode media.
     * @param ev the event to handle.
     */
    public void controllerUpdate(ControllerEvent ev)
    {
        if (ev == null || ev.getSourceController() == null)
        {
            return;
        }

        Processor processor = (Processor) ev.getSourceController();
        ReceiveStreamDesc desc = findReceiveStream(processor);

        if (desc == null)
        {
            logger.warn("Event from an orphaned processor, ignoring: " + ev);
            return;
        }

        if (ev instanceof ConfigureCompleteEvent)
        {
            if (logger.isInfoEnabled())
            {
                logger.info("Configured processor for ReceiveStream ssrc="
                                    + desc.ssrc + " (" + desc.format + ")" +" "+System.currentTimeMillis());
            }

            boolean audio = desc.format instanceof AudioFormat;

            if (audio)
            {
                ContentDescriptor cd =
                        processor.setContentDescriptor(AUDIO_CONTENT_DESCRIPTOR);
                if (!AUDIO_CONTENT_DESCRIPTOR.equals(cd))
                {
                    logger.error("Failed to set the Processor content "
                                         + "descriptor to " + AUDIO_CONTENT_DESCRIPTOR
                                         + ". Actual result: " + cd);
                    removeReceiveStream(desc, false);
                    return;
                }
            }

            for (TrackControl track : processor.getTrackControls())
            {
                Format trackFormat = track.getFormat();

                if (audio)
                {
                    List<Codec> codecList = new LinkedList<Codec>();
                    final long ssrc = desc.ssrc;
                    SilenceEffect silenceEffect;

                    if (Constants.OPUS_RTP.equals(desc.format.getEncoding()))
                    {
                        silenceEffect = new SilenceEffect(48000);
                    }
                    else
                    {
                        // We haven't tested that the RTP timestamps survive
                        // the journey through the chain when codecs other than
                        // opus are in use, so for the moment we rely on FMJ's
                        // timestamps for non-opus formats.
                        silenceEffect = new SilenceEffect();
                    }

                    silenceEffect.setListener(
                            new SilenceEffect.Listener()
                            {
                                boolean first = true;
                                @Override
                                public void onSilenceNotInserted(long timestamp)
                                {
                                    if (first)
                                    {
                                        first = false;
                                        //send event only
                                        audioRecordingStarted(ssrc, timestamp);
                                    }
                                    else
                                    {
                                        //change file and send event
                                        resetRecording(ssrc, timestamp);
                                    }

                                }
                            }
                    );
                    desc.silenceEffect = silenceEffect;
                    codecList.add(silenceEffect);

                    if (performActiveSpeakerDetection)
                    {
                        AudioLevelEffect audioLevelEffect
                                = new AudioLevelEffect();
                        audioLevelEffect.setAudioLevelListener(
                            new SimpleAudioLevelListener()
                            {
                                @Override
                                public void audioLevelChanged(int level)
                                {
                                    activeSpeakerDetector.levelChanged(ssrc,level);
                                }
                            }
                        );

                        codecList.add(audioLevelEffect);
                    }

                    try
                    {
                        // We add an effect, which will insert "silence" in
                        // place of lost packets.
                        track.setCodecChain(
                                codecList.toArray(new Codec[codecList.size()]));
                    }
                    catch (UnsupportedPlugInException upie)
                    {
                        logger.warn("Failed to insert silence effect: " + upie);
                        // But do go on, a recording without extra silence is
                        // better than nothing ;)
                    }
                }
                else
                {
                    // transcode vp8/rtp to vp8 (i.e. depacketize vp8)
                    if (trackFormat.matches(vp8RtpFormat))
                        track.setFormat(vp8Format);
                    else
                    {
                        logger.error("Unsupported track format: " + trackFormat
                                             + " for ssrc=" + desc.ssrc);
                        // we currently only support vp8
                        removeReceiveStream(desc, false);
                        return;
                    }
                }
            }

            processor.realize();
        }
        else if (ev instanceof RealizeCompleteEvent)
        {
            desc.dataSource = processor.getDataOutput();

            long ssrc = desc.ssrc;
            boolean audio = desc.format instanceof AudioFormat;
            String suffix
                    = audio
                    ? AUDIO_FILENAME_SUFFIX
                    : VIDEO_FILENAME_SUFFIX;

            // XXX '\' on windows?
            String filename = getNextFilename(path + "/" + ssrc, suffix);
            desc.filename = filename;

            DataSink dataSink;
            if (audio)
            {
                try
                {
                    dataSink
                            = Manager.createDataSink(desc.dataSource,
                                                     new MediaLocator(
                                                             "file:" + filename));
                }
                catch (NoDataSinkException ndse)
                {
                    logger.error("Could not create DataSink: " + ndse);
                    removeReceiveStream(desc, false);
                    return;
                }

            }
            else
            {
                dataSink = new WebmDataSink(filename, desc.dataSource);
            }

            if (logger.isInfoEnabled())
                logger.info("Created DataSink (" + dataSink + ") for SSRC="
                                    + ssrc + ". Output filename: " + filename);
            try
            {
                dataSink.open();
            }
            catch (IOException e)
            {
                logger.error("Failed to open DataSink (" + dataSink + ") for"
                                     + " SSRC=" + ssrc + ": " + e);
                removeReceiveStream(desc, false);
                return;
            }

            if (!audio)
            {
                final WebmDataSink webmDataSink = (WebmDataSink) dataSink;
                webmDataSink.setSsrc(ssrc);
                webmDataSink.setEventHandler(eventHandler);
                webmDataSink.setKeyFrameControl(new KeyFrameControlAdapter()
                {
                    @Override
                    public boolean requestKeyFrame(
                            boolean urgent)
                    {
                        return requestFIR(webmDataSink);
                    }
                });
            }

            try
            {
                dataSink.start();
            }
            catch (IOException e)
            {
                logger.error("Failed to start DataSink (" + dataSink + ") for"
                                     + " SSRC=" + ssrc + ". " + e);
                removeReceiveStream(desc, false);
                return;
            }

            if (logger.isInfoEnabled())
                logger.info("Started DataSink for SSRC=" + ssrc);

            desc.dataSink = dataSink;

            processor.start();
        }
        else if (logger.isDebugEnabled())
        {
            logger.debug("Unhandled ControllerEvent from the Processor for ssrc="
                                 + desc.ssrc + ": " + ev);
        }
    }

    /**
     * Restarts the recording for a specific SSRC.
     * @param ssrc the SSRC for which to restart recording.
     * RTP packet of the new recording).
     */
    private void resetRecording(long ssrc, long timestamp)
    {
        ReceiveStreamDesc receiveStream = findReceiveStream(ssrc);

        //we only restart audio recordings
        if (receiveStream != null
                && receiveStream.format instanceof AudioFormat)
        {
            String newFilename
                    = getNextFilename(path + "/" + ssrc, AUDIO_FILENAME_SUFFIX);

            //flush the buffer contained in the MP3 encoder
            Processor p = receiveStream.processor;
            if (p != null)
            {
                for (TrackControl tc : p.getTrackControls())
                {
                    Object o = tc.getControl(FlushableControl.class.getName());
                    if (o != null)
                        ((FlushableControl)o).flush();
                }
            }

            if (logger.isInfoEnabled())
            {
                logger.info("Restarting recording for SSRC=" + ssrc
                                    + ". New filename: "+ newFilename);
            }

            receiveStream.dataSink.close();
            receiveStream.dataSink = null;

            // flush the FMJ jitter buffer
            //DataSource ds = receiveStream.receiveStream.getDataSource();
            //if (ds instanceof net.sf.fmj.media.protocol.rtp.DataSource)
            //    ((net.sf.fmj.media.protocol.rtp.DataSource)ds).flush();


            receiveStream.filename = newFilename;
            try
            {
                receiveStream.dataSink
                        = Manager.createDataSink(receiveStream.dataSource,
                                                 new MediaLocator(
                                                         "file:" + newFilename));
            }
            catch (NoDataSinkException ndse)
            {
                logger.warn("Could not reset recording for SSRC=" + ssrc + ": "
                                    + ndse);
                removeReceiveStream(receiveStream, false);
            }

            try
            {
                receiveStream.dataSink.open();
                receiveStream.dataSink.start();
            }
            catch (IOException ioe)
            {
                logger.warn("Could not reset recording for SSRC=" + ssrc + ": "
                                    + ioe);
                removeReceiveStream(receiveStream, false);
            }

            audioRecordingStarted(ssrc, timestamp);
        }
    }

    private void audioRecordingStarted(long ssrc, long timestamp)
    {
        ReceiveStreamDesc desc = findReceiveStream(ssrc);
        if (desc == null)
            return;

        RecorderEvent event = new RecorderEvent();
        event.setType(RecorderEvent.Type.RECORDING_STARTED);
        event.setMediaType(MediaType.AUDIO);
        event.setSsrc(ssrc);
        event.setRtpTimestamp(timestamp);
        event.setFilename(desc.filename);

        if (eventHandler != null)
            eventHandler.handleEvent(event);
    }

    /**
     * Handles a request from a specific <tt>DataSink</tt> to request a keyframe
     * by sending an RTCP feedback FIR message to the media source.
     *
     * @param dataSink the <tt>DataSink</tt> which requests that a keyframe be
     * requested with a FIR message.
     *
     * @return <tt>true</tt> if a keyframe was successfully requested,
     * <tt>false</tt> otherwise
     */
    private boolean requestFIR(WebmDataSink dataSink)
    {
        ReceiveStreamDesc desc = findReceiveStream(dataSink);
        if (desc != null && rtcpFeedbackSender != null)
        {
            return rtcpFeedbackSender.sendFIR((int)desc.ssrc);
        }

        return false;
    }

    /**
     * Returns "prefix"+"suffix" if the file with this name does not exist.
     * Otherwise, returns the first inexistant filename of the form
     * "prefix-"+i+"suffix", for an integer i. i is bounded by 100 to prevent
     * hanging, and on failure to find an inexistant filename the method will
     * return null.
     *
     * @param prefix
     * @param suffix
     * @return
     */
    private String getNextFilename(String prefix, String suffix)
    {
        if (!new File(prefix + suffix).exists())
            return prefix + suffix;

        int i = 1;
        String s;
        do
        {
            s = prefix + "-" + i + suffix;
            if (!new File(s).exists())
                return s;
            i++;
        }
        while (i < 1000); //don't hang indefinitely...

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>Processor</tt>
     * @param processor The <tt>Processor</tt> to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>Processor</tt>, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(Processor processor)
    {
        if (processor == null)
            return null;

        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (processor.equals(r.processor))
                    return r;
        }

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>DataSink</tt>
     * @param dataSink The <tt>DataSink</tt> to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>DataSink</tt>, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(DataSink dataSink)
    {
        if (dataSink == null)
            return null;

        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (dataSink.equals(r.dataSink))
                    return r;
        }

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * SSRC.
     * @param ssrc The SSRC to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * SSRC, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(long ssrc)
    {
        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (ssrc == r.ssrc)
                    return r;
        }

        return null;
    }

    /**
     * Gets the SSRC of a <tt>ReceiveStream</tt> as a (non-negative)
     * <tt>long</tt>.
     *
     * FMJ stores the 32-bit SSRC values in <tt>int</tt>s, and the
     * <tt>ReceiveStream.getSSRC()</tt> implementation(s) don't take care of
     * converting the negative <tt>int</tt> values sometimes resulting from
     * reading of a 32-bit field into the correct unsigned <tt>long</tt> value.
     * So do the conversion here.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> for which to get the SSRC.
     * @return the SSRC of <tt>receiveStream</tt> an a (non-negative)
     * <tt>long</tt>.
     */
    private long getReceiveStreamSSRC(ReceiveStream receiveStream)
    {
        return 0xffffffffL & receiveStream.getSSRC();
    }

    /**
     * Implements {@link ActiveSpeakerChangedListener#activeSpeakerChanged(long)}.
     * Notifies this <tt>RecorderRtpImpl</tt> that the audio
     * <tt>ReceiveStream</tt> considered active has changed, and that the new
     * active stream has SSRC <tt>ssrc</tt>.
     * @param ssrc the SSRC of the new active stream.
     */
    @Override
    public void activeSpeakerChanged(long ssrc)
    {
        if (performActiveSpeakerDetection)
        {
            if (eventHandler != null)
            {
                RecorderEvent e = new RecorderEvent();
                e.setAudioSsrc(ssrc);
                //TODO: how do we time this?
                e.setInstant(System.currentTimeMillis());
                e.setType(RecorderEvent.Type.SPEAKER_CHANGED);
                e.setMediaType(MediaType.VIDEO);
                eventHandler.handleEvent(e);
            }
        }
    }

    private void handleRtpPacket(RawPacket pkt)
    {
        if (pkt != null && pkt.getPayloadType() == vp8PayloadType)
        {
            long ssrc = pkt.getSSRCAsLong();
            if (!activeVideoSsrcs.contains(ssrc))
            {
                synchronized (activeVideoSsrcs)
                {
                    if (!activeVideoSsrcs.contains(ssrc))
                    {
                        activeVideoSsrcs.add(ssrc);
                        rtcpFeedbackSender.sendFIR((int) ssrc);
                    }
                }
            }
        }
    }

    private void handleRtcpPacket(RawPacket pkt)
    {
        getSynchronizer().addRTCPPacket(pkt);
        eventHandler.nudge();
    }

    public SynchronizerImpl getSynchronizer()
    {
        if (synchronizer == null)
            synchronizer = new SynchronizerImpl();
        return synchronizer;
    }

    public void setSynchronizer(Synchronizer synchronizer)
    {
        if (synchronizer instanceof SynchronizerImpl)
        {
            this.synchronizer = (SynchronizerImpl) synchronizer;
        }
    }

    public void connect(Recorder recorder)
    {
        if (!(recorder instanceof RecorderRtpImpl))
            return;

        ((RecorderRtpImpl)recorder).setSynchronizer(getSynchronizer());
    }

    private void emptyPacketBuffer(long ssrc)
    {
        RawPacket[] pkts = rtpConnector.packetBuffer.emptyBuffer(ssrc);
        RTPConnectorImpl.OutputDataStreamImpl dataStream;

        try
        {
            dataStream = rtpConnector.getDataOutputStream();
        }
        catch (IOException ioe)
        {
            logger.error("Failed to empty packet buffer for SSRC=" + ssrc +": "
                            + ioe);
            return;
        }
        for (RawPacket pkt : pkts)
            dataStream.write(pkt.getBuffer(),
                             pkt.getOffset(),
                             pkt.getLength(),
                             false /* already transformed */);
    }
    /**
     * The <tt>RTPConnector</tt> implementation used by this
     * <tt>RecorderRtpImpl</tt>.
     */
    private class RTPConnectorImpl
            implements RTPConnector
    {
        private PushSourceStreamImpl controlInputStream;
        private OutputDataStreamImpl controlOutputStream;

        private PushSourceStreamImpl dataInputStream;
        private OutputDataStreamImpl dataOutputStream;

        private SourceTransferHandler dataTransferHandler;
        private SourceTransferHandler controlTransferHandler;

        private RawPacket pendingDataPacket = new RawPacket();
        private RawPacket pendingControlPacket = new RawPacket();

        private PacketTransformer rtpPacketTransformer = null;
        private PacketTransformer rtcpPacketTransformer = null;

        /**
         * The PacketBuffer instance which we use as a jitter buffer.
         */
        private PacketBuffer packetBuffer;

        private RTPConnectorImpl(byte redPT, byte ulpfecPT)
        {
            packetBuffer = new PacketBuffer();
            // The chain of transformers will be applied in reverse order for
            // incoming packets.
            TransformEngine transformEngine
                    = new TransformEngineChain(
                    new TransformEngine[]
                            {
                                    packetBuffer,
                                    new TransformEngineImpl(),
                                    new CompoundPacketEngine(),
                                    new FECTransformEngine(FECTransformEngine.FecType.ULPFEC,
                                        ulpfecPT, (byte)-1, mediaStream),
                                    new REDTransformEngine(redPT, (byte)-1)
                            });

            rtpPacketTransformer = transformEngine.getRTPTransformer();
            rtcpPacketTransformer = transformEngine.getRTCPTransformer();
        }

        private RTPConnectorImpl()
        {
        }

        @Override
        public void close()
        {
            try
            {
                if (dataOutputStream != null)
                    dataOutputStream.close();
                if (controlOutputStream != null)
                    controlOutputStream.close();
            }
            catch (IOException ioe)
            {
                throw new UndeclaredThrowableException(ioe);
            }
        }

        @Override
        public PushSourceStream getControlInputStream() throws IOException
        {
            if (controlInputStream == null)
            {
                controlInputStream = new PushSourceStreamImpl(true);
            }

            return controlInputStream;
        }

        @Override
        public OutputDataStream getControlOutputStream() throws IOException
        {
            if (controlOutputStream == null)
            {
                controlOutputStream = new OutputDataStreamImpl(true);
            }

            return controlOutputStream;
        }

        @Override
        public PushSourceStream getDataInputStream() throws IOException
        {
            if (dataInputStream == null)
            {
                dataInputStream = new PushSourceStreamImpl(false);
            }

            return dataInputStream;
        }

        @Override
        public OutputDataStreamImpl getDataOutputStream() throws IOException
        {
            if (dataOutputStream == null)
            {
                dataOutputStream = new OutputDataStreamImpl(false);
            }

            return dataOutputStream;
        }

        @Override
        public double getRTCPBandwidthFraction()
        {
            return -1;
        }

        @Override
        public double getRTCPSenderBandwidthFraction()
        {
            return -1;
        }

        @Override
        public int getReceiveBufferSize()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public int getSendBufferSize()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void setReceiveBufferSize(int arg0) throws IOException
        {
            // TODO Auto-generated method stub

        }

        @Override
        public void setSendBufferSize(int arg0) throws IOException
        {
            // TODO Auto-generated method stub
        }

        private class OutputDataStreamImpl
                implements OutputDataStream
        {
            boolean isControlStream;
            private RawPacket[] rawPacketArray = new RawPacket[1];

            public OutputDataStreamImpl(boolean isControlStream)
            {
                this.isControlStream = isControlStream;
            }

            public synchronized int write(byte[] buffer,
                             int offset,
                             int length)
            {
                return write(buffer, offset, length, true);
            }

            public synchronized int write(byte[] buffer,
                             int offset,
                             int length,
                             boolean transform)
            {
                RawPacket pkt = rawPacketArray[0];
                if (pkt == null)
                    pkt = new RawPacket();
                rawPacketArray[0] = pkt;

                byte[] pktBuf = pkt.getBuffer();
                if (pktBuf == null || pktBuf.length < length)
                {
                    pktBuf = new byte[length];
                    pkt.setBuffer(pktBuf);
                }
                System.arraycopy(buffer, offset, pktBuf, 0, length);
                pkt.setOffset(0);
                pkt.setLength(length);

                if (transform)
                {
                    PacketTransformer packetTransformer
                            = isControlStream
                            ? rtcpPacketTransformer
                            : rtpPacketTransformer;

                    if (packetTransformer != null)
                        rawPacketArray
                            = packetTransformer.reverseTransform(rawPacketArray);
                }

                SourceTransferHandler transferHandler;
                PushSourceStream pushSourceStream;
                try
                {
                    if (isControlStream)
                    {
                        transferHandler = controlTransferHandler;
                        pushSourceStream = getControlInputStream();
                    }
                    else
                    {
                        transferHandler = dataTransferHandler;
                        pushSourceStream = getDataInputStream();
                    }
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }

                for (int i = 0; i < rawPacketArray.length; i++)
                {
                    RawPacket packet = rawPacketArray[i];

                    //keep the first element for reuse
                    if (i != 0)
                        rawPacketArray[i] = null;

                    if (packet != null)
                    {
                        if (isControlStream)
                            pendingControlPacket = packet;
                        else
                            pendingDataPacket = packet;

                        if (transferHandler != null)
                        {
                            transferHandler.transferData(pushSourceStream);
                        }
                    }
                }

                return length;
            }

            public void close() throws IOException
            {
            }
        }

        /**
         * A dummy implementation of {@link PushSourceStream}.
         * @author Vladimir Marinov
         */
        private class PushSourceStreamImpl implements PushSourceStream
        {

            private boolean isControlStream = false;

            public PushSourceStreamImpl(boolean isControlStream)
            {
                this.isControlStream = isControlStream;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public boolean endOfStream()
            {
                return false;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public ContentDescriptor getContentDescriptor()
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public long getContentLength()
            {
                return 0;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public Object getControl(String arg0)
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public Object[] getControls()
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public int getMinimumTransferSize()
            {
                if (isControlStream)
                {
                    if (pendingControlPacket.getBuffer() != null)
                    {
                        return pendingControlPacket.getLength();
                    }
                }
                else
                {
                    if (pendingDataPacket.getBuffer() != null)
                    {
                        return pendingDataPacket.getLength();
                    }
                }

                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length)
                    throws IOException
            {

                RawPacket pendingPacket;
                if (isControlStream)
                {
                    pendingPacket = pendingControlPacket;
                }
                else
                {
                    pendingPacket = pendingDataPacket;
                }
                int bytesToRead = 0;
                byte[] pendingPacketBuffer = pendingPacket.getBuffer();
                if (pendingPacketBuffer != null)
                {
                    int pendingPacketLength = pendingPacket.getLength();
                    bytesToRead = length > pendingPacketLength ?
                            pendingPacketLength: length;
                    System.arraycopy(
                            pendingPacketBuffer,
                            pendingPacket.getOffset(),
                            buffer,
                            offset,
                            bytesToRead);
                }
                return bytesToRead;
            }

            /**
             * {@inheritDoc}
             *
             * We keep the first non-null <tt>SourceTransferHandler</tt> that
             * was set, because we don't want it to be overwritten when we
             * initialize a second <tt>RTPManager</tt> with this
             * <tt>RTPConnector</tt>.
             *
             * See {@link RecorderRtpImpl#start(String, String)}
             */
            @Override
            public void setTransferHandler(
                    SourceTransferHandler transferHandler)
            {
                if (isControlStream)
                {
                    if (RTPConnectorImpl.this.controlTransferHandler == null)
                    {
                        RTPConnectorImpl.this.
                                controlTransferHandler = transferHandler;
                    }
                }
                else
                {
                    if (RTPConnectorImpl.this.dataTransferHandler == null)
                    {
                        RTPConnectorImpl.this.
                                dataTransferHandler = transferHandler;
                    }
                }
            }
        }

        /**
         * A transform engine implementation which allows
         * <tt>RecorderRtpImpl</tt> to intercept RTP and RTCP packets in.
         */
        private class TransformEngineImpl
            implements TransformEngine
        {
            SinglePacketTransformer rtpTransformer
                    = new SinglePacketTransformerAdapter()
                    {
                        @Override
                        public RawPacket reverseTransform(RawPacket pkt)
                        {
                            RecorderRtpImpl.this.handleRtpPacket(pkt);
                            return pkt;
                        }

                        @Override
                        public void close()
                        {
                        }
                    };

            SinglePacketTransformer rtcpTransformer
                    = new SinglePacketTransformerAdapter()
            {
                @Override
                public RawPacket reverseTransform(RawPacket pkt)
                {
                    RecorderRtpImpl.this.handleRtcpPacket(pkt);
                    if (pkt != null && pkt.getRTCPPacketType() == 203)
                    {
                        // An RTCP BYE packet. Remove the receive stream before
                        // it gets to FMJ, because we want to, for example,
                        // flush the packet buffer before that.

                        long ssrc = pkt.getRTCPSSRC();
                        if (logger.isInfoEnabled())
                            logger.info("RTCP BYE for SSRC="+ssrc);

                        ReceiveStreamDesc receiveStream = findReceiveStream(ssrc);
                        if (receiveStream != null)
                            removeReceiveStream(receiveStream, false);
                    }
                    else if (pkt != null && pkt.getRTCPPacketType() == 201)
                    {
                        // Do not pass Receiver Reports to FMJ, because it does
                        // not need them (it isn't sending) and because they
                        // causes weird problems.
                        return null;
                    }

                    return pkt;
                }

                @Override
                public void close()
                {
                }
            };

            @Override
            public PacketTransformer getRTPTransformer()
            {
                return rtpTransformer;
            }

            @Override
            public PacketTransformer getRTCPTransformer()
            {
                return rtcpTransformer;
            }
        }
    }

    private class RecorderEventHandlerImpl
            implements RecorderEventHandler
    {
        private RecorderEventHandler handler;
        private final Set<RecorderEvent> pendingEvents
                = new HashSet<RecorderEvent>();

        private RecorderEventHandlerImpl(RecorderEventHandler handler)
        {
            this.handler = handler;
        }

        @Override
        public boolean handleEvent(RecorderEvent ev)
        {
            if (ev == null)
                return true;
            if (RecorderEvent.Type.RECORDING_STARTED.equals(ev.getType()))
            {
                long instant
                        = getSynchronizer().getLocalTime(ev.getSsrc(),
                                                         ev.getRtpTimestamp());
                if (instant != -1)
                {
                    ev.setInstant(instant);
                    return handler.handleEvent(ev);
                }
                else
                {
                    pendingEvents.add(ev);
                    return true;
                }
            }
            return handler.handleEvent(ev);
        }

        private void nudge()
        {
            for(Iterator<RecorderEvent> iter = pendingEvents.iterator();
                iter.hasNext();)
            {
                RecorderEvent ev = iter.next();
                long instant
                        = getSynchronizer().getLocalTime(ev.getSsrc(),
                                                         ev.getRtpTimestamp());
                if (instant != -1)
                {
                    iter.remove();
                    ev.setInstant(instant);
                    handler.handleEvent(ev);
                }
            }
        }

        @Override
        public void close()
        {
            for (RecorderEvent ev : pendingEvents)
                handler.handleEvent(ev);
        }
    }

    /**
     * Represents a <tt>ReceiveStream</tt> for the purposes of this
     * <tt>RecorderRtpImpl</tt>.
     */
    private class ReceiveStreamDesc
    {
        /**
         * The actual <tt>ReceiveStream</tt> which is represented by this
         * <tt>ReceiveStreamDesc</tt>.
         */
        private ReceiveStream receiveStream;

        /**
         * The SSRC of the stream.
         */
        long ssrc;

        /**
         * The <tt>Processor</tt> used to transcode this receive stream into a
         * format appropriate for saving to a file.
         */
        private Processor processor;

        /**
         * The <tt>DataSink</tt> which saves the <tt>this.dataSource</tt> to a
         * file.
         */
        private DataSink dataSink;

        /**
         * The <tt>DataSource</tt> for this receive stream which is to be saved
         * using a <tt>DataSink</tt> (i.e. the <tt>DataSource</tt> "after" all
         * needed transcoding is done).
         */
        private DataSource dataSource;

        /**
         * The name of the file into which this stream is being saved.
         */
        private String filename;

        /**
         * The (original) format of this receive stream.
         */
        private Format format;

        /**
         * The <tt>SilenceEffect</tt> used for this stream (for audio streams
         * only).
         */
        private SilenceEffect silenceEffect;

        private ReceiveStreamDesc(ReceiveStream receiveStream)
        {
            this.receiveStream = receiveStream;
            this.ssrc = getReceiveStreamSSRC(receiveStream);
        }

    }
}
