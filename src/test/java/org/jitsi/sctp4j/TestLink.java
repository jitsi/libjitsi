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
package org.jitsi.sctp4j;

import java.io.*;
import java.util.*;

/**
 * The link that loses some packets and
 * produces IOExceptions from time to time.
 *
 * @author Pawel Domas
 */
public class TestLink
    extends DirectLink
{
    private final double lossRate;

    private final double errorRate;

    /**
     * Set random seed for consistent tests
     */
    private static final Random rand = new Random(1234567);

    public TestLink(
            SctpSocket a,
            SctpSocket b,
            double lossRate,
            double errorRate)
    {
        super(a, b);
        this.lossRate = lossRate;
        this.errorRate = errorRate;
    }

    @Override
    public void onConnOut(SctpSocket s, byte[] packet)
        throws IOException
    {
        double r = rand.nextDouble();

        if (r < (errorRate + lossRate))
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
