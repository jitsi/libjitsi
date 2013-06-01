/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
