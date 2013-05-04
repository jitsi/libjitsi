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
import javax.swing.*;

import org.jitsi.service.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.util.*;

/**
 * Implements a <tt>VideoRenderer</tt> which uses hardware to perform native
 * painting in an AWT or Swing <tt>Component</tt>.
 *
 * @author Lyubomir Marinov
 */
public class HWRenderer
    extends AbstractAWTRenderer
{
    /**
     * The <tt>Logger</tt> used by the <tt>HWRenderer</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(HWRenderer.class);

    /**
     * The human-readable <tt>PlugIn</tt> name of the <tt>HWRenderer</tt>
     * instances.
     */
    private static final String PLUGIN_NAME = "HW Renderer";

    /**
     * The array of supported input formats.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /* some static initialization */
    static
    {
    	try
    	{
    		System.loadLibrary("jnhwrenderer");
    	}
    	catch(Throwable t)
    	{
    		System.err.println("Error loading jnhwrenderer: " + t.toString());
    	}
        
        boolean h264_supported = FFmpeg.hw_decoder_is_supported(
                FFmpeg.CODEC_ID_H264);
        boolean h263_supported =FFmpeg.hw_decoder_is_supported(
                FFmpeg.CODEC_ID_H263);
        int nb = h264_supported && h263_supported ? 2 :
            h264_supported || h263_supported ? 1 : 0;
        int idx = 0;
        
        SUPPORTED_INPUT_FORMATS = new Format[nb];

        if(h264_supported)
        {
            SUPPORTED_INPUT_FORMATS[idx] = new VideoFormat(
                 Constants.FFMPEG_H264, null, -1, AVFrame.class, 
                 Format.NOT_SPECIFIED);
            idx++;
        }
        
        if(h263_supported)
        {
            SUPPORTED_INPUT_FORMATS[idx] = new VideoFormat(
                 Constants.FFMPEG_H263, null, -1, AVFrame.class, 
                 Format.NOT_SPECIFIED);
            idx++;
        }
    }
  
    /**
     * Closes the native counterpart of a <tt>HWRenderer</tt> specified by its
     * handle as returned by {@link #open(Component)} and rendering into a
     * specific AWT <tt>Component</tt>. Releases the resources which the
     * specified native counterpart has retained during its execution and its
     * handle is considered to be invalid afterwards.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>HWRenderer</tt> as returned by {@link #open(Component)} which is to
     * be closed
     * @param component the AWT <tt>Component</tt> into which the
     * <tt>HWRenderer</tt> and its native counterpart are drawing. The
     * platform-specific info of <tt>component</tt> is not guaranteed to be
     * valid.
     */
    private static native void close(long handle, Component component);

    /**
     * Opens a handle to a native counterpart of a <tt>HWRenderer</tt> which
     * is to draw into a specific AWT <tt>Component</tt>.
     *
     * @param component the AWT <tt>Component</tt> into which a
     * <tt>HWRenderer</tt> and the native counterpart to be opened are to
     * draw. The platform-specific info of <tt>component</tt> is not guaranteed
     * to be valid.
     * @return a handle to a native counterpart of a <tt>HWRenderer</tt> which
     * is to draw into the specified AWT <tt>Component</tt>
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    private static native long open(Component component)
        throws ResourceUnavailableException;

    /**
     * Paints a specific <tt>Component</tt> which is the AWT <tt>Component</tt>
     * of a <tt>HWRenderer</tt> specified by the handle to its native
     * counterpart.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>HWRenderer</tt> which is to draw into the specified AWT
     * <tt>Component</tt>
     * @param component the AWT <tt>Component</tt> into which the
     * <tt>HWRenderer</tt> and its native counterpart specified by
     * <tt>handle</tt> are to draw. The platform-specific info of
     * <tt>component</tt> is guaranteed to be valid only during the execution of
     * <tt>paint</tt>.
     * @param g the <tt>Graphics</tt> context into which the drawing is to be
     * performed
     * @param zOrder
     * @return <tt>true</tt> if the native counterpart of a
     * <tt>HWRenderer</tt> wants to continue receiving the <tt>paint</tt>
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
     * represented by a <tt>HWRenderer</tt> specified by the handle to it
     * native counterpart.
     *
     * @param handle the handle to the native counterpart of a
     * <tt>HWRenderer</tt> to process the specified data and render it
     * @param component the <tt>AWT</tt> component into which the specified
     * <tt>HWRenderer</tt> and its native counterpart draw
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
            long data, int offset, int length,
            int width, int height);

    /**
     * Initializes a new <tt>HWRenderer</tt> instance.
     */
    public HWRenderer()
    {
        super(PLUGIN_NAME, SUPPORTED_INPUT_FORMATS);
    }

    /**
     * Closes this <tt>PlugIn</tt> and releases the resources it has retained
     * during its execution. No more data will be accepted by this
     * <tt>PlugIn</tt> afterwards. A closed <tt>PlugIn</tt> can be reinstated by
     * calling <tt>open</tt> again.
     */
    public synchronized void close()
    {
        if (handle != 0)
        {
            close(handle, component);
            handle = 0;
        }
    }

    /**
     * Gets the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt>
     * draws.
     *
     * @return the AWT <tt>Component</tt> into which this <tt>VideoRenderer</tt>
     * draws
     */
    public synchronized Component getComponent()
    {
        if (component == null)
        {
            component = new HWRendererVideoComponent(this);

            /*
             * Make sure to have non-zero height and width because actual video
             * frames may have not been processed yet.
             */
            component.setSize(
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH,
                    DEFAULT_COMPONENT_HEIGHT_OR_WIDTH);
            /*
             * XXX The component has not been exposed outside of this instance
             * yet so it seems relatively safe to set its properties outside the
             * AWT event dispatching thread.
             */
            reflectInputFormatOnComponentInEventDispatchThread();
        }
        return component;
    }

    /**
     * Opens this <tt>PlugIn</tt> and acquires the resources that it needs to
     * operate. The input format of this <tt>Renderer</tt> has to be set before
     * <tt>open</tt> is called. Buffers should not be passed into this
     * <tt>PlugIn</tt> without first calling <tt>open</tt>.
     *
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    public void open()
        throws ResourceUnavailableException
    {
        boolean addNotify;
        final Component component;

        synchronized (this)
        {
            if (handle == 0)
            {
                /*
                 * If this HWRenderer gets opened after its visual/video
                 * Component has been created, send addNotify to the Component
                 * once this HWRenderer gets opened so that the Component may
                 * use the handle if it needs to.
                 */
                addNotify
                    = (this.component != null)
                        && (this.component.getParent() != null);
                component = getComponent();

                handle = open(component);
                if (handle == 0)
                {
                    throw new ResourceUnavailableException(
                            "Failed to open the native HWRenderer.");
                }
            }
            else
            {
                addNotify = false;
                component = null;
            }
        }
        /*
         * The #addNotify() invocation, if any, should happen outside the
         * synchronized block in order to avoid a deadlock.
         */
        if (addNotify)
        {
            SwingUtilities.invokeLater(
                    new Runnable()
                    {
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
    public synchronized int process(Buffer buffer)
    {
        if (buffer.isDiscard())
            return BUFFER_PROCESSED_OK;

        int bufferLength = buffer.getLength();

        /* in case of hardware decoding bufferLength is 0 so do not check
         * buffer length.
     	 */

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

            Component component = getComponent();
            boolean repaint
                = process(
                        handle,
                        component,
                        ((AVFrame)buffer.getData()).getPtr(),
                        buffer.getOffset(),
                        bufferLength,
                        size.width,
                        size.height);

                if (repaint)
                    component.repaint();
            return BUFFER_PROCESSED_OK;
        }
    }
}
