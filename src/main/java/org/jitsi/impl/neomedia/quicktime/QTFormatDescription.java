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

import java.awt.*;

/**
 * Describes the media format of media samples and of media sources, such as
 * devices and capture connections. Includes basic information about the media,
 * such as media type and format type (or codec type), as well as extended
 * information specific to each media type.
 *
 * @author Lyubomir Marinov
 */
public class QTFormatDescription
    extends NSObject
{
    /**
     * Initializes a new <tt>QTFormatDescription</tt> instance which is to
     * represent a specific QTKit <tt>QTFormatDescription</tt> object.
     *
     * @param ptr the pointer to the QTKit <tt>QTFormatDescription</tt> object
     * which is to be represented by the new instance
     */
    public QTFormatDescription(long ptr)
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

    public Dimension size()
    {
        return size(getPtr());
    }

    private static native Dimension size(long ptr);
}
