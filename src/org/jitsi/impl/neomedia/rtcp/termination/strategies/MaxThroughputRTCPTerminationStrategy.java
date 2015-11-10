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
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.rtp.*;

/**
 * Maximizes endpoint throughput. It does that by sending REMB messages with the
 * largest possible exp and mantissa values. This strategy is only meant to be
 * used in tests.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class MaxThroughputRTCPTerminationStrategy
    extends BasicRTCPTerminationStrategy
{
    /**
     * The maximum value of the exponent in the REMB calculation.
     */
    public static final int MAX_EXP = 63;

    /**
     * The maximum value of the mantissa in the REMB calculation.
     */
    public static final int MAX_MANTISSA = 262143;

    /**
     * {@inheritDoc}
     */
    @Override
    protected RTCPREMBPacket makeREMB(
            RemoteBitrateEstimator remoteBitrateEstimator,
            long senderSSRC, long mediaSSRC, long[] dest)
    {
        return
            new RTCPREMBPacket(
                    senderSSRC,
                    mediaSSRC,
                    MAX_EXP, MAX_MANTISSA,
                    dest);
    }
}
