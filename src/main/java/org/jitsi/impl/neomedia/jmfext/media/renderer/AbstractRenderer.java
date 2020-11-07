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
package org.jitsi.impl.neomedia.jmfext.media.renderer;

import javax.media.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.utils.logging.*;

/**
 * Provides an abstract base implementation of <tt>Renderer</tt> in order to
 * facilitate extenders.
 *
 * @param <T> the type of <tt>Format</tt> of the media data processed as input
 * by <tt>AbstractRenderer</tt>
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractRenderer<T extends Format>
    extends ControlsAdapter
    implements Renderer
{
    /**
     * The <tt>Logger</tt> used by the <tt>AbstractRenderer</tt> class and its
     * instance to print debugging information.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractRenderer.class);

    /**
     * The <tt>Format</tt> of the media data processed as input by this
     * <tt>Renderer</tt>.
     */
    protected T inputFormat;

    /**
     * Resets the state of this <tt>PlugIn</tt>.
     */
    public void reset()
    {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the <tt>Format</tt> of the media data to be rendered by this
     * <tt>Renderer</tt>.
     *
     * @param format the <tt>Format</tt> of the media data to be rendered by
     * this <tt>Renderer</tt>
     * @return <tt>null</tt> if the specified <tt>format</tt> is not compatible
     * with this <tt>Renderer</tt>; otherwise, the <tt>Format</tt> which has
     * been successfully set
     */
    public Format setInputFormat(Format format)
    {
        Format matchingFormat = null;

        for (Format supportedInputFormat : getSupportedInputFormats())
        {
            if (supportedInputFormat.matches(format))
            {
                matchingFormat = supportedInputFormat.intersects(format);
                break;
            }
        }
        if (matchingFormat == null)
            return null;

        @SuppressWarnings("unchecked")
        T t = (T) matchingFormat;

        inputFormat = t;
        return inputFormat;
    }

    /**
     * Changes the priority of the current thread to a specific value.
     *
     * @param threadPriority the priority to set the current thread to
     */
    public static void useThreadPriority(int threadPriority)
    {
        Throwable throwable = null;

        try
        {
            Thread.currentThread().setPriority(threadPriority);
        }
        catch (IllegalArgumentException iae)
        {
            throwable = iae;
        }
        catch (SecurityException se)
        {
            throwable = se;
        }
        if (throwable != null)
        {
            logger.warn(
                    "Failed to use thread priority: " + threadPriority,
                    throwable);
        }
    }
}
