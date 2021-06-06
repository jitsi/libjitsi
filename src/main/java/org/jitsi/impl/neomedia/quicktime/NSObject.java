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
 * Represents the root of most Objective-C class hierarchies which which objects
 * inherit a basic interface to the runtime system and the ability to behave as
 * Objective-C objects.
 *
 * @author Lyubomir Marinov
 */
public class NSObject
{
    static
    {
        JNIUtils.loadLibrary("jnquicktime", NSObject.class);
    }

    /**
     * The pointer to the Objective-C object represented by this instance.
     */
    private long ptr;

    /**
     * Initializes a new <tt>NSObject</tt> instance which is to represent a
     * specific Objective-C object.
     *
     * @param ptr the pointer to the Objective-C object to be represented by the
     * new instance
     */
    public NSObject(long ptr)
    {
        setPtr(ptr);
    }

    /**
     * Gets the pointer to the Objective-C object represented by this instance.
     *
     * @return the pointer to the Objective-C object represented by this
     * instance
     */
    public long getPtr()
    {
        return ptr;
    }

    /**
     * Decrements the reference count of the Objective-C object represented by
     * this instance. It is sent a <tt>dealloc</tt> message when its reference
     * count reaches <tt>0</tt>.
     */
    public void release()
    {
        release(ptr);
    }

    /**
     * Decrements the reference count of a specific Objective-C object. It is
     * sent a <tt>dealloc</tt> message when its reference count reaches
     * <tt>0</tt>.
     *
     * @param ptr the pointer to the Objective-C object to decrement the
     * reference count of
     */
    public static native void release(long ptr);

    /**
     * Increments the reference count of the Objective-C object represented by
     * this instance.
     */
    public void retain()
    {
        retain(ptr);
    }

    /**
     * Increments the reference count of a specific Objective-C object.
     *
     * @param ptr the pointer to be Objective-C object to increment the
     * reference count of
     */
    static native void retain(long ptr);

    /**
     * Sets the pointer to the Objective-C object represented by this instance.
     *
     * @param ptr the pointer to the Objective-C object to be represented by
     * this instance
     */
    protected void setPtr(long ptr)
    {
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");

        this.ptr = ptr;
    }
}
