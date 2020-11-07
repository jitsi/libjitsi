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

import javax.media.*;

/**
 * Defines an FMJ <tt>Control</tt> which allows the diagnosis of the functional
 * health of a procedure/process.
 *
 * @author Lyubomir Marinov
 */
public interface DiagnosticsControl
    extends Control
{
    /**
     * The constant which expresses a non-existent time in milliseconds for the
     * purposes of {@link #getMalfuntioningSince()}. Explicitly chosen to be
     * <tt>0</tt> rather than <tt>-1</tt> in the name of efficiency.
     */
    public static final long NEVER = 0;

    /**
     * Gets the time in milliseconds at which the associated procedure/process
     * has started malfunctioning.
     *
     * @return the time in milliseconds at which the associated
     * procedure/process has started malfunctioning or <tt>NEVER</tt> if the
     * associated procedure/process is functioning normally
     */
    public long getMalfunctioningSince();

    /**
     * Returns a human-readable <tt>String</tt> representation of the associated
     * procedure/process.
     *
     * @return a human-readable <tt>String</tt> representation of the associated
     * procedure/process
     */
    public String toString();
}
