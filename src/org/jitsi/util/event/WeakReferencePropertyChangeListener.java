/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.event;

import java.beans.*;
import java.lang.ref.*;

/**
 * Implements <tt>PropertyChangeListener</tt> which delegates to another
 * <tt>PropertyChangeListener</tt> while weakly referencing it.
 *
 * @author Lyubomir Marinov
 */
public class WeakReferencePropertyChangeListener
    implements PropertyChangeListener
{
    private final WeakReference<PropertyChangeListener> delegate;

    public WeakReferencePropertyChangeListener(PropertyChangeListener delegate)
    {
        this.delegate = new WeakReference<PropertyChangeListener>(delegate);
    }

    @Override
    public void propertyChange(PropertyChangeEvent ev)
    {
        PropertyChangeListener delegate = this.delegate.get();

        if (delegate == null)
        {
            Object source = ev.getSource();

            if (source instanceof PropertyChangeNotifier)
            {
                ((PropertyChangeNotifier) source)
                    .removePropertyChangeListener(this);
            }
        }
        else
        {
            delegate.propertyChange(ev);
        }
    }
}
