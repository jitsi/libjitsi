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
 * Definitions For FLP pitch estimator.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class PitchEstDefinesFLP
{
    static final float PITCH_EST_FLP_SHORTLAG_BIAS =            0.2f;    /* for logarithmic weighting    */
    static final float PITCH_EST_FLP_PREVLAG_BIAS =             0.2f;    /* for logarithmic weighting    */
    static final float PITCH_EST_FLP_FLATCONTOUR_BIAS =         0.05f;
}
