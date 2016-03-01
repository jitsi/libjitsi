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
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation,
 * which has the following copyright notice:
 *
 * Copyright (C) 2004-2006 the Minisip Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
*/
package org.jitsi.impl.neomedia.transform.srtp;

import java.util.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.engines.*;
import org.jitsi.bccontrib.macs.*;
import org.jitsi.impl.neomedia.*;

/**
 * SRTPCryptoContext class is the core class of SRTP implementation. There can
 * be multiple SRTP sources in one SRTP session. And each SRTP stream has a
 * corresponding SRTPCryptoContext object, identified by SSRC. In this way,
 * different sources can be protected independently.
 *
 * SRTPCryptoContext class acts as a manager class and maintains all the
 * information used in SRTP transformation. It is responsible for deriving
 * encryption/salting/authentication keys from master keys. And it will invoke
 * certain class to encrypt/decrypt (transform/reverse transform) RTP packets.
 * It will hold a replay check db and do replay check against incoming packets.
 *
 * Refer to section 3.2 in RFC3711 for detailed description of cryptographic
 * context.
 *
 * Cryptographic related parameters, i.e. encryption mode / authentication mode,
 * master encryption key and master salt key are determined outside the scope of
 * SRTP implementation. They can be assigned manually, or can be assigned
 * automatically using some key management protocol, such as MIKEY (RFC3830),
 * SDES (RFC4568) or Phil Zimmermann's ZRTP protocol (RFC6189).
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 */
class BaseSRTPCryptoContext
{
    /**
     * The replay check windows size.
     */
    protected static final long REPLAY_WINDOW_SIZE = 64;

    /**
     * Derived session authentication key
     */
    protected final byte[] authKey;

    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    protected final SRTPCipherCTR cipherCtr;

    /**
     * Derived session encryption key
     */
    protected final byte[] encKey;

    /**
     * Temp store.
     */
    protected final byte[] ivStore = new byte[16];

    /**
     * The HMAC object we used to do packet authentication
     */
    protected final Mac mac; // used for various HMAC computations

    /**
     * Master encryption key
     */
    protected final byte[] masterKey;

    /**
     * Master salting key
     */
    protected final byte[] masterSalt;

    /**
     * Master key identifier
     */
    private final byte[] mki = null;

    /**
     * Encryption / Authentication policy for this session
     */
    protected final SRTPPolicy policy;

    /**
     * Temp store.
     */
    protected final byte[] rbStore = new byte[4];

    /**
     * Bit mask for replay check
     */
    protected long replayWindow;

    /**
     * Derived session salting key
     */
    protected final byte[] saltKey;

    /**
     * RTP/RTCP SSRC of this cryptographic context
     */
    protected final int ssrc;

    /**
     * Temp store.
     */
    protected final byte[] tagStore;

    /**
     * this is a working store, used by some methods to avoid new operations
     * the methods must use this only to store results for immediate processing
     */
    protected final byte[] tempStore = new byte[100];

    protected BaseSRTPCryptoContext(int ssrc)
    {
        this.ssrc = ssrc;

        authKey = null;
        cipherCtr = null;
        encKey = null;
        mac = null;
        masterKey = null;
        masterSalt = null;
        policy = null;
        saltKey = null;
        tagStore = null;
    }

    @SuppressWarnings("fallthrough")
    protected BaseSRTPCryptoContext(
            int ssrc,
            byte[] masterK,
            byte[] masterS,
            SRTPPolicy policy)
    {
        this.ssrc = ssrc;
        this.policy = policy;

        int encKeyLength = policy.getEncKeyLength();

        masterKey = new byte[encKeyLength];
        System.arraycopy(masterK, 0, masterKey, 0, encKeyLength);

        int saltKeyLength = policy.getSaltKeyLength();

        masterSalt = new byte[saltKeyLength];
        System.arraycopy(masterS, 0, masterSalt, 0, saltKeyLength);

        switch (policy.getEncType())
        {
        case SRTPPolicy.AESCM_ENCRYPTION:
            cipherCtr = new SRTPCipherCTRJava(AES.createBlockCipher());
            encKey = new byte[encKeyLength];
            saltKey = new byte[saltKeyLength];
            break;

        case SRTPPolicy.TWOFISH_ENCRYPTION:
            cipherCtr = new SRTPCipherCTRJava(new TwofishEngine());
            encKey = new byte[encKeyLength];
            saltKey = new byte[saltKeyLength];
            break;

        case SRTPPolicy.NULL_ENCRYPTION:
            cipherCtr = null;
            encKey = null;
            saltKey = null;
            break;
        default:
            throw new IllegalArgumentException("Invalid SRTPPolicy EncType");
        }

        switch (policy.getAuthType())
        {
        case SRTPPolicy.HMACSHA1_AUTHENTICATION:
            authKey = new byte[policy.getAuthKeyLength()];
            mac = HMACSHA1.createMac();
            tagStore = new byte[mac.getMacSize()];
            break;

        case SRTPPolicy.SKEIN_AUTHENTICATION:
            authKey = new byte[policy.getAuthKeyLength()];
            mac = new SkeinMac();
            tagStore = new byte[policy.getAuthTagLength()];
            break;

        case SRTPPolicy.NULL_AUTHENTICATION:
            authKey = null;
            mac = null;
            tagStore = null;
            break;
        default:
            throw new IllegalArgumentException("Invalid SRTPPolicy AuthType");
        }
    }

    /**
     * Authenticates a packet. Calculated authentication tag is returned/stored
     * in {@link #tagStore}.
     *
     * @param pkt the RTP packet to be authenticated
     * @param rocIn Roll-Over-Counter
     */
    synchronized protected void authenticatePacketHMAC(RawPacket pkt, int rocIn)
    {
        mac.update(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
        rbStore[0] = (byte) (rocIn >> 24);
        rbStore[1] = (byte) (rocIn >> 16);
        rbStore[2] = (byte) (rocIn >> 8);
        rbStore[3] = (byte) rocIn;
        mac.update(rbStore, 0, rbStore.length);
        mac.doFinal(tagStore, 0);
    }

    /**
     * Closes this crypto context. The close functions deletes key data and
     * performs a cleanup of this crypto context. Clean up key data, maybe this
     * is the second time. However, sometimes we cannot know if the
     * CryptoContext was used and the application called deriveSrtpKeys(...).
     */
    synchronized public void close()
    {
        Arrays.fill(masterKey, (byte) 0);
        Arrays.fill(masterSalt, (byte) 0);
    }

    /**
     * Gets the authentication tag length of this SRTP cryptographic context
     *
     * @return the authentication tag length of this SRTP cryptographic context
     */
    public int getAuthTagLength()
    {
        return policy.getAuthTagLength();
    }

    /**
     * Gets the MKI length of this SRTP cryptographic context
     *
     * @return the MKI length of this SRTP cryptographic context
     */
    public int getMKILength()
    {
        return (mki == null) ? 0 : mki.length;
    }

    /**
     * Gets the SSRC of this SRTP cryptographic context
     *
     * @return the SSRC of this SRTP cryptographic context
     */
    public int getSSRC()
    {
        return ssrc;
    }
}
