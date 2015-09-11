/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtp.*;

import java.util.*;

/**
 * Represents an RTP translator which forwards RTP and RTCP traffic between
 * multiple <tt>MediaStream</tt>s.
 *
 * @author Lyubomir Marinov
 */
public interface RTPTranslator
{
    /**
     * Returns a list of <tt>StreamRTPManager</tt>s currently attached to
     * this <tt>RTPTranslator</tt>. This is
     * admittedly wrong, to expose the bare <tt>SSRCCache</tt> to the use of
     * of the <tt>StreamRTPManager</tt>. We should find a better way of exposing
     * this information. Currently it is necessary for RTCP termination.
     *
     * @return a list of <tt>StreamRTPManager</tt>s currently attached to
     * this <tt>RTPTranslator</tt>.
     */
    public List<StreamRTPManager> getStreamRTPManagers();

    /**
     * Provides access to the underlying <tt>SSRCCache</tt> that holds
     * statistics information about each SSRC that we receive.
     *
     * @return the underlying <tt>SSRCCache</tt> that holds statistics
     * information about each SSRC that we receive.
     */
    public SSRCCache getSSRCCache();

    /**
     * Defines a packet filter which allows an observer of an
     * <tt>RTPTranslator</tt> to disallow the writing of specific packets into
     * a specific destination identified by a <tt>MediaStream</tt>.
     */
    public interface WriteFilter
    {
        public boolean accept(
                MediaStream source,
                byte[] buffer, int offset, int length,
                MediaStream destination,
                boolean data);
    }

    /**
     * Adds a <tt>WriteFilter</tt> to this <tt>RTPTranslator</tt>.
     *
     * @param writeFilter the <tt>WriteFilter</tt> to add to this
     * <tt>RTPTranslator</tt>
     */
    public void addWriteFilter(WriteFilter writeFilter);

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    public void dispose();

    /**
     * Removes a <tt>WriteFilter</tt> from this <tt>RTPTranslator</tt>.
     *
     * @param writeFilter the <tt>WriteFilter</tt> to remove from this
     * <tt>RTPTranslator</tt>
     */
    public void removeWriteFilter(WriteFilter writeFilter);
}
