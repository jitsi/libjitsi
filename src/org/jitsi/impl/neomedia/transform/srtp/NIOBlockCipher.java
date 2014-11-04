/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.nio.*;

import org.bouncycastle.crypto.*;

/**
 * Extends <tt>org.bouncycastle.crypto.BlockCipher</tt> with a
 * <tt>processBlock</tt> method which reads from and writes into
 * <tt>java.nio.ByteBuffer</tt>s.
 *
 * @author Lyubomir Marinov
 */
public interface NIOBlockCipher
    extends BlockCipher
{
    /**
     * Processes one block of input from the <tt>ByteBuffer</tt> <tt>in</tt> and
     * writes it to the <tt>ByteBuffer</tt> out.
     *
     * @param in the <tt>ByteBuffer</tt> containing the input data
     * @param inOff offset into the <tt>ByteBuffer</tt> <tt>in</tt> the data
     * starts at
     * @param out the <tt>ByteBuffer</tt> the output data will be copied into
     * @param outOff the offset into the <tt>ByteBuffer</tt> out the output
     * will start at
     * @exception DataLengthException if there isn't enough data in <tt>in</tt>,
     * or space in <tt>out</tt>
     * @exception IllegalStateException if the cipher isn't initialized
     * @return the number of bytes processed and produced
     */
    public int processBlock(
            ByteBuffer in, int inOff,
            ByteBuffer out, int outOff)
        throws DataLengthException, IllegalStateException;
}
