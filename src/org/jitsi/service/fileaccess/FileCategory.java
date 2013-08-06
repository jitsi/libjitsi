/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.service.fileaccess;

/**
 * High-level classification for files or directories created by Jitsi.
 * 
 * @author Ingo Bauersachs
 */
public enum FileCategory
{
    /**
     * For files or directories that contain configuration data or similar data
     * that belongs to a specific user, but might non-simultaneously be shared
     * across different computers or operating systems.
     */
    PROFILE,

    /**
     * For files or directories that contain cached data. It must be safe to
     * delete these files at any time.
     */
    CACHE,

    /**
     * For files or directories that contain log data that is bound to a
     * specific user and computer. This is NOT for history related data.
     */
    LOG
}
