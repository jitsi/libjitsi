/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.video;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.renderer.*;
import javax.swing.*;

import org.jitsi.impl.neomedia.jmfext.media.renderer.*;
import org.jitsi.util.*;
import org.jitsi.util.swing.*;

/**
 * Implements a <tt>VideoRenderer</tt> which uses JAWT to perform native
 * painting in an AWT or Swing <tt>Component</tt>.
 *
 * @author Lyubomir Marinov
 */
abstract public class AbstractAWTRenderer
    extends AbstractRenderer<VideoFormat>
    implements VideoRenderer
{
    /**
     * The default, initial height and width to set on the <tt>Component</tt>s
     * of <tt>HWRenderer</tt>s before video frames with actual sizes are
     * processed. Introduced to mitigate multiple failures to realize the actual
     * video frame size and/or to properly scale the visual/video
     * <tt>Component</tt>s.
     */
    protected static final int DEFAULT_COMPONENT_HEIGHT_OR_WIDTH = 16;

    /**
     * The <tt>Logger</tt> used by the <tt>AbstractAWTRenderer</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(
    		AbstractAWTRenderer.class);

    /**
     * The human-readable <tt>PlugIn</tt> name of the
     * <tt>AbstractAWTRenderer</tt> instances.
     */
    private final String pluginName;

    /**
     * The array of supported input formats.
     */
    private final Format[] supportedInputFormats;

    /**
     * The AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt> draws.
     */
    protected Component component = null;

    /**
     * The handle to the native counterpart of this
     * <tt>AbstractAWTRenderer</tt>.
     */
    protected long handle = 0;

    /**
     * The last known height of the input processed by this
     * <tt>AbstractAWTRenderer</tt>.
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
            public void run()
            {
                reflectInputFormatOnComponentInEventDispatchThread();
            }
        };

    /**
     * The last known width of the input processed by this
     * <tt>AbstractAWTRenderer</tt>.
     */
    private int width = 0;

    /**
     * Initializes a new <tt>AbstractAWTRenderer</tt> instance.
     * @param pluginName plugin name.
     * @param inputFormats input <tt>Format</tt> array.
     */
    public AbstractAWTRenderer(String pluginName, Format[] inputFormats)
    {
        this.pluginName = pluginName;
        supportedInputFormats = inputFormats.clone();
    }

    /**
     * Closes this <tt>PlugIn</tt> and releases the resources it has retained
     * during its execution. No more data will be accepted by this
     * <tt>PlugIn</tt> afterwards. A closed <tt>PlugIn</tt> can be reinstated by
     * calling <tt>open</tt> again.
     */
    abstract public void close();

    /**
     * Gets the region in the component of this <tt>VideoRenderer</tt> where the
     * video is rendered. <tt>AbstractAWTRenderer</tt> always uses the entire component
     * i.e. always returns <tt>null</tt>.
     *
     * @return the region in the component of this <tt>VideoRenderer</tt> where
     * the video is rendered; <tt>null</tt> if the entire component is used
     */
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
    abstract public Component getComponent();

    /**
     * Gets the handle to the native counterpart of this
     * <tt>AbstractAWTRenderer</tt>.
     *
     * @return the handle to the native counterpart of this
     * <tt>AbstractAWTRenderer</tt>
     */
    public long getHandle()
    {
        return handle;
    }

    /**
     * Gets the <tt>Object</tt> which synchronizes the access to the handle to
     * the native counterpart of this <tt>AbstractAWTRenderer</tt>.
     *
     * @return the <tt>Object</tt> which synchronizes the access to the handle
     * to the native counterpart of this <tt>AbstractAWTRenderer</tt>
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
    public String getName()
    {
        return pluginName;
    }

    /**
     * Gets the list of input <tt>Format</tt>s supported by this
     * <tt>Renderer</tt>.
     *
     * @return an array of <tt>Format</tt> elements which represent the input
     * <tt>Format</tt>s supported by this <tt>Renderer</tt>
     */
    public Format[] getSupportedInputFormats()
    {
        return supportedInputFormats.clone();
    }

    /**
     * Opens this <tt>PlugIn</tt> and acquires the resources that it needs to
     * operate. The input format of this <tt>Renderer</tt> has to be set before
     * <tt>open</tt> is called. Buffers should not be passed into this
     * <tt>PlugIn</tt> without first calling <tt>open</tt>.
     *
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    abstract public void open()
        throws ResourceUnavailableException;

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
    abstract public int process(Buffer buffer);

    /**
     * Sets properties of the AWT <tt>Component</tt> of this <tt>Renderer</tt>
     * which depend on the properties of the <tt>inputFormat</tt> of this
     * <tt>Renderer</tt>. Makes sure that the procedure is executed on the AWT
     * event dispatching thread because an AWT <tt>Component</tt>'s properties
     * (such as <tt>preferredSize</tt>) should be accessed in the AWT event
     * dispatching thread.
     */
    protected void reflectInputFormatOnComponent()
    {
        if (SwingUtilities.isEventDispatchThread())
            reflectInputFormatOnComponentInEventDispatchThread();
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
    protected void reflectInputFormatOnComponentInEventDispatchThread()
    {
        /*
         * Reflect the width and height of the input onto the prefSize of our
         * AWT Component (if necessary).
         */
        if ((component != null) && (width > 0) && (height > 0))
        {
            Dimension prefSize = component.getPreferredSize();

            /*
             * Apart from the simplest of cases in which the component has no
             * prefSize, it is also necessary to reflect the width and height of
             * the input onto the prefSize when the ratio of the input is
             * different than the ratio of the prefSize. It may also be argued
             * that the component needs to know of the width and height of the
             * input if its prefSize is with the same ratio but is smaller.
             */
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

            /*
             * If the component does not have a size, it looks strange given
             * that we know a prefSize for it. However, if the component has
             * already been added into a Container, the Container will dictate
             * the size as part of its layout logic.
             */
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
     * video is to be rendered. <tt>AbstractAWTRenderer</tt> always uses the entire
     * component and, consequently, the method does nothing.
     *
     * @param bounds the region in the component of this <tt>VideoRenderer</tt>
     * where the video is to be rendered; <tt>null</tt> if the entire component
     * is to be used
     */
    public void setBounds(Rectangle bounds) {}

    /**
     * Sets the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt> is
     * to draw. <tt>AbstractAWTRenderer</tt> cannot draw into any other AWT
     * <tt>Component</tt> but its own so it always returns <tt>false</tt>.
     *
     * @param component the AWT <tt>Component</tt> into which this
     * <tt>VideoRenderer</tt> is to draw
     * @return <tt>true</tt> if this <tt>VideoRenderer</tt> accepted the
     * specified <tt>component</tt> as the AWT <tt>Component</tt> into which it
     * is to draw; <tt>false</tt>, otherwise
     */
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

        /*
         * Short-circuit because we will be calculating a lot and we do not want
         * to do that unless necessary.
         */
        if (oldInputFormat == inputFormat)
            return newInputFormat;

        if (logger.isDebugEnabled())
        {
            logger.debug(
                    getClass().getName()
                        + " 0x" + Integer.toHexString(hashCode())
                        + " set to input in " + inputFormat);
        }

        /*
         * Know the width and height of the input because we'll be depicting it
         * and we may want, for example, to report them as the preferred size of
         * our AWT Component. More importantly, know them because they determine
         * certain arguments to be passed to the native counterpart of this
         * AbstractAWTRenderer i.e. handle.
         */
        Dimension size = inputFormat.getSize();

        if (size == null)
            width = height = 0;
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
    public void start() {}

    /**
     * Stops the rendering process.
     */
    public void stop() {}
}
