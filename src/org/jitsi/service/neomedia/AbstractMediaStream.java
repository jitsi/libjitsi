/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.beans.*;
import java.util.*;

import org.jitsi.service.neomedia.format.*;

/**
 * Abstract base implementation of <tt>MediaStream</tt> to ease the
 * implementation of the interface.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractMediaStream
    implements MediaStream
{
    /**
     * The name of this stream, that some protocols may use for diagnostic
     * purposes.
     */
    private String name;

    /**
     * The opaque properties of this <tt>MediaStream</tt>.
     */
    private final Map<String,Object> properties
        = Collections.synchronizedMap(new HashMap<String,Object>());

    /**
     * The delegate of this instance which implements support for property
     * change notifications for its
     * {@link #addPropertyChangeListener(PropertyChangeListener)} and
     * {@link #removePropertyChangeListener(PropertyChangeListener)}.
     */
    private final PropertyChangeSupport propertyChangeSupport
        = new PropertyChangeSupport(this);

    /**
     * Adds a <tt>PropertyChangelistener</tt> to this stream which is to be
     * notified upon property changes such as a SSRC ID which becomes known.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to register for
     * <tt>PropertyChangeEvent</tt>s
     * @see MediaStream#addPropertyChangeListener(PropertyChangeListener)
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Asserts that the state of this instance will remain consistent if a
     * specific <tt>MediaDirection</tt> (i.e. <tt>direction</tt>) and a
     * <tt>MediaDevice</tt> with a specific <tt>MediaDirection</tt> (i.e.
     * <tt>deviceDirection</tt>) are both set on this instance.
     *
     * @param direction the <tt>MediaDirection</tt> to validate against the
     * specified <tt>deviceDirection</tt>
     * @param deviceDirection the <tt>MediaDirection</tt> of a
     * <tt>MediaDevice</tt> to validate against the specified <tt>direction</tt>
     * @param illegalArgumentExceptionMessage the message of the
     * <tt>IllegalArgumentException</tt> to be thrown if the state of this
     * instance would've been compromised if <tt>direction</tt> and the
     * <tt>MediaDevice</tt> associated with <tt>deviceDirection</tt> were both
     * set on this instance
     * @throws IllegalArgumentException if the state of this instance would've
     * been compromised were both <tt>direction</tt> and the
     * <tt>MediaDevice</tt> associated with <tt>deviceDirection</tt> set on this
     * instance
     */
    protected void assertDirection(
            MediaDirection direction,
            MediaDirection deviceDirection,
            String illegalArgumentExceptionMessage)
        throws IllegalArgumentException
    {
        if ((direction != null)
                && !direction.and(deviceDirection).equals(direction))
            throw new IllegalArgumentException(illegalArgumentExceptionMessage);
    }

    /**
     * Fires a new <tt>PropertyChangeEvent</tt> to the
     * <tt>PropertyChangeListener</tt>s registered with this instance in order
     * to notify about a change in the value of a specific property which had
     * its old value modified to a specific new value.
     *
     * @param property the name of the property of this instance which had its
     * value changed
     * @param oldValue the value of the property with the specified name before
     * the change
     * @param newValue the value of the property with the specified name after
     * the change
     */
    protected void firePropertyChange(
        String property,
        Object oldValue,
        Object newValue)
    {
        propertyChangeSupport.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Returns the name of this stream or <tt>null</tt> if no name has been
     * set. A stream name is used by some protocols, for diagnostic purposes
     * mostly. In XMPP for example this is the name of the content element that
     * describes a stream.
     *
     * @return the name of this stream or <tt>null</tt> if no name has been
     * set.
     */
    public String getName()
    {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getProperty(String propertyName)
    {
        return properties.get(propertyName);
    }

    /**
     * Handles attributes contained in <tt>MediaFormat</tt>.
     *
     * @param format the <tt>MediaFormat</tt> to handle the attributes of
     * @param attrs the attributes <tt>Map</tt> to handle
     */
    protected void handleAttributes(
            MediaFormat format,
            Map<String,String> attrs)
    {
    }

    /**
     * Removes the specified <tt>PropertyChangeListener</tt> from this stream so
     * that it won't receive further property change events.
     *
     * @param listener the <tt>PropertyChangeListener</tt> to remove
     * @see MediaStream#removePropertyChangeListener(PropertyChangeListener)
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Sets the name of this stream. Stream names are used by some protocols,
     * for diagnostic purposes mostly. In XMPP for example this is the name of
     * the content element that describes a stream.
     *
     * @param name the name of this stream or <tt>null</tt> if no name has been
     * set.
     */
    @Override
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperty(String propertyName, Object value)
    {
        if (value == null)
            properties.remove(propertyName);
        else
            properties.put(propertyName, value);
    }
}
