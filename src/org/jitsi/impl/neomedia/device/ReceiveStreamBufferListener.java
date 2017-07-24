package org.jitsi.impl.neomedia.device;

import javax.media.Buffer;
import javax.media.rtp.ReceiveStream;

/**
 *  Represents a listener for every packet which is read by a
 *  <tt>MediaDevice</tt>
 *
 *  @author Boris Grozev
 *  @author Nik Vaessen
 */
public interface ReceiveStreamBufferListener
{
    /**
     * Notify the listener that the data in the <tt>Buffer</tt> (as byte[])
     * has been read by the MediaDevice the listener is attached to
     *
     * @param receiveStream the <tt>ReceiveStream</tt> which provided the
     *                      packet(s)
     * @param buffer the <tt>Buffer</tt> into which the packets has been read
     */
    void bufferReceived(ReceiveStream receiveStream, Buffer buffer);
}
