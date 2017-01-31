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
package org.jitsi.impl.neomedia;

import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * Implements {@link ByteArrayBuffer}.
 * @author Boris Grozev
 */
public class ByteArrayBufferImpl
    implements ByteArrayBuffer
{
    /**
     * The byte array represented by this {@link ByteArrayBufferImpl}.
     */
    private byte[] buffer;

    /**
     * The offset in the byte buffer where the actual data starts.
     */
    private int offset;

    /**
     * The length of the data in the buffer.
     */
    private int length;

    /**
     * Initializes a new {@link ByteArrayBufferImpl} instance.
     * @param buffer
     * @param offset
     * @param length
     */
    public ByteArrayBufferImpl(byte[] buffer, int offset, int length)
    {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        if (offset + length > buffer.length)
        {
            throw new IllegalArgumentException("length");
        }
        this.offset = offset;
        this.length = length;
    }

    /**
     * Initializes a new {@link ByteArrayBufferImpl} based on a newly allocated
     * byte array with the given size.
     * @param size the size of the underlying byte array.
     */
    public ByteArrayBufferImpl(int size)
    {
        buffer = new byte[size];
        offset = 0;
        length = size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getBuffer()
    {
        return buffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOffset()
    {
        return offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLength()
    {
        return length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLength(int length)
    {
        if (offset + length > buffer.length)
        {
            throw new IllegalArgumentException("length");
        }
        this.length = length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOffset(int offset)
    {
        if (offset + length > buffer.length)
        {
            throw new IllegalArgumentException("length");
        }
        this.offset = offset;
    }

    @Override
    public void setOffsetLength(int offset, int length)
    {
        if (offset + length > buffer.length)
        {
            throw new IllegalArgumentException("length");
        }
        this.offset = offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInvalid()
    {
        return false;
    }
}