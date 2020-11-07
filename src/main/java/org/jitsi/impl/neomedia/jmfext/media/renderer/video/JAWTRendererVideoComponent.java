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

import org.jitsi.impl.neomedia.codec.video.*;
import org.jitsi.util.*;

/**
 * Implements an AWT <tt>Component</tt> in which <tt>JAWTRenderer</tt> paints.
 *
 * @author Lyubomir Marinov
 */
public class JAWTRendererVideoComponent
    extends Canvas
{
    /**
     * The serial version UID of the <tt>JAWTRendererVideoComponent</tt> class
     * defined to silence a serialization compile-time warning.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The <tt>JAWTRenderer</tt> which paints in this
     * <tt>JAWTRendererVideoComponent</tt>.
     */
    protected final JAWTRenderer renderer;

    /**
     * The indicator which determines whether the native counterpart of this
     * <tt>JAWTRenderer</tt> wants <tt>paint</tt> calls on its AWT
     * <tt>Component</tt> to be delivered. For example, after the native
     * counterpart has been able to acquire the native handle of the AWT
     * <tt>Component</tt>, it may be able to determine when the native
     * handle needs painting without waiting for AWT to call <tt>paint</tt>
     * on the <tt>Component</tt>. In such a scenario, the native counterpart
     * may indicate with <tt>false</tt> that it does not need further
     * <tt>paint</tt> deliveries.
     */
    private boolean wantsPaint = true;

    /**
     * Initializes a new <tt>JAWTRendererVideoComponent</tt> instance.
     *
     * @param renderer
     */
    public JAWTRendererVideoComponent(JAWTRenderer renderer)
    {
        this.renderer = renderer;
    }

    /**
     * Overrides {@link Component#addNotify()} to reset the indicator which
     * determines whether the native counterpart of this <tt>JAWTRenderer</tt>
     * wants <tt>paint</tt> calls on its AWT <tt>Component</tt> to be delivered.
     */
    @Override
    public void addNotify()
    {
        super.addNotify();

        wantsPaint = true;

        synchronized (getHandleLock())
        {
            long handle;

            if ((handle = getHandle()) != 0)
            {
                try
                {
                    JAWTRenderer.addNotify(handle, this);
                }
                catch (UnsatisfiedLinkError uler)
                {
                    // The function/method has been introduced in a revision of
                    // the JAWTRenderer API and may not be available in the
                    // binary.
                }
                // The first task of the method paint(Graphics) is to attach to
                // the native view/widget/window of this Canvas. The sooner, the
                // better. Technically, it should be possible to do it
                // immediately after the method addNotify().
                try
                {
                    paint(null);

                    if (OSUtils.IS_MAC)
                    {
                        // XXX After JAWT is told about the CALayer via
                        // assignment to JAWT_SurfaceLayers, JAWT does not
                        // automatically place the CALayer in the necessary
                        // location and no video is drawn. A resize was observed
                        // to fix the two issues.
                        int x = getX(), y = getY();
                        int width = getWidth(), height = getHeight();

                        setBounds(
                                x - SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                y - SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                width + SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH,
                                height + SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH);
                        setBounds(x, y, width, height);
                    }
                }
                finally
                {
                    // Well, we explicitly invoked the method paint(Graphics)
                    // which is kind of extraordinary.
                    wantsPaint = true;
                }
            }
        }
    }

    /**
     * Gets the handle of the native counterpart of the
     * <tt>JAWTRenderer</tt> which paints in this
     * <tt>AWTVideoComponent</tt>.
     *
     * @return the handle of the native counterpart of the
     * <tt>JAWTRenderer</tt> which paints in this <tt>AWTVideoComponent</tt>
     */
    protected long getHandle()
    {
        return renderer.getHandle();
    }

    /**
     * Gets the synchronization lock which protects the access to the
     * <tt>handle</tt> property of this <tt>AWTVideoComponent</tt>.
     *
     * @return the synchronization lock which protects the access to the
     * <tt>handle</tt> property of this <tt>AWTVideoComponent</tt>
     */
    protected Object getHandleLock()
    {
        return renderer.getHandleLock();
    }

    /**
     * Overrides {@link Canvas#paint(Graphics)} to paint this <tt>Component</tt>
     * in the native counterpart of its associated <tt>JAWTRenderer</tt>.
     */
    @Override
    public void paint(Graphics g)
    {
        // XXX If the size of this Component is tiny enough to crash sws_scale,
        // then it may cause issues with other functionality as well. Stay on
        // the safe side.
        if (wantsPaint
                && getWidth() >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH
                && getHeight() >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
        {
            synchronized (getHandleLock())
            {
                long handle;

                if ((handle = getHandle()) != 0)
                {
                    Container parent = getParent();
                    int zOrder;

                    if (parent == null)
                    {
                        zOrder = -1;
                    }
                    else
                    {
                        zOrder = parent.getComponentZOrder(this);
                        // CALayer is used in the implementation of JAWTRenderer
                        // on OS X and its zPosition is the reverse of AWT's
                        // componentZOrder (in terms of what appears above and
                        // bellow). 
                        if (OSUtils.IS_MAC && (zOrder != -1))
                            zOrder = parent.getComponentCount() - 1 - zOrder;
                    }

                    wantsPaint = JAWTRenderer.paint(handle, this, g, zOrder);
                }
            }
        }
    }

    /**
     * Overrides {@link Component#removeNotify()} to reset the indicator which
     * determines whether the native counterpart of this <tt>JAWTRenderer</tt>
     * wants <tt>paint</tt> calls on its AWT <tt>Component</tt> to be delivered.
     */
    @Override
    public void removeNotify()
    {
        synchronized (getHandleLock())
        {
            long handle;

            if ((handle = getHandle()) != 0)
            {
                try
                {
                    JAWTRenderer.removeNotify(handle, this);
                }
                catch (UnsatisfiedLinkError uler)
                {
                    // The function/method has been introduced in a revision of
                    // the JAWTRenderer API and may not be available in the
                    // binary.
                }
            }
        }

        // In case the associated JAWTRenderer has said that it does not want
        // paint events/notifications, ask it again next time because the native
        // handle of this Canvas may be recreated.
        wantsPaint = true;

        super.removeNotify();
    }

    /**
     * Overrides {@link Canvas#update(Graphics)} to skip the filling with the
     * background color in order to prevent flickering.
     */
    @Override
    public void update(Graphics g)
    {
        synchronized (getHandleLock())
        {
            if (!wantsPaint || getHandle() == 0)
            {
                super.update(g);
                return;
            }
        }

        // Skip the filling with the background color because it causes
        // flickering.
        paint(g);
    }
}
