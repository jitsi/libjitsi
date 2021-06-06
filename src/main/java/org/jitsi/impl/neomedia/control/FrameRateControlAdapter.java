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
package org.jitsi.impl.neomedia.control;

import java.awt.*;

import javax.media.*;
import javax.media.control.*;

/**
 * Provides a default implementation of <tt>FrameRateControl</tt>.
 *
 * @author Lyubomir Marinov
 */
public class FrameRateControlAdapter
    implements FrameRateControl
{
    /**
     * Gets the UI <tt>Component</tt> associated with this <tt>Control</tt>
     * object. <tt>FrameRateControlAdapter</tt> always returns <tt>null</tt>.
     *
     * @return the UI <tt>Component</tt> associated with this <tt>Control</tt>
     * object
     * @see Control#getControlComponent()
     */
    public Component getControlComponent()
    {
        return null;
    }

    /**
     * Gets the current output frame rate. <tt>FrameRateControlAdapter</tt>
     * always returns <tt>-1</tt>.
     *
     * @return the current output frame rate if it is known; otherwise,
     * <tt>-1</tt>
     * @see FrameRateControl#getFrameRate()
     */
    public float getFrameRate()
    {
        return -1;
    }

    /**
     * Gets the maximum supported output frame rate.
     * <tt>FrameRateControlAdapter</tt> always returns <tt>-1</tt>.
     *
     * @return the maximum supported output frame rate if it is known;
     * otherwise, <tt>-1</tt>
     * @see FrameRateControl#getMaxSupportedFrameRate()
     */
    public float getMaxSupportedFrameRate()
    {
        return -1;
    }

    /**
     * Gets the default/preferred output frame rate.
     * <tt>FrameRateControlAdapter</tt> always returns <tt>-1</tt>.
     *
     * @return the default/preferred output frame rate if it is known;
     * otherwise, <tt>-1</tt>
     * @see FrameRateControl#getPreferredFrameRate()
     */
    public float getPreferredFrameRate()
    {
        return -1;
    }

    /**
     * Sets the output frame rate. <tt>FrameRateControlAdapter</tt> always
     * returns <tt>-1</tt>.
     *
     * @param frameRate the output frame rate to change the current one to
     * @return the actual current output frame rate or <tt>-1</tt> if it is
     * unknown or not controllable
     * @see FrameRateControl#setFrameRate(float)
     */
    public float setFrameRate(float frameRate)
    {
        return -1;
    }
}
