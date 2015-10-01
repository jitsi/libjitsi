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
 * Represents a QuickTime/QTKit <tt>QTSampleBuffer</tt> object.
 *
 * @author Lyubomir Marinov
 */
public class QTSampleBuffer
    extends NSObject
{

    /**
     * Initializes a new <tt>QTSampleBuffer</tt> which is to represent a
     * specific QuickTime/QTKit <tt>QTSampleBuffer</tt> object.
     *
     * @param ptr the pointer to the QuickTime/QTKit <tt>QTSampleBuffer</tt>
     * object to be represented by the new instance
     */
    public QTSampleBuffer(long ptr)
    {
        super(ptr);
    }

    public byte[] bytesForAllSamples()
    {
        return bytesForAllSamples(getPtr());
    }

    private static native byte[] bytesForAllSamples(long ptr);

    public QTFormatDescription formatDescription()
    {
        long formatDescriptionPtr = formatDescription(getPtr());

        return
            (formatDescriptionPtr == 0)
                ? null
                : new QTFormatDescription(formatDescriptionPtr);
    }

    private static native long formatDescription(long ptr);
}
