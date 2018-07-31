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
package org.jitsi.impl.neomedia.codec.video.vp9;

/**
 * A depacketizer from VP9.
 * See {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-02"}
 *
 * @author George Politis
 */
public class DePacketizer
{
    /**
     * E bit from the first byte of the Payload Descriptor.
     */
    private static final byte E_BIT = (byte) (1 << 2);

    /**
     * B bit from the first byte of the Payload Descriptor.
     */
    private static final byte B_BIT = (byte) (1 << 3);

    /**
     * L bit from the first byte of the Payload Descriptor.
     */
    private static final byte L_BIT = (byte) (1 << 5);

    /**
     * P bit from the first byte of the Payload Descriptor.
     */
    private static final byte P_BIT = (byte) (1 << 6);

    /**
     * I bit from the first byte of the Payload Descriptor.
     */
    private static final byte I_BIT = (byte) (1 << 7);
    
    /**
     * Mask for SID value from Layer Indices byte of the Payload Descriptor.
     */
    private static final byte SID_MASK = 0xE;
    
    /**
     * Mask for D value from Layer Indices byte of the Payload Descriptor.
     */
    private static final byte D_MASK = 0x1;

    /**
     * A class that represents the VP9 Payload Descriptor structure defined
     * in {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-02"}
     */
    public static class VP9PayloadDescriptor
    {
        /**
         * Returns <tt>true</tt> if the B bit from the first byte of the payload
         * descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         *
         * @return  <tt>true</tt> if the B bit from the first byte of the
         * payload descriptor has value 0, false otherwise.
         */
        public static boolean isStartOfFrame(byte[] buf, int off, int len)
        {
            // Check if this is the start of a VP9 layer frame in the payload
            // descriptor.

            return isValid(buf, off, len) && (buf[off] & B_BIT) != 0;
        }

        /**
         * Returns <tt>true</tt> if the E bit from the first byte of the payload
         * descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         *
         * @return  <tt>true</tt> if the E bit from the first byte of the
         * payload descriptor has value 0, false otherwise.
         */
        public static boolean isEndOfFrame(byte[] buf, int off, int len)
        {
            // Check if this is the end of a VP9 layer frame in the payload
            // descriptor.

            return isValid(buf, off, len) && (buf[off] & E_BIT) != 0;
        }

        /**
         * Returns <tt>true</tt> if the arguments specify a valid non-empty
         * buffer.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         *
         * @return <tt>true</tt> if the arguments specify a valid non-empty
         * buffer.
         */
        private static boolean isValid(byte[] buf, int off, int len)
        {
            return
                (buf != null && buf.length >= off + len && off > -1 && len > 0);
        }

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         *
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        public static int getTemporalLayerIndex(byte[] buf, int off, int len)
        {
            if (!isValid(buf, off, len) || (buf[off] & L_BIT) == 0)
            {
                return -1;
            }

            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0)
            {
                loff += 1;
                if ((buf[off + 1] & (1 << 7)) != 0)
                {
                    // extended pid.
                    loff += 1;
                }
            }

            return (buf[loff] & 0xE0) >> 5;
        }

        /**
         * Gets the spatial layer index (SID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         *
         * @return the spatial layer index (SID), if that's set, -1 otherwise.
         */
        public static int getSpatialLayerIndex(byte[] buf, int off, int len)
        {
            if (!isValid(buf, off, len) || (buf[off] & L_BIT) == 0)
            {
                return -1;
            }


            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0)
            {
                loff += 1;
                if ((buf[off + 1] & (1 << 7)) != 0)
                {
                    // extended pid.
                    loff += 1;
                }
            }

            return (buf[loff] & 0xE) >> 1;
        }
        
        /**
         * Check if the current the packet contains a key frame
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return true if the frame is a key frame, false otherwise
         */
        public static boolean isKeyFrame(byte[] buf, int off, int len)
        {
            // This packet will have its P bit equal to zero, SID or D
            // bit (described below) equal to zero, and B bit (described below)
            // equal to 1
            
            if (!isValid(buf, off, len))
            {
                return false;
            }

            // P_BIT must be 0 and B_BIT 1
            // L_BIT must be 1 to ensure we can do further checks for SID and D
            if((buf[off] & P_BIT) != 0 || 
               (buf[off] & B_BIT) == 0 || 
               (buf[off] & L_BIT) == 0 )
            {
                return false;
            }

            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0)
            {
                loff += 1;
                if ((buf[off + 1] & (1 << 7)) != 0)
                {
                    // extended pid.
                    loff += 1;
                }
            }
            
            //SID or D bit equal to zero
            return 
              ((buf[loff] & SID_MASK) >> 1) == 0 || (buf[loff] & D_MASK) == 0;
        }
    }

    public static boolean isKeyFrame(byte[] buf, int off, int len)
    {
        return VP9PayloadDescriptor.isKeyFrame(buf, off, len);
    }
}
