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
 */
public class SDKAPI
{
    static final int SILK_MAX_FRAMES_PER_PACKET = 5;
}

/**
 * Struct for TOC (Table of Contents).
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_TOC_struct
{
    int     framesInPacket;                             /* Number of 20 ms frames in packet     */
    int     fs_kHz;                                     /* Sampling frequency in packet         */
    int     inbandLBRR;                                 /* Does packet contain LBRR information */
    int     corrupt;                                    /* Packet is corrupt                    */
    int[]     vadFlags = new int[SDKAPI.SILK_MAX_FRAMES_PER_PACKET ]; /* VAD flag for each frame in packet    */
    int[]     sigtypeFlags = new int[SDKAPI.SILK_MAX_FRAMES_PER_PACKET ]; /* Signal type for each frame in packet */
}
