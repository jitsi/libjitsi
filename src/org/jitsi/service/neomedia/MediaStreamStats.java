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
package org.jitsi.service.neomedia;

import java.awt.*;
import java.util.*;

import org.jitsi.service.neomedia.rtp.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 */
public interface MediaStreamStats
{
    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getDownloadJitterMs();

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getDownloadPercentLoss();

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    public double getDownloadRateKiloBitPerSec();

    /**
     * Returns the download video size if this stream downloads a video, or
     * <tt>null</tt> if not.
     *
     * @return the download video size if this stream downloads a video, or
     * <tt>null</tt> if not.
     */
    public Dimension getDownloadVideoSize();

    /**
     * Returns the <tt>MediaStream</tt> enconding.
     *
     * @return the encoding used by the stream.
     */
    public String getEncoding();

    /**
     * Returns the <tt>MediaStream</tt> enconding rate (in Hz).
     *
     * @return the encoding rate used by the stream.
     */
    public String getEncodingClockRate();

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer.
     *
     * @return the delay in milliseconds introduced by the jitter buffer
     */
    public int getJitterBufferDelayMs();

    /**
     * Returns the delay in number of packets introduced by the jitter buffer.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    public int getJitterBufferDelayPackets();

    /**
     * Returns the local IP address of the <tt>MediaStream</tt>.
     *
     * @return the local IP address of the stream.
     */
    public String getLocalIPAddress();

    /**
     * Returns the local port of the <tt>MediaStream</tt>.
     *
     * @return the local port of the stream.
     */
    public int getLocalPort();

    /**
     * Returns the number of received bytes since the beginning of the
     * session.
     *
     * @return the number of received bytes for this stream.
     */
    public long getNbReceivedBytes();

    /**
     * Returns the number of sent bytes since the beginning of the
     * session.
     *
     * @return the number of sent bytes for this stream.
     */
    public long getNbSentBytes();

    /**
     * Returns the total number of discarded packets since the beginning of the
     * session.
     *
     * @return the total number of discarded packets since the beginning of the
     * session.
     */
    public long getNbDiscarded();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, because the packet queue was full.
     *
     * @return the number of packets discarded since the beginning of the
     * session, because the packet queue was full.
     */
    public int getNbDiscardedFull();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, because they were late.
     *
     * @return the number of packets discarded since the beginning of the
     * session, because they were late.
     */
    public int getNbDiscardedLate();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, because the packet queue was reset.
     *
     * @return the number of packets discarded since the beginning of the
     * session, because the packet queue was reset.
     */
    public int getNbDiscardedReset();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, while the packet queue was shrinking.
     *
     * @return the number of packets discarded since the beginning of the
     * session, while the packet queue was shrinking.
     */
    public int getNbDiscardedShrink();

    /**
     * Returns the number of packets for which FEC data was decoded.
     *
     * @return the number of packets for which FEC data was decoded
     */
    public long getNbFec();

    /**
     * Returns the total number of packets that are send or receive for this
     * stream since the stream is created.
     * @return the total number of packets.
     */
    public long getNbPackets();

    /**
     * Returns the number of lost packets for that stream.
     * @return the number of lost packets.
     */
    public long getNbPacketsLost();

    /**
     * Returns the number of packets currently in the packet queue.
     *
     * @return the number of packets currently in the packet queue.
     */
    public int getPacketQueueCountPackets();

    /**
     * Returns the current size of the packet queue.
     *
     * @return the current size of the packet queue.
     */
    public int getPacketQueueSize();

    /**
     * Returns the current percent of discarded packets.
     *
     * @return the current percent of discarded packets.
     */
    public double getPercentDiscarded();

    /**
     * Returns the remote IP address of the <tt>MediaStream</tt>.
     *
     * @return the remote IP address of the stream.
     */
    public String getRemoteIPAddress();

    /**
     * Returns the remote port of the <tt>MediaStream</tt>.
     *
     * @return the remote port of the stream.
     */
    public int getRemotePort();

    /**
     * Gets the detailed statistics about the RTCP reports sent and received by
     * the associated local peer.
     *
     * @return the detailed statistics about the RTCP reports sent and received
     * by the associated local peer
     */
    public RTCPReports getRTCPReports();

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns <tt>-1</tt> if
     * the RTT has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs();

    /**
     * Returns the jitter average of this upload stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getUploadJitterMs();

    /**
     * Returns the percent loss of the upload stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getUploadPercentLoss();

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    public double getUploadRateKiloBitPerSec();

    /**
     * Returns the upload video size if this stream uploads a video, or
     * <tt>null</tt> if not.
     *
     * @return the upload video size if this stream uploads a video, or
     * <tt>null</tt> if not.
     */
    public Dimension getUploadVideoSize();

    /**
     * Checks whether there is an adaptive jitter buffer enabled for at least
     * one of the <tt>ReceiveStream</tt>s of the <tt>MediaStreamImpl</tt>.
     *
     * @return <tt>true</tt> if there is an adaptive jitter buffer enabled for
     * at least one of the <tt>ReceiveStream</tt>s of the
     * <tt>MediaStreamImpl</tt>; otherwise, <tt>false</tt>
     */
    public boolean isAdaptiveBufferEnabled();

    /**
     * Computes and updates information for a specific stream.
     */
    public void updateStats();

    /**
     * Gets the minimum RTP jitter value reported by us in an RTCP report, in
     * milliseconds. Returns -1D if the value is unknown.
     * @return the minimum RTP jitter value reported by us in an RTCP report, in
     * milliseconds.
     */
    public double getMinDownloadJitterMs();

    /**
     * Gets the maximum RTP jitter value reported by us in an RTCP report, in
     * milliseconds. Returns -1D if the value is unknown.
     * @return the maximum RTP jitter value reported by us in an RTCP report, in
     * milliseconds.
     */
    public double getMaxDownloadJitterMs();

    /**
     * Gets the average of the RTP jitter values reported to us in RTCP reports,
     * in milliseconds. Returns -1D if the value is unknown.
     * @return  the average of the RTP jitter values reported to us in RTCP
     * reports, in milliseconds. Returns -1D if the value is unknown.
     */
    public double getAvgDownloadJitterMs();

    /**
     * Gets the minimum RTP jitter value reported to us in an RTCP report, in
     * milliseconds. Returns -1D if the value is unknown.
     * @return the minimum RTP jitter value reported to us in an RTCP report, in
     * milliseconds.
     */
    public double getMinUploadJitterMs();

    /**
     * Gets the maximum RTP jitter value reported to us in an RTCP report, in
     * milliseconds. Returns -1D if the value is unknown.
     * @return the maximum RTP jitter value reported to us in an RTCP report, in
     * milliseconds.
     */
    public double getMaxUploadJitterMs();

    /**
     * Gets the average of the RTP jitter values reported to us in RTCP reports,
     * in milliseconds. Returns -1D if the value is unknown.
     * @return  the average of the RTP jitter values reported to us in RTCP
     * reports, in milliseconds. Returns -1D if the value is unknown.
     */
    public double getAvgUploadJitterMs();

    /**
     * Returns the number of packets sent since the beginning of the session.
     * @return  the number of packets sent since the beginning of the session.
     */
    public long getNbPacketsSent();

    /**
     * Returns the number of packets received since the beginning of the
     * session.
     * @return  the number of packets received since the beginning of the
     * session.
     */
    public long getNbPacketsReceived();

    /**
     * Returns the number of RTP packets sent by the remote side, but not
     * received by us.
     * @return the number of RTP packets sent by the remote side, but not
     * received by us.
     */
    public long getDownloadNbPacketLost();

    /**
     * Returns the number of RTP packets sent by us, but not
     * received by the remote side.
     * @return the number of RTP packets sent by us, but not
     * received by the remote side.
     */
    public long getUploadNbPacketLost();

    /**
     * Adds a listener which will be notified when NACK packets are received.
     * @param listener the listener.
     */
    public void addNackListener(NACKListener listener);

    /**
     * Adds a listener which will be notified when REMB packets are received.
     * @param listener the listener.
     */
    public void addRembListener(REMBListener listener);

    /**
     * Gets the rate at which we are currently sending data to the remote
     * endpoint in bits per second. This is almost the same as {@link
     * #getUploadRateKiloBitPerSec()}. The duplication is necessary, because
     * of implementation details.
     * @return the rate at which we are currently sending data to the remote
     * endpoint, in bits per second.
     */
    public long getSendingBitrate();

    /**
     * Returns the received stats.
     *
     * @return a list holding all received stats.
     */
    public Collection<? extends MediaStreamSSRCStats> getReceivedStats();

    /**
     * Returns the sent stats.
     *
     * @return a list holding all sent stats.
     */
    public Collection<? extends MediaStreamSSRCStats> getSentStats();
}
