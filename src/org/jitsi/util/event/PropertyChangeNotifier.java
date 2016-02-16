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
package org.jitsi.util.event;

import java.beans.*;
import java.util.*;

import org.jitsi.util.*;

/**
 * Represents a source of <tt>PropertyChangeEvent</tt>s which notifies
 * <tt>PropertyChangeListener</tt>s about changes in the values of properties.
 *
 * @author Lyubomir Marinov
 */
public class PropertyChangeNotifier
{
    /**
     * The <tt>Logger</tt> used by the <tt>PropertyChangeNotifier</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(PropertyChangeNotifier.class);

    /**
     * The list of <tt>PropertyChangeListener</tt>s interested in and notified
     * about changes in the values of the properties of this
     * <tt>PropertyChangeNotifier</tt>.
     */
    private final List<PropertyChangeListener> listeners
        = new ArrayList<PropertyChangeListener>();

    /**
     * Initializes a new <tt>PropertyChangeNotifier</tt> instance.
     */
    public PropertyChangeNotifier()
    {
    }

    /**
     * Adds a specific <tt>PropertyChangeListener</tt> to the list of listeners
     * interested in and notified about changes in the values of the properties
     * of this <tt>PropertyChangeNotifier</tt>.
     *
     * @param listener a <tt>PropertyChangeListener</tt> to be notified about
     * changes in the values of the properties of this
     * <tt>PropertyChangeNotifier</tt>. If the specified listener is already in
     * the list of interested listeners (i.e. it has been previously added), it
     * is not added again.
     */
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        if (listener == null)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug(
                        "The specified argument listener is null"
                            + " and that does not make sense.");
            }
        }
        else
        {
            synchronized (listeners)
            {
                if (!listeners.contains(listener))
                    listeners.add(listener);
            }
        }
    }

    /**
     * Fires a new <tt>PropertyChangeEvent</tt> to the
     * <tt>PropertyChangeListener</tt>s registered with this
     * <tt>PropertyChangeNotifier</tt> in order to notify about a change in the
     * value of a specific property which had its old value modified to a
     * specific new value. <tt>PropertyChangeNotifier</tt> does not check
     * whether the specified <tt>oldValue</tt> and <tt>newValue</tt> are indeed
     * different.
     *
     * @param property the name of the property of this
     * <tt>PropertyChangeNotifier</tt> which had its value changed
     * @param oldValue the value of the property with the specified name before
     * the change
     * @param newValue the value of the property with the specified name after
     * the change
     */
    protected void firePropertyChange(
            String property,
            Object oldValue, Object newValue)
    {
        PropertyChangeListener[] ls;

        synchronized (listeners)
        {
            ls
                = listeners.toArray(
                        new PropertyChangeListener[listeners.size()]);
        }

        if (ls.length != 0)
        {
            PropertyChangeEvent ev
                = new PropertyChangeEvent(
                        getPropertyChangeSource(property, oldValue, newValue),
                        property,
                        oldValue, newValue);

            for (PropertyChangeListener l : ls)
            {
                try
                {
                    l.propertyChange(ev);
                }
                catch (Throwable t)
                {
                    if (t instanceof InterruptedException)
                    {
                        Thread.currentThread().interrupt();
                    }
                    else if (t instanceof ThreadDeath)
                    {
                        throw (ThreadDeath) t;
                    }
                    else
                    {
                        logger.warn(
                                "A PropertyChangeListener threw an exception"
                                    + " while handling a PropertyChangeEvent.",
                                t);
                    }
                }
            }
        }
    }

    /**
     * Gets the <tt>Object</tt> to be reported as the source of a new
     * <tt>PropertyChangeEvent</tt> which is to notify the
     * <tt>PropertyChangeListener</tt>s registered with this
     * <tt>PropertyChangeNotifier</tt> about the change in the value of a
     * property with a specific name from a specific old value to a specific new
     * value.
     *
     * @param property the name of the property which had its value changed from
     * the specified old value to the specified new value
     * @param oldValue the value of the property with the specified name before
     * the change
     * @param newValue the value of the property with the specified name after
     * the change
     * @return the <tt>Object</tt> to be reported as the source of the new
     * <tt>PropertyChangeEvent</tt> which is to notify the
     * <tt>PropertyChangeListener</tt>s registered with this
     * <tt>PropertyChangeNotifier</tt> about the change in the value of the
     * property with the specified name from the specified old value to the
     * specified new value
     */
    protected Object getPropertyChangeSource(
            String property,
            Object oldValue, Object newValue)
    {
        return this;
    }

    /**
     * Removes a specific <tt>PropertyChangeListener</tt> from the list of
     * listeners interested in and notified about changes in the values of the
     * properties of this <tt>PropertyChangeNotifer</tt>.
     *
     * @param listener a <tt>PropertyChangeListener</tt> to no longer be
     * notified about changes in the values of the properties of this
     * <tt>PropertyChangeNotifier</tt>
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        if (listener != null)
        {
            synchronized (listeners)
            {
                listeners.remove(listener);
            }
        }
    }
}
