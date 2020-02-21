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
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

import javax.media.*;
import org.jitsi.utils.*;

/**
 * DirectShow video format.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DSFormat
{

    /**
     * The ARGB32 constant.
     */
    public static final int ARGB32;

    /**
     * The I420 constant.
     */
    public static final int I420;

    /**
     * The NV12 constant.
     */
    public static final int NV12;

    /**
     * The RGB24 constant.
     */
    public static final int RGB24;

    /**
     * The RGB32 constant.
     */
    public static final int RGB32;

    /**
     * The UYVY constant.
     */
    public static final int UYVY;

    /**
     * The Y411 constant.
     */
    public static final int Y411;

    /**
     * The Y41P constant.
     */
    public static final int Y41P;

    /**
     * The YUY2 constant.
     */
    public static final int YUY2;

    /**
     * The MJPEG constant.
     */
    public static final int MJPG;

    static
    {
        JNIUtils.loadLibrary("jndirectshow", DSFormat.class);

        ARGB32 = ARGB32();
        I420 = I420();
        MJPG = MJPG();
        NV12 = NV12();
        RGB24 = RGB24();
        RGB32 = RGB32();
        UYVY = UYVY();
        Y411 = Y411();
        Y41P = Y41P();
        YUY2 = YUY2();
    }

    private static native int ARGB32();

    public static native int AYUV();

    private static native int I420();

    public static native int IF09();

    public static native int IMC1();

    public static native int IMC2();

    public static native int IMC3();

    public static native int IMC4();

    public static native int IYUV();

    private static native int MJPG();

    private static native int NV12();

    private static native int RGB24();

    private static native int RGB32();

    private static native int UYVY();

    public static native int Y211();

    private static native int Y411();

    private static native int Y41P();

    private static native int YUY2();

    public static native int YV12();

    public static native int YVU9();

    public static native int YVYU();

    /**
     * Video height.
     */
    private final int height;

    /**
     * Color space.
     */
    private final int pixelFormat;

    /**
     * Video width.
     */
    private final int width;

    /**
     * Constructor.
     *
     * @param width video width
     * @param height video height
     * @param pixelFormat pixel format
     */
    public DSFormat(int width, int height, int pixelFormat)
    {
        this.width = width;
        this.height = height;
        this.pixelFormat = pixelFormat;
    }

    /**
     * Get video height.
     *
     * @return video height
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Get color space.
     *
     * @return color space
     */
    public int getPixelFormat()
    {
        return pixelFormat;
    }

    /**
     * Get video width.
     *
     * @return video width
     */
    public int getWidth()
    {
        return width;
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder(getClass().getName());

        if (pixelFormat != Format.NOT_SPECIFIED)
        {
            s.append(", pixelFormat 0x");
            /*
             * The value of pixelFormat is GUID.Data1 which is a DWORD i.e. a
             * 32-bit unsigned integer). The most suitable display is surely
             * hexadecimal.
             */
            s.append(Long.toHexString(pixelFormat & 0xffffffffL));
        }
        if (width != Format.NOT_SPECIFIED)
            s.append(", width ").append(width);
        if (height != Format.NOT_SPECIFIED)
            s.append(", height ").append(height);

        return s.toString();
    }
}
