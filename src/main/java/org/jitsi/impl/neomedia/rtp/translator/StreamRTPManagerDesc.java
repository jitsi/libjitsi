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
package org.jitsi.impl.neomedia.rtp.translator;

import java.util.*;

import javax.media.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.rtp.*;

/**
 * Describes additional information about a <tt>StreamRTPManager</tt> for
 * the purposes of <tt>RTPTranslatorImpl</tt>.
 *
 * @author Lyubomir Marinov
 */
class StreamRTPManagerDesc
{
    /**
     * An array with <tt>int</tt> element type and no elements explicitly
     * defined to reduce unnecessary allocations. 
     */
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    public RTPConnectorDesc connectorDesc;

    private final Map<Integer, Format> formats = new HashMap<>();

    /**
     * The list of synchronization source (SSRC) identifiers received by
     * {@link #streamRTPManager} (as <tt>ReceiveStream</tt>s).
     *
     *
     * XXX(gp) I'm sure there's a reason why we do it the way we do it, but we
     * might want to re-think about how we manage receive SSRCs. We keep track
     * of the receive SSRC in at least 3 places, in the MediaStreamImpl (we have
     * a remoteSourceIDs vector), in StreamRTPManager.receiveSSRCs and in
     * RtpChannel.receiveSSRCs. TAG(cat4-remote-ssrc-hurricane)
     */
    private int[] receiveSSRCs = EMPTY_INT_ARRAY;

    private final List<ReceiveStreamListener> receiveStreamListeners
        = new LinkedList<>();

    public final StreamRTPManager streamRTPManager;

    /**
     * Initializes a new <tt>StreamRTPManagerDesc</tt> instance which is to
     * describe a specific <tt>StreamRTPManager</tt>.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> to be described
     * by the new instance
     */
    public StreamRTPManagerDesc(StreamRTPManager streamRTPManager)
    {
        this.streamRTPManager = streamRTPManager;
    }

    public void addFormat(Format format, int payloadType)
    {
        synchronized (formats)
        {
            formats.put(payloadType, format);
        }
    }

    /**
     * Adds a new synchronization source (SSRC) identifier to the list of
     * SSRC received by the associated <tt>StreamRTPManager</tt>.
     *
     * @param receiveSSRC the new SSRC to add to the list of SSRC received
     * by the associated <tt>StreamRTPManager</tt>
     */
    public synchronized void addReceiveSSRC(int receiveSSRC)
    {
        if (!containsReceiveSSRC(receiveSSRC))
        {
            int receiveSSRCCount = receiveSSRCs.length;
            int[] newReceiveSSRCs = new int[receiveSSRCCount + 1];

            System.arraycopy(
                    receiveSSRCs, 0,
                    newReceiveSSRCs, 0,
                    receiveSSRCCount);
            newReceiveSSRCs[receiveSSRCCount] = receiveSSRC;
            receiveSSRCs = newReceiveSSRCs;
        }
    }

    public void addReceiveStreamListener(ReceiveStreamListener listener)
    {
        synchronized (receiveStreamListeners)
        {
            if (!receiveStreamListeners.contains(listener))
                receiveStreamListeners.add(listener);
        }
    }

    /**
     * Determines whether the list of synchronization source (SSRC)
     * identifiers received by the associated <tt>StreamRTPManager</tt>
     * contains a specific SSRC.
     *
     * @param receiveSSRC the SSRC to check whether it is contained in the
     * list of SSRC received by the associated <tt>StreamRTPManager</tt>
     * @return <tt>true</tt> if the specified <tt>receiveSSRC</tt> is
     * contained in the list of SSRC received by the associated
     * <tt>StreamRTPManager</tt>; otherwise, <tt>false</tt>
     */
    public synchronized boolean containsReceiveSSRC(int receiveSSRC)
    {
        for (int i = 0; i < receiveSSRCs.length; i++)
        {
            if (receiveSSRCs[i] == receiveSSRC)
                return true;
        }
        return false;
    }

    public Format getFormat(int payloadType)
    {
        synchronized (formats)
        {
            return formats.get(payloadType);
        }
    }

    public Format[] getFormats()
    {
        synchronized (this.formats)
        {
            Collection<Format> formats = this.formats.values();

            return formats.toArray(new Format[formats.size()]);
        }
    }

    public Integer getPayloadType(Format format)
    {
        synchronized (formats)
        {
            for (Map.Entry<Integer, Format> entry : formats.entrySet())
            {
                Format entryFormat = entry.getValue();

                if (entryFormat.matches(format)
                        || format.matches(entryFormat))
                    return entry.getKey();
            }
        }
        return null;
    }

    public ReceiveStreamListener[] getReceiveStreamListeners()
    {
        synchronized (receiveStreamListeners)
        {
            return
                receiveStreamListeners.toArray(
                        new ReceiveStreamListener[
                                receiveStreamListeners.size()]);
        }
    }

    public void removeReceiveStreamListener(ReceiveStreamListener listener)
    {
        synchronized (receiveStreamListeners)
        {
            receiveStreamListeners.remove(listener);
        }
    }
}
