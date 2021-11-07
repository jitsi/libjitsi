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
 * @author Dingxin Xu
 */
public class RegularizeCorrelationsFLP
{
    /**
     *
     * @param XX Correlation matrices
     * @param xx Correlation values
     * @param xx_offset offset of valid data.
     * @param noise Noise energy to add
     * @param D Dimension of XX
     */
   static void SKP_Silk_regularize_correlations_FLP(
        float                 []XX,                /* I/O  Correlation matrices                    */
        int                   XX_offset,
        float                 []xx,                /* I/O  Correlation values                      */
        int                   xx_offset,
        float                 noise,              /* I    Noise energy to add                     */
        int                   D                   /* I    Dimension of XX                         */
   )
  {
      int i;

      for( i = 0; i < D; i++ ) {
          XX[XX_offset + i*D+i] += noise;
      }
      xx[ xx_offset ] += noise;
   }
}
