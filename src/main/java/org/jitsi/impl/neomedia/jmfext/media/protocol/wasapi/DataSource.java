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
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.utils.logging.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> using Windows Audio
 * Session API (WASAPI) and related Core Audio APIs such as Multimedia Device
 * (MMDevice) API.
 *
 * @author Lyubomir Marinov
 */
public class DataSource
    extends AbstractPushBufferCaptureDevice
{
    /**
     * The <tt>Logger</tt> used by the <tt>DataSource</tt> class and its
     * instances to log debugging information.
     */
    private static final Logger logger = Logger.getLogger(DataSource.class);

    /**
     * The indicator which determines whether the voice capture DMO is to be
     * used to perform echo cancellation and/or noise reduction.
     */
    final boolean aec;

    /**
     * The <tt>WASAPISystem</tt> which has contributed this
     * <tt>CaptureDevice</tt>/<tt>DataSource</tt>.
     */
    final WASAPISystem audioSystem;

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
        this(null);
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance with a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to initialize the new instance
     * with
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);

        audioSystem
            = (WASAPISystem)
                AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_WASAPI);
        aec = audioSystem.isDenoise() || audioSystem.isEchoCancel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WASAPIStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new WASAPIStream(this, formatControl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doConnect()
        throws IOException
    {
        super.doConnect();

        MediaLocator locator = getLocator();

        synchronized (getStreamSyncRoot())
        {
            for (Object stream : getStreams())
                ((WASAPIStream) stream).setLocator(locator);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doDisconnect()
    {
        try
        {
            synchronized (getStreamSyncRoot())
            {
                for (Object stream : getStreams())
                {
                    try
                    {
                        ((WASAPIStream) stream).setLocator(null);
                    }
                    catch (IOException ioe)
                    {
                        logger.error(
                                "Failed to disconnect "
                                    + stream.getClass().getName(),
                                ioe);
                    }
                }
            }
        }
        finally
        {
            super.doDisconnect();
        }
    }

    /**
     * Gets the <tt>Format</tt>s of media data supported by the audio endpoint
     * device associated with this instance.
     *
     * @return the <tt>Format</tt>s of media data supported by the audio
     * endpoint device associated with this instance
     */
    Format[] getIAudioClientSupportedFormats()
    {
        return getIAudioClientSupportedFormats(/* streamIndex */ 0);
    }

    /**
     * Gets the <tt>Format</tt>s of media data supported by the audio endpoint
     * device associated with this instance.
     *
     * @param streamIndex the index of the <tt>SourceStream</tt> within the list
     * of <tt>SourceStream</tt>s of this <tt>DataSource</tt> on behalf of which
     * the query is being made
     * @return the <tt>Format</tt>s of media data supported by the audio
     * endpoint device associated with this instance
     */
    private Format[] getIAudioClientSupportedFormats(int streamIndex)
    {
        Format[] superSupportedFormats = super.getSupportedFormats(streamIndex);

        /*
         * If the capture endpoint device reports to support no Format, then
         * acoustic echo cancellation (AEC) will surely not work.
         */
        if ((superSupportedFormats == null)
                || (superSupportedFormats.length == 0))
            return superSupportedFormats;

        // Return the NativelySupportedAudioFormat instances only.
        List<Format> supportedFormats
            = new ArrayList<Format>(superSupportedFormats.length);

        for (Format format : superSupportedFormats)
        {
            if ((format instanceof NativelySupportedAudioFormat)
                    && !supportedFormats.contains(format))
            {
                supportedFormats.add(format);
            }
        }

        int supportedFormatCount = supportedFormats.size();

        return
            (supportedFormatCount == superSupportedFormats.length)
                ? superSupportedFormats
                : supportedFormats.toArray(new Format[supportedFormatCount]);
    }

    /**
     * {@inheritDoc}
     *
     * The <tt>Format</tt>s supported by this
     * <tt>CaptureDevice</tt>/<tt>DataSource</tt> are either the ones supported
     * by the capture endpoint device or the ones supported by the voice capture
     * DMO that implements the acoustic echo cancellation (AEC) feature
     * depending on whether the feature in question is disabled or enabled.
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        if (aec)
        {
            List<AudioFormat> aecSupportedFormats
                = audioSystem.getAECSupportedFormats();

            return
                aecSupportedFormats.toArray(
                        new Format[aecSupportedFormats.size()]);
        }
        else
        {
            return getIAudioClientSupportedFormats(streamIndex);
        }
    }
}
