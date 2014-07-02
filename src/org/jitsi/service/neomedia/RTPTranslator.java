/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

/**
 * Represents an RTP translator which forwards RTP and RTCP traffic between
 * multiple <tt>MediaStream</tt>s.
 *
 * @author Lyubomir Marinov
 */
public interface RTPTranslator
{
    /**
     * Gets the current active <tt>RTCPTerminationStrategy</tt> which is to
     * inspect and modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return the <tt>RTCPTerminationStrategy</tt> which is to inspect and
     * modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     */
    public RTCPTerminationStrategy getRTCPTerminationStrategy();

    /**
     * Sets the current active <tt>RTCPTerminationStrategy</tt> which is to
     * inspect and modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     *
     * @param rtcpTerminationStrategy the <tt>RTCPTerminationStrategy</tt> which
     * is to inspect and modify RTCP traffic between multiple
     * <tt>MediaStream</tt>s.
     */
    public void setRTCPTerminationStrategy(
            RTCPTerminationStrategy rtcpTerminationStrategy);

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
