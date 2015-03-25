/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 *
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
     * The symmetric cipher engines we need here
     */
    protected final BlockCipher cipher;

    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    protected final SRTPCipherCTR cipherCtr = new SRTPCipherCTR();

    /**
     * Used inside F8 mode only
     */
    protected final BlockCipher cipherF8;

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
        cipher = null;
        cipherF8 = null;
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

        BlockCipher cipher = null;
        BlockCipher cipherF8 = null;
        byte[] encKey = null;
        byte[] saltKey = null;

        switch (policy.getEncType())
        {
        case SRTPPolicy.NULL_ENCRYPTION:
            break;

        case SRTPPolicy.AESF8_ENCRYPTION:
            cipherF8 = AES.createBlockCipher();
            //$FALL-THROUGH$

        case SRTPPolicy.AESCM_ENCRYPTION:
            cipher = AES.createBlockCipher();
            encKey = new byte[encKeyLength];
            saltKey = new byte[saltKeyLength];
            break;

        case SRTPPolicy.TWOFISHF8_ENCRYPTION:
            cipherF8 = new TwofishEngine();
            //$FALL-THROUGH$

        case SRTPPolicy.TWOFISH_ENCRYPTION:
            cipher = new TwofishEngine();
            encKey = new byte[encKeyLength];
            saltKey = new byte[saltKeyLength];
            break;
        }
        this.cipher = cipher;
        this.cipherF8 = cipherF8;
        this.encKey = encKey;
        this.saltKey = saltKey;

        byte[] authKey;
        Mac mac;
        byte[] tagStore;

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
        default:
            authKey = null;
            mac = null;
            tagStore = null;
            break;
        }
        this.authKey = authKey;
        this.mac = mac;
        this.tagStore = tagStore;
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
