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
import java.util.*;
import java.util.List;

import javax.swing.*;

/**
 * Implements the <tt>LayoutManager</tt> which lays out the local and remote
 * videos in a video <tt>Call</tt>.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 */
public class VideoLayout
    extends FitLayout
{
    /**
     * The video canvas constraint.
     */
    public static final String CANVAS = "CANVAS";

    /**
     * The center remote video constraint.
     */
    public static final String CENTER_REMOTE = "CENTER_REMOTE";

    /**
     * The close local video constraint.
     */
    public static final String CLOSE_LOCAL_BUTTON = "CLOSE_LOCAL_BUTTON";

    /**
     * The east remote video constraint.
     */
    public static final String EAST_REMOTE = "EAST_REMOTE";

    /**
     * The horizontal gap between the <tt>Component</tt> being laid out by
     * <tt>VideoLayout</tt>.
     */
    private static final int HGAP = 10;

    /**
     * The local video constraint.
     */
    public static final String LOCAL = "LOCAL";

    /**
     * The ration between the local and the remote video.
     */
    private static final float LOCAL_TO_REMOTE_RATIO = 0.30f;

    /**
     * The video canvas.
     */
    private Component canvas;

    /**
     * The close local video button component.
     */
    private Component closeButton;

    /**
     * The indicator which determines whether this instance is dedicated to a
     * conference.
     */
    private final boolean conference;

    /**
     * The map of component constraints.
     */
    private final Map<Component, Object> constraints
        = new HashMap<Component, Object>();

    /**
     * The component containing the local video.
     */
    private Component local;

    /**
     * The x coordinate alignment of the remote video.
     */
    private float remoteAlignmentX = Component.CENTER_ALIGNMENT;

    /**
     * The list of <tt>Component</tt>s depicting remote videos.
     */
    private final List<Component> remotes = new LinkedList<Component>();

    /**
     * Creates an instance of <tt>VideoLayout</tt> by also indicating if this
     * video layout is dedicated to a conference interface.
     *
     * @param conference <tt>true</tt> if the new instance will be dedicated to
     * a conference; otherwise, <tt>false</tt>
     */
    public VideoLayout(boolean conference)
    {
        this.conference = conference;
    }

    /**
     * Adds the given component in this layout on the specified by name
     * position.
     *
     * @param name the constraint giving the position of the component in this
     * layout
     * @param comp the component to add
     */
    @Override
    public void addLayoutComponent(String name, Component comp)
    {
        super.addLayoutComponent(name, comp);

        synchronized (constraints)
        {
            constraints.put(comp, name);
        }

        if ((name == null) || name.equals(CENTER_REMOTE))
        {
            if (!remotes.contains(comp))
                remotes.add(comp);
            remoteAlignmentX = Component.CENTER_ALIGNMENT;
        }
        else if (name.equals(EAST_REMOTE))
        {
            if (!remotes.contains(comp))
                remotes.add(comp);
            remoteAlignmentX = Component.RIGHT_ALIGNMENT;
        }
        else if (name.equals(LOCAL))
            local = comp;
        else if (name.equals(CLOSE_LOCAL_BUTTON))
            closeButton = comp;
        else if (name.equals(CANVAS))
            canvas = comp;
    }

    /**
     * Determines whether the aspect ratio of a specific <tt>Dimension</tt> is
     * to be considered equal to the aspect ratio of specific <tt>width</tt> and
     * <tt>height</tt>.
     *
     * @param size the <tt>Dimension</tt> whose aspect ratio is to be compared
     * to the aspect ratio of <tt>width</tt> and <tt>height</tt>
     * @param width the width which defines in combination with <tt>height</tt>
     * the aspect ratio to be compared to the aspect ratio of <tt>size</tt>
     * @param height the height which defines in combination with <tt>width</tt>
     * the aspect ratio to be compared to the aspect ratio of <tt>size</tt>
     * @return <tt>true</tt> if the aspect ratio of <tt>size</tt> is to be
     * considered equal to the aspect ratio of <tt>width</tt> and
     * <tt>height</tt>; otherwise, <tt>false</tt>
     */
    public static boolean areAspectRatiosEqual(
            Dimension size,
            int width, int height)
    {
        if ((size.height == 0) || (height == 0))
            return false;
        else
        {
            double a = size.width / (double) size.height;
            double b = width / (double) height;
            double diff = a - b;

            return (-0.01 < diff) && (diff < 0.01);
        }
    }

    /**
     * Determines how may columns to use for the grid display of specific remote
     * visual/video <tt>Component</tt>s.
     *
     * @param remotes the remote visual/video <tt>Component</tt>s to be
     * displayed in a grid
     * @return the number of columns to use for the grid display of the
     * specified remote visual/video <tt>Component</tt>s
     */
    private int calculateColumnCount(List<Component> remotes)
    {
        int remoteCount = remotes.size();

        if (remoteCount == 1)
            return 1;
        else if ((remoteCount == 2) || (remoteCount == 4))
            return 2;
        else
            return 3;
    }

    /**
     * Returns the remote video component.
     *
     * @return the remote video component
     */
    @Override
    protected Component getComponent(Container parent)
    {
        return (remotes.size() == 1) ? remotes.get(0) : null;
    }

    /**
     * Returns the constraints for the given component.
     *
     * @param c the component for which constraints we're looking for
     * @return the constraints for the given component
     */
    public Object getComponentConstraints(Component c)
    {
        synchronized (constraints)
        {
            return constraints.get(c);
        }
    }

    /**
     * Returns the local video component.
     *
     * @return the local video component
     */
    public Component getLocal()
    {
        return local;
    }

    /**
     * Returns the local video close button.
     *
     * @return the local video close button
     */
    public Component getLocalCloseButton()
    {
        return closeButton;
    }

    /**
     * Lays out the specified <tt>Container</tt> (i.e. the <tt>Component</tt>s
     * it contains) in accord with the logic implemented by this
     * <tt>LayoutManager</tt>.
     *
     * @param parent the <tt>Container</tt> to lay out
     */
    @Override
    public void layoutContainer(Container parent)
    {
        /*
         * XXX The methods layoutContainer and preferredLayoutSize must be kept
         * in sync.
         */

        List<Component> visibleRemotes = new ArrayList<Component>();
        List<Component> remotes;
        Component local = getLocal();

        for (int i = 0; i < this.remotes.size(); i++)
        {
            if (this.remotes.get(i).isVisible())
                visibleRemotes.add(this.remotes.get(i));
        }

        /*
         * When there are multiple remote visual/video Components, the local one
         * will be displayed as if it is a remote one i.e. in the same grid, not
         * on top of a remote one. The same layout will be used when this
         * instance is dedicated to a telephony conference.
         */
        if (conference || ((visibleRemotes.size() > 1) && (local != null)))
        {
            remotes = new ArrayList<Component>();
            remotes.addAll(visibleRemotes);
            if (local != null)
                remotes.add(local);
        }
        else
            remotes = visibleRemotes;

        int remoteCount = remotes.size();
        Dimension parentSize = parent.getSize();

        if (!conference && (remoteCount == 1))
        {
            /*
             * If the videos are to be laid out as in a one-to-one call, the
             * remote video has to fill the parent and the local video will be
             * placed on top of the remote video. The remote video will be laid
             * out now and the local video will be laid out later/further
             * bellow.
             */
            super.layoutContainer(
                    parent,
                    (local == null)
                        ? Component.CENTER_ALIGNMENT
                        : remoteAlignmentX);
        }
        else if (remoteCount > 0)
        {
            int columns = calculateColumnCount(remotes);
            int columnsMinus1 = columns - 1;
            int rows = (remoteCount + columnsMinus1) / columns;
            int rowsMinus1 = rows - 1;
            Rectangle bounds
                = new Rectangle(
                        0,
                        0,
                        /*
                         * HGAP is the horizontal gap between the Components
                         * being laid out by this VideoLayout so the number of
                         * HGAPs will be with one less than the number of
                         * columns and that horizontal space cannot be allocated
                         * to the bounds of the Components.
                         */
                        (parentSize.width - (columnsMinus1 * HGAP)) / columns,
                        parentSize.height / rows);

            for (int i = 0; i < remoteCount; i++)
            {
                int column = i % columns;
                int row = i / columns;

                /*
                 * On the x axis, the first column starts at zero and each
                 * subsequent column starts relative to the end of its preceding
                 * column.
                 */
                if (column == 0)
                {
                    bounds.x = 0;
                    /*
                     * Eventually, there may be empty cells in the last row.
                     * Center the non-empty cells horizontally.
                     */
                    if (row == rowsMinus1)
                    {
                        int available = remoteCount - i;

                        if (available < columns)
                        {
                            bounds.x
                                = (parentSize.width
                                        - available * bounds.width
                                        - (available - 1) * HGAP)
                                    / 2;
                        }
                    }
                }
                else
                    bounds.x += (bounds.width + HGAP);
                bounds.y = row * bounds.height;

                super.layoutComponent(
                        remotes.get(i),
                        bounds,
                        Component.CENTER_ALIGNMENT,
                        Component.CENTER_ALIGNMENT);
            }
        }

        if (local == null)
        {
            /*
             * It is plain wrong to display a close button for the local video
             * if there is no local video.
             */
            if (closeButton != null)
                closeButton.setVisible(false);
        }
        else
        {
            /*
             * If the local visual/video Component is not displayed as if it is
             * a remote one, it will be placed on top of a remote one.
             */
            if (!remotes.contains(local))
            {
                Component remote0 = remotes.isEmpty() ? null : remotes.get(0);
                int localX;
                int localY;
                int height
                    = Math.round(parentSize.height * LOCAL_TO_REMOTE_RATIO);
                int width
                    = Math.round(parentSize.width * LOCAL_TO_REMOTE_RATIO);
                float alignmentX;

                /*
                 * XXX The remote Component being a JLabel is meant to signal
                 * that there is no remote video and the remote is the
                 * photoLabel.
                 */
                if ((remoteCount == 1) && (remote0 instanceof JLabel))
                {
                    localX = (parentSize.width - width) / 2;
                    localY = parentSize.height - height;
                    alignmentX = Component.CENTER_ALIGNMENT;
                }
                else
                {
                    localX = ((remote0 == null) ? 0 : remote0.getX()) + 5;
                    localY = parentSize.height - height - 5;
                    alignmentX = Component.LEFT_ALIGNMENT;
                }
                super.layoutComponent(
                        local,
                        new Rectangle(localX, localY, width, height),
                        alignmentX,
                        Component.BOTTOM_ALIGNMENT);
            }

            /* The closeButton has to be on top of the local video. */
            if (closeButton != null)
            {
                /*
                 * XXX We may be overwriting the visible property set by our
                 * client (who has initialized the close button) but it is wrong
                 * to display a close button for the local video if the local
                 * video is not visible.
                 */
                closeButton.setVisible(local.isVisible());

                super.layoutComponent(
                        closeButton,
                        new Rectangle(
                                local.getX()
                                    + local.getWidth()
                                    - closeButton.getWidth(),
                                local.getY(),
                                closeButton.getWidth(),
                                closeButton.getHeight()),
                        Component.CENTER_ALIGNMENT,
                        Component.CENTER_ALIGNMENT);
            }
        }

        /*
         * The video canvas will get the locations of the other components to
         * paint so it has to cover the parent completely.
         */
        if (canvas != null)
            canvas.setBounds(0, 0, parentSize.width, parentSize.height);
    }

    /**
     * Returns the preferred layout size for the given container.
     *
     * @param parent the container which preferred layout size we're looking for
     * @return a Dimension containing, the preferred layout size for the given
     * container
     */
    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        List<Component> visibleRemotes = new ArrayList<Component>();
        List<Component> remotes;
        Component local = getLocal();

        for (int i = 0; i < this.remotes.size(); i++)
        {
            if (this.remotes.get(i).isVisible())
                visibleRemotes.add(this.remotes.get(i));
        }

        /*
         * When there are multiple remote visual/video Components, the local one
         * will be displayed as if it is a remote one i.e. in the same grid, not
         * on top of a remote one. The same layout will be used when this
         * instance is dedicated to a telephony conference.
         */
        if (conference || ((visibleRemotes.size() > 1) && (local != null)))
        {
            remotes = new ArrayList<Component>();
            remotes.addAll(visibleRemotes);
            if (local != null)
                remotes.add(local);
        }
        else
            remotes = visibleRemotes;

        int remoteCount = remotes.size();
        Dimension prefLayoutSize;

        if (!conference && (remoteCount == 1))
        {
            /*
             * If the videos are to be laid out as in a one-to-one call, the
             * remote video has to fill the parent and the local video will be
             * placed on top of the remote video. The remote video will be laid
             * out now and the local video will be laid out later/further
             * bellow.
             */
            prefLayoutSize = super.preferredLayoutSize(parent);
        }
        else if (remoteCount > 0)
        {
            int columns = calculateColumnCount(remotes);
            int columnsMinus1 = columns - 1;
            int rows = (remoteCount + columnsMinus1) / columns;
            int i = 0;
            Dimension[] prefSizes = new Dimension[columns * rows];

            for (Component remote : remotes)
            {
                int column = columnsMinus1 - (i % columns);
                int row = i / columns;

                prefSizes[column + row * columns] = remote.getPreferredSize();

                i++;
                if (i >= remoteCount)
                    break;
            }

            int prefLayoutWidth = 0;

            for (int column = 0; column < columns; column++)
            {
                int prefColumnWidth = 0;

                for (int row = 0; row < rows; row++)
                {
                    Dimension prefSize = prefSizes[column + row * columns];

                    if (prefSize != null)
                        prefColumnWidth += prefSize.width;
                }
                prefColumnWidth /= rows;

                prefLayoutWidth += prefColumnWidth;
            }

            int prefLayoutHeight = 0;

            for (int row = 0; row < rows; row++)
            {
                int prefRowHeight = 0;

                for (int column = 0; column < columns; column++)
                {
                    Dimension prefSize = prefSizes[column + row * columns];

                    if (prefSize != null)
                        prefRowHeight = prefSize.height;
                }
                prefRowHeight /= columns;

                prefLayoutHeight += prefRowHeight;
            }

            prefLayoutSize
                = new Dimension(
                        prefLayoutWidth + columnsMinus1 * HGAP,
                        prefLayoutHeight);
        }
        else
            prefLayoutSize = null;

        if (local != null)
        {
            /*
             * If the local visual/video Component is not displayed as if it is
             * a remote one, it will be placed on top of a remote one. Then for
             * the purposes of the preferredLayoutSize method it needs to be
             * considered only if there is no remote video whatsoever.
             */
            if (!remotes.contains(local) && (prefLayoutSize == null))
            {
                Dimension prefSize = local.getPreferredSize();

                if (prefSize != null)
                {
                    int prefHeight
                        = Math.round(prefSize.height * LOCAL_TO_REMOTE_RATIO);
                    int prefWidth
                        = Math.round(prefSize.width * LOCAL_TO_REMOTE_RATIO);

                    prefLayoutSize = new Dimension(prefWidth, prefHeight);
                }
            }

            /*
             * The closeButton has to be on top of the local video.
             * Consequently, the preferredLayoutSize method does not have to
             * consider it. Well, maybe if does if the local video is smaller
             * than the closeButton... but that's just not cool anyway.
             */
        }

        /*
         * The video canvas will get the locations of the other components to
         * paint so it has to cover the parent completely. In other words, the
         * preferredLayoutSize method does not have to consider it.
         */

        if (prefLayoutSize == null)
            prefLayoutSize = super.preferredLayoutSize(parent);
        else if ((prefLayoutSize.height < 1) || (prefLayoutSize.width < 1))
        {
            prefLayoutSize.height = DEFAULT_HEIGHT_OR_WIDTH;
            prefLayoutSize.width = DEFAULT_HEIGHT_OR_WIDTH;
        }

        return prefLayoutSize;
    }

    /**
     * Removes the given component from this layout.
     *
     * @param comp the component to remove from the layout
     */
    @Override
    public void removeLayoutComponent(Component comp)
    {
        super.removeLayoutComponent(comp);

        synchronized (constraints)
        {
            constraints.remove(comp);
        }

        if (local == comp)
            local = null;
        else if (closeButton == comp)
            closeButton = null;
        else if (canvas == comp)
            canvas = null;
        else
            remotes.remove(comp);
    }
}
