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
package org.jitsi.impl.neomedia.jmfext.media.protocol.portaudio;

import java.io.*;

import javax.media.*;
import javax.media.control.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.utils.logging.*;

/**
 * Implements <tt>DataSource</tt> and <tt>CaptureDevice</tt> for PortAudio.
 *
 * @author Damian Minkov
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

    /**
     * The indicator which determines whether this <tt>DataSource</tt> will
     * use audio quality improvement in accord with the preferences of the user.
     */
    private final boolean audioQualityImprovement;

    /**
     * The list of <tt>Format</tt>s in which this <tt>DataSource</tt> is
     * capable of capturing audio data.
     */
    private final Format[] supportedFormats;

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
        this.supportedFormats = null;
        this.audioQualityImprovement = true;
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance from a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     */
    public DataSource(MediaLocator locator)
    {
        this(locator, null, true);
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance from a specific
     * <tt>MediaLocator</tt> and which has a specific list of <tt>Format</tt>
     * in which it is capable of capturing audio data overriding its
     * registration with JMF and optionally uses audio quality improvement in
     * accord with the preferences of the user.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     * @param supportedFormats the list of <tt>Format</tt>s in which the new
     * instance is to be capable of capturing audio data
     * @param audioQualityImprovement <tt>true</tt> if audio quality improvement
     * is to be enabled in accord with the preferences of the user or
     * <tt>false</tt> to completely disable audio quality improvement
     */
    public DataSource(
            MediaLocator locator,
            Format[] supportedFormats,
            boolean audioQualityImprovement)
    {
        super(locator);

        this.supportedFormats
            = (supportedFormats == null)
                ? null
                : supportedFormats.clone();
        this.audioQualityImprovement = audioQualityImprovement;
    }

    /**
     * Creates a new <tt>PullBufferStream</tt> which is to be at a specific
     * zero-based index in the list of streams of this
     * <tt>PullBufferDataSource</tt>. The <tt>Format</tt>-related information of
     * the new instance is to be abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PullBufferStream</tt>
     * in the list of streams of this <tt>PullBufferDataSource</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @return a new <tt>PullBufferStream</tt> which is to be at the specified
     * <tt>streamIndex</tt> in the list of streams of this
     * <tt>PullBufferDataSource</tt> and which has its <tt>Format</tt>-related
     * information abstracted by the specified <tt>formatControl</tt>
     * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
     */
    @Override
    protected PortAudioStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return
            new PortAudioStream(this, formatControl, audioQualityImprovement);
    }

    /**
     * Opens a connection to the media source specified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while opening the connection
     * to the media source specified by the <tt>MediaLocator</tt> of this
     * <tt>DataSource</tt>
     * @see AbstractPullBufferCaptureDevice#doConnect()
     */
    @Override
    protected void doConnect()
        throws IOException
    {
        super.doConnect();

        String deviceID = getDeviceID();

        synchronized (getStreamSyncRoot())
        {
            for (Object stream : getStreams())
                ((PortAudioStream) stream).setDeviceID(deviceID);
        }
    }

    /**
     * Closes the connection to the media source specified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>. Allows extenders to
     * override and be sure that there will be no request to close a connection
     * if the connection has not been opened yet.
     */
    @Override
    protected void doDisconnect()
    {
        try
        {
            synchronized (getStreamSyncRoot())
            {
                Object[] streams = streams();

                if (streams != null)
                {
                    for (Object stream : streams)
                    {
                        try
                        {
                            ((PortAudioStream) stream).setDeviceID(null);
                        }
                        catch (IOException ioex)
                        {
                            logger.error(
                                    "Failed to close "
                                        + stream.getClass().getSimpleName(),
                                    ioex);
                        }
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
     * Gets the device index of the PortAudio device identified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>.
     *
     * @return the device index of a PortAudio device identified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>
     * @throws IllegalStateException if there is no <tt>MediaLocator</tt>
     * associated with this <tt>DataSource</tt>
     */
    private String getDeviceID()
    {
        MediaLocator locator = getLocator();

        if (locator == null)
            throw new IllegalStateException("locator");
        else
            return getDeviceID(locator);
    }

    /**
     * Gets the device index of a PortAudio device from a specific
     * <tt>MediaLocator</tt> identifying it.
     *
     * @param locator the <tt>MediaLocator</tt> identifying the device index of
     * a PortAudio device to get
     * @return the device index of a PortAudio device identified by
     * <tt>locator</tt>
     */
    public static String getDeviceID(MediaLocator locator)
    {
        if (locator == null)
        {
            /*
             * Explicitly throw a NullPointerException because the implicit one
             * does not have a message and is thus a bit more difficult to
             * debug.
             */
            throw new NullPointerException("locator");
        }
        else if (AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO.equalsIgnoreCase(
                locator.getProtocol()))
        {
            String remainder = locator.getRemainder();

            if ((remainder != null) && (remainder.charAt(0) == '#'))
                remainder = remainder.substring(1);
            return remainder;
        }
        else
        {
            throw new IllegalArgumentException("locator.protocol");
        }
    }

    /**
     * Gets the <tt>Format</tt>s which are to be reported by a
     * <tt>FormatControl</tt> as supported formats for a
     * <tt>PullBufferStream</tt> at a specific zero-based index in the list of
     * streams of this <tt>PullBufferDataSource</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PullBufferStream</tt>
     * for which the specified <tt>FormatControl</tt> is to report the list of
     * supported <tt>Format</tt>s
     * @return an array of <tt>Format</tt>s to be reported by a
     * <tt>FormatControl</tt> as the supported formats for the
     * <tt>PullBufferStream</tt> at the specified <tt>streamIndex</tt> in the
     * list of streams of this <tt>PullBufferDataSource</tt>
     * @see AbstractPullBufferCaptureDevice#getSupportedFormats(int)
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return
            (supportedFormats == null)
                ? super.getSupportedFormats(streamIndex)
                : supportedFormats;
    }
}
