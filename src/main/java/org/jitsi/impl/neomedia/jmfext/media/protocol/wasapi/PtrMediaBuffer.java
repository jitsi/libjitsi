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
