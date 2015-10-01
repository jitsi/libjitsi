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
package org.jitsi.impl.neomedia.transform.csrc;

import java.util.concurrent.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * A simple thread that waits for new levels to be reported from incoming
 * RTP packets and then delivers them to the <tt>AudioMediaStream</tt>
 * associated with this engine. The reason we need to do this in a separate
 * thread is, of course, the time sensitive nature of incoming RTP packets.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class CsrcAudioLevelDispatcher
    implements Runnable
{
    /**
     * The pool of <tt>Thread</tt>s which run
     * <tt>CsrcAudioLevelDispatcher</tt>s.
     */
    private static final ExecutorService threadPool
        = ExecutorUtils.newCachedThreadPool(
                true,
                "CsrcAudioLevelDispatcher");

    /**
     * The levels added to this instance (by the <tt>reverseTransform</tt>
     * method of a <tt>PacketTransformer</tt> implementation) last.
     */
    private long[] levels;

    /**
     * The <tt>AudioMediaStreamImpl</tt> which listens to this event dispatcher.
     * If <tt>null</tt>, this event dispatcher is stopped. If non-<tt>null</tt>,
     * this event dispatcher is started.
     */
    private AudioMediaStreamImpl mediaStream;

    /**
     * The indicator which determines whether this event dispatcher has been
     * scheduled for execution by {@link #threadPool}.
     */
    private boolean scheduled = false;

    /**
     * Initializes a new <tt>CsrcAudioLevelDispatcher</tt> to dispatch events
     * to a specific <tt>AudioMediaStreamImpl</tt>.
     *
     * @param mediaStream the <tt>AudioMediaStreamImpl</tt> to which the new
     * instance is to dispatch events
     */
    public CsrcAudioLevelDispatcher(AudioMediaStreamImpl mediaStream)
    {
        setMediaStream(mediaStream);
    }

    /**
     * A level matrix that we should deliver to our media stream and its
     * listeners in a separate thread.
     *
     * @param levels the levels that we'd like to queue for processing.
     * @param rtpTime the timestamp carried by the RTP packet which carries the
     * specified <tt>levels</tt>
     */
    public void addLevels(long[] levels, long rtpTime)
    {
        synchronized(this)
        {
            this.levels = levels;

            if ((mediaStream != null) && !scheduled)
            {
                threadPool.execute(this);
                scheduled = true;
            }

            notifyAll();
        }
    }

    /**
     * Waits for new levels to be reported via the <tt>addLevels()</tt> method
     * and then delivers them to the <tt>AudioMediaStream</tt> that we are
     * associated with.
     */
    @Override
    public void run()
    {
        try
        {
            do
            {
                AudioMediaStreamImpl mediaStream;
                long[] levels;

                synchronized(this)
                {
                    // If the mediaStream is null, this instance is to stop.
                    mediaStream = this.mediaStream;
                    if (mediaStream == null)
                    {
                        scheduled = false;
                        break;
                    }

                    if(this.levels == null)
                    {
                        try { wait(); } catch (InterruptedException ie) {}
                        continue;
                    }
                    else
                    {
                        levels = this.levels;
                        this.levels = null;
                    }
                }

                if(levels != null)
                    mediaStream.audioLevelsReceived(levels);
            }
            while (true);
        }
        finally
        {
            synchronized (this)
            {
                scheduled = false;
            }
        }
    }

    /**
     * Causes our run method to exit so that this thread would stop
     * handling levels.
     */
    public void setMediaStream(AudioMediaStreamImpl mediaStream)
    {
        synchronized(this)
        {
            if (this.mediaStream != mediaStream)
            {
                this.mediaStream = mediaStream;

                /*
                 * If the mediaStream changes, it is unlikely that the (audio)
                 * levels are associated with it.
                 */
                this.levels = null;

                notifyAll();
            }
        }
    }
}
