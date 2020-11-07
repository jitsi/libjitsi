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
import javax.media.protocol.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;

/**
 * Defines the interface for <tt>MediaDevice</tt> required by the
 * <tt>org.jitsi.impl.neomedia</tt> implementation of
 * <tt>org.jitsi.service.neomedia</tt>.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractMediaDevice
    implements MediaDevice
{
    /**
     * Connects to a specific <tt>CaptureDevice</tt> given in the form of a
     * <tt>DataSource</tt>. Explicitly defined in order to allow extenders to
     * customize the connect procedure.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> to be connected to
     * @throws IOException if anything wrong happens while connecting to the
     * specified <tt>captureDevice</tt>
     */
    public void connect(DataSource captureDevice)
        throws IOException
    {
        if (captureDevice == null)
            throw new NullPointerException("captureDevice");
        try
        {
            captureDevice.connect();
        }
        catch (NullPointerException npe)
        {
            /*
             * The old media says it happens when the operating system does not
             * support the operation.
             */
            IOException ioe = new IOException();

            ioe.initCause(npe);
            throw ioe;
        }
    }

    /**
     * Creates a <tt>DataSource</tt> instance for this <tt>MediaDevice</tt>
     * which gives access to the captured media.
     *
     * @return a <tt>DataSource</tt> instance which gives access to the media
     * captured by this <tt>MediaDevice</tt>
     */
    protected abstract DataSource createOutputDataSource();

    /**
     * Initializes a new <tt>Processor</tt> instance which is to be used to play
     * back media on this <tt>MediaDevice</tt>. Allows extenders to, for
     * example, disable the playback on this <tt>MediaDevice</tt> by completely
     * overriding and returning <tt>null</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is to be played back by
     * the new <tt>Processor</tt> instance
     * @return a new <tt>Processor</tt> instance which is to be used to play
     * back the media provided by the specified <tt>dataSource</tt> or
     * <tt>null</tt> if the specified <tt>dataSource</tt> is to not be played
     * back
     * @throws Exception if an exception is thrown by
     * {@link DataSource#connect()},
     * {@link Manager#createProcessor(DataSource)}, or
     * {@link DataSource#disconnect()}
     */
    protected Processor createPlayer(DataSource dataSource)
        throws Exception
    {
        Processor player = null;

        // A Player is documented to be created on a connected DataSource.
        dataSource.connect();
        try
        {
            player = Manager.createProcessor(dataSource);
        }
        finally
        {
            if (player == null)
                dataSource.disconnect();
        }
        return player;
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to play back media
     * on this <tt>MediaDevice</tt>. Allows extenders to initialize a specific
     * <tt>Renderer</tt> instance. The implementation of
     * <tt>AbstractMediaDevice</tt> returns <tt>null</tt> which means that it is
     * left to FMJ to choose a suitable <tt>Renderer</tt> irrespective of this
     * <tt>MediaDevice</tt>.
     *
     * @return a new <tt>Renderer</tt> instance which is to play back media on
     * this <tt>MediaDevice</tt> or <tt>null</tt> if a suitable
     * <tt>Renderer</tt> is to be chosen irrespective of this
     * <tt>MediaDevice</tt>
     */
    protected Renderer createRenderer()
    {
        return null;
    }

    /**
     * Creates a new <tt>MediaDeviceSession</tt> instance which is to represent
     * the use of this <tt>MediaDevice</tt> by a <tt>MediaStream</tt>.
     *
     * @return a new <tt>MediaDeviceSession</tt> instance which is to represent
     * the use of this <tt>MediaDevice</tt> by a <tt>MediaStream</tt>
     */
    public MediaDeviceSession createSession()
    {
        switch (getMediaType())
        {
        case VIDEO:
            return new VideoMediaDeviceSession(this);
        default:
            return new AudioMediaDeviceSession(this);
        }
    }

    /**
     * Returns a <tt>List</tt> containing (at the time of writing) a single
     * extension descriptor indicating <tt>RECVONLY</tt> support for
     * mixer-to-client audio levels.
     *
     * @return a <tt>List</tt> containing the <tt>CSRC_AUDIO_LEVEL_URN</tt>
     * extension descriptor.
     */
    public List<RTPExtension> getSupportedExtensions()
    {
        return null;
    }

    /**
     * Gets a list of <tt>MediaFormat</tt>s supported by this
     * <tt>MediaDevice</tt>.
     *
     * @return the list of <tt>MediaFormat</tt>s supported by this device
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats()
    {
        return getSupportedFormats(null, null);
    }
}
