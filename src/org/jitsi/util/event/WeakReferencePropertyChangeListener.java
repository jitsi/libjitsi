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
