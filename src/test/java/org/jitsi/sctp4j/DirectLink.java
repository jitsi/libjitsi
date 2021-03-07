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

import org.jitsi.utils.logging.*;

import java.io.*;

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
     * The logger used by this class instances.
     */
    private static final Logger logger = Logger.getLogger(DirectLink.class);

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
        throws IOException
    {
        final SctpSocket dest = s == this.a ? this.b : this.a;
        new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    dest.onConnIn(packet, 0, packet.length);
                }
                catch (IOException e)
                {
                    logger.error(e, e);
                }
            }
        }).start();
    }
}
