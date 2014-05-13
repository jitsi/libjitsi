/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import java.io.*;

/**
 * The link that loses some packets and produces IOExceptions from time to time.
 *
 * @author Pawel Domas
 */
public class TestLink
    extends DirectLink
{
    private final double lossRate;

    private final double errorRate;

    public TestLink(SctpSocket a, SctpSocket b,
                    double lossRate, double errorRate)
    {
        super(a, b);

        this.lossRate = lossRate;

        this.errorRate = errorRate;
    }

    @Override
    public void onConnOut(SctpSocket s, byte[] packet)
        throws IOException
    {
        double r = Math.random();

        if(r < (errorRate + lossRate))
        {
            if (r < errorRate)
            {
                throw new IOException("Link failure");
            }
            else
            {
                // Packet lost, nothing happens
                return;
            }
        }
        // Eventually pass the data
        super.onConnOut(s, packet);
    }
}
