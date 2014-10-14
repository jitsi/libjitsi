/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.protocol;

import javax.media.*;
import javax.media.protocol.*;

/**
 * Implements a <tt>BufferTransferHandler</tt> which reads from a specified
 * <tt>PushBufferStream</tt> as soon as possible and throws the read data away.
 *
 * @author Lyubomir Marinov
 */
public class NullBufferTransferHandler
    implements BufferTransferHandler
{
    /**
     * The FMJ <tt>Buffer</tt> into which this <tt>BufferTransferHandler</tt> is
     * to read data from any <tt>PushBufferStream</tt>.
     */
    private final Buffer buffer = new Buffer();

    @Override
    public void transferData(PushBufferStream stream)
    {
        try
        {
            stream.read(buffer);
        }
        catch (Exception ex)
        {
            // The purpose of NullBufferTransferHandler is to read from the
            // specified PushBufferStream as soon as possible and throw the read
            // data away. Hence, Exceptions are of no concern.
        }
    }
}
