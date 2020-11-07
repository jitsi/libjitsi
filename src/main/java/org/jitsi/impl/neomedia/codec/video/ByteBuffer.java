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
package org.jitsi.impl.neomedia.codec.video;

import org.jitsi.impl.neomedia.codec.*;

/**
 * Represents a buffer of native memory with a specific size/capacity which may
 * contains a specific number of bytes of valid data. If the memory represented
 * by a <tt>ByteBuffer</tt> instance has been allocated by the
 * <tt>ByteBuffer</tt> instance itself, the native memory will automatically be
 * freed upon finalization.
 *
 * @author Lyubomir Marinov
 */
public class ByteBuffer
{

    /**
     * The maximum number of bytes which may be written into the native memory
     * represented by this instance. If <tt>0</tt>, this instance has been
     * initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     */
    private int capacity;

    /**
     * The number of bytes of valid data that the native memory represented by
     * this instance contains.
     */
    private int length;

    /**
     * The pointer to the native memory represented by this instance.
     */
    private long ptr;

    /**
     * Initializes a new <tt>ByteBuffer</tt> instance with a specific
     * <tt>capacity</tt> of native memory. The new instance allocates the native
     * memory and automatically frees it upon finalization.
     *
     * @param capacity the maximum number of bytes which can be written into the
     * native memory represented by the new instance
     */
    public ByteBuffer(int capacity)
    {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity");

        this.ptr = FFmpeg.av_malloc(capacity);
        if (this.ptr == 0)
            throw new OutOfMemoryError("av_malloc(" + capacity + ")");

        this.capacity = capacity;
        this.length = 0;
    }

    /**
     * Initializes a new <tt>ByteBuffer</tt> instance which is to represent a
     * specific block of native memory. Since the specified native memory has
     * been allocated outside the new instance, the new instance will not
     * automatically free it.
     *
     * @param ptr a pointer to the block of native memory to be represented by
     * the new instance
     */
    public ByteBuffer(long ptr)
    {
        this.ptr = ptr;

        this.capacity = 0;
        this.length = 0;
    }

    /**
     * {@inheritDoc}
     *
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet i.e.
     * ensures that {@link #free()} is invoked on this instance.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
        throws Throwable
    {
        try
        {
            free();
        }
        finally
        {
            super.finalize();
        }
    }

    /**
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet.
     */
    public synchronized void free()
    {
        if ((capacity != 0) && (ptr != 0))
        {
            FFmpeg.av_free(ptr);
            capacity = 0;
            ptr = 0;
        }
    }

    /**
     * Gets the maximum number of bytes which may be written into the native
     * memory represented by this instance. If <tt>0</tt>, this instance has
     * been initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     *
     * @return the maximum number of bytes which may be written into the native
     * memory represented by this instance
     */
    public synchronized int getCapacity()
    {
        return capacity;
    }

    /**
     * Gets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @return the number of bytes of valid data that the native memory
     * represented by this instance contains
     */
    public int getLength()
    {
        return length;
    }

    /**
     * Gets the pointer to the native memory represented by this instance.
     *
     * @return the pointer to the native memory represented by this instance
     */
    public synchronized long getPtr()
    {
        return ptr;
    }

    /**
     * Sets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @param length the number of bytes of valid data that the native memory
     * represented by this instance contains
     * @throws IllegalArgumentException if <tt>length</tt> is a negative value
     */
    public void setLength(int length)
    {
        if (length < 0)
            throw new IllegalArgumentException("length");

        this.length = length;
    }
}
