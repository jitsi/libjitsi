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
package org.jitsi.impl.neomedia.rtcp;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;

/**
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class RemoteClock
{
    /**
     * Finds the {@code MediaStream} which receives a specific SSRC.
     *
     * @param caller the {@code MediaStream} which is attempting to find the
     * {@code MediaStream} which receives {@code receiveSSRC}
     * @param receiveSSRC the SSRC which is supposedly received by the
     * {@code MediaStream} to be returned
     * @return the {@code MediaStream} which receives {@code receiveSSRC} or
     * {@code null}
     */
    private static MediaStream findMediaStreamByReceiveSSRC(
            MediaStream caller,
            int receiveSSRC)
    {
        // RTPTranslator knows which MediaStream receives a specific SSRC.

        RTPTranslator rtpTranslator = caller.getRTPTranslator();
        MediaStream ret = null;

        if (rtpTranslator != null)
        {
            StreamRTPManager streamRTPManager
                = rtpTranslator.findStreamRTPManagerByReceiveSSRC(receiveSSRC);

            if (streamRTPManager != null)
                ret = streamRTPManager.getMediaStream();
        }
        return ret;
    }

    /**
     * Finds the {@code MediaStreamImpl} which receives a specific SSRC.
     *
     * @param caller the {@code MediaStream} which is attempting to find the
     * {@code MediaStreamImpl} which receives {@code receiveSSRC}
     * @param receiveSSRC the SSRC which is supposedly received by the
     * {@code MediaStreamImpl} to be returned
     * @return the {@code MediaStreamImpl} which receives {@code receiveSSRC} or
     * {@code null}
     */
    private static MediaStreamImpl findMediaStreamImplByReceiveSSRC(
            MediaStream caller,
            int receiveSSRC)
    {
        MediaStream ret = findMediaStreamByReceiveSSRC(caller, receiveSSRC);

        return (ret instanceof MediaStreamImpl) ? (MediaStreamImpl) ret : null;
    }

    /**
     * Finds a {@code RemoteClock} describing the wallclock and RTP timestamp
     * (base) of a received RTP stream identified by a specific SSRC.
     *
     * @param caller the {@code MediaStream} which is looking for the
     * {@code RemoteClock} of {@code ssrc}
     * @param ssrc the SSRC of a received RTP stream whose {@code RemoteClock}
     * is to be returned
     * @return the {@code RemoteClock} of {@code ssrc} or {@code null}
     */
    public static RemoteClock findRemoteClock(MediaStream caller, int ssrc)
    {
        return findRemoteClocks(caller, ssrc)[0];
    }

    /**
     * Finds the {@code RemoteClock}s describing the wallclocks and RTP
     * timestamp bases of received RTP streams identified by specific SSRCs.
     *
     * @param caller the {@code MediaStream} which is looking for the
     * {@code RemoteClock}s of {@code ssrcs}
     * @param ssrcs the SSRCs of received RTP streams whose {@code RemoteClock}s
     * are to be returned
     * @return an array of the {@code RemoteClock}s of {@code ssrcs}. The
     * elements at the same indices of the returned array and {@code ssrcs}
     * correspond to one another.
     */
    public static RemoteClock[] findRemoteClocks(
            MediaStream caller,
            int... ssrcs)
    {
        RemoteClock[] clocks = new RemoteClock[ssrcs.length];

        // MediaStreamImpl has an associated RemoteClockEstimator so check
        // whether the caller itself knows about (some of) the RemoteClocks that
        // it is asking after.
        if (caller instanceof MediaStreamImpl
                && findRemoteClocks((MediaStreamImpl) caller, ssrcs, clocks)
                    >= ssrcs.length)
        {
            return clocks;
        }

        // Additionally, ask the MediaStreamImpls which are receiving the
        // specified SSRCs.
        for (int ssrc : ssrcs)
        {
            MediaStreamImpl mediaStream
                = findMediaStreamImplByReceiveSSRC(caller, ssrc);

            if (mediaStream != null
                    && findRemoteClocks(mediaStream, ssrcs, clocks)
                        >= ssrcs.length)
            {
                return clocks;
            }
        }

        return clocks;
    }
    /**
     * Finds the {@code RemoteClock}s describing the wallclocks and RTP
     * timestamp bases of received RTP streams identified by specific SSRCs.
     *
     * @param caller the {@code MediaStream} which is looking for the
     * {@code RemoteClock}s of {@code ssrcs}
     * @param ssrcs the SSRCs of received RTP streams whose {@code RemoteClock}s
     * are to be returned
     * @param clocks an array of the {@code RemoteClock}s of {@code ssrcs}. The
     * elements at the same indices of the returned array and {@code ssrcs}
     * correspond to one another.
     * @return the number of elements of {@code clocks} which are not
     * {@code null}
     */
    private static int findRemoteClocks(
            MediaStreamImpl caller, int[] ssrcs,
            RemoteClock[] clocks)
    {
        RemoteClockEstimator remoteClockEstimator
            = caller.getMediaStreamStats().getRemoteClockEstimator();
        int nonNullClockCount = 0;

        for (int i = 0; i < ssrcs.length; ++i)
        {
            if (clocks[i] == null)
            {
                RemoteClock clock
                    = remoteClockEstimator.getRemoteClock(ssrcs[i]);

                if (clock != null)
                {
                    clocks[i] = clock;
                    ++nonNullClockCount;
                }
            }
            else
            {
                ++nonNullClockCount;
            }
        }
        return nonNullClockCount;
    }

    /**
     * The SSRC.
     */
    private final int ssrc;

    /**
     * The remote <tt>Timestamp</tt> which was received at
     * {@link #localReceiptTimeMs} for this RTP stream.
     */
    private final Timestamp remoteTimestamp;

    /**
     * The local time (in milliseconds since the epoch) at which we received the
     * RTCP report with the RTP/NTP timestamps. It's a signed long.
     */
    private final long localReceiptTimeMs;

    /**
     * The clock rate for {@link #ssrc}. We need to have received at least two
     * SRs in order to be able to calculate this. Unsigned short.
     */
    private final int frequencyHz;

    /**
     * Ctor.
     *
     * @param ssrc
     * @param remoteTime the remote (system/wallclock) time in milliseconds
     * since the epoch
     * @param rtpTimestamp the RTP timestamp corresponding to
     * <tt>remoteTime</tt>.
     * @param frequencyHz the RTP clock rate.
     */
    RemoteClock(int ssrc, long remoteTime, int rtpTimestamp, int frequencyHz)
    {
        this.ssrc = ssrc;
        this.remoteTimestamp = new Timestamp(remoteTime, rtpTimestamp);
        this.frequencyHz = frequencyHz;
        this.localReceiptTimeMs = System.currentTimeMillis();
    }

    public Timestamp estimate(long localTimeMs)
    {
        int frequencyHz = getFrequencyHz();

        if (frequencyHz == -1)
        {
            // We can't continue if we don't have the sender's clock
            // frequency/rate.
            return null;
        }

        long delayMs = localTimeMs - getLocalReceiptTimeMs();

        // Estimate the remote wall clock.
        Timestamp remoteTs = getRemoteTimestamp();
        long remoteTime = remoteTs.getSystemTimeMs();
        long estimatedRemoteTime = remoteTime + delayMs;

        // Drift the RTP timestamp.
        long rtpTimestamp
            = remoteTs.getRtpTimestampAsLong() + delayMs * (frequencyHz / 1000);

        return new Timestamp(estimatedRemoteTime, (int) rtpTimestamp);
    }

    /**
     * Gets the RTP timestamp that corresponds to a specific (remote) system
     * time in milliseconds.
     *
     * @param systemTimeMs the (remote) system time in milliseconds for which
     * the corresponding RTP timestamp is to be returned
     * @return the RTP timestamp that corresponds to the specified (remote)
     * {@code systemTimeMs} or {@code null}
     */
    public Timestamp remoteSystemTimeMs2rtpTimestamp(long systemTimeMs)
    {
        int frequencyHz = getFrequencyHz();

        if (frequencyHz < 1000)
        {
            // We can't continue if (1) we don't have the sender's clock
            // frequency/rate or (2) the sender's clock frequency/rate is bellow
            // 1kHz.
            return null;
        }

        Timestamp ts = getRemoteTimestamp();
        long rtpTimestamp
            = ts.getRtpTimestampAsLong()
                + (systemTimeMs - ts.getSystemTimeMs()) * (frequencyHz / 1000);

        return new Timestamp(systemTimeMs, (int) rtpTimestamp);
    }

    /**
     * Gets the (remote) system time in milliseconds that corresponds to a
     * specific RTP timestamp.
     *
     * @param rtpTimestamp the RTP timestamp for which the (remote) system time
     * in milliseconds is to be returned
     * @return the (remote) system time in milliseconds that corresponds to
     * {@code rtpTimestamp}
     */
    public Timestamp rtpTimestamp2remoteSystemTimeMs(long rtpTimestamp)
    {
        int frequencyHz = getFrequencyHz();

        if (frequencyHz < 1000)
        {
            // We can't continue if (1) we don't have the sender's clock
            // frequency/rate or (2) the sender's clock frequency/rate is bellow
            // 1kHz.
            return null;
        }

        Timestamp ts = getRemoteTimestamp();

        return
            new Timestamp(
                    ts.getSystemTimeMs()
                        + (rtpTimestamp - ts.getRtpTimestampAsLong())
                            / (frequencyHz / 1000),
                    (int) rtpTimestamp);
    }

    /**
     * Gets the remote {@code Timestamp} (i.e. RTP timestamp and system time in
     * milliseconds) which was received at {@link #localReceiptTimeMs} for this
     * RTP stream.
     *
     * @return the remote {@code Timestamp} which was received at
     * {@code localReceiptTimeMs} for this RTP stream
     */
    public Timestamp getRemoteTimestamp()
    {
        return remoteTimestamp;
    }

    /**
     * Gets the local time (in milliseconds since the epoch) at which we
     * received the RTCP report with the RTP/NTP timestamps.
     *
     * @return the local time (in milliseconds since the epoch) at which we
     * received the RTCP report with the RTP/NTP timestamps
     */
    public long getLocalReceiptTimeMs()
    {
        return localReceiptTimeMs;
    }

    /**
     *
     * @return
     */
    public int getSsrc()
    {
        return ssrc;
    }

    /**
     *
     * @return
     */
    public int getFrequencyHz()
    {
        return frequencyHz;
    }
}
