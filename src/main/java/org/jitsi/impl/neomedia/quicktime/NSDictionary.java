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

/**
 * Represents an Objective-C <tt>NSDictionary</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class NSDictionary
    extends NSObject
{

    /**
     * Initializes a new <tt>NSDictionary</tt> instance which is to represent a
     * specific Objective-C <tt>NSDictionary</tt> object.
     *
     * @param ptr the pointer to the Objective-C <tt>NSDictionary</tt> object to
     * be represented by the new instance
     */
    public NSDictionary(long ptr)
    {
        super(ptr);
    }

    /**
     * Called by the garbage collector to release system resources and perform
     * other cleanup.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
    {
        release();
    }

    public int intForKey(long key)
    {
        return intForKey(getPtr(), key);
    }

    private static native int intForKey(long ptr, long key);
}
