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
package org.jitsi.service.version;

/**
 * The version service keeps track of the SIP Communicator version that we are
 * currently running. Other modules (such as a Help->About dialog) query and
 * use this service in order to show the current application version.
 *
 * @author Emil Ivov
 */
public interface VersionService
{
    /**
     * Returns a <tt>Version</tt> object containing version details of the SIP
     * Communicator version that we're currently running.
     *
     * @return a <tt>Version</tt> object containing version details of the SIP
     * Communicator version that we're currently running.
     */
    public Version getCurrentVersion();

    /**
     * Returns a Version instance corresponding to the <tt>version</tt> string.
     *
     * @param version a version String that we have obtained by calling a
     * <tt>Version.toString()</tt> method.
     *
     * @return the <tt>Version</tt> object corresponding to the <tt>version</tt>
     * string.
     */
    public Version parseVersionString(String version);
}
