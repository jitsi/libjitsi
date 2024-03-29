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
package org.jitsi.impl.neomedia.protocol;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements a <tt>PullBufferDataSource</tt> wrapper which provides mute
 * support for the wrapped instance.
 * <p>
 * Because the class wouldn't work for our use case without it,
 * <tt>CaptureDevice</tt> is implemented and is being delegated to the wrapped
 * <tt>DataSource</tt> (if it supports the interface in question).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class RewritablePullBufferDataSource
    extends PullBufferDataSourceDelegate<PullBufferDataSource>
    implements MuteDataSource,
               InbandDTMFDataSource
{
    /**
     * The indicator which determines whether this <tt>DataSource</tt> is mute.
     */
    private boolean mute;

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private final LinkedList<DTMFInbandTone> tones
        = new LinkedList<DTMFInbandTone>();

    /**
     * Initializes a new <tt>RewritablePullBufferDataSource</tt> instance which
     * is to provide mute support for a specific <tt>PullBufferDataSource</tt>.
     *
     * @param dataSource the <tt>PullBufferDataSource</tt> the new instance is
     *            to provide mute support for
     */
    public RewritablePullBufferDataSource(PullBufferDataSource dataSource)
    {
        super(dataSource);
    }

    /**
     * Sets the mute state of this <tt>DataSource</tt>.
     *
     * @param mute <tt>true</tt> to mute this <tt>DataSource</tt>; otherwise,
     *            <tt>false</tt>
     */
    public void setMute(boolean mute)
    {
        this.mute = mute;
    }

    /**
     * Determines whether this <tt>DataSource</tt> is mute.
     *
     * @return <tt>true</tt> if this <tt>DataSource</tt> is mute; otherwise,
     *         <tt>false</tt>
     */
    public boolean isMute()
    {
        return mute;
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone)
    {
        this.tones.add(tone);
    }

    /**
     * Determines whether this <tt>DataSource</tt> sends a DTMF tone.
     *
     * @return <tt>true</tt> if this <tt>DataSource</tt> is sending a DTMF tone;
     * otherwise, <tt>false</tt>.
     */
    public boolean isSendingDTMF()
    {
        return !this.tones.isEmpty();
    }

    /**
     * Get wrapped DataSource.
     *
     * @return wrapped DataSource
     */
    public PullBufferDataSource getWrappedDataSource()
    {
        return dataSource;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to include the type hierarchy of the
     * very wrapped <tt>dataSource</tt> instance into the search for the
     * specified <tt>controlType</tt>. 
     */
    @Override
    public Object getControl(String controlType)
    {
        if (InbandDTMFDataSource.class.getName().equals(controlType)
                || MuteDataSource.class.getName().equals(controlType))
        {
            return this;
        }
        else
        {
            /*
             * The super implements a delegate so we can be sure that it
             * delegates the invocation of Controls#getControl(String) to the
             * wrapped dataSource.
             */
            return AbstractControls.queryInterface(dataSource, controlType);
        }
    }

    /**
     * Implements {@link PullBufferDataSource#getStreams()}. Wraps the streams
     * of the wrapped <tt>PullBufferDataSource</tt> into
     * <tt>MutePullBufferStream</tt> instances in order to provide mute support
     * to them.
     *
     * @return an array of <tt>PullBufferStream</tt> instances with enabled mute
     * support
     */
    @Override
    public PullBufferStream[] getStreams()
    {
        PullBufferStream[] streams = dataSource.getStreams();

        if (streams != null)
            for (int streamIndex = 0; streamIndex < streams.length; streamIndex++)
                streams[streamIndex] =
                    new MutePullBufferStream(streams[streamIndex]);
        return streams;
    }

    /**
     * Implements a <tt>PullBufferStream</tt> wrapper which provides mute
     * support for the wrapped instance.
     */
    private class MutePullBufferStream
        extends SourceStreamDelegate<PullBufferStream>
        implements PullBufferStream
    {

        /**
         * Initializes a new <tt>MutePullBufferStream</tt> instance which is to
         * provide mute support for a specific <tt>PullBufferStream</tt>.
         *
         * @param stream the <tt>PullBufferStream</tt> the new instance is to
         * provide mute support for
         */
        private MutePullBufferStream(PullBufferStream stream)
        {
            super(stream);
        }

        /**
         * Implements {@link PullBufferStream#getFormat()}. Delegates to the
         * wrapped <tt>PullBufferStream</tt>.
         *
         * @return the <tt>Format</tt> of the wrapped <tt>PullBufferStream</tt>
         */
        public Format getFormat()
        {
            return stream.getFormat();
        }

        /**
         * Implements PullBufferStream#read(Buffer). If this instance is muted
         * (through its owning RewritablePullBufferDataSource), overwrites the
         * data read from the wrapped PullBufferStream with silence data.
         * @param buffer which data will be filled.  @throws IOException Thrown
         * if an error occurs while reading.
         */
        public void read(Buffer buffer)
            throws IOException
        {
            stream.read(buffer);

            if (isSendingDTMF())
                RewritablePushBufferDataSource.sendDTMF(buffer, tones.poll());
            else if (isMute())
                RewritablePushBufferDataSource.mute(buffer);
        }

        /**
         * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped
         * PullSourceStream.
         * @return <tt>true</tt> if read would block; otherwise returns
         *          <tt>false</tt>.
         */
        public boolean willReadBlock()
        {
            return stream.willReadBlock();
        }
    }
}
