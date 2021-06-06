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

import java.io.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;

/**
 * Represents a <tt>PullBufferStream</tt> which reads its data from a
 * specific <tt>PullSourceStream</tt>.
 *
 * @author Lyubomir Marinov
 */
public class PullBufferStreamAdapter
    extends BufferStreamAdapter<PullSourceStream>
    implements PullBufferStream
{

    /**
     * Initializes a new <tt>PullBufferStreamAdapter</tt> instance which
     * reads its data from a specific <tt>PullSourceStream</tt> with a
     * specific <tt>Format</tt>
     *
     * @param stream the <tt>PullSourceStream</tt> the new instance is to
     *            read its data from
     * @param format the <tt>Format</tt> of the specified input
     *            <tt>stream</tt> and of the new instance
     */
    public PullBufferStreamAdapter(PullSourceStream stream, Format format)
    {
        super(stream, format);
    }

    /**
     * Gets the frame size measured in bytes defined by a specific
     * <tt>Format</tt>.
     *
     * @param format the <tt>Format</tt> to determine the frame size in
     *            bytes of
     * @return the frame size measured in bytes defined by the specified
     *         <tt>Format</tt>
     */
    private static int getFrameSizeInBytes(Format format)
    {
        AudioFormat audioFormat = (AudioFormat) format;
        int frameSizeInBits = audioFormat.getFrameSizeInBits();

        if (frameSizeInBits <= 0)
            return
                (audioFormat.getSampleSizeInBits() / 8)
                    * audioFormat.getChannels();
        return (frameSizeInBits <= 8) ? 1 : (frameSizeInBits / 8);
    }

    /**
     * Implements PullBufferStream#read(Buffer). Delegates to the wrapped
     * PullSourceStream by either allocating a new byte[] buffer or using the
     * existing one in the specified Buffer.
     *
     * @param buffer <tt>Buffer</tt> to read
     * @throws IOException if I/O errors occurred during read operation
     */
    public void read(Buffer buffer)
        throws IOException
    {
        Object data = buffer.getData();
        byte[] bytes = null;

        if (data != null)
        {
            if (data instanceof byte[])
            {
                bytes = (byte[]) data;
            }
            else if (data instanceof short[])
            {
                short[] shorts = (short[]) data;

                bytes = new byte[2 * shorts.length];
            }
            else if (data instanceof int[])
            {
                int[] ints = (int[]) data;

                bytes = new byte[4 * ints.length];
            }
        }
        if (bytes == null)
        {
            int frameSizeInBytes = getFrameSizeInBytes(getFormat());

            bytes
                = new byte[
                        1024
                            * ((frameSizeInBytes <= 0) ? 4 : frameSizeInBytes)];
        }

        read(buffer, bytes, 0, bytes.length);
    }

    /**
     * Implements <tt>BufferStreamAdapter#doRead(Buffer, byte[], int, int)</tt>.
     * Delegates to the wrapped <tt>PullSourceStream</tt>.
     *
     * @param buffer
     * @param data byte array to read
     * @param offset to start reading
     * @param length length to read
     * @return number of bytes read
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Override
    protected int doRead(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        return stream.read(data, offset, length);
    }

    /**
     * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped
     * PullSourceStream.
     *
     * @return true if this stream will block on read operation, false otherwise
     */
    public boolean willReadBlock()
    {
        return stream.willReadBlock();
    }
}
