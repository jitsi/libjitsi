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
 * Simple opy.
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class ResamplerPrivateCopy
{
    /**
     * Simple copy.
     * @param SS Resampler state (unused).
     * @param out Output signal
     * @param out_offset offset of valid data.
     * @param in Input signal
     * @param in_offset offset of valid data.
     * @param inLen Number of input samples
     */
    static void SKP_Silk_resampler_private_copy(
        Object                        SS,            /* I/O: Resampler state (unused)                */
        short[]                        out,        /* O:    Output signal                             */
        int out_offset,
        short[]                        in,            /* I:    Input signal                            */
        int in_offset,
        int                            inLen       /* I:    Number of input samples                    */
    )
    {
        for(int k=0; k<inLen; k++)
            out[out_offset+k] = in[in_offset+k];
    }
}
