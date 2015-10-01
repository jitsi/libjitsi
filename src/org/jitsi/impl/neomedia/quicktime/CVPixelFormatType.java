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
 * Defines the types of <tt>CVPixelBuffer</tt>s to be output by
 * <tt>QTCaptureDecompressedVideoOutput</tt>.
 *
 * @author Lyubomir Marinov
 */
public final class CVPixelFormatType
{

    /** 24 bit RGB */
    public static final int kCVPixelFormatType_24RGB = 0x00000018;

    /** 32 bit ARGB */
    public static final int kCVPixelFormatType_32ARGB = 0x00000020;

    /** Planar Component Y'CbCr 8-bit 4:2:0. */
    public static final int kCVPixelFormatType_420YpCbCr8Planar = 0x79343230;

    /**
     * Prevents the initialization of <tt>CVPixelFormatType</tt> instances.
     */
    private CVPixelFormatType()
    {
    }
}
