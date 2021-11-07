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
 * Defines an <tt>Exception</tt> which reports an <tt>NSError</tt>.
 *
 * @author Lyubomir Marinov
 */
public class NSErrorException
    extends Exception
{

    /**
     * The <tt>NSError</tt> reported by this instance.
     */
    private final NSError error;

    /**
     * Initializes a new <tt>NSErrorException</tt> instance which is to report a
     * specific Objective-C <tt>NSError</tt>.
     *
     * @param errorPtr the pointer to the Objective-C <tt>NSError</tt> object to
     * be reported by the new instance
     */
    public NSErrorException(long errorPtr)
    {
        this(new NSError(errorPtr));
    }

    /**
     * Initializes a new <tt>NSErrorException</tt> instance which is to report a
     * specific <tt>NSError</tt>.
     *
     * @param error the <tt>NSError</tt> to be reported by the new instance
     */
    public NSErrorException(NSError error)
    {
        this.error = error;
    }

    /**
     * Gets the <tt>NSError</tt> reported by this instance.
     *
     * @return the <tt>NSError</tt> reported by this instance
     */
    public NSError getError()
    {
        return error;
    }
}
