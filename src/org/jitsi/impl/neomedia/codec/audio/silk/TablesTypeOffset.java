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
public class TablesTypeOffset
{
    static final int[] SKP_Silk_type_offset_CDF = {
             0,  37522,  41030,  44212,  65535
    };

    static final int SKP_Silk_type_offset_CDF_offset = 2;

    static final int[][] SKP_Silk_type_offset_joint_CDF =
    {
    {
             0,  57686,  61230,  62358,  65535
    },
    {
             0,  18346,  40067,  43659,  65535
    },
    {
             0,  22694,  24279,  35507,  65535
    },
    {
             0,   6067,   7215,  13010,  65535
    }
    };
}
