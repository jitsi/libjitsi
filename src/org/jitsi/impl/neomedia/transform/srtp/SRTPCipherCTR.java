/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation,
 * which has the following copyright notice:
 *
  Copyright (C) 2004-2006 the Minisip Team

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation; either
  version 2.1 of the License, or (at your option) any later version.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this library; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
*/
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;

/**
 * SRTPCipherCTR implements SRTP Counter Mode AES Encryption (AES-CM).
 * Counter Mode AES Encryption algorithm is defined in RFC3711, section 4.1.1.
 *
 * Other than Null Cipher, RFC3711 defined two two encryption algorithms:
 * Counter Mode AES Encryption and F8 Mode AES encryption. Both encryption
 * algorithms are capable to encrypt / decrypt arbitrary length data, and the
 * size of packet data is not required to be a multiple of the AES block
 * size (128bit). So, no padding is needed.
 *
 * Please note: these two encryption algorithms are specially defined by SRTP.
 * They are not common AES encryption modes, so you will not be able to find a
 * replacement implementation in common cryptographic libraries.
 *
 * As defined by RFC3711: Counter Mode Encryption is mandatory..
 *
 *                        mandatory to impl     optional      default
 * -------------------------------------------------------------------------
 *   encryption           AES-CM, NULL          AES-f8        AES-CM
 *   message integrity    HMAC-SHA1                -          HMAC-SHA1
 *   key derivation       (PRF) AES-CM             -          AES-CM
 *
 * We use AESCipher to handle basic AES encryption / decryption.
 *
 * @author Werner Dittmann (Werner.Dittmann@t-online.de)
 * @author Bing SU (nova.su@gmail.com)
 */
public class SRTPCipherCTR
{
    private static final int BLKLEN = 16;
    private static final int MAX_BUFFER_LENGTH = 10 * 1024;

    private final byte[] cipherInBlock = new byte[BLKLEN];
    private byte[] streamBuf = new byte[1024];
    private final byte[] tmpCipherBlock = new byte[BLKLEN];

    public SRTPCipherCTR()
    {
    }

    /**
     * Computes the cipher stream for AES CM mode. See section 4.1.1 in RFC3711
     * for detailed description.
     *
     * @param out byte array holding the output cipher stream
     * @param length length of the cipher stream to produce, in bytes
     * @param iv initialization vector used to generate this cipher stream
     */
    public void getCipherStream(
            BlockCipher aesCipher,
            byte[] out, int length,
            byte[] iv)
    {
        System.arraycopy(iv, 0, cipherInBlock, 0, 14);

        int ctr, ctrEnd;

        for (ctr = 0, ctrEnd = length / BLKLEN; ctr < ctrEnd; ctr++)
        {
            // compute the cipher stream
            cipherInBlock[14] = (byte) ((ctr & 0xFF00) >> 8);
            cipherInBlock[15] = (byte) (ctr & 0x00FF);

            aesCipher.processBlock(cipherInBlock, 0, out, ctr * BLKLEN);
        }

        // Treat the last bytes:
        cipherInBlock[14] = (byte) ((ctr & 0xFF00) >> 8);
        cipherInBlock[15] = (byte) ((ctr & 0x00FF));

        aesCipher.processBlock(cipherInBlock, 0, tmpCipherBlock, 0);
        System.arraycopy(tmpCipherBlock, 0, out, ctr * BLKLEN, length % BLKLEN);
    }

    public void process(
            BlockCipher cipher,
            byte[] data, int off, int len,
            byte[] iv)
    {
        if (off + len > data.length)
            return;

        // If data fits in inter buffer, use it. Otherwise, allocate bigger
        // buffer and store it (up to a defined maximum size) to use it for
        // later processing.
        byte[] cipherStream;

        if (len > streamBuf.length)
        {
            cipherStream = new byte[len];
            if (cipherStream.length <= MAX_BUFFER_LENGTH)
                streamBuf = cipherStream;
        }
        else
        {
            cipherStream = streamBuf;
        }

        getCipherStream(cipher, cipherStream, len, iv);
        for (int i = 0; i < len; i++)
            data[i + off] ^= cipherStream[i];
    }
}
