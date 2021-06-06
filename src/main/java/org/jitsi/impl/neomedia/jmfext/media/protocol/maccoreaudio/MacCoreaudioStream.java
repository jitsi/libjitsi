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
package org.jitsi.impl.neomedia.jmfext.media.protocol.maccoreaudio;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * Implements <tt>PullBufferStream</tt> for MacCoreaudio.
 *
 * @author Vincent Lucas
 */
public class MacCoreaudioStream
    extends AbstractPullBufferStream<DataSource>
{
    /**
     * The <tt>Logger</tt> used by the <tt>MacCoreaudioStream</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(MacCoreaudioStream.class);

    /**
     * The indicator which determines whether audio quality improvement is
     * enabled for this <tt>MacCoreaudioStream</tt> in accord with the
     * preferences of the user.
     */
    @SuppressWarnings("unused") // under development
    private final boolean audioQualityImprovement;

    /**
     * The buffer which stores the outgoing data before sending them to
     * the RTP stack.
     */
    private byte[] buffer = null;

    /**
     * The number of bytes to read from a native MacCoreaudio stream in a single
     * invocation. Based on {@link #framesPerBuffer}.
     */
    private int bytesPerBuffer;

    /**
     * The device identifier (the device UID, or if not available, the device
     * name) of the MacCoreaudio device read through this
     * <tt>PullBufferStream</tt>.
     */
    private String deviceUID;

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PullBufferStream</tt>.
     */
    private AudioFormat format = null;

    /**
     * A list of already allocated buffers, ready to accept new captured data.
     */
    private Vector<byte[]> freeBufferList = new Vector<byte[]>();

    /**
     * A list of already allocated and filled buffers, ready to be send throw
     * the network.
     */
    private Vector<byte[]> fullBufferList = new Vector<byte[]>();

    /**
     * The <tt>GainControl</tt> through which the volume/gain of captured media
     * is controlled.
     */
    private final GainControl gainControl;

    /**
     * The number of data available to feed the RTP stack.
     */
    private int nbBufferData = 0;

    /**
     * Current sequence number.
     */
    private int sequenceNumber = 0;

    /**
     * A mutual eclusion used to avoid conflict when starting / stoping the
     * stream for this stream;
     */
    private Object startStopMutex = new Object();

    /**
     * Locked when currently stopping the stream. Prevents deadlock between the
     * CaoreAudio callback and the AudioDeviceStop function.
     */
    private Lock stopLock = new ReentrantLock();

    /**
     * The stream structure used by the native maccoreaudio library.
     */
    private long stream = 0;

    private final UpdateAvailableDeviceListListener
        updateAvailableDeviceListListener
            = new UpdateAvailableDeviceListListener()
            {
                /**
                 * The device ID (could be deviceUID or name but that is not
                 * really of concern to MacCoreaudioStream) used before and
                 * after (if still available) the update.
                 */
                private String deviceUID = null;

                private boolean start = false;

                @Override
                public void didUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized(startStopMutex)
                    {
                        if(stream == 0 && start)
                        {
                            setDeviceUID(deviceUID);
                            start();
                        }
                        deviceUID = null;
                        start = false;
                    }
                }

                @Override
                public void willUpdateAvailableDeviceList()
                    throws Exception
                {
                    synchronized(startStopMutex)
                    {
                        if(stream == 0)
                        {
                            deviceUID = null;
                            start = false;
                        }
                        else
                        {
                            deviceUID = MacCoreaudioStream.this.deviceUID;
                            start = true;
                            stop();
                            setDeviceUID(null);
                        }
                    }
                }
            };

    /**
     * Initializes a new <tt>MacCoreaudioStream</tt> instance which is to have
     * its <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @param audioQualityImprovement <tt>true</tt> to enable audio quality
     * improvement for the new instance in accord with the preferences of the
     * user or <tt>false</tt> to completely disable audio quality improvement
     */
    public MacCoreaudioStream(
            DataSource dataSource,
            FormatControl formatControl,
            boolean audioQualityImprovement)
    {
        super(dataSource, formatControl);

        this.audioQualityImprovement = audioQualityImprovement;

        MediaServiceImpl mediaServiceImpl
            = NeomediaServiceUtils.getMediaServiceImpl();

        gainControl = (mediaServiceImpl == null)
            ? null
            : (GainControl) mediaServiceImpl.getInputVolumeControl();

        // XXX We will add a UpdateAvailableDeviceListListener and will not
        // remove it because we will rely on MacCoreaudioSystem's use of
        // WeakReference.
        AudioSystem2 audioSystem
            = (AudioSystem2)
                AudioSystem.getAudioSystem(
                        AudioSystem.LOCATOR_PROTOCOL_MACCOREAUDIO);

        if (audioSystem != null)
        {
            audioSystem.addUpdateAvailableDeviceListListener(
                    updateAvailableDeviceListListener);
        }
    }

    private void connect()
    {
        AudioFormat format = (AudioFormat) getFormat();
        int channels = format.getChannels();
        if (channels == Format.NOT_SPECIFIED)
            channels = 1;
        int sampleSizeInBits = format.getSampleSizeInBits();
        double sampleRate = format.getSampleRate();
        int framesPerBuffer
            = (int) ((sampleRate * MacCoreAudioDevice.DEFAULT_MILLIS_PER_BUFFER)
                    / (channels * 1000));
        bytesPerBuffer = (sampleSizeInBits / 8) * channels * framesPerBuffer;

        // Know the Format in which this MacCoreaudioStream will output audio
        // data so that it can report it without going through its DataSource.
        this.format = new AudioFormat(
                AudioFormat.LINEAR,
                sampleRate,
                sampleSizeInBits,
                channels,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */,
                Format.NOT_SPECIFIED /* frameRate */,
                Format.byteArray);
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PullBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PullBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PullBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     * @see AbstractPullBufferStream#doGetFormat()
     */
    @Override
    protected Format doGetFormat()
    {
        return (format == null) ? super.doGetFormat() : format;
    }

    /**
     * Reads media data from this <tt>PullBufferStream</tt> into a specific
     * <tt>Buffer</tt> with blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PullBufferStream</tt>
     * @throws IOException if anything goes wrong while reading media data from
     * this <tt>PullBufferStream</tt> into the specified <tt>buffer</tt>
     */
    public void read(Buffer buffer)
        throws IOException
    {
        int length = 0;
        byte[] data = AbstractCodec2.validateByteArraySize(
                        buffer,
                        bytesPerBuffer,
                        false);

        synchronized(startStopMutex)
        {
            // Waits for the next buffer.
            while(this.fullBufferList.size() == 0 && stream != 0)
            {
                try
                {
                    startStopMutex.wait();
                }
                catch(InterruptedException ex)
                {}
            }

            // If the stream is running.
            if(stream != 0)
            {
                this.freeBufferList.add(data);
                data = this.fullBufferList.remove(0);
                length = data.length;
            }
        }

        // Take into account the user's preferences with respect to the
        // input volume.
        if(length != 0 && gainControl != null)
        {
            BasicVolumeControl.applyGain(
                    gainControl,
                    data,
                    0,
                    length);
        }

        long bufferTimeStamp = System.nanoTime();

        buffer.setData(data);
        buffer.setFlags(Buffer.FLAG_SYSTEM_TIME);
        if (format != null)
            buffer.setFormat(format);
        buffer.setHeader(null);
        buffer.setLength(length);
        buffer.setOffset(0);
        buffer.setSequenceNumber(sequenceNumber++);
        buffer.setTimeStamp(bufferTimeStamp);
    }

    /**
     * Callback which receives the data from the coreaudio library.
     *
     * @param buffer The data captured from the input.
     * @param bufferLength The length of the data captured.
     */
    public void readInput(byte[] buffer, int bufferLength)
    {
        int nbCopied = 0;
        while(bufferLength > 0)
        {
            int length = this.buffer.length - nbBufferData;
            if(bufferLength < length)
            {
                length = bufferLength;
            }

            System.arraycopy(
                    buffer,
                    nbCopied,
                    this.buffer,
                    nbBufferData,
                    length);

            nbBufferData += length;
            nbCopied += length;
            bufferLength -= length;

            if(nbBufferData == this.buffer.length)
            {
                this.fullBufferList.add(this.buffer);
                this.buffer = null;
                nbBufferData = 0;
                if(stopLock.tryLock())
                {
                    try
                    {
                        synchronized(startStopMutex)
                        {
                            startStopMutex.notify();
                            if(this.freeBufferList.size() > 0)
                            {
                                this.buffer = this.freeBufferList.remove(0);
                            }
                        }
                    }
                    finally
                    {
                        stopLock.unlock();
                    }
                }

                if(this.buffer == null)
                {
                    this.buffer = new byte[bytesPerBuffer];
                }
            }
        }
    }

    /**
     * Sets the device index of the MacCoreaudio device to be read through this
     * <tt>PullBufferStream</tt>.
     *
     * @param deviceID The ID of the device used to be read trough this
     * MacCoreaudioStream.  This String contains the deviceUID, or if not
     * available, the device name.  If set to null, then there was no device
     * used before the update.
     */
    void setDeviceUID(String deviceUID)
    {
        synchronized(startStopMutex)
        {
            if (this.deviceUID != null)
            {
                // If there is a running stream, then close it.
                try
                {
                    stop();
                }
                catch(IOException ioex)
                {
                    logger.info(ioex);
                }

                // Make sure this AbstractPullBufferStream asks its DataSource
                // for the Format in which it is supposed to output audio data
                // the next time it is opened instead of using its Format from a
                // previous open.
                this.format = null;
            }
            this.deviceUID = deviceUID;

            if (this.deviceUID != null)
            {
                connect();
            }
        }
    }

    /**
     * Starts the transfer of media data from this <tt>PullBufferStream</tt>.
     */
    @Override
    public void start()
        throws IOException
    {
        synchronized(startStopMutex)
        {
            if(stream == 0 && deviceUID != null)
            {
                buffer = new byte[bytesPerBuffer];
                nbBufferData = 0;
                this.fullBufferList.clear();
                this.freeBufferList.clear();

                AudioSystem2 audioSystem
                    = (AudioSystem2)
                        AudioSystem.getAudioSystem(
                                AudioSystem.LOCATOR_PROTOCOL_MACCOREAUDIO);

                if (audioSystem != null)
                    audioSystem.willOpenStream();
                try
                {
                    stream
                        = MacCoreAudioDevice.startStream(
                                deviceUID,
                                this,
                                (float) format.getSampleRate(),
                                format.getChannels(),
                                format.getSampleSizeInBits(),
                                false,
                                format.getEndian() == AudioFormat.BIG_ENDIAN,
                                false,
                                true,
                                (audioSystem == null)
                                    ? true
                                    : audioSystem.isEchoCancel());
                }
                finally
                {
                    if (audioSystem != null)
                        audioSystem.didOpenStream();
                }
            }
        }
    }

    /**
     * Stops the transfer of media data from this <tt>PullBufferStream</tt>.
     */
    @Override
    public void stop()
        throws IOException
    {
        stopLock.lock();
        try
        {
            synchronized(startStopMutex)
            {
                if(stream != 0 && deviceUID != null)
                {
                    MacCoreAudioDevice.stopStream(deviceUID, stream);

                    stream = 0;
                    this.fullBufferList.clear();
                    this.freeBufferList.clear();
                    startStopMutex.notify();
                }
            }
        }
        finally
        {
            stopLock.unlock();
        }
    }
}
