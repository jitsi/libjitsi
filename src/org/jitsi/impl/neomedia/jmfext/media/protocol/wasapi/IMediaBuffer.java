/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import java.io.*;

/**
 * Defines the API of Microsoft's <tt>IMediaBuffer</tt> interface (referred to
 * as unmanaged) and allows implementing similar abstractions on the Java side
 * (referred to as managed).
 *
 * @author Lyubomir Marinov
 */
public interface IMediaBuffer
{
    int GetLength()
        throws IOException;

    int GetMaxLength()
        throws IOException;

    int pop(byte[] buffer, int offset, int length)
        throws IOException;

    int push(byte[] buffer, int offset, int length)
        throws IOException;

    int Release();

    void SetLength(int length)
        throws IOException;
}
