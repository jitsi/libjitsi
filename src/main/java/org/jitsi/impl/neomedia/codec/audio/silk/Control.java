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
 * Silk codec encoder/decoder control class.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
public class Control
{
}

/**
 * Class for controlling encoder operation
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_SDK_EncControlStruct
{
    /**
     * (Input) Input signal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    int API_sampleRate;

    /**
     * (Input) Maximum internal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    int maxInternalSampleRate;

    /**
     * (Input) Number of samples per packet; must be equivalent of 20, 40, 60, 80 or 100 ms.
     */
    int packetSize;

    /**
     * (Input) Bitrate during active speech in bits/second; internally limited.
     */
    int bitRate;

    /**
     * (Inpupt) Uplink packet loss in percent (0-100).
     */
    int packetLossPercentage;

    /**
     * (Input) Complexity mode; 0 is lowest; 1 is medium and 2 is highest complexity.
     */
    int complexity;

    /**
     * (Input) Flag to enable in-band Forward Error Correction (FEC); 0/1
     */
    int useInBandFEC;

    /**
     * (Input) Flag to enable discontinuous transmission (DTX); 0/1
     */
    int useDTX;
}

/**
 * Class for controlling decoder operation and reading decoder status.
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_SDK_DecControlStruct
{
    /**
     * (Input) Output signal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    int API_sampleRate;

    /**
     * (Output) Number of samples per frame.
     */
    int frameSize;

    /**
     * (Output) Frames per packet 1, 2, 3, 4, 5.
     */
    int framesPerPacket;

    /**
     * (Output) Flag to indicate that the decoder has remaining payloads internally.
     */
    int moreInternalDecoderFrames;

    /**
     * (Output) Distance between main payload and redundant payload in packets.
     */
    int inBandFECOffset;
}
