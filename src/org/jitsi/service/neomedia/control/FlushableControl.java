/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.control;

import javax.media.*;

/**
 * An interface which allows to flush a buffer.
 *
 * @author Boris Grozev
 */
public interface FlushableControl extends Control
{
    /**
     * Flushes the buffer.
     */
    public void flush();
}

