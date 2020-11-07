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

import java.awt.*;

import javax.media.*;

import org.jitsi.impl.neomedia.codec.*;

/**
 * Represents a pointer to a native FFmpeg <tt>AVFrame</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class AVFrame
{
    public static int read(Buffer buffer, Format format, ByteBuffer data)
    {
        AVFrameFormat frameFormat = (AVFrameFormat) format;

        Object o = buffer.getData();
        AVFrame frame;

        if (o instanceof AVFrame)
            frame = (AVFrame) o;
        else
        {
            frame = new AVFrame();
            buffer.setData(frame);
        }

        return frame.avpicture_fill(data, frameFormat);
    }

    /**
     * The <tt>ByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     */
    private ByteBuffer data;

    /**
     * The indicator which determines whether the native memory represented by
     * this instance is to be freed upon finalization.
     */
    private boolean free;

    /**
     * The pointer to the native FFmpeg <tt>AVFrame</tt> object represented by
     * this instance.
     */
    private long ptr;

    /**
     * Initializes a new <tt>FinalizableAVFrame</tt> instance which is to
     * allocate a new native FFmpeg <tt>AVFrame</tt> and represent it.
     */
    public AVFrame()
    {
        this.ptr = FFmpeg.avcodec_alloc_frame();
        if (this.ptr == 0)
            throw new OutOfMemoryError("avcodec_alloc_frame()");

        this.free = true;
    }

    /**
     * Initializes a new <tt>AVFrame</tt> instance which is to represent a
     * specific pointer to a native FFmpeg <tt>AVFrame</tt> object. Because the
     * native memory/<tt>AVFrame</tt> has been allocated outside the new
     * instance, the new instance does not automatically free it upon
     * finalization.
     *
     * @param ptr the pointer to the native FFmpeg <tt>AVFrame</tt> object to be
     * represented by the new instance
     */
    public AVFrame(long ptr)
    {
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");

        this.ptr = ptr;
        this.free = false;
    }

    public synchronized int avpicture_fill(
            ByteBuffer data,
            AVFrameFormat format)
    {
        Dimension size = format.getSize();
        int ret
            = FFmpeg.avpicture_fill(
                    ptr,
                    data.getPtr(),
                    format.getPixFmt(),
                    size.width, size.height);

        if (ret >= 0)
        {
            if (this.data != null)
                this.data.free();

            this.data = data;
        }
        return ret;
    }

    /**
     * Deallocates the native memory/FFmpeg <tt>AVFrame</tt> object represented
     * by this instance if this instance has allocated it upon initialization
     * and it has not been deallocated yet i.e. ensures that {@link #free()} is
     * invoked on this instance.
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
     * Deallocates the native memory/FFmpeg <tt>AVFrame</tt> object represented
     * by this instance if this instance has allocated it upon initialization
     * and it has not been deallocated yet.
     */
    public synchronized void free()
    {
        if (free && (ptr != 0))
        {
            FFmpeg.avcodec_free_frame(ptr);
            free = false;
            ptr = 0;
        }

        if (data != null)
        {
            data.free();
            data = null;
        }
    }

    /**
     * Gets the <tt>ByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     *
     * @return the <tt>ByteBuffer</tt> whose native memory is set on the native
     * counterpart of this instance/<tt>AVFrame</tt>.
     */
    public synchronized ByteBuffer getData()
    {
        return data;
    }

    /**
     * Gets the pointer to the native FFmpeg <tt>AVFrame</tt> object represented
     * by this instance.
     *
     * @return the pointer to the native FFmpeg <tt>AVFrame</tt> object
     * represented by this instance
     */
    public synchronized long getPtr()
    {
        return ptr;
    }
}
