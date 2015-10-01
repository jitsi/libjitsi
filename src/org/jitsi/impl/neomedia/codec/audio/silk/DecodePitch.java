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

import static org.jitsi.impl.neomedia.codec.audio.silk.CommonPitchEstDefines.*;
import static org.jitsi.impl.neomedia.codec.audio.silk.Macros.*;

/**
 * Pitch analyzer function.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class DecodePitch
{
    /**
     * Pitch analyzer function.
     * @param lagIndex
     * @param contourIndex
     * @param pitch_lags 4 pitch values.
     * @param Fs_kHz sampling frequency(kHz).
     */
    static void SKP_Silk_decode_pitch(
            int          lagIndex,                        /* I                             */
            int          contourIndex,                    /* O                             */
            int          pitch_lags[],                    /* O 4 pitch values              */
            int          Fs_kHz                           /* I sampling frequency (kHz)    */
    )
    {
        int lag, i, min_lag;

        min_lag = SKP_SMULBB(PITCH_EST_MIN_LAG_MS, Fs_kHz);

        /* Only for 24 / 16 kHz version for now */
        lag = min_lag + lagIndex;
        if( Fs_kHz == 8 ) {
            /* Only a small codebook for 8 khz */
            for( i = 0; i < PITCH_EST_NB_SUBFR; i++ ) {
                pitch_lags[ i ] = lag + PitchEstTables.SKP_Silk_CB_lags_stage2[ i ][ contourIndex ];
            }
        } else {
            for( i = 0; i < PITCH_EST_NB_SUBFR; i++ ) {
                pitch_lags[ i ] = lag + PitchEstTables.SKP_Silk_CB_lags_stage3[ i ][ contourIndex ];
            }
        }
    }
}
