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
     * The default height and width to be used by <tt>FitLayout</tt> and its
     * extenders in order to avoid falling back to zero height and/or width.
     * Introduced to mitigate issues arising from the fact that a
     * <tt>Component</tt> zero height and/or width.
     */
    protected static final int DEFAULT_HEIGHT_OR_WIDTH = 16;

    /**
     * {@inheritDoc}
     *
     * Does nothing because this <tt>LayoutManager</tt> lays out only the first
     * <tt>Component</tt> of the parent <tt>Container</tt> and thus doesn't need
     * any <tt>String</tt> associations.
     */
    public void addLayoutComponent(String name, Component comp) {}

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
        Dimension size;

        /*
         * XXX The following (mostly) represents a quick and dirty hack for the
         * purposes of video conferencing which adds transparent JPanels to
         * VideoContainer and does not want them fitted because they contain
         * VideoContainers themselves and the videos get fitted in them.
         */
        if (((component instanceof JPanel)
                    && !component.isOpaque()
                    && (((Container) component).getComponentCount() > 1))
                || (component instanceof VideoContainer)
                /*
                 * If the specified component does not have a preferredSize, we
                 * cannot know its aspect ratio and we are left with no choice
                 * but to stretch it within the complete bounds.
                 */
                || ((size = component.getPreferredSize()) == null))
        {
            size = bounds.getSize();
        }
        else
        {
            boolean scale = false;
            double widthRatio;
            double heightRatio;

            if ((size.width != bounds.width) && (size.width > 0))
            {
                scale = true;
                widthRatio = bounds.width / (double) size.width;
            }
            else
                widthRatio = 1;
            if ((size.height != bounds.height) && (size.height > 0))
            {
                scale = true;
                heightRatio = bounds.height / (double) size.height;
            }
            else
                heightRatio = 1;
            if (scale)
            {
                double ratio = Math.min(widthRatio, heightRatio);

                size.width = (int) (size.width * ratio);
                size.height = (int) (size.height * ratio);
            }
        }

        // Respect the maximumSize of the component.
        if (component.isMaximumSizeSet())
        {
            Dimension maxSize = component.getMaximumSize();

            if (size.width > maxSize.width)
                size.width = maxSize.width;
            if (size.height > maxSize.height)
                size.height = maxSize.height;
        }

        /*
         * Why would one fit a Component into a rectangle with zero width and
         * height?
         */
        if (size.height < 1)
            size.height = 1;
        if (size.width < 1)
            size.width = 1;

        component.setBounds(
                bounds.x + Math.round((bounds.width - size.width) * alignmentX),
                bounds.y
                    + Math.round((bounds.height - size.height) * alignmentY),
                size.width,
                size.height);
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
                : new Dimension(
                        DEFAULT_HEIGHT_OR_WIDTH,
                        DEFAULT_HEIGHT_OR_WIDTH);
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
                : new Dimension(
                        DEFAULT_HEIGHT_OR_WIDTH,
                        DEFAULT_HEIGHT_OR_WIDTH);
    }

    /**
     * {@inheritDoc}
     *
     * Does nothing because this <tt>LayoutManager</tt> lays out only the first
     * <tt>Component</tt> of the parent <tt>Container</tt> and thus doesn't need
     * any <tt>String</tt> associations.
     */
    public void removeLayoutComponent(Component comp) {}
}
