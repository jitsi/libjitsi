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
package org.jitsi.impl.neomedia.rtp.remotebitrateestimator;


import org.ice4j.util.RateStatistics;
import org.jitsi.impl.neomedia.rtp.TransportCCEngine;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.rtp.*;
import org.jitsi.util.*;
import org.jitsi.util.concurrent.RecurringRunnable;
import org.jitsi.util.function.Predicate;

import java.util.*;

/**
 * webrtc.org abs_send_time implementation as of June 26, 2017.
 * commit ID: 23fbd2aa2c81d065b84d17b09b747e75672e1159
 * @author Julian Chukwu
 */
public class RemoteBitrateEstimatorAbsSendTime
    extends SinglePacketTransformer
    implements RemoteBitrateEstimator,
        RecurringRunnable
{
    private static final Logger logger = Logger
            .getLogger(RemoteBitrateEstimatorAbsSendTime.class);

    /**
     * Ctor.
     * <p>
     * XXX At some point ideally we would get rid of this ctor and all the
     * inheritors will use the parametrized ctor. Also, we might want to move
     * this check inside the <tt>TransformEngineChain</tt> so that we only make
     * the check once per packet: The RTCP transformer is only supposed to
     * (reverse) transform RTCP packets and the RTP transformer is only
     * supposed to modify RTP packets.
     */
    public RemoteBitrateEstimatorAbsSendTime() {
        super();
    }

    /**
     * Ctor.
     *
     * @param packetPredicate the <tt>PacketPredicate</tt> to use to match
     *                        packets to (reverse) transform.
     */
    public RemoteBitrateEstimatorAbsSendTime(Predicate<ByteArrayBuffer> packetPredicate) {
        super(packetPredicate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The (default) implementation of {@code SinglePacketTransformer} does
     * nothing.
     */
    @Override
    public void close() {
        super.close();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reverse-transforms an array of packets by calling
     * {@link #reverseTransform(RawPacket)} on each one.
     *
     * @param pkts
     */
    @Override
    public RawPacket[] reverseTransform(RawPacket[] pkts) {
        return super.reverseTransform(pkts);
    }

    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed.
     * @return the transformed packet.
     */
    @Override
    public RawPacket transform(RawPacket pkt) {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Transforms an array of packets by calling {@link #transform(RawPacket)}
     * on each one.
     *
     * @param pkts
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts) {
        return super.transform(pkts);
    }

    /**
     * Returns the number of milliseconds until this instance wants a worker
     * thread to call {@link #run()}. The method is called on the same
     * worker thread as Process will be called on.
     *
     * @return the number of milliseconds until this instance wants a worker
     * thread to call {@link #run()}
     */
    @Override
    public long getTimeUntilNextRun() {
        return 0;
    }

    private enum ProbeResult
    {
        kBitrateUpdated(0),
        kNoUpdate(1);
        int value;
        ProbeResult(int x)
        {
            this.value = x;
        };
    }

    private final Object critSect = new Object();
    private Collection<Integer> ssrcs = new ArrayList<Integer>();
    private final static int kTimestampGroupLengthMs = 5;
    private final static int kAbsSendTimeFraction = 18;
    private final static int kAbsSendTimeInterArrivalUpshift = 8;
    private final static int kInterArrivalShift
            =  kAbsSendTimeFraction + kAbsSendTimeInterArrivalUpshift;
    private final static int kInitialProbingIntervalMs = 2000;
    private final static int kMinClusterSize = 4;
    private final static int kMaxProbePackets = 15;
    private final static int kExpectedNumberOfProbes = 3;

    private static final double kTimestampToMs = 1000.0 /
            (1 << kInterArrivalShift) ;

    private <K,V> List<K> Keys(TreeMap<K,V> _map)
    {
        ArrayList<K> keys = new ArrayList<K>();
        for(Map.Entry<K,V> entry : _map.entrySet())
        {
            keys.add(entry.getKey());
        }
        return keys;
    }

    private long ConvertMsTo24Bits(long timeMs)
    {
        long time24Bits = (long)(((timeMs << kAbsSendTimeFraction) + 500) /
                1000) & 0x00FFFFFF;
        return time24Bits;
    }

    private  TreeMap<Long,Long> ssrcs_ = new TreeMap<Long, Long>();
    private  ArrayList<Probe> probes_ = new ArrayList<>();
    private  long totalProbesReceived;
    private  long firstPacketTimeMs;
    private  long lastUpdateMs;
    public RemoteBitrateObserver observer_;
    private AimdRateControl remoteRate  = new AimdRateControl();
    private InterArrival interArrival;
    private OveruseEstimator estimator;
    private OveruseDetector detector;
    private OverUseDetectorOptions options;
    private RateStatistics incomingBitrate ;
    boolean incomingBitrateInitialized;
    ArrayList<Integer> recentPropagationDeltaMs;
    ArrayList<Long> recentUpdateTimeMs;
    private AbsSendTimeEngine absoluteSendTimeEngine = new AbsSendTimeEngine();

    boolean IsWithinClusterBounds(long sendDeltaMs, Cluster clusterAggregate)
    {
        if(clusterAggregate.count == 0)
        {
            return true;
        }
        double clusterMean = clusterAggregate.sendMeanMs /
                (double)clusterAggregate.count;
        return  Math.abs((double)sendDeltaMs - clusterMean) < 2.5f;
    }

    void addCluster(List<Cluster> clusters, Cluster cluster)
    {
        cluster.sendMeanMs /= (double)cluster.count;
        cluster.recvMeanMs /= (double)cluster.count;
        cluster.meanSize /= cluster.count;
        clusters.add(cluster);
    }

    public RemoteBitrateEstimatorAbsSendTime(RemoteBitrateObserver observer)
    {
        this.observer_ = observer;
        this.interArrival = new InterArrival(90 * kTimestampGroupLengthMs,
                kTimestampToMs,true);
        this.estimator = new OveruseEstimator(new OverUseDetectorOptions());
        this.detector = new OveruseDetector(new OverUseDetectorOptions());
        this.incomingBitrate = new RateStatistics(kBitrateWindowMs,8000);
        this.incomingBitrateInitialized = false;
        this.totalProbesReceived = 0;
        this.firstPacketTimeMs = -1;
        this.lastUpdateMs = -1;
        logger.info("; RemoteBitrateEstimatorAbsSendTime: Instantiating.");

    }

    void computeClusters(List<Cluster> clusters)
    {
        Cluster current = new Cluster();
        long prevSendTime =  -1;
        long prevRecvTime = -1;
        for(Probe probe : probes_)
        {
            if(prevSendTime >= 0)
            {
                long sendDeltaMs = probe.sendTimeMs - prevSendTime;
                long recvDeltaMs = probe.recvTimeMs - prevRecvTime;

                if(sendDeltaMs >= 1 && recvDeltaMs >= 1)
                {
                    ++current.numAboveMinDelta;
                }

                if(!IsWithinClusterBounds(sendDeltaMs,current))
                {
                    if(current.count >= kMinClusterSize)
                    {
                        addCluster(clusters,current);
                    }
                    current = new Cluster();
                }
                current.sendMeanMs += sendDeltaMs;
                current.recvMeanMs += recvDeltaMs;
                current.meanSize += probe.payloadSize;
                ++current.count;
            }

            prevSendTime = probe.sendTimeMs;
            prevRecvTime = probe.recvTimeMs;
        }
        if(current.count >= kMinClusterSize)
            addCluster(clusters, current);
    }

    /**
     * @param clusters
     * @returns a cluster that shows the best probe
     */
    Cluster findBestProbe(List<Cluster> clusters)
    {
        int highestProbeBitrateBps = 0;
        Cluster bestIt = new Cluster();
        for (Cluster cluster : clusters) {
            if (cluster.sendMeanMs == 0 || cluster.recvMeanMs == 0)
                continue;
            if (cluster.numAboveMinDelta > cluster.count / 2 &&
                    (cluster.recvMeanMs - cluster.sendMeanMs <= 2.0f &&
                            cluster.sendMeanMs - cluster.recvMeanMs <= 5.0f))
            {
                int probeBitrateBps =
                        Math.min(cluster.getSendBitrateBps(), cluster
                                .getRecvBitrateBps());
                if (probeBitrateBps > highestProbeBitrateBps)
                {
                    highestProbeBitrateBps = probeBitrateBps;
                    bestIt = cluster;
                }
            }
            else
            {
                double sendBitrateBps = cluster.meanSize * 8 * 1000
                        / cluster.sendMeanMs;
                double recvBitrateBps = cluster.meanSize * 8 * 1000
                        / cluster.recvMeanMs;
                logger.warn( "Probe failed, sent at " + sendBitrateBps
                        + " bps, received at " + recvBitrateBps
                        + " bps. Mean send delta: " + cluster.sendMeanMs
                        + " ms, mean recv delta: " + cluster.recvMeanMs
                        + " ms, num probes: " + cluster.count);
                break;
            }
        }
        return bestIt;
    }

    @Override
    public void run(){}

    ProbeResult processClusters(long nowMs)
    {
        synchronized (critSect) {
            List<Cluster> clusters = new ArrayList<Cluster>();
            computeClusters(clusters);
            if (clusters.isEmpty()) {
                // If we reach the max number of probe packets and still
                // have no clusters,
                // we will remove the oldest one.
                if (probes_.size() >= kMaxProbePackets)
                    probes_.remove(0);
                return ProbeResult.kNoUpdate;
            }

            Cluster bestProbe = findBestProbe(clusters);
            int probeBitrateBps =
                    Math.min(bestProbe.getSendBitrateBps(), bestProbe
                            .getRecvBitrateBps());
            // Make sure that a probe sent on a lower bitrate
            // than our estimate can't
            // reduce the estimate.
            if (isBitrateImproving(probeBitrateBps))
            {
                logger.warn("Probe successful, sent at "
                        + bestProbe.getSendBitrateBps() +
                        " bps, received at "
                        + bestProbe.getRecvBitrateBps()
                        + " bps. Mean send delta: " + bestProbe.sendMeanMs
                        + " ms, mean recv delta: " + bestProbe.recvMeanMs
                        + " ms, num probes: " + bestProbe.count);
                remoteRate.setEstimate(probeBitrateBps, nowMs);
                return ProbeResult.kBitrateUpdated;
            }
            // Not probing and received non-probe packet,
            // or finished with current set  of probes.
            if (clusters.size() >= kExpectedNumberOfProbes)
                probes_.clear();
            return ProbeResult.kNoUpdate;
        }
    }

    boolean isBitrateImproving(int newBitrateBps)
    {
        synchronized (critSect)
        {
            boolean initialProbe = !remoteRate.isValidEstimate()
                    && newBitrateBps > 0;
            boolean bitrateAboveEstimate =
                    remoteRate.isValidEstimate() &&
                            newBitrateBps > (int) (remoteRate
                                    .getLatestEstimate());
            return initialProbe || bitrateAboveEstimate;
        }
    }

    /**
     * Reverse-transforms a specific packet.
     *
     * @param packet the transformed packet to be restored.
     * @return the restored packet.
     */
    @Override
    public RawPacket reverseTransform(
            RawPacket packet)
    {
        if (absoluteSendTimeEngine
                .hasAbsoluteSendTimeExtension(packet) == null) {
            logger.warn( "RemoteBitrateEstimatorAbsSendTimeImpl: Incoming" +
                    " packet is missing absolute send time extension!");
        return null;
    }
        logger.info("Using RemoteBitrateEstimatorAbsSendTime: Instantiating.");
        incomingPacketInfo(System.currentTimeMillis(), absoluteSendTimeEngine
                        .getAbsSendTime(packet),
            packet.getPayloadLength(), packet.getSSRC());
        return packet;
    }

    void incomingPacketInfo(
        long arrivalTimeMs,
        long sendTime24bits,
        long payloadSize,
        long ssrc) {
        /**
         * @ToDo 1L << 24 should be 1L << 23 since we want to check if
         * sendTime24bits is less than 24 bits
         */
        if (sendTime24bits < (1L << 24)){
            logger.warn("Send Time not valid");
        }
        // Shift up send time to use the full 32 bits that inter_arrival
        // works with,
        // so wrapping works properly.
        long timestamp = sendTime24bits << kAbsSendTimeInterArrivalUpshift;
        long sendTimeMs = (long) (timestamp * kTimestampToMs);
        long nowMs = System.currentTimeMillis();
        // TODO(holmer): SSRCs are only needed for REMB,
        // should be broken out from  here.
        // Check if incoming bitrate estimate is valid, and if it
        // needs to be reset.
        long incomingBitrate_ =
                incomingBitrate.getRate(arrivalTimeMs);
        if (incomingBitrate_ != 0)
        {
            incomingBitrateInitialized = true;
        } else if (incomingBitrateInitialized)
        {
            // Incoming bitrate had a previous valid value, but now not
            // enough data point are left within the current window.
            // Reset incoming bitrate estimator so that the window
            // size will only contain new data points.
            incomingBitrate = new RateStatistics(kBitrateWindowMs,8000);
            incomingBitrateInitialized = false;
        }
        incomingBitrate.update((int) payloadSize, arrivalTimeMs);
        if (firstPacketTimeMs == -1) {
            firstPacketTimeMs = nowMs;
        }
        long tsDelta = 0;
        long tDelta = 0;
        int sizeDelta = 0;
        boolean updateEstimate = false;
        long targetBitrateBps = 0;
        synchronized (critSect) {
            //rtc.CritScope lock(&crit_);
            timeoutStreams(nowMs);
            // RTC_DCHECK(interArrival.get());
            // RTC_DCHECK(estimator.get());
            ssrcs_.put(ssrc, nowMs);
            // For now only try to detect probes while we don't have
            // a valid estimate. We currently assume that only packets
            // larger than 200 bytes are paced by  the sender.
            long kMinProbePacketSize = 200;
            if (payloadSize > kMinProbePacketSize &&
                    (!remoteRate.isValidEstimate() ||
                            nowMs - firstPacketTimeMs
                                    < kInitialProbingIntervalMs)) {
                // TODO(holmer): Use a map instead to get correct order?
                if (totalProbesReceived < kMaxProbePackets) {
                    long sendDeltaMs = -1;
                    long recvDeltaMs = -1;
                    if (!probes_.isEmpty()) {
                        sendDeltaMs = sendTimeMs - probes_
                                .get(probes_.size() - 1).sendTimeMs;
                        recvDeltaMs = arrivalTimeMs - probes_
                                .get(probes_.size() - 1).sendTimeMs;
                    }
                    logger.warn("Probe packet received: send time="
                            + sendTimeMs
                            + " ms, recv time=" + arrivalTimeMs
                            + " ms, send delta=" + sendDeltaMs
                            + " ms, recv delta=" + recvDeltaMs + " ms.");
                }
                probes_.add(new Probe(sendTime24bits, arrivalTimeMs,
                        payloadSize));
                ++totalProbesReceived;
                // Make sure that a probe which updated the bitrate immediately
                // has an effect by calling the
                // OnReceiveBitrateChanged callback.
                if (processClusters(nowMs) == ProbeResult.kBitrateUpdated)
                    updateEstimate = true;
            }

            long[] deltas = new long[]{tsDelta, tDelta, sizeDelta};
            if (interArrival.computeDeltas(timestamp, arrivalTimeMs,
                    (int) payloadSize, deltas, nowMs))
            {
                double tsDeltaMs = (1000.0 * tsDelta)
                        / (1 << kInterArrivalShift);
                estimator.update(tDelta, tsDeltaMs, sizeDelta,
                        detector.getState());
                detector.detect(estimator.getOffset(), tsDeltaMs,
                        estimator.getNumOfDeltas(), arrivalTimeMs);
            }

            if (!updateEstimate)
            {
                // Check if it's time for a periodic update or if we
                // should update because of an over-use.
                if (lastUpdateMs == -1 ||
                        nowMs - lastUpdateMs > remoteRate
                                .getFeedBackInterval())
                {
                    updateEstimate = true;
                }
                else if (detector.getState() == BandwidthUsage.kBwOverusing)
                {
                    long incomingRate_ =
                            incomingBitrate.getRate(arrivalTimeMs);
                    if (incomingRate_ > 0 &&
                            remoteRate.isTimeToReduceFurther(nowMs,
                                    incomingBitrate_))
                    {
                        updateEstimate = true;
                    }
                }
            }
            if (updateEstimate)
            {
                // The first overuse should immediately trigger a new estimate.
                // We also have to update the estimate immediately if we are
                // overusing and the target bitrate is too high compared to
                // what we are receiving.
                RateControlInput input = new RateControlInput(detector
                        .getState(), incomingBitrate.getRate(arrivalTimeMs),
                        estimator.getVarNoise());

                /**
                 * @Todo remote.update returns nothing. This is fine since we
                 * can retrieve the value from remoteRate.getLatestEstimate
                 */
                remoteRate.update(input, nowMs);
                targetBitrateBps = remoteRate.getLatestEstimate();
                updateEstimate = remoteRate.isValidEstimate();
            }
        }
        if (updateEstimate)
        {
            lastUpdateMs = nowMs;
            observer_.onReceiveBitrateChanged(getSsrcs(), targetBitrateBps);
        }
    }

    void timeoutStreams(long nowMs)
    {
        synchronized (critSect) {
            Iterator<Map.Entry<Long, Long>> itr = ssrcs_.entrySet().iterator();
            while (itr.hasNext()) {
                /**
                 * @Todo ask if this is the best way to modify a treemap object
                 * while going through
                 */
                Map.Entry entry = itr.next();
                if ((nowMs - (Long) entry.getValue() > kStreamTimeOutMs)) {
                    itr.remove();
                }
            }
            if (ssrcs_.isEmpty()) {
                // We can't update the estimate if we don't have any active streams.
                interArrival = new InterArrival((kTimestampGroupLengthMs
                        << kInterArrivalShift) / 1000,
                        kTimestampToMs, true);
                estimator = new OveruseEstimator(new OverUseDetectorOptions());
                // We deliberately don't reset the first_packet_time_ms_
                // here for now since we only probe for bandwidth in the
                // beginning of a call right now.
            }
        }
    }

    void OnRttUpdate(long avg_rtt_ms,
                     long max_rtt_ms)
    {
        synchronized (critSect) {
            remoteRate.setRtt(avg_rtt_ms);
        }
    }

    void removeStream(long ssrc)
    {
        synchronized (critSect)
        {
            ssrcs_.remove(ssrc);
        }
    }

    long getlatestEstimate(List<Long> ssrcs,
        long bitrateBps) {

        synchronized (critSect)
        {
            if (!remoteRate.isValidEstimate()) {
                return -1;
            }
            ssrcs = Keys(ssrcs_);
            if (ssrcs_.isEmpty()) {
                bitrateBps = 0;
            } else {
                bitrateBps = remoteRate.getLatestEstimate();
            }
            return bitrateBps;
        }
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTP packets
     */
    @Override
    public PacketTransformer getRTPTransformer() {
        return this;
    }

    /**
     * Gets the <tt>PacketTransformer</tt> for RTCP packets.
     *
     * @return the <tt>PacketTransformer</tt> for RTCP packets
     */
    @Override
    public PacketTransformer getRTCPTransformer() {
        return null;
    }

    /**
     * Returns the estimated payload bitrate in bits per second if a valid
     * estimate exists; otherwise, <tt>-1</tt>.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid
     * estimate exists; otherwise, <tt>-1</tt>
     *
     * @// TODO: 30/06/2017 confirm if this is correct
     * "return incomingBitrate.getRate()"
     */
    @Override
    public long getLatestEstimate() {
        return incomingBitrate.getRate();
    }

    @Override
    public Collection<Integer> getSsrcs() {

        synchronized (critSect)
        {
            for(Long ssrcValue : ssrcs_.keySet()){
                Number value = ssrcValue;
                ssrcs.add(value.intValue());
            }
            return ssrcs;
        }

    }


    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    @Override
    public void removeStream(int ssrc)
    {
        synchronized (critSect) {
            try {
                ssrcs_.remove(ssrc);
            }
            catch (ArrayIndexOutOfBoundsException e)
            {
                logger.info("Cannot remove SSRC, "
                        + "SSRC not found");
            }
        }
    }

    @Override
    public void setMinBitrate(int minBitrateBps)
    {
        // Called from both the configuration thread and the network thread.
        // Shouldn't be called from the network thread in the future.
        synchronized (critSect)
        {
            remoteRate.setMinBitrate(minBitrateBps);
        }

    }

    public class Cluster {
        double sendMeanMs = 0L;
        double recvMeanMs = 0L;
        int meanSize = 0;
        int count = 0;
        int numAboveMinDelta = 0;

        public Cluster() {
        }

        public Cluster(double send_mean_ms, double recv_mean_ms,
                       int mean_size, int counter, int num_above_min_delta)
        {
            this.sendMeanMs = send_mean_ms;
            this.recvMeanMs = recv_mean_ms;
            this.meanSize = mean_size;
            this.count = counter;
            this.numAboveMinDelta = num_above_min_delta;

        }


        public int getSendBitrateBps() {

            //RTC_CHECK_GT(this.sendMeanMs, 0.0f);
            return (int) (this.meanSize * 8 * 1000 / sendMeanMs);
        }

        public int getRecvBitrateBps() {
           // RTC_CHECK_GT(this.recvMeanMs, 0.0f);
            return (int) (this.meanSize * 8 * 1000 / this.recvMeanMs);
        }
        // TODO(holmer): Add some variance metric as well?
    }

    public class Probe
    {
        long sendTimeMs = -1L;
        long recvTimeMs = -1L;
        long payloadSize = 0;

        public Probe(long send_time_ms, long recv_time_ms, long payload_size)
        {
            this.sendTimeMs = send_time_ms;
            this.recvTimeMs = recv_time_ms;
            this.payloadSize = payload_size;
        }
    }
}
