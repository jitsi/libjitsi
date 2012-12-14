/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.awt.*;

import net.sf.fmj.media.rtp.*;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 */
public interface MediaStreamStats
{
    /**
     * Computes and updates information for a specific stream.
     */
    public void updateStats();

    /**
     * Returns the MediaStream enconding.
     *
     * @return the encoding used by the stream.
     */
    public String getEncoding();

    /**
     * Returns the MediaStream enconding rate (in Hz)..
     *
     * @return the encoding rate used by the stream.
     */
    public String getEncodingClockRate();

    /**
     * Returns the upload video size if this stream uploads a video, or null if
     * not.
     *
     * @return the upload video size if this stream uploads a video, or null if
     * not.
     */
    public Dimension getUploadVideoSize();

    /**
     * Returns the download video size if this stream downloads a video, or
     * null if not.
     *
     * @return the download video size if this stream downloads a video, or null
     * if not.
     */
    public Dimension getDownloadVideoSize();

    /**
     * Returns the local IP address of the MediaStream.
     *
     * @return the local IP address of the stream.
     */
    public String getLocalIPAddress();

    /**
     * Returns the local port of the MediaStream.
     *
     * @return the local port of the stream.
     */
    public int getLocalPort();

    /**
     * Returns the remote IP address of the MediaStream.
     *
     * @return the remote IP address of the stream.
     */
    public String getRemoteIPAddress();

    /**
     * Returns the remote port of the MediaStream.
     *
     * @return the remote port of the stream.
     */
    public int getRemotePort();

    /**
     * Returns the percent loss of the download stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getDownloadPercentLoss();

    /**
     * Returns the percent loss of the upload stream.
     *
     * @return the last loss rate computed (in %).
     */
    public double getUploadPercentLoss();

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used download bandwidth computed (in Kbit/s).
     */
    public double getDownloadRateKiloBitPerSec();

    /**
     * Returns the bandwidth used by this download stream.
     *
     * @return the last used upload bandwidth computed (in Kbit/s).
     */
    public double getUploadRateKiloBitPerSec();

    /**
     * Returns the jitter average of this download stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getDownloadJitterMs();

    /**
     * Returns the jitter average of this upload stream.
     *
     * @return the last jitter average computed (in ms).
     */
    public double getUploadJitterMs();

    /**
     * Updates this stream stats with the new feedback sent.
     *
     * @param feedback The last RTCP feedback sent by the MediaStream.
     */
    public void updateNewSentFeedback(RTCPFeedback feedback);

    /**
     * Updates this stream stats with the new feedback received.
     *
     * @param feedback The last RTCP feedback received by the MediaStream.
     */
    public void updateNewReceivedFeedback(RTCPFeedback feedback);

    /**
     * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section
     * 6.4.1, subsection "delay since last SR (DLSR): 32 bits").
     *
     * @return The RTT computed with the RTCP feedback. Returns -1 if the RTT
     * has not been computed yet. Otherwise the RTT in ms.
     */
    public long getRttMs();

    /**
     * Returns the number of packets for which FEC data was decoded.
     */
    public long getNbFec();

    /**
     * Returns the total number of discarded packets since the beginning of the
     * session.
     *
     * @return the total number of discarded packets since the beginning of the
     * session.
     */
    public long getNbDiscarded();

    /**
     * Returns the current percent of discarded packets.
     *
     * @return the current percent of discarded packets.
     */
    public double getPercentDiscarded();

    /**
     * Checks whether there is an adaptive jitter buffer enabled for at least
     * one of the <tt>ReceiveStream</tt>s of the <tt>MediaStreamImpl</tt>.
     *
     * @return <tt>true</tt> if there is an adaptive jitter buffer enabled for
     * at least one of the <tt>ReceiveStream</tt>s of the
     * <tt>MediaStreamImpl</tt>. Otherwise, <tt>false</tt>
     */
    public boolean isAdaptiveBufferEnabled();

    /**
     * Returns the delay in number of packets introduced by the jitter buffer.
     *
     * @return the delay in number of packets introduced by the jitter buffer
     */
    public int getJitterBufferDelayPackets();

    /**
     * Returns the delay in milliseconds introduced by the jitter buffer.
     *
     * @return the delay in milliseconds introduces by the jitter buffer
     */
    public int getJitterBufferDelayMs();

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
     * session, because they were late.
     *
     * @return the number of packets discarded since the beginning of the
     * session, because they were late.
     */
    public int getNbDiscardedLate();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, while the packet queue was shrinking.
     *
     * @return the number of packets discarded since the beginning of the
     * session, while the packet queue was shrinking.
     */
    public int getNbDiscardedShrink();

    /**
     * Returns the number of packets discarded since the beginning of the
     * session, because the packet queue was full.
     *
     * @return the number of packets discarded since the beginning of the
     * session, because the packet queue was full.
     */
    public int getNbDiscardedFull();

    /**
     * Returns the current size of the packet queue.
     *
     * @return the current size of the packet queue.
     */
    public int getPacketQueueSize();

    /**
     * Returns the number of packets currently in the packet queue.
     *
     * @return the number of packets currently in the packet queue.
     */
    public int getPacketQueueCountPackets();


}
