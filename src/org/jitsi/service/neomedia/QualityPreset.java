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

import java.awt.*;

/**
 * Predefined quality preset used to specify some video settings during an
 * existing call or when starting a new call.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class QualityPreset
    implements Comparable<QualityPreset>
{
    /**
     * 720p HD
     */
    public static final QualityPreset HD_QUALITY
        = new QualityPreset(new Dimension(1280, 720), 30);

    /**
     * Low
     */
    public static final QualityPreset LO_QUALITY
        = new QualityPreset(new Dimension(320, 240), 15);

    /**
     * SD
     */
    public static final QualityPreset SD_QUALITY
        = new QualityPreset(new Dimension(640, 480), 20);

    /**
     * The frame rate to use.
     */
    private final float frameRate;

    /**
     * The resolution to use.
     */
    private final Dimension resolution;

    /**
     * Initializes a new quality preset with a specific <tt>resolution</tt> and
     * a specific <tt>frameRate</tt>.
     *
     * @param resolution the resolution
     * @param frameRate the frame rate
     */
    public QualityPreset(Dimension resolution, float frameRate)
    {
        this.frameRate = frameRate;
        this.resolution = resolution;
    }

    /**
     * Initializes a new quality preset with a specific <tt>resolution</tt> and
     * an unspecified <tt>frameRate</tt>.
     *
     * @param resolution the resolution
     */
    public QualityPreset(Dimension resolution)
    {
        this(resolution, -1 /* unspecified */);
    }

    /**
     * Returns this preset frame rate.
     * @return the frame rate.
     */
    public float getFameRate()
    {
        return frameRate;
    }

    /**
     * Returns this preset resolution.
     * @return the resolution.
     */
    public Dimension getResolution()
    {
        return resolution;
    }

    /**
     * Compares to presets and its dimensions.
     * @param o object to compare to.
     * @return a negative integer, zero, or a positive integer as this object is
     * less than, equal to, or greater than the specified object.
     */
    public int compareTo(QualityPreset o)
    {
        if(resolution == null)
            return -1;
        else if((o == null) || (o.resolution == null))
            return 1;
        else if(resolution.equals(o.resolution))
            return 0;
        else if((resolution.height < o.resolution.height)
                && (resolution.width < o.resolution.width))
            return -1;
        else
            return 1;
    }
}
