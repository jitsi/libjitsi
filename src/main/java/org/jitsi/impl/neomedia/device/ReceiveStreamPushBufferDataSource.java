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

import javax.media.protocol.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.protocol.*;

/**
 * Wraps the <tt>DataSource</tt> of a specific <tt>ReceiveStream</tt> so that
 * calls to its {@link DataSource#disconnect()} can be explicitly controlled. It
 * is introduced because it seems that after the <tt>DataSource</tt> of a
 * <tt>ReceiveStream</tt> is disconnected, it cannot be connected to or started
 * and if a <tt>Processor</tt> is created on it, it freezes in the
 * {@link javax.media.Processor#Configuring} state.
 *
 * @author Lyubomir Marinov
 */
public class ReceiveStreamPushBufferDataSource
    extends PushBufferDataSourceDelegate<PushBufferDataSource>
{
    /**
     * Sets a <tt>BufferTransferHandler</tt> on a specific
     * <tt>ReceiveStream</tt> which reads data as soon as possible and throws it
     * away.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> on which to set a
     * <tt>BufferTransferHandler</tt> which reads data as soon as possible and
     * throws it away
     */
    public static void setNullTransferHandler(ReceiveStream receiveStream)
    {
        DataSource dataSource = receiveStream.getDataSource();

        if (dataSource != null)
        {
            if (dataSource instanceof PushBufferDataSource)
            {
                PushBufferStream[] streams
                    = ((PushBufferDataSource) dataSource).getStreams();

                if ((streams != null) && (streams.length != 0))
                {
                    for (PushBufferStream stream : streams)
                    {
                        stream.setTransferHandler(
                                new NullBufferTransferHandler());
                    }
                }

                // If data is to be read as soon as possible and thrown away,
                // it sounds reasonable that buffering while stopped should be
                // disabled.
                if (dataSource
                        instanceof net.sf.fmj.media.protocol.rtp.DataSource)
                {
                    ((net.sf.fmj.media.protocol.rtp.DataSource) dataSource)
                        .setBufferWhenStopped(false);
                }
            }
        }
    }

    /**
     * The <tt>ReceiveStream</tt> which has its <tt>DataSource</tt> wrapped by
     * this instance. Currently, remembered just to be made available to callers
     * in case they need it and not used by this instance.
     */
    private final ReceiveStream receiveStream;

    /**
     * The indicator which determines whether {@link DataSource#disconnect()} is
     * to be called on the wrapped <tt>DataSource</tt> when it is called on this
     * instance.
     */
    private boolean suppressDisconnect;

    /**
     * Initializes a new <tt>ReceiveStreamPushBufferDataSource</tt> instance
     * which is to wrap a specific <tt>DataSource</tt> of a specific
     * <tt>ReceiveStream</tt> for the purposes of enabling explicitly control of
     * calls to its {@link DataSource#disconnect()}.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which is to have its
     * <tt>DataSource</tt>
     * @param dataSource the <tt>DataSource</tt> of <tt>receiveStream</tt> which
     * is to be wrapped by this instance
     */
    public ReceiveStreamPushBufferDataSource(
            ReceiveStream receiveStream,
            PushBufferDataSource dataSource)
    {
        super(dataSource);

        this.receiveStream = receiveStream;
    }

    /**
     * Initializes a new <tt>ReceiveStreamPushBufferDataSource</tt> instance
     * which is to wrap a specific <tt>DataSource</tt> of a specific
     * <tt>ReceiveStream</tt> for the purposes of enabling explicitly control of
     * calls to its {@link DataSource#disconnect()} and, optionally, activates
     * the suppresses the call in question.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which is to have its
     * <tt>DataSource</tt>
     * @param dataSource the <tt>DataSource</tt> of <tt>receiveStream</tt> which
     * is to be wrapped by this instance
     * @param suppressDisconnect <tt>true</tt> if calls to
     * <tt>DataSource#disconnect()</tt> on the wrapped <tt>dataSource</tt> are
     * to be suppressed when there are such calls on the new instance;
     * otherwise, <tt>false</tt>
     */
    public ReceiveStreamPushBufferDataSource(
            ReceiveStream receiveStream,
            PushBufferDataSource dataSource,
            boolean suppressDisconnect)
    {
        this(receiveStream, dataSource);

        setSuppressDisconnect(suppressDisconnect);
    }

    /**
     * Implements {@link DataSource#disconnect()}. Disconnects the wrapped
     * <tt>DataSource</tt> if it has not been explicitly suppressed by setting
     * the <tt>suppressDisconnect</tt> property of this instance.
     */
    @Override
    public void disconnect()
    {
        if (!suppressDisconnect)
            super.disconnect();
    }

    /**
     * Gets the <tt>ReceiveStream</tt> which has its <tt>DataSource</tt> wrapped
     * by this instance.
     *
     * @return the <tt>ReceiveStream</tt> which has its <tt>DataSource</tt>
     * wrapped by this instance
     */
    public ReceiveStream getReceiveStream()
    {
        return receiveStream;
    }

    /**
     * Implements {@link PushBufferDataSource#getStreams()}. Delegates to the
     * wrapped <tt>DataSource</tt> of the <tt>ReceiveStream</tt>.
     *
     * @return an array of the <tt>PushBufferStream</tt>s of the wrapped
     * <tt>DataSource</tt> of the <tt>ReceiveStream</tt>
     */
    @Override
    public PushBufferStream[] getStreams()
    {
        return dataSource.getStreams();
    }

    /**
     * Sets the indicator which determines whether calls to
     * {@link DataSource#disconnect()} on the wrapped <tt>DataSource</tt> are to
     * be suppressed when there are such calls on this instance.
     *
     * @param suppressDisconnect <tt>true</tt> to suppress calls to
     * <tt>DataSource#disconnect()</tt> on the wrapped <tt>DataSource</tt> when
     * there are such calls on this instance; otherwise, <tt>false</tt>
     */
    public void setSuppressDisconnect(boolean suppressDisconnect)
    {
        this.suppressDisconnect = suppressDisconnect;
    }
}
