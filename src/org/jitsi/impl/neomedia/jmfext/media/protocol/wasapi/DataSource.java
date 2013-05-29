/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import java.io.*;

import javax.media.*;
import javax.media.control.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.util.*;

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
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
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
}
