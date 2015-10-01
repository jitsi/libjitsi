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
 * compute autocorrelation.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class AutocorrelationFLP
{
    /**
     * compute autocorrelation.
     * @param results result (length correlationCount)
     * @param results_offset offset of valid data.
     * @param inputData input data to correlate
     * @param inputData_offset offset of valid data.
     * @param inputDataSize length of input
     * @param correlationCount number of correlation taps to compute
     */
 //TODO: float or double???
    static void SKP_Silk_autocorrelation_FLP(
        float[]       results,           /* O    result (length correlationCount)            */
        int results_offset,
        float[]       inputData,         /* I    input data to correlate                     */
        int inputData_offset,
        int         inputDataSize,      /* I    length of input                             */
        int         correlationCount    /* I    number of correlation taps to compute       */
    )
    {
        int i;

        if ( correlationCount > inputDataSize )
        {
            correlationCount = inputDataSize;
        }

        for( i = 0; i < correlationCount; i++ )
        {
            results[ results_offset+i ] =  (float)InnerProductFLP.SKP_Silk_inner_product_FLP( inputData,inputData_offset, inputData,inputData_offset + i, inputDataSize - i );
        }
    }
}
