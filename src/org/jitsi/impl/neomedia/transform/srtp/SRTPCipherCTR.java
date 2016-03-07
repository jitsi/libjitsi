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
import org.bouncycastle.crypto.params.*;

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

    private final byte[] tmpCipherBlock = new byte[BLKLEN];
    private final BlockCipher cipher;

    public SRTPCipherCTR(BlockCipher cipher)
    {
        this.cipher = cipher;
    }

    public void init(byte[] key)
    {
        if (key.length != BLKLEN)
            throw new IllegalArgumentException("key.length != BLKLEN");

        cipher.init(true, new KeyParameter(key));
    }

    /**
     * Process (encrypt/decrypt) data from offset for len bytes
     * iv is modified by this function but you MUST never reuse
     * an IV so it's ok
     *
     * @param data byte array to be processed
     * @param off the offset
     * @param len the length
     * @param iv initial value of the counter (this value is modified)
     *           iv.length == BLKLEN
     */
    public void process(byte[] data, int off, int len, byte[] iv)
    {
        if (iv.length != BLKLEN)
            throw new IllegalArgumentException("iv.length != BLKLEN");
        if (off < 0)
            throw new IllegalArgumentException("off < 0");
        if (len < 0)
            throw new IllegalArgumentException("len < 0");
        if (off + len > data.length)
            throw new IllegalArgumentException("off + len > data.length");
        /*
         * we increment only the last 16 bits of the iv, so we can encrypt
         * a maximum of 2^16 blocks, ie 1048576 bytes
         */
        if (data.length > 1048576)
            throw new IllegalArgumentException("data.length > 1048576");

        int l = len, o = off;
        while (l >= BLKLEN)
        {
            cipher.processBlock(iv, 0, tmpCipherBlock, 0);
            //incr counter
            if(++iv[15] == 0) ++iv[14];
            //unroll XOR loop to force java to optimise it
            data[o+0]  ^= tmpCipherBlock[0];
            data[o+1]  ^= tmpCipherBlock[1];
            data[o+2]  ^= tmpCipherBlock[2];
            data[o+3]  ^= tmpCipherBlock[3];
            data[o+4]  ^= tmpCipherBlock[4];
            data[o+5]  ^= tmpCipherBlock[5];
            data[o+6]  ^= tmpCipherBlock[6];
            data[o+7]  ^= tmpCipherBlock[7];
            data[o+8]  ^= tmpCipherBlock[8];
            data[o+9]  ^= tmpCipherBlock[9];
            data[o+10] ^= tmpCipherBlock[10];
            data[o+11] ^= tmpCipherBlock[11];
            data[o+12] ^= tmpCipherBlock[12];
            data[o+13] ^= tmpCipherBlock[13];
            data[o+14] ^= tmpCipherBlock[14];
            data[o+15] ^= tmpCipherBlock[15];
            l -= BLKLEN;
            o += BLKLEN;
        }

        if (l > 0)
        {
            cipher.processBlock(iv, 0, tmpCipherBlock, 0);
            //incr counter
            if(++iv[15] == 0) ++iv[14];
            for (int i = 0; i < l; i++)
                data[o+i] ^= tmpCipherBlock[i];
        }
    }
}
