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

import javax.media.*;
import javax.media.control.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements {@link PushBufferDataSource} for the purposes of
 * {@link RTPTranslatorImpl} when it does not have a <tt>CaptureDevice</tt> yet
 * <tt>RTPManager.createSendStream(DataSource, int)</tt> has to be called to
 * have <tt>RTPTranslatorImpl</tt> send packets.
 *
 * @author Lyubomir Marinov
 */
public class FakePushBufferDataSource
    extends AbstractPushBufferCaptureDevice
{
    /**
     * The <tt>Format</tt>s in which this <tt>DataSource</tt> is capable of
     * providing media.
     */
    private final Format[] supportedFormats;

    /**
     * Initializes a new <tt>FakePushBufferCaptureDevice</tt> instance which is
     * to report a specific list of <tt>Format</tt>s as supported.
     *
     * @param supportedFormats the list of <tt>Format</tt>s to be reported as
     * supported by the new instance
     */
    public FakePushBufferDataSource(Format... supportedFormats)
    {
        this.supportedFormats
            = (supportedFormats == null) ? null : supportedFormats.clone();
    }

    /**
     * Opens a connection to the media source specified by the
     * <tt>MediaLocator</tt> of this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while opening the connection
     * to the media source specified by the <tt>MediaLocator</tt> of this
     * <tt>DataSource</tt>
     */
    @Override
    public void connect()
        throws IOException
    {
        /*
         * The connect, disconnect, start and stop methods of the super have
         * been overridden in order to disable consistency checks with respect
         * to the connected and started states.
         */
    }

    /**
     * Create a new <tt>PushBufferStream</tt> which is to be at a specific
     * zero-based index in the list of streams of this
     * <tt>PushBufferDataSource</tt>. The <tt>Format</tt>-related information of
     * the new instance is to be abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt>
     * in the list of streams of this <tt>PushBufferDataSource</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @return a new <tt>PushBufferStream</tt> which is to be at the specified
     * <tt>streamIndex</tt> in the list of streams of this
     * <tt>PushBufferDataSource</tt> and which has its <tt>Format</tt>-related
     * information abstracted by the specified <tt>formatControl</tt>
     */
    @Override
    protected FakePushBufferStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new FakePushBufferStream(this, formatControl);
    }

    /**
     * Closes the connection to the media source specified of this
     * <tt>AbstractBufferCaptureDevice</tt>. If such a connection has not been
     * opened, the call is ignored.
     */
    @Override
    public void disconnect()
    {
        /*
         * The connect, disconnect, start and stop methods of the super have
         * been overridden in order to disable consistency checks with respect
         * to the connected and started states.
         */
    }

    /**
     * Gets the <tt>Format</tt>s which are to be reported by a
     * <tt>FormatControl</tt> as supported formats for a
     * <tt>PushBufferStream</tt> at a specific zero-based index in the list of
     * streams of this <tt>PushBufferDataSource</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt>
     * for which the specified <tt>FormatControl</tt> is to report the list of
     * supported <tt>Format</tt>s
     * @return an array of <tt>Format</tt>s to be reported by a
     * <tt>FormatControl</tt> as the supported formats for the
     * <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt> in the
     * list of streams of this <tt>PushBufferDataSource</tt>
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
       return (supportedFormats == null) ? null : supportedFormats.clone();
    }

    /**
     * {@inheritDoc}
     *
     * Allows setting an arbitrary <tt>Format</tt> on this <tt>DataSource</tt>
     * because it does not really provide any media.
     */
    @Override
    protected Format setFormat(
            int streamIndex,
            Format oldValue, Format newValue)
    {
        return newValue;
    }

    /**
     * Starts the transfer of media data from this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of
     * media data from this <tt>DataSource</tt>
     */
    @Override
    public void start()
        throws IOException
    {
        /*
         * The connect, disconnect, start and stop methods of the super have
         * been overridden in order to disable consistency checks with respect
         * to the connected and started states.
         */
    }

    /**
     * Stops the transfer of media data from this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of
     * media data from this <tt>DataSource</tt>
     */
    @Override
    public void stop()
        throws IOException
    {
        /*
         * The connect, disconnect, start and stop methods of the super have
         * been overridden in order to disable consistency checks with respect
         * to the connected and started states.
         */
    }

    /**
     * Implements {@link PushBufferStream} for the purposes of
     * <tt>FakePushBufferDataSource</tt>.
     */
    private static class FakePushBufferStream
        extends AbstractPushBufferStream<FakePushBufferDataSource>
    {
        /**
         * Initializes a new <tt>FakePushBufferStream</tt> instance which is to
         * have its <tt>Format</tt>-related information abstracted by a specific
         * <tt>FormatControl</tt>.
         *
         * @param dataSource the <tt>FakePushBufferDataSource</tt> which is
         * creating the new instance so that it becomes one of its
         * <tt>streams</tt>
         * @param formatControl the <tt>FormatControl</tt> which is to abstract
         * the <tt>Format</tt>-related information of the new instance
         */
        FakePushBufferStream(
                FakePushBufferDataSource dataSource,
                FormatControl formatControl)
        {
            super(dataSource, formatControl);
        }

        /**
         * {@inheritDoc}
         *
         * Allows setting an arbitrary format on this <tt>SourceStream</tt>
         * because it does not really provide any media.
         */
        @Override
        protected Format doSetFormat(Format format)
        {
            return format;
        }

        /**
         * Reads media data from this <tt>PushBufferStream</tt> into a specific
         * <tt>Buffer</tt> without blocking.
         *
         * @param buffer the <tt>Buffer</tt> in which media data is to be read
         * from this <tt>PushBufferStream</tt>
         * @throws IOException if anything goes wrong while reading media data
         * from this <tt>PushBufferStream</tt> into the specified
         * <tt>buffer</tt>
         */
        @Override
        public void read(Buffer buffer)
            throws IOException
        {
            /*
             * The whole point of FakePushBufferDataSource and
             * FakePushBufferStream is that this read method is a no-op (and
             * this FakePushBufferStream will never invoke its associated
             * transferHandler).
             */
        }
    }
}
