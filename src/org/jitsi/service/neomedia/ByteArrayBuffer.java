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
package org.jitsi.service.neomedia;

/**
 * A simple interface that encapsulates all the information needed for byte
 * buffer access.
 *
 * @author George Politis
 */
public interface ByteArrayBuffer
{
    /**
     * Gets the byte buffer that supports this instance.
     *
     * @return the byte buffer that supports this instance.
     */
    byte[] getBuffer();

    /**
     * Gets the offset in the byte buffer where the actual data starts.
     *
     * @return the offset in the byte buffer where the actual data starts.
     */
    int getOffset();

    /**
     * Gets the length of the data in the buffer.
     *
     * @return the length of the data in the buffer.
     */
    int getLength();

    /**
     * Sets the length of the data in the buffer.
     *
     * @param len the length of the data in the buffer.
     */
    void setLength(int len);

    /**
     * Sets the offset of the data in the buffer.
     *
     * @param off the offset of the data in the buffer.
     */
    void setOffset(int off);

    /**
     * Perform checks on the byte buffer represented by this instance and
     * return <tt>true</tt> if it is found to be invalid.
     *
     * @return <tt>true</tt> if the byte buffer represented by this
     * instance is found to be invalid, <tt>false</tt> otherwise.
     */
    boolean isInvalid();

    /**
     * Copies {@code len} bytes from the given offset in this buffer to the given {@code outBuf}
     * @param off the offset relative to the beginning of this buffer's data from where to start copying.
     * @param len the number of bytes to copy.
     * @param outBuf the array to copy to
     */
    void readRegionToBuff(int off, int len, byte[] outBuf);

    /**
     * Increases the size of this buffer by {@code howMuch}. This may result in a new {@code byte[]} to be allocated
     * if the existing one is not large enough.
     * @param howMuch
     */
    void grow(int howMuch);

    /**
     * Appends {@code len} bytes from {@code data} to the end of this buffer. This grows the buffer and as a result
     * a new {@code byte[]} may be allocated.
     * @param data
     * @param len
     */
    void append(byte[] data, int len);

    /**
     * Shrinks the length of this buffer by {@code len}
     * @param len
     */
    void shrink(int len);
}
