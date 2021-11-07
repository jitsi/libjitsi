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
package org.jitsi.impl.neomedia.protocol;

import javax.media.*;
import javax.media.protocol.*;

/**
 * Implements a <tt>BufferTransferHandler</tt> which reads from a specified
 * <tt>PushBufferStream</tt> as soon as possible and throws the read data away.
 *
 * @author Lyubomir Marinov
 */
public class NullBufferTransferHandler
    implements BufferTransferHandler
{
    /**
     * The FMJ <tt>Buffer</tt> into which this <tt>BufferTransferHandler</tt> is
     * to read data from any <tt>PushBufferStream</tt>.
     */
    private final Buffer buffer = new Buffer();

    @Override
    public void transferData(PushBufferStream stream)
    {
        try
        {
            stream.read(buffer);
        }
        catch (Exception ex)
        {
            // The purpose of NullBufferTransferHandler is to read from the
            // specified PushBufferStream as soon as possible and throw the read
            // data away. Hence, Exceptions are of no concern.
        }
    }
}
