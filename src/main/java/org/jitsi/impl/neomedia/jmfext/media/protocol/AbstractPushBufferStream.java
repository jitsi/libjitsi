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
package org.jitsi.impl.neomedia.jmfext.media.protocol;

import javax.media.control.*;
import javax.media.protocol.*;

/**
 * Provides a base implementation of <tt>PushBufferStream</tt> in order to
 * facilitate implementers by taking care of boilerplate in the most common
 * cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractPushBufferStream<T extends PushBufferDataSource>
    extends AbstractBufferStream<T>
    implements PushBufferStream
{
    /**
     * The name of the <tt>PushBufferStream</tt> class.
     */
    public static final String PUSH_BUFFER_STREAM_CLASS_NAME
        = PushBufferStream.class.getName();

    /**
     * The <tt>BufferTransferHandler</tt> which is notified by this
     * <tt>PushBufferStream</tt> when data is available for reading.
     */
    protected BufferTransferHandler transferHandler;

    /**
     * Initializes a new <tt>AbstractPushBufferStream</tt> instance which is to
     * have its <tt>Format</tt>-related information abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param dataSource the <tt>PushBufferDataSource</tt> which is creating the
     * new instance so that it becomes one of its <tt>streams</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     */
    protected AbstractPushBufferStream(
            T dataSource,
            FormatControl formatControl)
    {
        super(dataSource, formatControl);
    }

    /**
     * Sets the <tt>BufferTransferHandler</tt> which is to be notified by this
     * <tt>PushBufferStream</tt> when data is available for reading.
     *
     * @param transferHandler the <tt>BufferTransferHandler</tt> which is to be
     * notified by this <tt>PushBufferStream</tt> when data is available for
     * reading
     */
    public void setTransferHandler(BufferTransferHandler transferHandler)
    {
        this.transferHandler = transferHandler;
    }
}
