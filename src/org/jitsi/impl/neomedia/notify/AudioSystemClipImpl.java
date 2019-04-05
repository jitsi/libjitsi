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

import java.io.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.audio.speex.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.audionotifier.*;
import org.jitsi.utils.logging.*;

/**
 * Implementation of SCAudioClip using PortAudio.
 *
 * @author Damyian Minkov
 * @author Lyubomir Marinov
 */
public class AudioSystemClipImpl
    extends AbstractSCAudioClip
{
    /**
     * The default length of {@link #bufferData}.
     */
    private static final int DEFAULT_BUFFER_DATA_LENGTH = 8 * 1024;

    /**
     * The <tt>Logger</tt> used by the <tt>AudioSystemClipImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioSystemClipImpl.class);

    /**
     * The minimum duration in milliseconds to be assumed for the audio streams
     * played by <tt>AudioSystemClipImpl</tt> in order to ensure that they are
     * played back long enough to be heard.
     */
    private static final long MIN_AUDIO_STREAM_DURATION = 200;

    private final AudioSystem audioSystem;

    private Buffer buffer;

    private byte[] bufferData;

    private final boolean playback;

    private Renderer renderer;

    /**
     * Creates the audio clip and initializes the listener used from the
     * loop timer.
     *
     * @param url the URL pointing to the audio file
     * @param audioNotifier the audio notify service
     * @param playback to use playback or notification device
     * @throws IOException cannot audio clip with supplied URL.
     */
    public AudioSystemClipImpl(
            String url,
            AudioNotifierService audioNotifier,
            AudioSystem audioSystem,
            boolean playback)
        throws IOException
    {
        super(url, audioNotifier);

        this.audioSystem = audioSystem;
        this.playback = playback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void enterRunInPlayThread()
    {
        buffer = new Buffer();
        bufferData = new byte[DEFAULT_BUFFER_DATA_LENGTH];
        buffer.setData(bufferData);

        renderer = audioSystem.createRenderer(playback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void exitRunInPlayThread()
    {
        buffer = null;
        bufferData = null;
        renderer = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void exitRunOnceInPlayThread()
    {
        try
        {
            renderer.stop();
        }
        finally
        {
            renderer.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    protected boolean runOnceInPlayThread()
    {
        if (renderer == null || buffer == null)
        {
            return false;
        }

        InputStream audioStream = null;

        try
        {
            audioStream = audioSystem.getAudioInputStream(uri);
        }
        catch (IOException ioex)
        {
            logger.error("Failed to get audio stream " + uri, ioex);
        }
        if (audioStream == null)
            return false;

        Codec resampler = null;
        boolean success = true;
        AudioFormat audioStreamFormat = null;
        int audioStreamLength = 0;
        long rendererProcessStartTime = 0;

        try
        {
            Format rendererFormat
                = audioStreamFormat
                    = audioSystem.getFormat(audioStream);

            if (rendererFormat == null)
                return false;

            Format resamplerFormat = null;

            if (renderer.setInputFormat(rendererFormat) == null)
            {
                /*
                 * Try to negotiate a resampling of the audioStream to one of
                 * the formats supported by the renderer.
                 */
                resampler = new SpeexResampler();
                resamplerFormat = rendererFormat;
                resampler.setInputFormat(resamplerFormat);

                Format[] supportedResamplerFormats
                    = resampler.getSupportedOutputFormats(resamplerFormat);

                for (Format supportedRendererFormat
                        : renderer.getSupportedInputFormats())
                {
                    for (Format supportedResamplerFormat
                            : supportedResamplerFormats)
                    {
                        if (supportedRendererFormat.matches(
                                supportedResamplerFormat))
                        {
                            rendererFormat = supportedRendererFormat;
                            resampler.setOutputFormat(rendererFormat);
                            renderer.setInputFormat(rendererFormat);
                            break;
                        }
                    }
                }
            }

            Buffer rendererBuffer = buffer;
            Buffer resamplerBuffer;

            rendererBuffer.setFormat(rendererFormat);
            if (resampler == null)
                resamplerBuffer = null;
            else
            {
                resamplerBuffer = new Buffer();

                int bufferDataLength = DEFAULT_BUFFER_DATA_LENGTH;

                if (resamplerFormat instanceof AudioFormat)
                {
                    AudioFormat af = (AudioFormat) resamplerFormat;
                    int frameSize
                        = af.getSampleSizeInBits() / 8 * af.getChannels();

                    bufferDataLength = bufferDataLength / frameSize * frameSize;
                }
                bufferData = new byte[bufferDataLength];
                resamplerBuffer.setData(bufferData);
                resamplerBuffer.setFormat(resamplerFormat);

                resampler.open();
            }

            try
            {
                renderer.open();
                renderer.start();

                int bufferLength;

                while (isStarted()
                        && ((bufferLength = audioStream.read(bufferData))
                                != -1))
                {
                    audioStreamLength += bufferLength;

                    if (resampler == null)
                    {
                        rendererBuffer.setLength(bufferLength);
                        rendererBuffer.setOffset(0);
                    }
                    else
                    {
                        resamplerBuffer.setLength(bufferLength);
                        resamplerBuffer.setOffset(0);
                        rendererBuffer.setLength(0);
                        rendererBuffer.setOffset(0);
                        resampler.process(resamplerBuffer, rendererBuffer);
                    }

                    int rendererProcess;

                    if (rendererProcessStartTime == 0)
                        rendererProcessStartTime = System.currentTimeMillis();
                    do
                    {
                        rendererProcess = renderer.process(rendererBuffer);
                        if (rendererProcess == Renderer.BUFFER_PROCESSED_FAILED)
                        {
                            logger.error(
                                    "Failed to render audio stream " + uri);
                            success = false;
                            break;
                        }
                    }
                    while ((rendererProcess
                                & Renderer.INPUT_BUFFER_NOT_CONSUMED)
                            == Renderer.INPUT_BUFFER_NOT_CONSUMED);
                }
            }
            catch (IOException ioex)
            {
                logger.error("Failed to read from audio stream " + uri, ioex);
                success = false;
            }
            catch (ResourceUnavailableException ruex)
            {
                logger.error(
                        "Failed to open " + renderer.getClass().getName(),
                        ruex);
                success = false;
            }
        }
        catch (ResourceUnavailableException ruex)
        {
            if (resampler != null)
            {
                logger.error(
                        "Failed to open " + resampler.getClass().getName(),
                        ruex);
                success = false;
            }
        }
        finally
        {
            try
            {
                audioStream.close();
            }
            catch (IOException ioex)
            {
                /*
                 * The audio stream failed to close but it doesn't mean the URL
                 * will fail to open again so ignore the exception.
                 */
            }

            if (resampler != null)
                resampler.close();

            /*
             * XXX We do not know whether the Renderer implementation of the
             * stop method will wait for the playback to complete.
             */
            if (success
                    && (audioStreamFormat != null)
                    && (audioStreamLength > 0)
                    && (rendererProcessStartTime > 0)
                    && isStarted())
            {
                long audioStreamDuration
                    = (audioStreamFormat.computeDuration(audioStreamLength)
                            + 999999)
                        / 1000000;

                if (audioStreamDuration > 0)
                {
                    /*
                     * XXX The estimation is not accurate because we do not
                     * know, for example, how much the Renderer may be buffering
                     * before it starts the playback.
                     */
                    audioStreamDuration += MIN_AUDIO_STREAM_DURATION;

                    boolean interrupted = false;

                    synchronized (sync)
                    {
                        while (isStarted())
                        {
                            long timeout
                                = System.currentTimeMillis()
                                    - rendererProcessStartTime;

                            if ((timeout >= audioStreamDuration)
                                    || (timeout <= 0))
                            {
                                break;
                            }
                            else
                            {
                                try
                                {
                                    sync.wait(timeout);
                                }
                                catch (InterruptedException ie)
                                {
                                    interrupted = true;
                                }
                            }
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
            }
        }
        return success;
    }
}
