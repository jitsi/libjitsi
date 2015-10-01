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
