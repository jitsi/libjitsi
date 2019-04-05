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
package org.jitsi.impl.neomedia.conference;

import java.io.*;

import javax.media.*;
import javax.media.protocol.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.utils.logging.*;

/**
 * Describes additional information about a specific input <tt>DataSource</tt>
 * of an <tt>AudioMixer</tt> so that the <tt>AudioMixer</tt> can, for example,
 * quickly discover the output <tt>AudioMixingPushBufferDataSource</tt> in the
 * mix of which the contribution of the <tt>DataSource</tt> is to not be
 * included.
 * <p>
 * Private to <tt>AudioMixer</tt> and <tt>AudioMixerPushBufferStream</tt> but
 * extracted into its own file for the sake of clarity.
 * </p>
 *
 * @author Lyubomir Marinov
 */
class InDataSourceDesc
{

    /**
     * The constant which represents an empty array with <tt>SourceStream</tt>
     * element type. Explicitly defined in order to avoid unnecessary allocations.
     */
    private static final SourceStream[] EMPTY_STREAMS = new SourceStream[0];

    /**
     * The <tt>Logger</tt> used by the <tt>InDataSourceDesc</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(InDataSourceDesc.class);

    /**
     * The indicator which determines whether the effective input
     * <tt>DataSource</tt> described by this instance is currently connected.
     */
    private boolean connected;

    /**
     * The <tt>Thread</tt> which currently executes {@link DataSource#connect()}
     * on the effective input <tt>DataSource</tt> described by this instance.
     */
    private Thread connectThread;

    /**
     * The <tt>DataSource</tt> for which additional information is described by
     * this instance.
     */
    public final DataSource inDataSource;

    /**
     * The <tt>AudioMixingPushBufferDataSource</tt> in which the mix
     * contributions of {@link #inDataSource} are to not be included.
     */
    public final AudioMixingPushBufferDataSource outDataSource;

    /**
     * The <tt>DataSource</tt>, if any, which transcodes the tracks of
     * {@link #inDataSource} in the output <tt>Format</tt> of the associated
     * <tt>AudioMixer</tt>.
     */
    private DataSource transcodingDataSource;

    /**
     * Initializes a new <tt>InDataSourceDesc</tt> instance which is to
     * describe additional information about a specific input
     * <tt>DataSource</tt> of an <tt>AudioMixer</tt>. Associates the specified
     * <tt>DataSource</tt> with the <tt>AudioMixingPushBufferDataSource</tt> in
     * which the mix contributions of the specified input <tt>DataSource</tt>
     * are to not be included.
     *
     * @param inDataSource a <tt>DataSource</tt> for which additional
     * information is to be described by the new instance
     * @param outDataSource the <tt>AudioMixingPushBufferDataSource</tt> in
     * which the mix contributions of <tt>inDataSource</tt> are to not be
     * included
     */
    public InDataSourceDesc(
            DataSource inDataSource,
            AudioMixingPushBufferDataSource outDataSource)
    {
        this.inDataSource = inDataSource;
        this.outDataSource = outDataSource;
    }

    /**
     * Connects the effective input <tt>DataSource</tt> described by this
     * instance upon request from a specific <tt>AudioMixer</tt>. If the
     * effective input <tt>DataSource</tt> is to be asynchronously connected,
     * the completion of the connect procedure will be reported to the specified
     * <tt>AudioMixer</tt> by calling its
     * {@link AudioMixer#connected(InDataSourceDesc)}.
     *
     * @param audioMixer the <tt>AudioMixer</tt> requesting the effective input
     * <tt>DataSource</tt> described by this instance to be connected
     * @throws IOException if anything wrong happens while connecting the
     * effective input <tt>DataSource</tt> described by this instance
     */
    synchronized void connect(final AudioMixer audioMixer)
        throws IOException
    {
        final DataSource effectiveInDataSource
            = (transcodingDataSource == null)
                ? inDataSource
                : transcodingDataSource;

        if (effectiveInDataSource instanceof TranscodingDataSource)
        {
            if (connectThread == null)
            {
                connectThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            audioMixer.connect(
                                    effectiveInDataSource,
                                    inDataSource);
                            synchronized (InDataSourceDesc.this)
                            {
                                connected = true;
                            }
                            audioMixer.connected(InDataSourceDesc.this);
                        }
                        catch (IOException ioex)
                        {
                            logger.error(
                                    "Failed to connect to inDataSource "
                                        + MediaStreamImpl.toString(
                                                inDataSource),
                                    ioex);
                        }
                        finally
                        {
                            synchronized (InDataSourceDesc.this)
                            {
                                if (connectThread == Thread.currentThread())
                                    connectThread = null;
                            }
                        }
                    }
                };
                connectThread.setDaemon(true);
                connectThread.start();
            }
        }
        else
        {
            audioMixer.connect(effectiveInDataSource, inDataSource);
            connected = true;
        }
    }

    /**
     * Creates a <tt>DataSource</tt> which attempts to transcode the tracks of
     * the input <tt>DataSource</tt> described by this instance into a specific
     * output <tt>Format</tt>.
     *
     * @param outFormat the <tt>Format</tt> in which the tracks of the input
     * <tt>DataSource</tt> described by this instance are to be transcoded
     * @return <tt>true</tt> if a new transcoding <tt>DataSource</tt> has been
     * created for the input <tt>DataSource</tt> described by this instance;
     * otherwise, <tt>false</tt>
     */
    synchronized boolean createTranscodingDataSource(Format outFormat)
    {
        if (transcodingDataSource == null)
        {
            setTranscodingDataSource(
                    new TranscodingDataSource(inDataSource, outFormat));
            return true;
        }
        else
            return false;
    }

    /**
     * Disconnects the effective input <tt>DataSource</tt> described by this
     * instance if it is already connected.
     */
    synchronized void disconnect()
    {
        if (connected)
        {
            getEffectiveInDataSource().disconnect();
            connected = false;
        }
    }

    /**
     * Gets the control available for the effective input <tt>DataSource</tt>
     * described by this instance with a specific type.
     *
     * @param controlType a <tt>String</tt> value which specifies the type of
     * the control to be retrieved
     * @return an <tt>Object</tt> which represents the control available for the
     * effective input <tt>DataSource</tt> described by this instance with the
     * specified <tt>controlType</tt> if such a control exists; otherwise,
     * <tt>null</tt>
     */
    public synchronized Object getControl(String controlType)
    {
        DataSource effectiveInDataSource = getEffectiveInDataSource();

        return
            (effectiveInDataSource == null)
                ? null
                : effectiveInDataSource.getControl(controlType);
    }

    /**
     * Gets the actual <tt>DataSource</tt> from which the associated
     * <tt>AudioMixer</tt> directly reads in order to retrieve the mix
     * contribution of the <tt>DataSource</tt> described by this instance.
     *
     * @return the actual <tt>DataSource</tt> from which the associated
     * <tt>AudioMixer</tt> directly reads in order to retrieve the mix
     * contribution of the <tt>DataSource</tt> described by this instance
     */
    public synchronized DataSource getEffectiveInDataSource()
    {
        return
            (transcodingDataSource == null)
                ? inDataSource
                : (connected ? transcodingDataSource : null);
    }

    /**
     * Returns this instance's <tt>inDataSource</tt>
     *
     * @return this instance's <tt>inDataSource</tt>
     */
    public DataSource getInDataSource()
    {
        return inDataSource;
    }

    /**
     * Gets the <tt>SourceStream</tt>s of the effective input
     * <tt>DataSource</tt> described by this instance.
     *
     * @return an array of the <tt>SourceStream</tt>s of the effective input
     * <tt>DataSource</tt> described by this instance
     */
    public synchronized SourceStream[] getStreams()
    {
        if (!connected)
            return EMPTY_STREAMS;

        DataSource inDataSource = getEffectiveInDataSource();

        if (inDataSource instanceof PushBufferDataSource)
            return ((PushBufferDataSource) inDataSource).getStreams();
        else if (inDataSource instanceof PullBufferDataSource)
            return ((PullBufferDataSource) inDataSource).getStreams();
        else if (inDataSource instanceof TranscodingDataSource)
            return ((TranscodingDataSource) inDataSource).getStreams();
        else
            return null;
    }

    /**
     * Returns the <tt>TranscodingDataSource</tt> object used in this instance.
     *
     * @return the <tt>TranscodingDataSource</tt> object used in this instance.
     */
    public TranscodingDataSource getTranscodingDataSource()
    {
        return (TranscodingDataSource) transcodingDataSource;
    }

    /**
     * Sets the <tt>DataSource</tt>, if any, which transcodes the tracks of the
     * input <tt>DataSource</tt> described by this instance in the output
     * <tt>Format</tt> of the associated <tt>AudioMixer</tt>.
     *
     * @param transcodingDataSource the <tt>DataSource</tt> which transcodes
     * the tracks of the input <tt>DataSource</tt> described by this instance in
     * the output <tt>Format</tt> of the associated <tt>AudioMixer</tt>
     */
    private synchronized void setTranscodingDataSource(
            DataSource transcodingDataSource)
    {
        this.transcodingDataSource = transcodingDataSource;
        connected = false;
    }

    /**
     * Starts the effective input <tt>DataSource</tt> described by this instance
     * if it is connected.
     *
     * @throws IOException if starting the effective input <tt>DataSource</tt>
     * described by this instance fails
     */
    synchronized void start()
        throws IOException
    {
        if (connected)
            getEffectiveInDataSource().start();
    }

    /**
     * Stops the effective input <tt>DataSource</tt> described by this instance
     * if it is connected.
     *
     * @throws IOException if stopping the effective input <tt>DataSource</tt>
     * described by this instance fails
     */
    synchronized void stop()
        throws IOException
    {
        if (connected)
            getEffectiveInDataSource().stop();
    }
}
