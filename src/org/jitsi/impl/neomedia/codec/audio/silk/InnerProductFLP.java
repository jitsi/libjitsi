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
 * inner product of two SKP_float arrays, with result as double.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class InnerProductFLP
{
    /**
     * inner product of two SKP_float arrays, with result as double.
     * @param data1 vector1.
     * @param data1_offset offset of valid data.
     * @param data2 vector2.
     * @param data2_offset offset of valid data.
     * @param dataSize length of vectors.
     * @return result.
     */
    static double SKP_Silk_inner_product_FLP(    /* O    result              */
        float[]     data1,         /* I    vector 1            */
        int data1_offset,
        float[]     data2,         /* I    vector 2            */
        int data2_offset,
        int         dataSize       /* I    length of vectors   */
    )
    {
        int  i, dataSize4;
        double   result;

        /* 4x unrolled loop */
        result = 0.0f;
        dataSize4 = dataSize & 0xFFFC;
        for( i = 0; i < dataSize4; i += 4 )
        {
            result += data1[ data1_offset + i + 0 ] * data2[ data2_offset + i + 0 ] +
                      data1[ data1_offset + i + 1 ] * data2[ data2_offset + i + 1 ] +
                      data1[ data1_offset + i + 2 ] * data2[ data2_offset + i + 2 ] +
                      data1[ data1_offset + i + 3 ] * data2[ data2_offset + i + 3 ];
        }

        /* add any remaining products */
        for( ; i < dataSize; i++ )
        {
            result += data1[ data1_offset+i ] * data2[ data2_offset+i ];
        }

        return result;
    }
}
