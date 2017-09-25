/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.util;

import java.io.*;

/**
 * Represents a <tt>byte</tt> array as an <tt>OutputStream</tt>.
 *
 * @author Lyubomir Marinov
 */
public class ByteBufferOutputStream
    extends OutputStream
{
    /**
     * The index within {@link #buf} which was initially writable.
     */
    private final int beginIndex;

    /**
     * The <tt>byte</tt> array represented as an <tt>OutputStream</tt> by this
     * instance.
     */
    private final byte[] buf;

    /**
     * The index right after the last writable index within {@link #buf}.
     */
    private final int endIndex;

    /**
     * The first writable index within {@link #buf}.
     */
    private int index;

    /**
     * Initializes a new <tt>ByteBufferOutputStream</tt> instance which is to
     * represent a specific <tt>byte</tt> array as an <tt>OutputStream</tt>.
     *
     * @param buf the <tt>byte</tt> array for which the new instance is to
     * implement <tt>OutputStream</tt>
     */
    public ByteBufferOutputStream(byte[] buf)
    {
        this(buf, 0, buf.length);
    }

    /**
     * Initializes a new <tt>ByteBufferOutputStream</tt> instance which is to
     * represent a specific <tt>byte</tt> array as an <tt>OutputStream</tt>.
     *
     * @param buf the <tt>byte</tt> array for which the new instance is to
     * implement <tt>OutputStream</tt>
     * @param off
     * @param len
     */
    public ByteBufferOutputStream(byte[] buf, int off, int len)
    {
        if (buf == null)
            throw new NullPointerException("buf");
        if (off < 0)
            throw new IndexOutOfBoundsException("off");
        if (len > buf.length)
            throw new IndexOutOfBoundsException("len");

        this.buf = buf;
        this.beginIndex = off;
        this.index = off;
        this.endIndex = off + len;
    }

    /**
     * Returns the number of bytes written into this <tt>OutputStream</tt>.
     *
     * @return the number of bytes written into this <tt>OutputStream</tt>
     */
    public int size()
    {
        return index - beginIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b)
        throws IOException
    {
        if (index >= endIndex)
        {
            throw new IOException(
                "This " + net.sf.fmj.utility.ByteBufferOutputStream.class.getName()
                    + " is fully written.");
        }
        else
        {
            buf[index++] = (byte) b;
        }
    }
}
