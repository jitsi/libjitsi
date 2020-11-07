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
package org.jitsi.impl.neomedia.jmfext.media.protocol.greyfading;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.utils.*;

/**
 * Implements a <tt>MediaDevice</tt> which provides a fading animation from
 * white to black to white... in form of video.
 *
 * @author Thomas Kuntz
 */
public class VideoGreyFadingMediaDevice
    extends MediaDeviceImpl
{
    /**
     * The default framerate the<tt>MediaDevice</tt> will have.
     */
    public final static int DEFAULT_FRAMERATE = 10;
    
    /**
     * The default dimension the<tt>MediaDevice</tt> will have.
     */
    public final static Dimension DEFAULT_DIMENSION = new Dimension(640,480);
    
    /**
     * Initializes a new <tt>VideoGreyFadingMediaDevice</tt> with default
     * framerate and dimension.
     */
    public VideoGreyFadingMediaDevice()
    {
        this(DEFAULT_FRAMERATE,DEFAULT_DIMENSION);
    }
    
    /**
     * Initializes a new <tt>VideoGreyFadingMediaDevice</tt> with the given
     * framerate and the default dimension.
     * @param framerate the framerate of the <tt>CaptureDevice</tt> behind this
     * <tt>MediaDevice</tt>.
     */
    public VideoGreyFadingMediaDevice(int framerate)
    {
        this(framerate,DEFAULT_DIMENSION);
    }
    
    /**
     * Initializes a new <tt>VideoGreyFadingMediaDevice</tt> with the given
     * dimension and the default framerate. 
     * @param dimension the dimension (width & height) of the
     * <tt>CaptureDevice</tt> behind this <tt>MediaDevice</tt>.
     */
    public VideoGreyFadingMediaDevice(Dimension dimension)
    {
        this(DEFAULT_FRAMERATE,dimension);
    }
    
    /**
     * Initializes a new <tt>VideoGreyFadingMediaDevice</tt> with the given
     * framerate and dimension.
     * @param framerate the framerate of the <tt>CaptureDevice</tt> behind this
     * <tt>MediaDevice</tt>.
     * @param dimension the dimension (width & height) of the
     * <tt>CaptureDevice</tt> behind this <tt>MediaDevice</tt>.
     */
    public VideoGreyFadingMediaDevice(int framerate, Dimension dimension)
    {
        super(new CaptureDeviceInfo(
                    "GreyFadingVideo",
                    new MediaLocator("greyfading:"),
                    new Format[]
                            {
                            new RGBFormat(
                                 dimension, // size
                                 Format.NOT_SPECIFIED, // maxDataLength
                                 Format.byteArray, // dataType
                                 framerate, // frameRate
                                 32, // bitsPerPixel
                                 2 /* red */,
                                 3 /* green */,
                                 4 /* blue */)
                            }),
                MediaType.VIDEO);
    }
}
