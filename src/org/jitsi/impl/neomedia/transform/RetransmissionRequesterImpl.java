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
package org.jitsi.impl.neomedia.transform;

import java.io.*;
import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;

/**
 * Detects lost RTP packets for a particular <tt>RtpChannel</tt> and requests
 * their retransmission by sending RTCP NACK packets.
 *
 * @author Boris Grozev
 * @author George Politis
 */
public class RetransmissionRequesterImpl
    extends SinglePacketTransformerAdapter
    implements TransformEngine, RetransmissionRequester
{
    /**
     * If more than <tt>MAX_MISSING</tt> consecutive packets are lost, we will
     * not request retransmissions for them, but reset our state instead.
     */
    private static final int MAX_MISSING = 100;

    /**
     * The maximum number of retransmission requests to be sent for a single
     * RTP packet.
     */
    private static final int MAX_REQUESTS = 10;

    /**
     * The interval after which another retransmission request will be sent
     * for a packet, unless it arrives. Ideally this should not be a constant,
     * but should be based on the RTT to the endpoint.
     */
    private static final int RE_REQUEST_AFTER = 150;

    /**
     * The <tt>Logger</tt> used by the <tt>RetransmissionRequesterImpl</tt> class
     * and its instances to print debug information.
     */
    private static final Logger logger
        = Logger.getLogger(RetransmissionRequesterImpl.class);

    /**
     * Maps an SSRC to the <tt>Requester</tt> instance corresponding to it.
     * TODO: purge these somehow (RTCP BYE? Timeout?)
     */
    private final Map<Long, Requester> requesters = new HashMap<>();

    /**
     * Whether this {@link RetransmissionRequester} is enabled or not.
     */
    private boolean enabled = true;

    /**
     * The thread which requests retransmissions by sending RTCP NACK packets.
     */
    private final Thread thread;

    /**
     * Whether this <tt>PacketTransformer</tt> has been closed.
     */
    private boolean closed = false;

    /**
     * The {@link MediaStream} that this instance belongs to.
     */
    private final MediaStream stream;

    /**
     * The SSRC which will be used as Packet Sender SSRC in NACK packets sent
     * by this {@code RetransmissionRequesterImpl}.
     */
    private long senderSsrc = -1;

    /**
     * Initializes a new <tt>RetransmissionRequesterImpl</tt> for the given
     * <tt>RtpChannel</tt>.
     * @param stream the {@link MediaStream} that the instance belongs to.
     */
    public RetransmissionRequesterImpl(MediaStream stream)
    {
        super(RTPPacketPredicate.INSTANCE);
        this.stream = stream;

        thread
            = new Thread()
            {
                @Override
                public void run()
                {
                    runInRequesterThread();
                }
            };
        thread.setDaemon(true);
        thread.setName(RetransmissionRequesterImpl.class.getName());
        thread.start();
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (enabled && !closed)
        {
            Long ssrc;
            int seq;

            MediaFormat format = stream.getFormat(pkt.getPayloadType());
            if (format == null)
            {
                ssrc = null;
                seq = -1;

                logger.warn("format_not_found" +
                    ",stream_hash=" + stream.hashCode());
            }
            else if (Constants.RTX.equalsIgnoreCase(format.getEncoding()))
            {
                MediaStreamTrackReceiver receiver
                    = stream.getMediaStreamTrackReceiver();

                RTPEncodingDesc encoding = receiver.findRTPEncodingDesc(pkt);

                if (encoding != null)
                {
                    ssrc = encoding.getPrimarySSRC();
                    seq = pkt.getOriginalSequenceNumber();
                }
                else
                {
                    ssrc = null;
                    seq = -1;

                    logger.warn("encoding_not_found" +
                        ",stream_hash=" + stream.hashCode());
                }
            }
            else
            {
                ssrc = pkt.getSSRCAsLong();
                seq = pkt.getSequenceNumber();
            }


            if (ssrc != null)
            {
                // TODO(gp) Don't NACK higher temporal layers.
                Requester requester;
                synchronized (requesters)
                {
                    requester = requesters.get(ssrc);
                    if (requester == null)
                    {
                        if (logger.isDebugEnabled())
                        {
                            logger.debug(
                                "Creating new Requester for SSRC " + ssrc);
                        }
                        requester = new Requester(ssrc);
                        requesters.put(ssrc, requester);
                    }
                }
                requester.received(seq);
            }
        }

        return pkt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        closed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(boolean enable)
    {
        this.enabled = enable;
    }

    /**
     * Runs a loop which checks for any pending requests to be sent, sends them
     * and then waits until another request is available or due.
     */
    private void runInRequesterThread()
    {
        if (Thread.currentThread() != thread)
            return;

        Map<Long, Set<Integer>> packetsToRequest = new HashMap<>();

        while (true)
        {
            if (closed)
            {
                break;
            }

            synchronized (thread)
            {
                // Check when the next request is due. -1 means there is no
                // request scheduled.
                long nextRequestAt = -1;
                synchronized (requesters)
                {
                    for (Requester requester : requesters.values())
                    {
                        if (requester.nextRequestAt != -1)
                        {
                            if (nextRequestAt == -1
                                    || nextRequestAt > requester.nextRequestAt)
                            {
                                nextRequestAt = requester.nextRequestAt;
                            }
                        }
                    }
                }

                long now = System.currentTimeMillis();
                if (nextRequestAt == -1 || nextRequestAt - now > 0)
                {
                    try
                    {
                        if (nextRequestAt == -1)
                        {
                            thread.wait(1000);
                            continue;
                        }
                        else
                        {
                            thread.wait(nextRequestAt - now);
                        }
                    }
                    catch (InterruptedException ie)
                    {
                        break;
                    }
                }
                //else the time has already come
            }

            if (!enabled || senderSsrc == -1)
                continue;

            synchronized (requesters)
            {
                for (Map.Entry<Long, Requester> entry : requesters.entrySet())
                {
                    Requester requester = entry.getValue();
                    Set<Integer> missingPackets = requester.getMissing();
                    if (missingPackets != null && !missingPackets.isEmpty())
                    {
                        packetsToRequest.put(requester.ssrc, missingPackets);
                    }
                }
            }

            for (Map.Entry<Long, Set<Integer>> entry : packetsToRequest.entrySet())
            {
                long sourceSsrc = entry.getKey();
                NACKPacket nack
                    = new NACKPacket(senderSsrc, sourceSsrc, entry.getValue());
                RawPacket pkt;

                try
                {
                    pkt = nack.toRawPacket();
                }
                catch (IOException ioe)
                {
                    pkt = null;
                    logger.warn("Failed to create a NACK packet: " + ioe);
                }

                if (pkt != null)
                {
                    try
                    {
                        if (logger.isTraceEnabled())
                        {
                            logger.trace("Sending a NACK: " + nack);
                        }
                        stream.injectPacket(
                                pkt,
                                /* data */ false,
                                /* after */ null);
                    }
                    catch (TransmissionFailedException e)
                    {
                        logger.warn(
                                "Failed to inject packet in MediaStream: " + e);
                    }
                }
            }

            packetsToRequest.clear();
        }

    }


    /**
     * Implements {@link TransformEngine#getRTPTransformer()}.
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    /**
     * Handles packets for a single SSRC.
     */
    private class Requester
    {
        /**
         * The SSRC for this instance.
         */
        private final long ssrc;

        /**
         * The highest received RTP sequence number.
         */
        private int lastReceivedSeq = -1;

        /**
         * The time that the next request for this SSRC should be sent.
         */
        private long nextRequestAt = -1;

        /**
         * The set of active requests for this SSRC. The keys are the sequence
         * numbers.
         */
        private final Map<Integer, Request> requests = new HashMap<>();

        /**
         * Initializes a new <tt>Requester</tt> instance for the given SSRC.
         */
        private Requester(long ssrc)
        {
            this.ssrc = ssrc;
        }

        /**
         * Handles a received RTP packet with a specific sequence number.
         * @param seq the RTP sequence number of the received packet.
         */
        synchronized private void received(int seq)
        {
            if (lastReceivedSeq == -1)
            {
                lastReceivedSeq = seq;
                return;
            }

            int diff = RTPUtils.sequenceNumberDiff(seq, lastReceivedSeq);
            if (diff <= 0)
            {
                // An older packet, possibly already requested.
                // We don't update nextRequestAt here. The reading thread might
                // wake up unnecessarily and do some extra work, but that's OK.
                Request r = requests.remove(seq);
                if (r != null && logger.isDebugEnabled())
                {
                    long rtt
                        = stream.getMediaStreamStats().getSendStats().getRtt();
                    if (rtt > 0)
                    {

                        // firstRequestSentAt is if we created a Request, but
                        // haven't yet sent a NACK. Assume a delta of 0 in that
                        // case.
                        long firstRequestSentAt = r.firstRequestSentAt;
                        long delta
                            = firstRequestSentAt > 0
                                ? System.currentTimeMillis()
                                        - r.firstRequestSentAt
                                : 0;

                        logger.debug(Logger.Category.STATISTICS,
                                     "retr_received,stream=" + stream
                                         .hashCode() +
                                         " delay=" + delta +
                                         ",rtt=" + rtt);
                    }
                }
            }
            else if (diff == 1)
            {
                // The very next packet, as expected.
                lastReceivedSeq = seq;
            }
            else if (diff <= MAX_MISSING)
            {
                for (int missing = (lastReceivedSeq + 1) % (1<<16);
                     missing != seq;
                     missing = (missing + 1) % (1<<16))
                {
                    Request request = new Request(missing);
                    requests.put(missing, request);
                }

                lastReceivedSeq = seq;
                nextRequestAt = 0;

                synchronized (thread)
                {
                    thread.notifyAll();
                }
            }
            else // if (diff > MAX_MISSING)
            {
                // Too many packets missing. Reset.
                lastReceivedSeq = seq;
                if (logger.isDebugEnabled())
                {
                    logger.debug("Resetting retransmission requester state. "
                                 + "SSRC: " + ssrc
                                 + ", last received: " + lastReceivedSeq
                                 + ", current: " + seq
                                 + ". Removing " + requests.size()
                                 + " unsatisfied requests.");
                }
                requests.clear();
                nextRequestAt = -1;
            }

        }

        /**
         * Returns a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         * Assumes that the returned set of sequence numbers will be requested
         * immediately and updates the state accordingly (i.e. increments the
         * timesRequested counters and sets the time of next request).
         *
         * @return a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         */
        synchronized private Set<Integer> getMissing()
        {
            long now = System.currentTimeMillis();
            Set<Integer> missingPackets = null;

            if (nextRequestAt == -1 || nextRequestAt > now)
                return null;

            for (Iterator<Map.Entry<Integer,Request>> iter
                        = requests.entrySet().iterator();
                    iter.hasNext();)
            {
                Request request = iter.next().getValue();

                if (missingPackets == null)
                    missingPackets = new HashSet<>();

                missingPackets.add(request.seq);
                request.timesRequested++;

                if (request.timesRequested == 1)
                {
                    request.firstRequestSentAt = now;
                }
                else if (request.timesRequested == MAX_REQUESTS)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug(
                            "Sending the last NACK for SSRC=" + ssrc + " seq="
                                + request.seq + ". "
                                + "Time since the first request: "
                                + (now - request.firstRequestSentAt));
                    }
                    iter.remove();
                }

            }

            nextRequestAt = (requests.size() > 0) ? now + RE_REQUEST_AFTER : -1;

            return missingPackets;
        }
    }

    /**
     * Represents a request for the retransmission of a specific RTP packet.
     */
    private static class Request
    {
        /**
         * The RTP sequence number.
         */
        final int seq;

        /**
         * The system time at the moment a retransmission request for this
         * packet was first sent.
         */
        long firstRequestSentAt = -1;

        /**
         * The number of times that a retransmission request for this packet
         * has been sent.
         */
        int timesRequested = 0;

        /**
         * Initializes a new <tt>Request</tt> instance with the given RTP
         * sequence number.
         * @param seq the RTP sequence number.
         */
        Request(int seq)
        {
            this.seq = seq;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setSenderSsrc(long ssrc)
    {
        senderSsrc = ssrc;
    }
}
