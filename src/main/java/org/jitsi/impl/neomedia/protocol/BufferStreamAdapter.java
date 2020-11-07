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
import javax.media.protocol.*;

/**
 * Represents a base class for adapters of <tt>SourceStream</tt>s, usually
 * ones reading data in arrays of bytes and not in <tt>Buffer</tt>s, to
 * <tt>SourceStream</tt>s reading data in <tt>Buffer</tt>s. An example
 * use is creating a PushBufferStream representation of a PushSourceStream.
 *
 * @param <T> the very type of <tt>SourceStream</tt> to be adapted by a
 * <tt>BufferStreamAdapter</tt>
 * @author Lyubomir Marinov
 */
public abstract class BufferStreamAdapter<T extends SourceStream>
    implements SourceStream
{

    /**
     * The <tt>Format</tt> of this stream to be reported through the output
     * <tt>Buffer</tt> this instance reads data into.
     */
    private final Format format;

    /**
     * The <tt>SourceStream</tt> being adapted by this instance.
     */
    protected final T stream;

    /**
     * Initializes a new <tt>BufferStreamAdapter</tt> which is to adapt a
     * specific <tt>SourceStream</tt> into a <tt>SourceStream</tt> with
     * a specific <tt>Format</tt>.
     *
     * @param stream the <tt>SourceStream</tt> to be adapted
     * @param format the specific <tt>Format</tt> of the <tt>SourceStream</tt>
     */
    public BufferStreamAdapter(T stream, Format format)
    {
        this.stream = stream;
        this.format = format;
    }

    /**
     * Implements SourceStream#endOfStream(). Delegates to the wrapped
     * SourceStream.
     *
     * @return true if the stream is finished, false otherwise
     */
    public boolean endOfStream()
    {
        return stream.endOfStream();
    }

    /**
     * Implements SourceStream#getContentDescriptor(). Delegates to the wrapped
     * SourceStream.
     *
     * @return the <tt>ContentDescriptor</tt> of the stream
     */
    public ContentDescriptor getContentDescriptor()
    {
        return stream.getContentDescriptor();
    }

    /**
     * Implements SourceStream#getContentLength(). Delegates to the wrapped
     * SourceStream.
     *
     * @return content length
     */
    public long getContentLength()
    {
        return stream.getContentLength();
    }

    /**
     * Implements Controls#getControl(String). Delegates to the wrapped
     * SourceStream.
     *
     * @param controlType a <tt>String</tt> value naming the type of the control
     * of this instance to be retrieved
     * @return an <tt>Object</tt> which represents the control of this instance
     * with the specified type
     */
    public Object getControl(String controlType)
    {
        return stream.getControl(controlType);
    }

    /**
     * Implements Controls#getControls(). Delegates to the wrapped SourceStream.
     *
     * @return array of JMF <tt>Control</tt> objects
     */
    public Object[] getControls()
    {
        return stream.getControls();
    }

    /**
     * Gets the <tt>Format</tt> of the data this stream provides.
     *
     * @return the <tt>Format</tt> of the data this stream provides
     */
    public Format getFormat()
    {
        return format;
    }

    /**
     * Gets the <tt>SourceStream</tt> wrapped by this instance.
     *
     * @return the <tt>SourceStream</tt> wrapped by this instance
     */
    public T getStream()
    {
        return stream;
    }

    /**
     * Reads byte data from this stream into a specific <tt>Buffer</tt>
     * which is to use a specific array of bytes for its data.
     *
     * @param buffer the <tt>Buffer</tt> to read byte data into from this
     * instance
     * @param data the array of <tt>byte</tt>s to read data into from this
     * instance and to be set as the data of the specified <tt>buffer</tt>
     * @throws IOException if I/O related errors occurred during read operation
     */
    protected void read(Buffer buffer, byte[] data, int offset, int length)
        throws IOException
    {
        int numberOfBytesRead = doRead(buffer, data, offset, length);

        buffer.setData(data);
        if (numberOfBytesRead >= 0)
        {
            buffer.setLength(numberOfBytesRead);
        }
        else
        {
            buffer.setLength(0);
            if (numberOfBytesRead == -1)
                buffer.setEOM(true);
        }
        buffer.setOffset(offset);

        Format format = getFormat();

        if (format != null)
            buffer.setFormat(format);
    }

    /**
     * Reads byte data from this stream into a specific array of <tt>byte</tt>s
     * starting the storing at a specific offset and reading at most a specific
     * number of bytes.
     *
     * @param buffer an optional <tt>Buffer</tt> instance associated with the
     * specified <tt>data</tt>, <tt>offset</tt> and <tt>length</tt> and
     * provided to the method in case the implementation would like to provide
     * additional <tt>Buffer</tt> properties such as <tt>flags</tt>
     * @param data the array of <tt>byte</tt>s into which the data read from
     * this stream is to be written
     * @param offset the offset in the specified <tt>data</tt> at which
     * writing data read from this stream should start
     * @param length the maximum number of bytes to be written into the
     * specified <tt>data</tt>
     * @return the number of bytes read from this stream and written into the
     * specified <tt>data</tt>
     * @throws IOException if I/O related errors occurred during read operation
     */
    protected abstract int doRead(
            Buffer buffer,
            byte[] data, int offset, int length)
        throws IOException;
}
