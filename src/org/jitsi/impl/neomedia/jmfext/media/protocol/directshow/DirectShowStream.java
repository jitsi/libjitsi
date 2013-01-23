/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

import java.io.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;

import net.java.sip.communicator.impl.neomedia.directshow.*;

import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements a <tt>PushBufferStream</tt> using DirectShow.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class DirectShowStream
    extends AbstractPushBufferStream
{
    /**
     * The indicator which determines whether {@link #grabber}
     * automatically drops late frames. If <tt>false</tt>, we have to drop them
     * ourselves because DirectShow will buffer them all and the video will
     * be late.
     */
    private boolean automaticallyDropsLateVideoFrames = false;

    /**
     * The pool of <tt>ByteBuffer</tt>s this instances is using to transfer the
     * media data captured by {@link #grabber} out of this instance
     * through the <tt>Buffer</tt>s specified in its {@link #read(Buffer)}.
     */
    private final ByteBufferPool byteBufferPool = new ByteBufferPool();

    /**
     * The captured media data to be returned in {@link #read(Buffer)}.
     */
    private ByteBuffer data;

    /**
     * The <tt>Object</tt> which synchronizes the access to the
     * {@link #data}-related fields of this instance.
     */
    private final Object dataSyncRoot = new Object();

    /**
     * The time stamp in nanoseconds of {@link #data}.
     */
    private long dataTimeStamp;

    /**
     * The last-known <tt>Format</tt> of the media data made available by this
     * <tt>PushBufferStream</tt>.
     */
    private final Format format;

    /**
     * Delegate class to handle video data.
     */
    final DSCaptureDevice.GrabberDelegate grabber
        = new DSCaptureDevice.GrabberDelegate()
        {
            @Override
            public void frameReceived(long ptr, int length)
            {
                processFrame(ptr, length);
            }
        };

    /**
     * The captured media data to become the value of {@link #data} as soon as
     * the latter becomes is consumed. Thus prepares this
     * <tt>DirectShowStream</tt> to provide the latest available frame and not
     * wait for DirectShow to capture a new one.
     */
    private ByteBuffer nextData;

    /**
     * The time stamp in nanoseconds of {@link #nextData}.
     */
    private long nextDataTimeStamp;

    /**
     * The <tt>Thread</tt> which is to call
     * {@link BufferTransferHandler#transferData(PushBufferStream)} for this
     * <tt>DirectShowStream</tt> so that the call is not made in DirectShow
     * and we can drop late frames when
     * {@link #automaticallyDropsLateVideoFrames} is <tt>false</tt>.
     */
    private Thread transferDataThread;

    /**
     * Initializes a new <tt>DirectShowStream</tt> instance which is to have its
     * <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is creating the new
     * instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    DirectShowStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);

        format = (VideoFormat) formatControl.getFormat();
    }

    /**
     * Gets the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it.
     *
     * @return the <tt>Format</tt> of this <tt>PushBufferStream</tt> as directly
     * known by it or <tt>null</tt> if this <tt>PushBufferStream</tt> does not
     * directly know its <tt>Format</tt> and it relies on the
     * <tt>PushBufferDataSource</tt> which created it to report its
     * <tt>Format</tt>
     */
    @Override
    protected Format doGetFormat()
    {
        return (this.format == null) ? super.doGetFormat() : this.format;
    }

    /**
     * Process received frames from DirectShow capture device
     *
     * @param ptr native pointer to data
     * @param length length of data
     */
    private void processFrame(long ptr, int length)
    {
        boolean transferData = false;

        synchronized (dataSyncRoot)
        {
            if(!automaticallyDropsLateVideoFrames && (data != null))
            {
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }
                nextData = byteBufferPool.getBuffer(length);
                if(nextData != null)
                {
                    nextData.setLength(
                            DSCaptureDevice.getBytes(ptr,
                                    nextData.getPtr(),
                                    nextData.getCapacity()));
                    nextDataTimeStamp = System.nanoTime();
                }

                return;
            }

            if (data != null)
            {
                data.free();
                data = null;
            }
            data = byteBufferPool.getBuffer(length);
            if(data != null)
            {
                data.setLength(
                        DSCaptureDevice.getBytes(
                                ptr,
                                data.getPtr(),
                                data.getCapacity()));
                dataTimeStamp = System.nanoTime();
            }

            if (nextData != null)
            {
                nextData.free();
                nextData = null;
            }

            if(automaticallyDropsLateVideoFrames)
                transferData = (data != null);
            else
            {
                transferData = false;
                dataSyncRoot.notifyAll();
            }
        }

        if(transferData)
        {
            BufferTransferHandler transferHandler = this.transferHandler;

            if(transferHandler != null)
                transferHandler.transferData(this);
        }
    }

    /**
     * Reads media data from this <tt>PushBufferStream</tt> into a specific
     * <tt>Buffer</tt> without blocking.
     *
     * @param buffer the <tt>Buffer</tt> in which media data is to be read from
     * this <tt>PushBufferStream</tt>
     * @throws IOException if anything goes wrong while reading media data from
     * this <tt>PushBufferStream</tt> into the specified <tt>buffer</tt>
     */
    public void read(Buffer buffer) throws IOException
    {
        synchronized (dataSyncRoot)
        {
            if(data == null)
            {
                buffer.setLength(0);
                return;
            }

            Format bufferFormat = buffer.getFormat();

            if(bufferFormat == null)
            {
                bufferFormat = getFormat();
                if(bufferFormat != null)
                    buffer.setFormat(bufferFormat);
            }
            if(bufferFormat instanceof AVFrameFormat)
            {
                if (AVFrame.read(buffer, bufferFormat, data) < 0)
                    data.free();
                /*
                 * XXX For the sake of safety, make sure that this instance does
                 * not reference the data instance as soon as it is set on the
                 * AVFrame.
                 */
                data = null;
            }
            else
            {
                Object o = buffer.getData();
                byte[] bytes;
                int length = data.getLength();

                if(o instanceof byte[])
                {
                    bytes = (byte[]) o;
                    if(bytes.length < length)
                        bytes = null;
                }
                else
                    bytes = null;
                if(bytes == null)
                {
                    bytes = new byte[length];
                    buffer.setData(bytes);
                }

                /*
                 * TODO Copy the media from the native memory into the Java
                 * heap.
                 */
                data.free();
                data = null;

                buffer.setLength(length);
                buffer.setOffset(0);
            }

            buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_SYSTEM_TIME);
            buffer.setTimeStamp(dataTimeStamp);

            if(!automaticallyDropsLateVideoFrames)
                dataSyncRoot.notifyAll();
        }
    }

    /**
     * Calls {@link BufferTransferHandler#transferData(PushBufferStream)} from
     * inside {@link #transferDataThread} so that the call is not made in
     * DirectShow and we can drop late frames in the meantime.
     */
    private void runInTransferDataThread()
    {
        boolean transferData = false;
        FrameRateControl frameRateControl
            = (FrameRateControl)
                dataSource.getControl(FrameRateControl.class.getName());
        long transferDataTimeStamp = -1;

        while (Thread.currentThread().equals(transferDataThread))
        {
            if (transferData)
            {
                BufferTransferHandler transferHandler = this.transferHandler;

                if (transferHandler != null)
                {
                    /*
                     * Respect the frame rate specified through the
                     * FrameRateControl of the associated DataSource.
                     */
                    if (frameRateControl != null)
                    {
                        float frameRate;
                        long newTransferDataTimeStamp
                            = System.currentTimeMillis();

                        if ((transferDataTimeStamp != -1)
                                && ((frameRate
                                            = frameRateControl.getFrameRate())
                                        > 0))
                        {
                            long minimumVideoFrameInterval
                                = (long) (1000 / frameRate);

                            if (minimumVideoFrameInterval > 0)
                            {
                                long t
                                    = newTransferDataTimeStamp
                                        - transferDataTimeStamp;

                                if ((t > 0) && (t < minimumVideoFrameInterval))
                                {
                                    boolean interrupted = false;

                                    try
                                    {
                                        Thread.sleep(
                                                minimumVideoFrameInterval - t);
                                    }
                                    catch (InterruptedException ie)
                                    {
                                        interrupted = true;
                                    }
                                    if (interrupted)
                                        Thread.currentThread().interrupt();
                                    continue;
                                }
                            }
                        }

                        transferDataTimeStamp = newTransferDataTimeStamp;
                    }

                    transferHandler.transferData(this);
                }

                synchronized (dataSyncRoot)
                {
                    if (data != null)
                        data.free();
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    nextData = null;
                }
            }

            synchronized (dataSyncRoot)
            {
                if (data == null)
                {
                    data = nextData;
                    dataTimeStamp = nextDataTimeStamp;
                    nextData = null;
                }
                if (data == null)
                {
                    boolean interrupted = false;

                    try
                    {
                        dataSyncRoot.wait();
                    }
                    catch (InterruptedException iex)
                    {
                        interrupted = true;
                    }
                    if(interrupted)
                        Thread.currentThread().interrupt();

                    transferData = (data != null);
                }
                else
                    transferData = true;
            }
        }
    }

    /**
     * Starts the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void start()
        throws IOException
    {
        super.start();

        if(!automaticallyDropsLateVideoFrames)
        {
            transferDataThread
                = new Thread(getClass().getSimpleName())
                {
                    @Override
                    public void run()
                    {
                        runInTransferDataThread();
                    }
                };
            transferDataThread.start();
        }
    }

    /**
     * Stops the transfer of media data from this <tt>PushBufferStream</tt>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of
     * media data from this <tt>PushBufferStream</tt>
     */
    @Override
    public void stop()
        throws IOException
    {
        try
        {
            transferDataThread = null;

            synchronized (dataSyncRoot)
            {
                if (data != null)
                {
                    data.free();
                    data = null;
                }
                if (nextData != null)
                {
                    nextData.free();
                    nextData = null;
                }

                if(!automaticallyDropsLateVideoFrames)
                    dataSyncRoot.notifyAll();
            }
        }
        finally
        {
            super.stop();

            byteBufferPool.drain();
        }
    }
}
