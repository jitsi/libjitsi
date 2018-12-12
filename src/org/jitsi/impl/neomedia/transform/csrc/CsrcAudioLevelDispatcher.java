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
import java.util.concurrent.atomic.*;

import org.ice4j.util.*;
import org.jitsi.impl.neomedia.*;

/**
 * A simple dispatcher that handles new audio levels reported from incoming
 * RTP packets and then asynchronously delivers them to associated
 * <tt>AudioMediaStreamImpl</tt>. The asynchronous processing is necessary
 * due to time sensitive nature of incoming RTP packets.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Yura Yaroshevich
 */
public class CsrcAudioLevelDispatcher
{
    /**
     * The executor service to asynchronously execute method which delivers
     * audio level updates to <tt>AudioMediaStreamImpl</tt>
     */
    private static final ExecutorService threadPool
        = ExecutorFactory.createFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            CsrcAudioLevelDispatcher.class.getName() + "-");

    /**
     * The levels added to this instance (by the <tt>reverseTransform</tt>
     * method of a <tt>PacketTransformer</tt> implementation) last.
     */
    private final AtomicReference<long[]> levels
        = new AtomicReference<>();

    /**
     * The <tt>AudioMediaStreamImpl</tt> which listens to this event dispatcher.
     * If <tt>null</tt>, this event dispatcher is stopped. If non-<tt>null</tt>,
     * this event dispatcher is started.
     */
    private final AtomicReference<AudioMediaStreamImpl> mediaStream
        = new AtomicReference<>();

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
        if (mediaStream.get() != null)
        {
            this.levels.set(levels);

            // submit asynchronous delivery of audio levels update
            threadPool.submit(this::deliverAudioLevelsToMediaStream);
        }
    }

    /**
     * Updates associated instance of <tt>AudioMediaStreamImpl</tt> with
     * current dispatcher
     * @param mediaStream - new <tt>AudioMediaStreamImpl</tt> to associate
     */
    public void setMediaStream(AudioMediaStreamImpl mediaStream)
    {
        // Reset current media stream and obtain
        AudioMediaStreamImpl oldStream
            = this.mediaStream.getAndSet(mediaStream);

        if (oldStream != mediaStream)
        {
            /*
             * If the mediaStream changes, it is unlikely that the (audio)
             * levels are associated with it.
             */
            this.levels.set(null);
        }
    }

    /**
     * Delivers last reported audio levels to associated {@link #mediaStream}
     */
    private void deliverAudioLevelsToMediaStream()
    {
        final AudioMediaStreamImpl stream = mediaStream.get();

        // read and reset latest audio levels
        final long[] latestAudioLevels = levels.getAndSet(null);

        if (stream != null && latestAudioLevels != null)
        {
            stream.audioLevelsReceived(latestAudioLevels);
        }
    }
}
