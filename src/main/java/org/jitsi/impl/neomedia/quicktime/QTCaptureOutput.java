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
 * Represents a QTKit <tt>QTCaptureOutput</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class QTCaptureOutput
    extends NSObject
{

    /**
     * Initializes a new <tt>QTCaptureOutput</tt> instance which is to represent
     * a specific QTKit <tt>QTCaptureOutput</tt> object.
     *
     * @param ptr the pointer to the QTKit <tt>QTCaptureOutput</tt> object to be
     * represented by the new instance
     */
    public QTCaptureOutput(long ptr)
    {
        super(ptr);
    }
}
