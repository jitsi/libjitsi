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
import java.lang.reflect.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.renderer.*;
import javax.swing.*;

import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.*;
import org.jitsi.util.OSUtils;
import org.jitsi.utils.*;
import org.jitsi.util.swing.*;
import org.jitsi.utils.logging.*;

/**
 * Implements a <tt>VideoRenderer</tt> which uses JAWT to perform native
 * painting in an AWT or Swing <tt>Component</tt>.
 *
 * @author Lyubomir Marinov
 */
public class JAWTRenderer
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
     * The <tt>Logger</tt> used by the <tt>JAWTRenderer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(JAWTRenderer.class);

    /**
     * The human-readable <tt>PlugIn</tt> name of the <tt>JAWTRenderer</tt>
     * instances.
     */
    private static final String PLUGIN_NAME = "JAWT Renderer";

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

    static
    {
        JNIUtils.loadLibrary("jnawtrenderer", JAWTRenderer.class);
    }

    static native void addNotify(long handle, Component component);

    /**
     * Closes the native counterpart of a <tt>JAWTRenderer</tt> specified by its
     * handle as returned by {@link #open(Component)} and rendering into a
     * specific AWT <tt>Component</tt>. Releases the resources which the
     * specified native counterpart has retained during its execution and its
     * handle is considered to be invalid afterwards.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>JAWTRenderer</tt> as returned by {@link #open(Component)} which is to
     * be closed
     * @param component the AWT <tt>Component</tt> into which the
     * <tt>JAWTRenderer</tt> and its native counterpart are drawing. The
     * platform-specific info of <tt>component</tt> is not guaranteed to be
     * valid.
     */
    private static native void close(long handle, Component component);

    /**
     * Opens a handle to a native counterpart of a <tt>JAWTRenderer</tt> which
     * is to draw into a specific AWT <tt>Component</tt>.
     *
     * @param component the AWT <tt>Component</tt> into which a
     * <tt>JAWTRenderer</tt> and the native counterpart to be opened are to
     * draw. The platform-specific info of <tt>component</tt> is not guaranteed
     * to be valid.
     * @return a handle to a native counterpart of a <tt>JAWTRenderer</tt> which
     * is to draw into the specified AWT <tt>Component</tt>
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    private static native long open(Component component)
        throws ResourceUnavailableException;

    /**
     * Paints a specific <tt>Component</tt> which is the AWT <tt>Component</tt>
     * of a <tt>JAWTRenderer</tt> specified by the handle to its native
     * counterpart.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>JAWTRenderer</tt> which is to draw into the specified AWT
     * <tt>Component</tt>
     * @param component the AWT <tt>Component</tt> into which the
     * <tt>JAWTRenderer</tt> and its native counterpart specified by
     * <tt>handle</tt> are to draw. The platform-specific info of
     * <tt>component</tt> is guaranteed to be valid only during the execution of
     * <tt>paint</tt>.
     * @param g the <tt>Graphics</tt> context into which the drawing is to be
     * performed
     * @param zOrder
     * @return <tt>true</tt> if the native counterpart of a
     * <tt>JAWTRenderer</tt> wants to continue receiving the <tt>paint</tt>
     * calls on the AWT <tt>Component</tt>; otherwise, false. For example, after
     * the native counterpart has been able to acquire the native handle of the
     * AWT <tt>Component</tt>, it may be able to determine when the native
     * handle needs painting without waiting for AWT to call <tt>paint</tt> on
     * the <tt>Component</tt>. In such a scenario, the native counterpart may
     * indicate with <tt>false</tt> that it does not need further <tt>paint</tt>
     * deliveries.
     */
    static native boolean paint(
            long handle,
            Component component, Graphics g,
            int zOrder);

    /**
     * Processes the data provided in a specific <tt>int</tt> array with a
     * specific offset and length and renders it to the output device
     * represented by a <tt>JAWTRenderer</tt> specified by the handle to it
     * native counterpart.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>JAWTRenderer</tt> to process the specified data and render it
     * @param component the <tt>AWT</tt> component into which the specified
     * <tt>JAWTRenderer</tt> and its native counterpart draw
     * @param data an <tt>int</tt> array which contains the data to be processed
     * and rendered
     * @param offset the index in <tt>data</tt> at which the data to be
     * processed and rendered starts
     * @param length the number of elements in <tt>data</tt> starting at
     * <tt>offset</tt> which represent the data to be processed and rendered
     * @param width the width of the video frame in <tt>data</tt>
     * @param height the height of the video frame in <tt>data</tt>
     * @return <tt>true</tt> if data has been successfully processed
     */
    static native boolean process(
            long handle,
            Component component,
            int[] data, int offset, int length,
            int width, int height);

    static native void removeNotify(long handle, Component component);

    private static native String sysctlbyname(String name);

    /**
     * The AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt> draws.
     */
    private Component component;

    /**
     * The handle to the native counterpart of this <tt>JAWTRenderer</tt>.
     */
    private long handle = 0;

    /**
     * The last known height of the input processed by this
     * <tt>JAWTRenderer</tt>.
     */
    private int height = 0;

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

    /**
     * The last known width of the input processed by this
     * <tt>JAWTRenderer</tt>.
     */
    private int width = 0;

    /**
     * Initializes a new <tt>JAWTRenderer</tt> instance.
     */
    public JAWTRenderer() {}

    /**
     * Closes this <tt>PlugIn</tt> and releases the resources it has retained
     * during its execution. No more data will be accepted by this
     * <tt>PlugIn</tt> afterwards. A closed <tt>PlugIn</tt> can be reinstated by
     * calling <tt>open</tt> again.
     */
    @Override
    public synchronized void close()
    {
        if (handle != 0)
        {
            close(handle, component);
            handle = 0;
        }
    }

    /**
     * Gets the region in the component of this <tt>VideoRenderer</tt> where the
     * video is rendered. <tt>JAWTRenderer</tt> always uses the entire component
     * i.e. always returns <tt>null</tt>.
     *
     * @return the region in the component of this <tt>VideoRenderer</tt> where
     * the video is rendered; <tt>null</tt> if the entire component is used
     */
    @Override
    public Rectangle getBounds()
    {
        return null;
    }

    /**
     * Gets the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt>
     * draws.
     *
     * @return the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt>
     * draws
     */
    @Override
    public synchronized Component getComponent()
    {
        if (component == null)
        {
            StringBuilder componentClassName = new StringBuilder();

            componentClassName.append(
                    "org.jitsi.impl.neomedia.jmfext.media.renderer.video"
                        + ".JAWTRenderer");
            if (OSUtils.IS_ANDROID)
                componentClassName.append("Android");
            componentClassName.append("VideoComponent");

            Throwable reflectiveOperationException = null;

            try
            {
                Class<?> componentClass
                    = Class.forName(componentClassName.toString());
                Constructor<?> componentConstructor
                    = componentClass.getConstructor(JAWTRenderer.class);

                component = (Component) componentConstructor.newInstance(this);
            }
            catch (ClassNotFoundException cnfe)
            {
                reflectiveOperationException = cnfe;
            }
            catch (IllegalAccessException iae)
            {
                reflectiveOperationException = iae;
            }
            catch (InstantiationException ie)
            {
                reflectiveOperationException = ie;
            }
            catch (InvocationTargetException ite)
            {
                reflectiveOperationException = ite;
            }
            catch (NoSuchMethodException nsme)
            {
                reflectiveOperationException = nsme;
            }
            if (reflectiveOperationException != null)
                throw new RuntimeException(reflectiveOperationException);

            // Make sure to have non-zero height and width because actual video
            // frames may have not been processed yet.
            component.setSize(
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH,
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH);
            // XXX The component has not been exposed outside of this instance
            // yet so it seems relatively safe to set its properties outside the
            // AWT event dispatching thread.
            reflectInputFormatOnComponentInEventDispatchThread();
        }
        return component;
    }

    /**
     * Gets the handle to the native counterpart of this <tt>JAWTRenderer</tt>.
     *
     * @return the handle to the native counterpart of this
     * <tt>JAWTRenderer</tt>
     */
    public long getHandle()
    {
        return handle;
    }

    /**
     * Gets the <tt>Object</tt> which synchronizes the access to the handle to
     * the native counterpart of this <tt>JAWTRenderer</tt>.
     *
     * @return the <tt>Object</tt> which synchronizes the access to the handle
     * to the native counterpart of this <tt>JAWTRenderer</tt>
     */
    public Object getHandleLock()
    {
        return this;
    }

    /**
     * Gets the human-readable name of this <tt>PlugIn</tt>.
     *
     * @return the human-readable name of this <tt>PlugIn</tt>
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Gets the list of input <tt>Format</tt>s supported by this
     * <tt>Renderer</tt>.
     *
     * @return an array of <tt>Format</tt> elements which represent the input
     * <tt>Format</tt>s supported by this <tt>Renderer</tt>
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        return SUPPORTED_INPUT_FORMATS.clone();
    }

    /**
     * Opens this <tt>PlugIn</tt> and acquires the resources that it needs to
     * operate. The input format of this <tt>Renderer</tt> has to be set before
     * <tt>open</tt> is called. Buffers should not be passed into this
     * <tt>PlugIn</tt> without first calling <tt>open</tt>.
     *
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    @Override
    public void open()
        throws ResourceUnavailableException
    {
        boolean addNotify;
        final Component component;

        synchronized (this)
        {
            if (handle == 0)
            {
                // If this JAWTRenderer gets opened after its visual/video
                // Component has been created, send addNotify to the Component
                // once this JAWTRenderer gets opened so that the Component may
                // use the handle if it needs to.
                addNotify
                    = (this.component != null)
                        && (this.component.getParent() != null);
                component = getComponent();

                handle = open(component);
                if (handle == 0)
                {
                    throw new ResourceUnavailableException(
                            "Failed to open the native JAWTRenderer.");
                }
            }
            else
            {
                addNotify = false;
                component = null;
            }
        }
        // The #addNotify() invocation, if any, should happen outside the
        // synchronized block in order to avoid a deadlock.
        if (addNotify)
        {
            SwingUtilities.invokeLater(
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            component.addNotify();
                        }
                    });
        }
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
            return BUFFER_PROCESSED_OK;

        int bufferLength = buffer.getLength();

        if (bufferLength == 0)
            return BUFFER_PROCESSED_OK;

        Format format = buffer.getFormat();

        if ((format != null)
                && (format != this.inputFormat)
                && !format.equals(this.inputFormat)
                && (setInputFormat(format) == null))
        {
            return BUFFER_PROCESSED_FAILED;
        }

        if (handle == 0)
            return BUFFER_PROCESSED_FAILED;
        else
        {
            Dimension size = null;

            if (format != null)
                size = ((VideoFormat) format).getSize();
            if (size == null)
            {
                size = this.inputFormat.getSize();
                if (size == null)
                    return BUFFER_PROCESSED_FAILED;
            }

            // XXX If the size of the video frame to be displayed is tiny enough
            // to crash sws_scale, then it may cause issues with other
            // functionality as well. Stay on the safe side.
            if ((size.width >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                    && (size.height >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH))
            {
                Component component = getComponent();
                boolean repaint
                    = process(
                            handle,
                            component,
                            (int[]) buffer.getData(),
                            buffer.getOffset(),
                            bufferLength,
                            size.width,
                            size.height);

                if (repaint)
                    component.repaint();
            }

            return BUFFER_PROCESSED_OK;
        }
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

    /**
     * Sets the region in the component of this <tt>VideoRenderer</tt> where the
     * video is to be rendered. <tt>JAWTRenderer</tt> always uses the entire
     * component and, consequently, the method does nothing.
     *
     * @param bounds the region in the component of this <tt>VideoRenderer</tt>
     * where the video is to be rendered; <tt>null</tt> if the entire component
     * is to be used
     */
    @Override
    public void setBounds(Rectangle bounds) {}

    /**
     * Sets the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt> is
     * to draw. <tt>JAWTRenderer</tt> cannot draw into any other AWT
     * <tt>Component</tt> but its own so it always returns <tt>false</tt>.
     *
     * @param component the AWT <tt>Component</tt> into which this
     * <tt>VideoRenderer</tt> is to draw
     * @return <tt>true</tt> if this <tt>VideoRenderer</tt> accepted the
     * specified <tt>component</tt> as the AWT <tt>Component</tt> into which it
     * is to draw; <tt>false</tt>, otherwise
     */
    @Override
    public boolean setComponent(Component component)
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

        if (logger.isDebugEnabled())
        {
            logger.debug(
                    getClass().getName()
                        + " 0x" + Integer.toHexString(hashCode())
                        + " set to input in " + inputFormat);
        }

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
     * Starts the rendering process. Begins rendering any data available in the
     * internal buffers of this <tt>Renderer</tt>.
     */
    @Override
    public void start() {}

    /**
     * Stops the rendering process.
     */
    @Override
    public void stop() {}
}
