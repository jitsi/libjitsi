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
package org.jitsi.service.neomedia.stats;

import org.jitsi.service.neomedia.*;

import java.util.*;

/**
 * An extended interface for accessing the statistics of a {@link MediaStream}.
 *
 * The reason to extend the {@link MediaStreamStats} interface rather than
 * adding methods into it is to allow the implementation to reside in a separate
 * class. This is desirable in order to:
 * 1. Help to keep the old interface for backward compatibility.
 * 2. Provide a "clean" place where future code can be added, thus avoiding
 * further cluttering of the already overly complicated
 * {@link org.jitsi.impl.neomedia.MediaStreamStatsImpl}.
 *
 * @author Boris Grozev
 */
public interface MediaStreamStats2
    extends MediaStreamStats
{
    /**
     * @return the instance which keeps aggregate statistics for the associated
     * {@link MediaStream} in the receive direction.
     */
    ReceiveTrackStats getReceiveStats();

    /**
     * @return the instance which keeps aggregate statistics for the associated
     * {@link MediaStream} in the send direction.
     */
    SendTrackStats getSendStats();

    /**
     * @return the instance which keeps statistics for a particular SSRC in the
     * receive direction.
     */
    ReceiveTrackStats getReceiveStats(long ssrc);

    /**
     * @return the instance which keeps statistics for a particular SSRC in the
     * send direction.
     */
    SendTrackStats getSendStats(long ssrc);

    /**
     * @return all per-SSRC statistics for the send direction.
     */
    Collection<? extends SendTrackStats> getAllSendStats();

    /**
     * @return all per-SSRC statistics for the receive direction.
     */
    Collection<? extends ReceiveTrackStats> getAllReceiveStats();

    /**
     * Clears send ssrc stats.
     * @param ssrc the ssrc to clear.
     */
    void clearSendSsrc(long ssrc);
}
