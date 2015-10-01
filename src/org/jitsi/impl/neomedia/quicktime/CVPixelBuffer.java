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
 * Represents a CoreVideo <tt>CVPixelBufferRef</tt>.
 *
 * @author Lyubomir Marinov
 */
public class CVPixelBuffer
    extends CVImageBuffer
{

    /**
     * Initializes a new <tt>CVPixelBuffer</tt> instance which is to represent
     * a specific CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to be represented by
     * the new instance
     */
    public CVPixelBuffer(long ptr)
    {
        super(ptr);
    }

    /**
     * Gets the number of bytes which represent the pixels of the associated
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @return the number of bytes which represent the pixels of the associated
     * CoreVideo <tt>CVPixelBufferRef</tt>
     */
    public int getByteCount()
    {
        return getByteCount(getPtr());
    }

    /**
     * Gets the number of bytes which represent the pixels of a specific
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the number of bytes which
     * represent its pixels of
     * @return the number of bytes which represent the pixels of the specified
     * CoreVideo <tt>CVPixelBufferRef</tt>
     */
    private static native int getByteCount(long ptr);

    /**
     * Gets a <tt>byte</tt> array which represents the pixels of the associated
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @return a <tt>byte</tt> array which represents the pixels of the
     * associated CoreVideo <tt>CVPixelBufferRef</tt>
     */
    public byte[] getBytes()
    {
        return getBytes(getPtr());
    }

    /**
     * Gets a <tt>byte</tt> array which represents the pixels of a specific
     * CoreVideo <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the pixel bytes of
     * @return a <tt>byte</tt> array which represents the pixels of the
     * specified CoreVideo <tt>CVPixelBufferRef</tt>
     */
    private static native byte[] getBytes(long ptr);

    /**
     * Gets the bytes which represent the pixels of the associated
     * <tt>CVPixelBufferRef</tt> into a specific native byte buffer with a
     * specific capacity.
     *
     * @param buf the native byte buffer to return the bytes into
     * @param bufLength the capacity in bytes of <tt>buf</tt>
     * @return the number of bytes written into <tt>buf</tt>
     */
    public int getBytes(long buf, int bufLength)
    {
        return getBytes(getPtr(), buf, bufLength);
    }

    /**
     * Gets the bytes which represent the pixels of a specific
     * <tt>CVPixelBufferRef</tt> into a specific native byte buffer with a
     * specific capacity.
     *
     * @param ptr the <tt>CVPixelBufferRef</tt> to get the bytes of
     * @param buf the native byte buffer to return the bytes into
     * @param bufLength the capacity in bytes of <tt>buf</tt>
     * @return the number of bytes written into <tt>buf</tt>
     */
    private static native int getBytes(long ptr, long buf, int bufLength);

    /**
     * Gets the height in pixels of this <tt>CVPixelBuffer</tt>.
     *
     * @return the height in pixels of this <tt>CVPixelBuffer</tt>
     */
    public int getHeight()
    {
        return getHeight(getPtr());
    }

    /**
     * Gets the height in pixels of a specific CoreVideo
     * <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to get the height in
     * pixels of
     * @return the height in pixels of the specified CoreVideo
     * <tt>CVPixelBufferRef</tt>
     */
    private static native int getHeight(long ptr);

    /**
     * Gets the width in pixels of this <tt>CVPixelBuffer</tt>.
     *
     * @return the width in pixels of this <tt>CVPixelBuffer</tt>
     */
    public int getWidth()
    {
        return getWidth(getPtr());
    }

    /**
     * Gets the width in pixels of a specific CoreVideo
     * <tt>CVPixelBufferRef</tt>.
     *
     * @param ptr the CoreVideo <tt>CVPixelBufferRef</tt> to get the width in
     * pixels of
     * @return the width in pixels of the specified CoreVideo
     * <tt>CVPixelBufferRef</tt>
     */
    private static native int getWidth(long ptr);

    /**
     * Native copy from native pointer <tt>src</tt> to byte array <tt>dst</tt>.
     * @param dst destination array
     * @param dstOffset offset of <tt>dst</tt> to copy data to
     * @param dstLength length of <tt>dst</tt>
     * @param src native pointer source
     */
    public static native void memcpy(
            byte[] dst, int dstOffset, int dstLength,
            long src);
}
