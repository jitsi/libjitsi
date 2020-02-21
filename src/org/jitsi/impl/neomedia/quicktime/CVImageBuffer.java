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
package org.jitsi.impl.neomedia.quicktime;

import org.jitsi.utils.*;

/**
 * Represents a CoreVideo <tt>CVImageBufferRef</tt>.
 *
 * @author Lyubomir Marinov
 */
public class CVImageBuffer
{
    static
    {
        JNIUtils.loadLibrary("jnquicktime", CVImageBuffer.class);
    }

    /**
     * The CoreVideo <tt>CVImageBufferRef</tt> represented by this instance.
     */
    private long ptr;

    /**
     * Initializes a new <tt>CVImageBuffer</tt> instance which is to represent
     * a specific CoreVideo <tt>CVImageBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVImageBufferRef</tt> to be represented by
     * the new instance
     */
    public CVImageBuffer(long ptr)
    {
        setPtr(ptr);
    }

    /**
     * Gets the CoreVideo <tt>CVImageBufferRef</tt> represented by this
     * instance.
     *
     * @return the CoreVideo <tt>CVImageBufferRef</tt> represented by this
     * instance
     */
    protected long getPtr()
    {
        return ptr;
    }

    /**
     * Sets the CoreVideo <tt>CVImageBufferRef</tt> represented by this
     * instance.
     *
     * @param ptr the CoreVideo <tt>CVImageBufferRef</tt> to be represented by
     * this instance
     */
    protected void setPtr(long ptr)
    {
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");

        this.ptr = ptr;
    }
}
