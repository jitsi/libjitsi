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
package org.jitsi.impl.neomedia.control;

/**
 * Provides a default implementation of <tt>Controls</tt> which does not expose
 * any controls.
 *
 * @author Lyubomir Marinov
 */
public class ControlsAdapter
    extends AbstractControls
{

    /**
     * The constant which represents an empty array of controls. Explicitly
     * defined in order to avoid unnecessary allocations.
     */
    public static final Object[] EMPTY_CONTROLS = new Object[0];

    /**
     * Implements {@link javax.media.Controls#getControls()}. Gets the controls
     * available for the owner of this instance. The current implementation
     * returns an empty array because it has no available controls.
     *
     * @return an array of <tt>Object</tt>s which represent the controls
     * available for the owner of this instance
     */
    public Object[] getControls()
    {
        return EMPTY_CONTROLS;
    }
}
