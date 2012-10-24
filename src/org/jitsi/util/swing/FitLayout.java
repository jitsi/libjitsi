/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.swing;

import java.awt.*;

import javax.swing.*;

/**
 * Represents a <tt>LayoutManager</tt> which centers the first
 * <tt>Component</tt> within its <tt>Container</tt> and, if the preferred size
 * of the <tt>Component</tt> is larger than the size of the <tt>Container</tt>,
 * scales the former within the bounds of the latter while preserving the aspect
 * ratio. <tt>FitLayout</tt> is appropriate for <tt>Container</tt>s which
 * display a single image or video <tt>Component</tt> in its entirety for which
 * preserving the aspect ratio is important.
 * 
 * @author Lyubomir Marinov
 */
public class FitLayout
    implements LayoutManager
{

    /**
     * {@inheritDoc}
     *
     * Does nothing because this <tt>LayoutManager</tt> lays out only the first
     * <tt>Component</tt> of the parent <tt>Container</tt> and thus doesn't need
     * any <tt>String</tt> associations.
     */
    public void addLayoutComponent(String name, Component comp)
    {
    }

    /**
     * Gets the first <tt>Component</tt> of a specific <tt>Container</tt> if
     * there is such a <tt>Component</tt>.
     *
     * @param parent the <tt>Container</tt> to retrieve the first
     * <tt>Component</tt> of
     * @return the first <tt>Component</tt> of a specific <tt>Container</tt> if
     * there is such a <tt>Component</tt>; otherwise, <tt>null</tt>
     */
    protected Component getComponent(Container parent)
    {
        Component[] components = parent.getComponents();

        return (components.length > 0) ? components[0] : null;
    }

    protected void layoutComponent(
            Component component,
            Rectangle bounds,
            float alignmentX, float alignmentY)
    {
        Dimension componentSize;

        /*
         * XXX The following is a quick and dirty hack for the purposes of video
         * conferencing which adds transparent JPanels to VideoContainer and
         * does not want them fitted because they contains VideoContainers
         * themselves and the videos get fitted in them.
         */
        if ((component instanceof JPanel)
                && !component.isOpaque()
                && (((Container) component).getComponentCount() > 1))
        {
            componentSize = bounds.getSize();
        }
        else
        {
            componentSize = component.getPreferredSize();

            boolean scale = false;
            double widthRatio;
            double heightRatio;

            if ((componentSize.width != bounds.width)
                    && (componentSize.width > 0))
            {
                scale = true;
                widthRatio = bounds.width / (double) componentSize.width;
            }
            else
                widthRatio = 1;
            if ((componentSize.height != bounds.height)
                    && (componentSize.height > 0))
            {
                scale = true;
                heightRatio = bounds.height / (double) componentSize.height;
            }
            else
                heightRatio = 1;
            if (scale)
            {
                double ratio = Math.min(widthRatio, heightRatio);

                componentSize.width = (int) (componentSize.width * ratio);
                componentSize.height = (int) (componentSize.height * ratio);
            }
        }

        // Respect the maximumSize of the component.
        if (component.isMaximumSizeSet())
        {
            Dimension maximumSize = component.getMaximumSize();

            if (componentSize.width > maximumSize.width)
                componentSize.width = maximumSize.width;
            if (componentSize.height > maximumSize.height)
                componentSize.height = maximumSize.height;
        }

        /*
         * Why would one fit a Component into a rectangle with zero width and
         * height?
         */
        if (componentSize.height < 1)
            componentSize.height = 1;
        if (componentSize.width < 1)
            componentSize.width = 1;

        component.setBounds(
                bounds.x
                    + Math.round(
                        (bounds.width - componentSize.width) * alignmentX),
                bounds.y
                    + Math.round(
                        (bounds.height - componentSize.height) * alignmentY),
                componentSize.width,
                componentSize.height);
    }

    /*
     * Scales the first Component if its preferred size is larger than the size
     * of its parent Container in order to display the Component in its entirety
     * and then centers it within the display area of the parent.
     */
    public void layoutContainer(Container parent)
    {
        layoutContainer(parent, Component.CENTER_ALIGNMENT);
    }

    protected void layoutContainer(Container parent, float componentAlignmentX)
    {
        Component component = getComponent(parent);

        if (component != null)
        {
            layoutComponent(
                    component,
                    new Rectangle(parent.getSize()),
                    componentAlignmentX, Component.CENTER_ALIGNMENT);
        }
    }

    /*
     * Since this LayoutManager lays out only the first Component of the
     * specified parent Container, the minimum size of the Container is the
     * minimum size of the mentioned Component.
     */
    public Dimension minimumLayoutSize(Container parent)
    {
        Component component = getComponent(parent);

        return
            (component != null)
                ? component.getMinimumSize()
                : new Dimension(0, 0);
    }

    /**
     * {@inheritDoc}
     *
     * Since this <tt>LayoutManager</tt> lays out only the first
     * <tt>Component</tt> of the specified parent <tt>Container</tt>, the
     * preferred size of the <tt>Container</tt> is the preferred size of the
     * mentioned <tt>Component</tt>.
     */
    public Dimension preferredLayoutSize(Container parent)
    {
        Component component = getComponent(parent);

        return
            (component != null)
                ? component.getPreferredSize()
                : new Dimension(0, 0);
    }

    /**
     * {@inheritDoc}
     *
     * Does nothing because this <tt>LayoutManager</tt> lays out only the first
     * <tt>Component</tt> of the parent <tt>Container</tt> and thus doesn't need
     * any <tt>String</tt> associations.
     */
    public void removeLayoutComponent(Component comp)
    {
    }
}
