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

import javax.media.protocol.*;

/**
 * Implements a <tt>BufferTransferHandler</tt> wrapper which doesn't
 * expose a <tt>PushBufferStream</tt> but rather a specific substitute in order
 * to give full control to the {@link PushBufferStream#read(javax.media.Buffer)}
 * method of the substitute.
 * <p>
 * The purpose is achieved in <code>#transferData(PushBufferStream)</code>
 * where the method argument <code>stream</code> is ignored and the substitute
 * is used instead.
 * </p>
 *
 * @author Lubomir Marinov
 */
public class StreamSubstituteBufferTransferHandler
    implements BufferTransferHandler
{

    /**
     * The <code>PushBufferStream</code> to be overridden for
     * <code>transferHandler</code> with the <code>substitute</code> of this
     * instance.
     */
    private final PushBufferStream stream;

    /**
     * The <code>PushBufferStream</code> to override the <code>stream</code> of
     * this instance for <code>transferHandler</code>.
     */
    private final PushBufferStream substitute;

    /**
     * The wrapped <tt>BufferTransferHandler</tt> which receives the
     * actual events from the wrapped <tt>PushBufferStream</tt>.
     */
    private final BufferTransferHandler transferHandler;

    /**
     * Initializes a new <tt>StreamSubstituteBufferTransferHandler</tt> instance
     * which is to overwrite the source <tt>PushBufferStream</tt> of a specific
     * <tt>BufferTransferHandler</tt>.
     *
     * @param transferHandler the <tt>BufferTransferHandler</tt> the new
     *            instance is to overwrite the source <tt>PushBufferStream</tt>
     *            of
     * @param stream the <code>PushBufferStream</code> to be overridden for the
     *            specified <code>transferHandler</code> with the specified
     *            <code>substitute</code>
     * @param substitute the <code>PushBufferStream</code> to override the
     *            specified <code>stream</code> for the specified
     *            <code>transferHandler</code>
     */
    public StreamSubstituteBufferTransferHandler(
        BufferTransferHandler transferHandler,
        PushBufferStream stream,
        PushBufferStream substitute)
    {
        this.transferHandler = transferHandler;
        this.stream = stream;
        this.substitute = substitute;
    }

    /**
     * Implements BufferTransferHandler#transferData(PushBufferStream). Puts in
     * place the essence of the StreamSubstituteBufferTransferHandler class
     * which is to report to the transferHandler from the same PushBufferStream
     * to which it was set so that the substitute can gain full control.
     *
     * @param stream the <tt>PushBufferStream</tt> to transfer
     */
    public void transferData(PushBufferStream stream)
    {
        transferHandler.transferData(
            (stream == this.stream) ? substitute : stream);
    }
}
