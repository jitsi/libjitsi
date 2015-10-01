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
package org.jitsi.service.configuration;

import java.beans.*;

/**
 * A PropertyVetoException is thrown when a proposed change to a
 * property represents an unacceptable value.
 *
 * @author Emil Ivov
 */
public class ConfigPropertyVetoException
    extends RuntimeException
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * A PropertyChangeEvent describing the vetoed change.
     */
    private final PropertyChangeEvent evt;

    /**
     * Constructs a <tt>PropertyVetoException</tt> with a
     * detailed message.
     *
     * @param message Descriptive message
     * @param evt A PropertyChangeEvent describing the vetoed change.
     */
    public ConfigPropertyVetoException(String message, PropertyChangeEvent evt)
    {
        super(message);

        this.evt = evt;
    }

    /**
     * Gets the vetoed <tt>PropertyChangeEvent</tt>.
     *
     * @return A PropertyChangeEvent describing the vetoed change.
     */
    public PropertyChangeEvent getPropertyChangeEvent()
    {
        return evt;
    }
}
