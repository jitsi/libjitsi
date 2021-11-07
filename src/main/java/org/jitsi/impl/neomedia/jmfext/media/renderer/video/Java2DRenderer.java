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
package org.jitsi.impl.neomedia.jmfext.media.renderer.video;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.renderer.*;
import javax.swing.*;

import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.*;
import org.jitsi.util.*;
import org.jitsi.util.swing.*;

/**
 * Video renderer using pure Java2D.
 * 
 * @author Ingo Bauersachs
 */
public class Java2DRenderer
    extends AbstractRenderer<VideoFormat>
    implements VideoRenderer
{
    /**
     * The default, initial height and width to set on the <tt>Component</tt>s
     * of <tt>JAWTRenderer</tt>s before video frames with actual sizes are
     * processed. Introduced to mitigate multiple failures to realize the actual
     * video frame size and/or to properly scale the visual/video
     * <tt>Component</tt>s.
     */
    private static final int DEFAULT_COMPONENT_HEIGHT_OR_WIDTH = 16;

    /**
     * The array of supported input formats.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS
        = new Format[]
                {
                    OSUtils.IS_LINUX
                        ? new YUVFormat(
                                null /* size */,
                                Format.NOT_SPECIFIED /* maxDataLength */,
                                Format.intArray,
                                Format.NOT_SPECIFIED /* frameRate */,
                                YUVFormat.YUV_420,
                                Format.NOT_SPECIFIED /* strideY */,
                                Format.NOT_SPECIFIED /* strideUV */,
                                Format.NOT_SPECIFIED /* offsetY */,
                                Format.NOT_SPECIFIED /* offsetU */,
                                Format.NOT_SPECIFIED /* offsetV */)
                        : OSUtils.IS_ANDROID
                            ? new RGBFormat(
                                    null,
                                    Format.NOT_SPECIFIED,
                                    Format.intArray,
                                    Format.NOT_SPECIFIED,
                                    32,
                                    0x000000ff, 0x0000ff00, 0x00ff0000)
                            : new RGBFormat(
                                    null,
                                    Format.NOT_SPECIFIED,
                                    Format.intArray,
                                    Format.NOT_SPECIFIED,
                                    32,
                                    0x00ff0000, 0x0000ff00, 0x000000ff)
                };

    private Java2DRendererVideoComponent component;

    /**
     * The last known height of the input processed by this
     * <tt>JAWTRenderer</tt>.
     */
    private int height = 0;

    /**
     * The last known width of the input processed by this
     * <tt>JAWTRenderer</tt>.
     */
    private int width = 0;

    /**
     * The <tt>Runnable</tt> which is executed to bring the invocations of
     * {@link #reflectInputFormatOnComponent()} into the AWT event dispatching
     * thread.
     */
    private final Runnable reflectInputFormatOnComponentInEventDispatchThread
        = new Runnable()
        {
            @Override
            public void run()
            {
                reflectInputFormatOnComponentInEventDispatchThread();
            }
        };

    @Override
    public Format[] getSupportedInputFormats()
    {
        return SUPPORTED_INPUT_FORMATS.clone();
    }

    /**
     * Processes the data provided in a specific <tt>Buffer</tt> and renders it
     * to the output device represented by this <tt>Renderer</tt>.
     *
     * @param buffer a <tt>Buffer</tt> containing the data to be processed and
     * rendered
     * @return <tt>BUFFER_PROCESSED_OK</tt> if the processing is successful;
     * otherwise, the other possible return codes defined in the <tt>PlugIn</tt>
     * interface
     */
    @Override
    public synchronized int process(Buffer buffer)
    {
        if (buffer.isDiscard())
        {
            return BUFFER_PROCESSED_OK;
        }

        int bufferLength = buffer.getLength();
        if (bufferLength == 0)
        {
            return BUFFER_PROCESSED_OK;
        }

        Format format = buffer.getFormat();
        if (format != null
                && format != this.inputFormat
                && !format.equals(this.inputFormat)
                && setInputFormat(format) == null)
        {
            return BUFFER_PROCESSED_FAILED;
        }

        Dimension size = null;
        if (format != null)
        {
            size = ((VideoFormat) format).getSize();
        }

        if (size == null)
        {
            size = this.inputFormat.getSize();
            if (size == null)
            {
                return BUFFER_PROCESSED_FAILED;
            }
        }

        // XXX If the size of the video frame to be displayed is tiny enough
        // to crash sws_scale, then it may cause issues with other
        // functionality as well. Stay on the safe side.
        if (size.width >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH
                && size.height >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
        {
            getComponent().process(buffer, size);
        }

        return BUFFER_PROCESSED_OK;
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public String getName()
    {
        return "Pure Java Video Renderer";
    }

    @Override
    public void open() throws ResourceUnavailableException
    {
    }

    @Override
    public Rectangle getBounds()
    {
        return null;
    }

    @Override
    public Java2DRendererVideoComponent getComponent()
    {
        if (component == null)
        {
            component = new Java2DRendererVideoComponent();

            // Make sure to have non-zero height and width because actual video
            // frames may have not been processed yet.
            component.setSize(
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH,
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH);
        }

        return component;
    }

    @Override
    public void setBounds(Rectangle rect)
    {
    }

    @Override
    public boolean setComponent(Component comp)
    {
        return false;
    }

    /**
     * Sets the <tt>Format</tt> of the input to be processed by this
     * <tt>Renderer</tt>.
     *
     * @param format the <tt>Format</tt> to be set as the <tt>Format</tt> of the
     * input to be processed by this <tt>Renderer</tt>
     * @return the <tt>Format</tt> of the input to be processed by this
     * <tt>Renderer</tt> if the specified <tt>format</tt> is supported or
     * <tt>null</tt> if the specified <tt>format</tt> is not supported by this
     * <tt>Renderer</tt>. Typically, it is the supported input <tt>Format</tt>
     * which most closely matches the specified <tt>Format</tt>.
     */
    @Override
    public synchronized Format setInputFormat(Format format)
    {
        VideoFormat oldInputFormat = inputFormat;
        Format newInputFormat = super.setInputFormat(format);

        // Short-circuit because we will be calculating a lot and we do not want
        // to do that unless necessary.
        if (oldInputFormat == inputFormat)
            return newInputFormat;

        // Know the width and height of the input because we'll be depicting it
        // and we may want, for example, to report them as the preferred size of
        // our AWT Component. More importantly, know them because they determine
        // certain arguments to be passed to the native counterpart of this
        // JAWTRenderer i.e. handle.
        Dimension size = inputFormat.getSize();

        if (size == null)
        {
            width = height = 0;
        }
        else
        {
            width = size.width;
            height = size.height;
        }

        reflectInputFormatOnComponent();

        return newInputFormat;
    }

    /**
     * Sets properties of the AWT <tt>Component</tt> of this <tt>Renderer</tt>
     * which depend on the properties of the <tt>inputFormat</tt> of this
     * <tt>Renderer</tt>. Makes sure that the procedure is executed on the AWT
     * event dispatching thread because an AWT <tt>Component</tt>'s properties
     * (such as <tt>preferredSize</tt>) should be accessed in the AWT event
     * dispatching thread.
     */
    private void reflectInputFormatOnComponent()
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            reflectInputFormatOnComponentInEventDispatchThread();
        }
        else
        {
            SwingUtilities.invokeLater(
                    reflectInputFormatOnComponentInEventDispatchThread);
        }
    }

    /**
     * Sets properties of the AWT <tt>Component</tt> of this <tt>Renderer</tt>
     * which depend on the properties of the <tt>inputFormat</tt> of this
     * <tt>Renderer</tt>. The invocation is presumed to be performed on the AWT
     * event dispatching thread.
     */
    private void reflectInputFormatOnComponentInEventDispatchThread()
    {
        // Reflect the width and height of the input onto the prefSize of our
        // AWT Component (if necessary).
        if ((component != null) && (width > 0) && (height > 0))
        {
            Dimension prefSize = component.getPreferredSize();

            // Apart from the simplest of cases in which the component has no
            // prefSize, it is also necessary to reflect the width and height of
            // the input onto the prefSize when the ratio of the input is
            // different than the ratio of the prefSize. It may also be argued
            // that the component needs to know of the width and height of the
            // input if its prefSize is with the same ratio but is smaller.
            if ((prefSize == null)
                    || (prefSize.width < 1) || (prefSize.height < 1)
                    || !VideoLayout.areAspectRatiosEqual(
                            prefSize,
                            width, height)
                    || (prefSize.width < width) || (prefSize.height < height))
            {
                component.setPreferredSize(
                        new Dimension(width, height));
            }

            // If the component does not have a size, it looks strange given
            // that we know a prefSize for it. However, if the component has
            // already been added into a Container, the Container will dictate
            // the size as part of its layout logic.
            if (component.isPreferredSizeSet()
                    && (component.getParent() == null))
            {
                Dimension size = component.getSize();

                prefSize = component.getPreferredSize();
                if ((size.width < 1) || (size.height < 1)
                        || !VideoLayout.areAspectRatiosEqual(
                                size,
                                prefSize.width, prefSize.height))
                {
                    component.setSize(prefSize.width, prefSize.height);
                }
            }
        }
    }
}
