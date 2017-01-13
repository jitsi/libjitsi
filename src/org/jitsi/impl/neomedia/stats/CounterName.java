package org.jitsi.impl.neomedia.stats;

public enum CounterName {

    /**
     * AimdRateControl counters.
     */

    RATE_CONTROL_HOLD("rateControlHold"),
    RATE_CONTROL_INCREASE("rateControlIncrease"),
    RATE_CONTROL_DECREASE("rateControlDecrease"),
    RATE_CONTROL_DROP_TO_MIN_BITRATE("rateControlDropToMinBitrate"),

    /**
     * RemoteBitrateEstimator counters.
     */

    TARGET_BITRATE_BPS("targetBitrateBps"),
    DELTA_ARRIVAL_TIME_MS("deltaArrivalTimeMs"),
    DELTA_DEPARTURE_TIME_MS("deltaDepartureTimeMs"),
    DELTA_SIZE_BYTES("deltaSizeBytes"),
    DELTA_GROUP_DELAY_VARIATION_MS("deltaGroupDelayVariationMs"),
    ESTIMATED_DELTA_GROUP_DELAY_VARIATION_MS("estimatedDeltaGroupDelayVariationMs"),
    ESTIMATION_NUM_DELTAS("estimationNumDeltas"),
    INCOMING_BITRATE_BPS("incomingBitrateBps"),
    BANDWIDTH_NORMAL("bandwidthNormal"),
    BANDWIDTH_OVERUSE("bandwidthOveruse"),
    BANDWIDTH_UNDERUSE("bandwidthUnderuse"),

    /**
     * SendSideBandwidthEstimation counters.
     */

    RECEIVER_ESTIMATED_MAXIMUM_BITRATE_BPS("receiverEstimatedMaximumBitrateBps"),
    AVAILABLE_SEND_BANDWIDTH_BPS("availableSendBandwidthBps"),
    ;

    private final String name;

    CounterName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
