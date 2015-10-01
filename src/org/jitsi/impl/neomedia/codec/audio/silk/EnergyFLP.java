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
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class EnergyFLP
{
    /**
     * sum of squares of a float array, with result as double.
     * @param data
     * @param data_offset
     * @param dataSize
     * @return
     */
//TODO: float or double???
    static double SKP_Silk_energy_FLP
    (
        float[]     data,
        int data_offset,
        int             dataSize
    )
    {
        int  i, dataSize4;
        double   result;

        /* 4x unrolled loop */
        result = 0.0f;
        dataSize4 = dataSize & 0xFFFC;
        for( i = 0; i < dataSize4; i += 4 )
        {
            result += data[data_offset+ i + 0 ] * data[data_offset+ i + 0 ] +
                      data[data_offset+ i + 1 ] * data[data_offset+ i + 1 ] +
                      data[data_offset+ i + 2 ] * data[data_offset+ i + 2 ] +
                      data[data_offset+ i + 3 ] * data[data_offset+ i + 3 ];
        }

        /* add any remaining products */
        for( ; i < dataSize; i++ )
        {
            result += data[data_offset+ i ] * data[data_offset+ i ];
        }

        assert( result >= 0.0 );
        return result;
    }
}
