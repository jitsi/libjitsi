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
 * scale a vector.
 *
 * @author Dingxin Xu
 */
public class ScaleVectorFLP
{
    /**
     * multiply a vector by a constant.
     * @param data1
     * @param gain
     * @param dataSize
     */
    static void SKP_Silk_scale_vector_FLP(
        float           []data1,
        int             data1_offset,
        float           gain,
        int             dataSize
    )
    {
        int  i, dataSize4;

        /* 4x unrolled loop */
        dataSize4 = dataSize & 0xFFFC;
        for( i = 0; i < dataSize4; i += 4 ) {
            data1[ data1_offset + i + 0 ] *= gain;
            data1[ data1_offset + i + 1 ] *= gain;
            data1[ data1_offset + i + 2 ] *= gain;
            data1[ data1_offset + i + 3 ] *= gain;
        }

        /* any remaining elements */
        for( ; i < dataSize; i++ ) {
            data1[ data1_offset + i ] *= gain;
        }
    }
}
