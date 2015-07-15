/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.quicktime;

import org.jitsi.util.*;

/**
 * @author Lyubomir Marinov
 */
public final class CVPixelBufferAttributeKey
{
    public static final long kCVPixelBufferHeightKey;

    public static final long kCVPixelBufferPixelFormatTypeKey;

    public static final long kCVPixelBufferWidthKey;

    static
    {
        JNIUtils.loadLibrary(
                "jnquicktime",
                CVPixelBufferAttributeKey.class.getClassLoader());

        kCVPixelBufferHeightKey = kCVPixelBufferHeightKey();
        kCVPixelBufferPixelFormatTypeKey = kCVPixelBufferPixelFormatTypeKey();
        kCVPixelBufferWidthKey = kCVPixelBufferWidthKey();
    }

    /**
     * Prevents the initialization of <tt>CVPixelBufferAttributeKey</tt>
     * instances.
     */
    private CVPixelBufferAttributeKey()
    {
    }

    private static native long kCVPixelBufferHeightKey();

    private static native long kCVPixelBufferPixelFormatTypeKey();

    private static native long kCVPixelBufferWidthKey();
}
