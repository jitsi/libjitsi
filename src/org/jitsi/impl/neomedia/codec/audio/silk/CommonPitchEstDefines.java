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
package org.jitsi.impl.neomedia.codec.audio.silk;

/**
 * Definitions For Fix pitch estimator.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class CommonPitchEstDefines
{
    static final int PITCH_EST_MAX_FS_KHZ =               24; /* Maximum sampling frequency used */

    static final int PITCH_EST_FRAME_LENGTH_MS =          40; /* 40 ms */

    static final int PITCH_EST_MAX_FRAME_LENGTH =         (PITCH_EST_FRAME_LENGTH_MS * PITCH_EST_MAX_FS_KHZ);
    static final int PITCH_EST_MAX_FRAME_LENGTH_ST_1 =    (PITCH_EST_MAX_FRAME_LENGTH >> 2);
    static final int PITCH_EST_MAX_FRAME_LENGTH_ST_2 =    (PITCH_EST_MAX_FRAME_LENGTH >> 1);
//TODO: PITCH_EST_SUB_FRAME is neither defined nor used, temporally ignore it;
//    static final int PITCH_EST_MAX_SF_FRAME_LENGTH =      (PITCH_EST_SUB_FRAME * PITCH_EST_MAX_FS_KHZ);

    static final int PITCH_EST_MAX_LAG_MS =               18;           /* 18 ms -> 56 Hz */
    static final int PITCH_EST_MIN_LAG_MS =               2;            /* 2 ms -> 500 Hz */
    static final int PITCH_EST_MAX_LAG =                  (PITCH_EST_MAX_LAG_MS * PITCH_EST_MAX_FS_KHZ);
    static final int PITCH_EST_MIN_LAG =                  (PITCH_EST_MIN_LAG_MS * PITCH_EST_MAX_FS_KHZ);

    static final int PITCH_EST_NB_SUBFR =                 4;

    static final int PITCH_EST_D_SRCH_LENGTH =            24;

    static final int PITCH_EST_MAX_DECIMATE_STATE_LENGTH = 7;

    static final int PITCH_EST_NB_STAGE3_LAGS =           5;

    static final int PITCH_EST_NB_CBKS_STAGE2 =           3;
    static final int PITCH_EST_NB_CBKS_STAGE2_EXT =       11;

    static final int PITCH_EST_CB_mn2 =                   1;
    static final int PITCH_EST_CB_mx2 =                   2;

    static final int PITCH_EST_NB_CBKS_STAGE3_MAX =       34;
    static final int PITCH_EST_NB_CBKS_STAGE3_MID =       24;
    static final int PITCH_EST_NB_CBKS_STAGE3_MIN =       16;
}
