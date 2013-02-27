/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.notify;

import java.io.*;

import javax.media.*;

import org.jitsi.impl.neomedia.codec.audio.speex.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.audionotifier.*;
import org.jitsi.util.*;

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
     * The <tt>Logger</tt> used by the <tt>AudioSystemClipImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AudioSystemClipImpl.class);

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
        bufferData = new byte[1024];
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

    protected boolean runOnceInPlayThread()
    {
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

        try
        {
            Format rendererFormat = audioSystem.getFormat(audioStream);

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
                bufferData = new byte[bufferData.length];
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
                    while ((renderer.process(rendererBuffer)
                                & Renderer.INPUT_BUFFER_NOT_CONSUMED)
                            == Renderer.INPUT_BUFFER_NOT_CONSUMED);
                }
            }
            catch (IOException ioex)
            {
                logger.error("Failed to read from audio stream " + uri, ioex);
                return false;
            }
            catch (ResourceUnavailableException ruex)
            {
                logger.error("Failed to open PortAudioRenderer.", ruex);
                return false;
            }
        }
        catch (ResourceUnavailableException ruex)
        {
            if (resampler != null)
            {
                logger.error("Failed to open SpeexResampler.", ruex);
                return false;
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
        }
        return true;
    }
}
