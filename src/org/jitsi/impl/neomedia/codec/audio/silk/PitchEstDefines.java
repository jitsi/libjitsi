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
 * Definitions For Fix pitch estimator
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class PitchEstDefines
{
    static final int PITCH_EST_SHORTLAG_BIAS_Q15 =        6554;    /* 0.2f. for logarithmic weighting    */
    static final int PITCH_EST_PREVLAG_BIAS_Q15 =         6554;    /* Prev lag bias    */
    static final int PITCH_EST_FLATCONTOUR_BIAS_Q20 =     52429;   /* 0.05f */
}
