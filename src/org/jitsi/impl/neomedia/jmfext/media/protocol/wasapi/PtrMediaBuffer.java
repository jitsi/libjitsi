/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.VoiceCaptureDSP.*;

import java.io.*;

/**
 * Implements a managed <tt>IMediaBuffer</tt> which wraps around an unmanaged
 * <tt>IMediaBuffer</tt>.
 *
 * @author Lyubomir Marinov
 */
public class PtrMediaBuffer
    implements IMediaBuffer
{
    /**
     * The unmanaged <tt>IMediaBuffer</tt> represented by this instance.
     */
    final long ptr;

    /**
     * Initializes a new managed <tt>IMediaBuffer</tt> which is to represent a
     * specific unmanaged <tt>IMediaBuffer</tt>.
     * 
     * @param ptr the unmanaged <tt>IMediaBuffer</tt> to be represented by the
     * new instance
     */
    public PtrMediaBuffer(long ptr)
    {
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");
        this.ptr = ptr;
    }

    public int GetLength()
        throws IOException
    {
        try
        {
            return IMediaBuffer_GetLength(ptr);
        }
        catch (HResultException hre)
        {
            throw new IOException(hre);
        }
    }

    public int GetMaxLength()
        throws IOException
    {
        try
        {
            return IMediaBuffer_GetMaxLength(ptr);
        }
        catch (HResultException hre)
        {
            throw new IOException(hre);
        }
    }

    public int pop(byte[] buffer, int offset, int length)
        throws IOException
    {
        try
        {
            return MediaBuffer_pop(ptr, buffer, offset, length);
        }
        catch (HResultException hre)
        {
            throw new IOException(hre);
        }
    }

    public int push(byte[] buffer, int offset, int length)
        throws IOException
    {
        try
        {
            return MediaBuffer_push(ptr, buffer, offset, length);
        }
        catch (HResultException hre)
        {
            throw new IOException(hre);
        }
    }

    public int Release()
    {
        return IMediaBuffer_Release(ptr);
    }

    public void SetLength(int length)
        throws IOException
    {
        try
        {
            IMediaBuffer_SetLength(ptr, length);
        }
        catch (HResultException hre)
        {
            throw new IOException(hre);
        }
    }
}
