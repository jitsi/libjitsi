/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.video;

import java.awt.*;

import org.jitsi.impl.neomedia.codec.video.*;

/**
 * Implements an AWT <tt>Component</tt> in which <tt>JAWTRenderer</tt> paints.
 *
 * @author Lyubomir Marinov
 */
public class JAWTRendererVideoComponent
    extends AbstractAWTRendererVideoComponent
{
    /**
     * The serial version UID of the <tt>JAWTRendererVideoComponent</tt> class
     * defined to silence a serialization compile-time warning.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Initializes a new <tt>JAWTRendererVideoComponent</tt> instance.
     *
     * @param renderer
     */
    public JAWTRendererVideoComponent(JAWTRenderer renderer)
    {
        super(renderer);
    }

    /**
     * Paint this <tt>Component</tt> in the native counterpart of its
     * associated <tt>AbstractAWTRenderer</tt>.
     * @param handle the handle to the native counterpart of a
     * <tt>AbstractAWTRenderer</tt> which is to draw into the specified AWT
     * <tt>Component</tt>
     * @param component the AWT <tt>Component</tt> into which the
     * <tt>JAWTRenderer</tt> and its native counterpart specified by
     * <tt>handle</tt> are to draw. The platform-specific info of
     * <tt>component</tt> is guaranteed to be valid only during the execution of
     * <tt>paint</tt>.
     * @param g the <tt>Graphics</tt> context into which the drawing is to be
     * performed
     * @param zOrder
     */
    protected boolean doPaint(long handle, Component component,
            Graphics g, int zOrder)
    {
        return JAWTRenderer.paint(handle, component, g, zOrder);
    }
}
