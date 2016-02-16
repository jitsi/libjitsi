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
import java.awt.event.*;
import java.beans.*;

import javax.swing.*;

/**
 * Implements a <tt>Container</tt> for video/visual <tt>Component</tt>s.
 * <tt>VideoContainer</tt> uses {@link VideoLayout} to layout the video/visual
 * <tt>Component</tt>s it contains. A specific <tt>Component</tt> can be
 * displayed by default at {@link VideoLayout#CENTER_REMOTE}.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 */
public class VideoContainer
    extends TransparentPanel
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The default background color of <tt>VideoContainer</tt> when it contains
     * <tt>Component</tt> instances other than {@link #noVideoComponent}.
     */
    public static final Color DEFAULT_BACKGROUND_COLOR = Color.BLACK;

    private static final String PREFERRED_SIZE_PROPERTY_NAME = "preferredSize";

    /**
     * The number of times that <tt>add</tt> or <tt>remove</tt> methods are
     * currently being executed on this instance. Decreases the number of
     * unnecessary invocations to {@link #doLayout()}, {@link #repaint()} and
     * {@link #validate()}.
     */
    private int inAddOrRemove;

    /**
     * The <tt>Component</tt> to be displayed by this <tt>VideoContainer</tt>
     * at {@link VideoLayout#CENTER_REMOTE} when no other <tt>Component</tt> has
     * been added to it to be displayed there. For example, the avatar of the
     * remote peer may be displayed in place of the remote video when the remote
     * video is not available.
     */
    private final Component noVideoComponent;

    private final PropertyChangeListener propertyChangeListener
        = new PropertyChangeListener()
        {
            public void propertyChange(PropertyChangeEvent ev)
            {
                VideoContainer.this.propertyChange(ev);
            }
        };

    private final Object syncRoot = new Object();

    /**
     * The indicator which determines whether this instance is aware that
     * {@link #doLayout()}, {@link #repaint()} and/or {@link #validate()} are to
     * be invoked (as soon as {@link #inAddOrRemove} decreases from a positive
     * number to zero).
     */
    private boolean validateAndRepaint;

    /**
     * Initializes a new <tt>VideoContainer</tt> with a specific
     * <tt>Component</tt> to be displayed when no remote video is available.
     *
     * @param noVideoComponent the component to be displayed when no remote
     * video is available
     * @param conference <tt>true</tt> to dedicate the new instance to a
     * telephony conferencing user interface; otherwise, <tt>false</tt>
     */
    public VideoContainer(Component noVideoComponent, boolean conference)
    {
        setLayout(new VideoLayout(conference));

        this.noVideoComponent = noVideoComponent;

        if (DEFAULT_BACKGROUND_COLOR != null)
            setBackground(DEFAULT_BACKGROUND_COLOR);

        addContainerListener(
                new ContainerListener()
                {
                    public void componentAdded(ContainerEvent ev)
                    {
                        VideoContainer.this.onContainerEvent(ev);
                    }

                    public void componentRemoved(ContainerEvent ev)
                    {
                        VideoContainer.this.onContainerEvent(ev);
                    }
                });

        if (this.noVideoComponent != null)
            add(this.noVideoComponent, VideoLayout.CENTER_REMOTE, -1);
    }

    /**
     * Adds the given component at the {@link VideoLayout#CENTER_REMOTE}
     * position in the default video layout.
     *
     * @param comp the component to add
     * @return the added component
     */
    @Override
    public Component add(Component comp)
    {
        add(comp, VideoLayout.CENTER_REMOTE);
        return comp;
    }

    @Override
    public Component add(Component comp, int index)
    {
        add(comp, null, index);
        return comp;
    }

    @Override
    public void add(Component comp, Object constraints)
    {
        add(comp, constraints, -1);
    }

    /**
     * Overrides the default behavior of add in order to be sure to remove the
     * default "no video" component when a remote video component is added.
     *
     * @param comp the component to add
     * @param constraints
     * @param index
     */
    @Override
    public void add(Component comp, Object constraints, int index)
    {
        enterAddOrRemove();
        try
        {
            if (VideoLayout.CENTER_REMOTE.equals(constraints)
                    && (noVideoComponent != null)
                    && !noVideoComponent.equals(comp)
                || (comp.equals(noVideoComponent)
                    && noVideoComponent.getParent() != null))
            {
                remove(noVideoComponent);
            }

            super.add(comp, constraints, index);
        }
        finally
        {
            exitAddOrRemove();
        }
    }

    private void enterAddOrRemove()
    {
        synchronized (syncRoot)
        {
            if (inAddOrRemove == 0)
                validateAndRepaint = false;
            inAddOrRemove++;
        }
    }

    private void exitAddOrRemove()
    {
        synchronized (syncRoot)
        {
            inAddOrRemove--;
            if (inAddOrRemove < 1)
            {
                inAddOrRemove = 0;
                if (validateAndRepaint)
                {
                    validateAndRepaint = false;

                    if (isDisplayable())
                    {
                        if (isValid())
                            doLayout();
                        else
                            validate();
                        repaint();
                    }
                    else
                        doLayout();
                }
            }
        }
    }

    /**
     * Notifies this instance that a specific <tt>Component</tt> has been added
     * to or removed from this <tt>Container</tt>.
     *
     * @param ev a <tt>ContainerEvent</tt> which details the specifics of the
     * notification such as the <tt>Component</tt> that has been added or
     * removed
     */
    private void onContainerEvent(ContainerEvent ev)
    {
        try
        {
            Component component = ev.getChild();

            switch (ev.getID())
            {
            case ContainerEvent.COMPONENT_ADDED:
                component.addPropertyChangeListener(
                        PREFERRED_SIZE_PROPERTY_NAME,
                        propertyChangeListener);
                break;
            case ContainerEvent.COMPONENT_REMOVED:
                component.removePropertyChangeListener(
                        PREFERRED_SIZE_PROPERTY_NAME,
                        propertyChangeListener);
                break;
            }

            /*
             * If an explicit background color is to be displayed by this
             * Component, make sure that its opaque property i.e. transparency
             * does not interfere with that display.
             */
            if (DEFAULT_BACKGROUND_COLOR != null)
            {
                int componentCount = getComponentCount();

                if ((componentCount == 1)
                        && (getComponent(0)
                                == VideoContainer.this.noVideoComponent))
                {
                    componentCount = 0;
                }

                setOpaque(componentCount > 0);
            }
        }
        finally
        {
            synchronized (syncRoot)
            {
                if (inAddOrRemove != 0)
                    validateAndRepaint = true;
            }
        }
    }

    /**
     * Notifies this instance about a change in the value of a property of a
     * <tt>Component</tt> contained by this <tt>Container</tt>. Since the
     * <tt>VideoLayout</tt> of this <tt>Container</tt> sizes the contained
     * <tt>Component</tt>s based on their <tt>preferredSize</tt>s, this
     * <tt>Container</tt> invokes {@link #doLayout()}, {@link #repaint()} and/or
     * {@link #validate()} upon changes in the values of the property in
     * question.
     *
     * @param ev a <tt>PropertyChangeEvent</tt> which details the specifics of
     * the notification such as the name of the property whose value changed and
     * the <tt>Component</tt> which fired the notification
     */
    private void propertyChange(PropertyChangeEvent ev)
    {
        if (PREFERRED_SIZE_PROPERTY_NAME.equals(ev.getPropertyName())
                && SwingUtilities.isEventDispatchThread())
        {
            /*
             * The goal is to invoke doLayout, repaint and/or validate. These
             * methods and the specifics with respect to avoiding unnecessary
             * calls to them are already dealt with by enterAddOrRemove,
             * exitAddOrRemove and validateAndRepaint.
             */
            synchronized (syncRoot)
            {
                enterAddOrRemove();
                validateAndRepaint = true;
                exitAddOrRemove();
            }
        }
    }

    /**
     * Overrides the default remove behavior in order to add the default no
     * video component when the remote video is removed.
     *
     * @param comp the component to remove
     */
    @Override
    public void remove(Component comp)
    {
        enterAddOrRemove();
        try
        {
            super.remove(comp);

            Component[] components = getComponents();
            VideoLayout videoLayout = (VideoLayout) getLayout();
            boolean hasComponentsAtCenterRemote = false;

            for (Component c : components)
            {
                if (!c.equals(noVideoComponent)
                        && VideoLayout.CENTER_REMOTE.equals(
                                videoLayout.getComponentConstraints(c)))
                {
                    hasComponentsAtCenterRemote = true;
                    break;
                }
            }

            if (!hasComponentsAtCenterRemote
                    && (noVideoComponent != null)
                    && !noVideoComponent.equals(comp))
            {
                add(noVideoComponent, VideoLayout.CENTER_REMOTE);
            }
        }
        finally
        {
            exitAddOrRemove();
        }
    }

    /**
     * Ensures noVideoComponent is displayed even when the clients of the
     * videoContainer invoke its #removeAll() to remove their previous visual
     * Components representing video. Just adding noVideoComponent upon
     * ContainerEvent#COMPONENT_REMOVED when there is no other Component left in
     * the Container will cause an infinite loop because Container#removeAll()
     * will detect that a new Component has been added while dispatching the
     * event and will then try to remove the new Component.
     */
    @Override
    public void removeAll()
    {
        enterAddOrRemove();
        try
        {
            super.removeAll();

            if (noVideoComponent != null)
                add(noVideoComponent, VideoLayout.CENTER_REMOTE);
        }
        finally
        {
            exitAddOrRemove();
        }
    }
}
