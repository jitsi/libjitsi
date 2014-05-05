/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

/**
 * A direct connection that passes packets between two <tt>SctpSocket</tt>
 * instances.
 *
 * @author Pawel Domas
 */
public class DirectLink
    implements NetworkLink
{
    /**
     * Instance "a" of this direct connection.
     */
    private final SctpSocket a;

    /**
     * Instance "b" of this direct connection.
     */
    private final SctpSocket b;
    
    public DirectLink(SctpSocket a, SctpSocket b)
    {
        this.a = a;
        this.b = b;
    }

    /**
     * {@inheritDoc}
     */
    public void onConnOut(final SctpSocket s, final byte[] packet)
    {
        final SctpSocket dest = s == this.a ? this.b : this.a;
        new Thread(new Runnable()
        {
            public void run()
            {
                dest.onConnIn(packet);
            }
        }).start();
    }
}
