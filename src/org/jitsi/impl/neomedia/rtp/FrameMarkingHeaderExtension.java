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
package org.jitsi.impl.neomedia.rtp;

import org.jitsi.service.neomedia.*;

/**
 * Provides utility functions for the frame marking RTP header extension
 * described in https://tools.ietf.org/html/draft-ietf-avtext-framemarking-03
 *
 * Non-Scalable
 * <pre>{@code
 *  0                   1
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ID=? |  L=0  |S|E|I|D|0 0 0 0|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 * 
 * Scalable
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ID=? |  L=2  |S|E|I|D|B| TID |   LID         |    TL0PICIDX  |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 * @author Boris Grozev
 * @author Sergio Garcia Murillo
 */
public class FrameMarkingHeaderExtension
{
    /**
     * The "start of frame" bit.
     */
    private static byte S_BIT = (byte) 0x80;
    
    /**
     * The "end of frame" bit.
     */
    private static byte E_BIT = (byte) 0x40;

    /**
     * The "independent frame" bit.
     */
    private static byte I_BIT = 0x20;

    /**
     * The bits that need to be set in order for a packet to be considered the
     * first packet of a keyframe.
     */
    private static byte KF_MASK = (byte) (S_BIT | I_BIT);
    
    /**
     * The bits for the temporalId 
     */
    private static byte TID_MASK = (byte) 0x07;

    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the first packet of a keyframe (i.e.
     * the S and I bits are set).
     */
    public static boolean isKeyframe(ByteArrayBuffer baf)
    {
        if (baf == null || baf.getLength() < 2)
        {
            return false;
        }

        // The data follows the one-byte header.
        byte b = baf.getBuffer()[baf.getOffset() + 1];
        return (byte)(b & KF_MASK) == KF_MASK;
    }
    
    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the first packet of a frame (i.e.
     * the S bit is set).
     */
    public static boolean isStartOfFrame(ByteArrayBuffer baf)
    {
        if (baf == null || baf.getLength() < 2)
        {
            return false;
        }

        // The data follows the one-byte header.
        byte b = baf.getBuffer()[baf.getOffset() + 1];
        return (b & S_BIT) != 0;
    }
    
    /**
     * @return true if the extension contained in the given buffer indicates
     * that the corresponding RTP packet is the last packet of a frame (i.e.
     * the E bit is set).
     */    
    public static boolean isEndOfFrame(ByteArrayBuffer baf) {
            
        if (baf == null || baf.getLength() < 2)
        {
            return false;
        }

        // The data follows the one-byte header.
        byte b = baf.getBuffer()[baf.getOffset() + 1];
        return (b & E_BIT) != 0;
    }

    /**
     * 
     * @param baf Header extension byte array
     * @param encoding Encoding type used to parse the LID field
     * @return the spatial layerd id present in the LID or 0 if not present. 
     */
    public static byte getSpatialID(ByteArrayBuffer baf, String encoding) {
        // Only present on scalable version
        if (baf == null || baf.getLength() < 4)
        {
            return 0;
        }
        /*
         * THIS IS STILL NOT YET CORRECTLY DEFINED ON FRAMEMARKING DRAFT!
         */
        return 0;
    }

    /**
     * 
     * @param baf Header extension byte array
     * @return The temporal layer id (the LID bits) or 0 if not present
     */
    public static byte getTemporalID(ByteArrayBuffer baf) {
        if (baf == null || baf.getLength() < 2)
        {
            return 0;
        }
        // The data follows the one-byte header.
        byte b = baf.getBuffer()[baf.getOffset() + 1];
        return (byte)(b & TID_MASK);
    }
}
