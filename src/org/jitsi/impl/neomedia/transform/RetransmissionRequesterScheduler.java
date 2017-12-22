/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.service.neomedia.*;

/**
 * The RetransmissionRequester needs to be able to asynchronously (with regards
 * to the thread executing the transform chain) generate and send NACK packets.
 * Rather than embedding that thread in the class itself (which makes it very
 * hard to test deterministically) this class is in charge of polling the
 * class for work and executing it when it's ready.
 * @author bbaldino
 */
public class RetransmissionRequesterScheduler
{
    protected final RetransmissionRequester retransmissionRequester;

    protected final Thread thread;

    protected final static long DEFAULT_SLEEP_INTERVAL_MILLIS = 1000L;

    protected final long sleepIntervalMillis;

    protected boolean closed = false;

    public RetransmissionRequesterScheduler(RetransmissionRequester retransmissionRequester)
    {
        this.retransmissionRequester = retransmissionRequester;
        this.sleepIntervalMillis = DEFAULT_SLEEP_INTERVAL_MILLIS;
        this.thread = new Thread()
        {
            @Override
            public void run()
            {
                runLoop();
            }
        };
        this.thread.setDaemon(true);
        this.thread.setName(RetransmissionRequester.class.getName());
        this.thread.start();
    }

    public void close()
    {
        closed = true;
        synchronized (thread)
        {
            thread.notify();
        }
    }

    protected void runLoop()
    {
        while(true)
        {
            if (closed)
            {
                break;
            }
            synchronized (thread)
            {
                if (retransmissionRequester.hasWork())
                {
                    retransmissionRequester.doWork();
                }
                else
                {
                    try
                    {
                        Thread.sleep(sleepIntervalMillis);
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            }
        }
    }
}
