/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.version.util;

import org.jitsi.service.version.*;

import java.util.regex.*;

/**
 * Base implementation of <tt>VersionService</tt> that uses major, minor
 * and nightly build id fields for versioning purposes.
 *
 * @author Emil Ivov
 * @author Pawel Domas
 */
public abstract class AbstractVersionService
    implements VersionService
{
    /**
     * The pattern that will parse strings to version object.
     */
    private static final Pattern PARSE_VERSION_STRING_PATTERN =
        Pattern.compile("(\\d+)\\.(\\d+)\\.([\\d\\.]+)");

    /**
     * Returns a Version instance corresponding to the <tt>version</tt>
     * string.
     *
     * @param version a version String that we have obtained by calling a
     *   <tt>Version.toString()</tt> method.
     * @return the <tt>Version</tt> object corresponding to the
     *   <tt>version</tt> string. Or null if we cannot parse the string.
     */
    public Version parseVersionString(String version)
    {
        Matcher matcher = PARSE_VERSION_STRING_PATTERN.matcher(version);

        if(matcher.matches() && matcher.groupCount() == 3)
        {
            return createVersionImpl(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3));
        }

        return null;
    }

    /**
     * Creates new <tt>Version</tt> instance specific to current implementation.
     * @param majorVersion major version number.
     * @param minorVersion minor version number.
     * @param nightlyBuildId nightly build id string.
     * @return new <tt>Version</tt> instance specific to current implementation
     *         for given major, minor and nightly build id parameters.
     */
    protected abstract Version createVersionImpl(int majorVersion,
                                                 int minorVersion,
                                                 String nightlyBuildId);
}
