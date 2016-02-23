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
