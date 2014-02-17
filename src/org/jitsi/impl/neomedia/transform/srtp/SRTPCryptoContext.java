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

import javax.media.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.digests.*;
import org.bouncycastle.crypto.engines.*;
import org.bouncycastle.crypto.macs.*;
import org.bouncycastle.crypto.params.*;
import org.jitsi.bccontrib.macs.SkeinMac;
import org.jitsi.bccontrib.params.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

/**
 * SRTPCryptoContext class is the core class of SRTP implementation.
 * There can be multiple SRTP sources in one SRTP session. And each SRTP stream
 * has a corresponding SRTPCryptoContext object, identified by SSRC. In this
 * way, different sources can be protected independently.
 *
 * SRTPCryptoContext class acts as a manager class and maintains all the
 * information used in SRTP transformation. It is responsible for deriving
 * encryption keys / salting keys / authentication keys from master keys. And
 * it will invoke certain class to encrypt / decrypt (transform / reverse
 * transform) RTP packets. It will hold a replay check db and do replay check
 * against incoming packets.
 *
 * Refer to section 3.2 in RFC3711 for detailed description of cryptographic
 * context.
 *
 * Cryptographic related parameters, i.e. encryption mode / authentication mode,
 * master encryption key and master salt key are determined outside the scope
 * of SRTP implementation. They can be assigned manually, or can be assigned
 * automatically using some key management protocol, such as MIKEY (RFC3830),
 * SDES (RFC4568) or Phil Zimmermann's ZRTP protocol (RFC6189).
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public class SRTPCryptoContext
{
    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether protection against replay attacks is to be
     * activated. The default value is <tt>true</tt>.
     */
    private static final String CHECK_REPLAY_PROPERTY_NAME
        = SRTPCryptoContext.class.getName() + ".checkReplay";

    /**
     * The indicator which determines whether protection against replay attacks
     * is to be activated. The default value is <tt>true</tt>.
     */
    private static Boolean checkReplay;

    /**
     * The indicator which determines whether <tt>SRTPCryptoContext</tt> is to
     * decrypt the payload of <tt>RawPacket<tt>s flagged with
     * {@link Buffer#FLAG_SILENCE}.
     */
    private static boolean DECRYPT_SILENCE = false;

    /**
     * The <tt>Logger</tt> used by the <tt>SRTPCryptoContext</tt> class and its
     * instances to print out debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SRTPCryptoContext.class);

    /**
     * The replay check windows size.
     */
    private static final long REPLAY_WINDOW_SIZE = 64;

    /**
     * RTP SSRC of this cryptographic context
     */
    private final int ssrc;

    /**
     * Master key identifier
     */
    private byte[] mki;

    /**
     * RFC 3711: a 32-bit unsigned rollover counter (ROC), which records how
     * many times the 16-bit RTP sequence number has been reset to zero after
     * passing through 65,535.  Unlike the sequence number (SEQ), which SRTP
     * extracts from the RTP packet header, the ROC is maintained by SRTP as
     * described in Section 3.3.1.
     */
    private int roc;

    /**
     * For the receiver only, the rollover counter guessed from the sequence
     * number of the received packet that is currently being processed (i.e. the
     * value is valid during the execution of
     * {@link #reverseTransformPacket(RawPacket)} only.) RFC 3711 refers to it
     * by the name <tt>v</tt>.
     */
    private int guessedROC;

    /**
     * RFC 3711: for the receiver only, a 16-bit sequence number <tt>s_l</tt>,
     * which can be thought of as the highest received RTP sequence number (see
     * Section 3.3.1 for its handling), which SHOULD be authenticated since
     * message authentication is RECOMMENDED.
     */
    private int s_l = 0;

    /**
     * The indicator which determines whether {@link #s_l} has seen set i.e.
     * appropriately initialized.
     */
    private boolean seqNumSet = false;

    /**
     * Key Derivation Rate, used to derive session keys from master keys
     */
    private long keyDerivationRate;

    /**
     * Bit mask for replay check
     */
    private long replayWindow;

    /**
     * Master encryption key
     */
    private byte[] masterKey;

    /**
     * Master salting key
     */
    private byte[] masterSalt;

    /**
     * Derived session encryption key
     */
    private byte[] encKey;

    /**
     * Derived session authentication key
     */
    private byte[] authKey;

    /**
     * Derived session salting key
     */
    private byte[] saltKey;

    /**
     * Encryption / Authentication policy for this session
     */
    private final SRTPPolicy policy;

    /**
     * The HMAC object we used to do packet authentication
     */
    private Mac mac; // used for various HMAC computations

    /**
     * The symmetric cipher engines we need here
     */
    private BlockCipher cipher = null;

    /**
     * Used inside F8 mode only
     */
    private BlockCipher cipherF8 = null;

    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    private final SRTPCipherCTR cipherCtr = new SRTPCipherCTR();

    /**
     * Temp store.
     */
    private final byte[] tagStore;

    /**
     * Temp store.
     */
    private final byte[] ivStore = new byte[16];

    /**
     * Temp store.
     */
    private final byte[] rbStore = new byte[4];

    /**
     * this is a working store, used by some methods to avoid new operations
     * the methods must use this only to store results for immediate processing
     */
    private final byte[] tempStore = new byte[100];

    /**
     * The indicator which determines whether this instance is used by an SRTP
     * sender (<tt>true</tt>) or receiver (<tt>false</tt>).
     */
    private final boolean sender;

    /**
     * Construct an empty SRTPCryptoContext using ssrc.
     * The other parameters are set to default null value.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP
     * sender; <tt>false</tt> if the new instance is to be used by an SRTP
     * receiver
     * @param ssrc SSRC of this SRTPCryptoContext
     */
    public SRTPCryptoContext(boolean sender, int ssrc)
    {
        authKey = null;
        encKey = null;
        keyDerivationRate = 0;
        masterKey = null;
        masterSalt = null;
        mki = null;
        policy = null;
        roc = 0;
        this.sender = sender;
        this.ssrc = ssrc;
        tagStore = null;
    }

    /**
     * Construct a normal SRTPCryptoContext based on the given parameters.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP
     * sender; <tt>false</tt> if the new instance is to be used by an SRTP
     * receiver
     * @param ssrc the RTP SSRC that this SRTP cryptographic context protects.
     * @param rocIn the initial Roll-Over-Counter according to RFC 3711. These
     * are the upper 32 bit of the overall 48 bit SRTP packet index. Refer to
     * chapter 3.2.1 of the RFC.
     * @param kdr the key derivation rate defines when to recompute the SRTP
     * session keys. Refer to chapter 4.3.1 in the RFC.
     * @param masterK byte array holding the master key for this SRTP
     * cryptographic context. Refer to chapter 3.2.1 of the RFC about the role
     * of the master key.
     * @param masterS byte array holding the master salt for this SRTP
     * cryptographic context. It is used to computer the initialization vector
     * that in turn is input to compute the session key, session authentication
     * key and the session salt.
     * @param policyIn SRTP policy for this SRTP cryptographic context, defined
     * the encryption algorithm, the authentication algorithm, etc
     */
    @SuppressWarnings("fallthrough")
    public SRTPCryptoContext(
            boolean sender,
            int ssrc,
            int rocIn,
            long kdr,
            byte[] masterK,
            byte[] masterS,
            SRTPPolicy policyIn)
    {
        keyDerivationRate = kdr;
        mki = null;
        roc = rocIn;
        policy = policyIn;
        this.sender = sender;
        this.ssrc = ssrc;

        masterKey = new byte[policy.getEncKeyLength()];
        System.arraycopy(masterK, 0, masterKey, 0, policy.getEncKeyLength());

        masterSalt = new byte[policy.getSaltKeyLength()];
        System.arraycopy(masterS, 0, masterSalt, 0, policy.getSaltKeyLength());

        mac = new HMac(new SHA1Digest());

        switch (policy.getEncType())
        {
        case SRTPPolicy.NULL_ENCRYPTION:
            encKey = null;
            saltKey = null;
            break;

        case SRTPPolicy.AESF8_ENCRYPTION:
            cipherF8 = new AESFastEngine();
            //$FALL-THROUGH$

        case SRTPPolicy.AESCM_ENCRYPTION:
            cipher = new AESFastEngine();
            encKey = new byte[policy.getEncKeyLength()];
            saltKey = new byte[policy.getSaltKeyLength()];
            break;

        case SRTPPolicy.TWOFISHF8_ENCRYPTION:
            cipherF8 = new TwofishEngine();

        case SRTPPolicy.TWOFISH_ENCRYPTION:
            cipher = new TwofishEngine();
            encKey = new byte[this.policy.getEncKeyLength()];
            saltKey = new byte[this.policy.getSaltKeyLength()];
            break;
        }

        switch (policy.getAuthType())
        {
        case SRTPPolicy.NULL_AUTHENTICATION:
            authKey = null;
            tagStore = null;
            break;

        case SRTPPolicy.HMACSHA1_AUTHENTICATION:
            mac = new HMac(new SHA1Digest());
            authKey = new byte[policy.getAuthKeyLength()];
            tagStore = new byte[mac.getMacSize()];
            break;

        case SRTPPolicy.SKEIN_AUTHENTICATION:
            mac = new SkeinMac();
            authKey = new byte[policy.getAuthKeyLength()];
            tagStore = new byte[policy.getAuthTagLength()];
            break;

        default:
            tagStore = null;
        }

        // checkReplay
        synchronized (SRTPCryptoContext.class)
        {
            if (SRTPCryptoContext.checkReplay == null)
            {
                ConfigurationService cfg = LibJitsi.getConfigurationService();
                boolean checkReplay = true;

                if (cfg != null)
                {
                    checkReplay
                        = cfg.getBoolean(
                                CHECK_REPLAY_PROPERTY_NAME,
                                checkReplay);
                }
                SRTPCryptoContext.checkReplay = Boolean.valueOf(checkReplay);
            }
        }
    }

    /**
     * Close the crypto context.
     *
     * The close functions deletes key data and performs a cleanup of the
     * crypto context.
     *
     * Clean up key data, maybe this is the second time however, sometimes
     * we cannot know if the CryptoCOntext was used and the application called
     * deriveSrtpKeys(...).
     */
    public void close()
    {
        Arrays.fill(masterKey, (byte)0);
        Arrays.fill(masterSalt, (byte)0);
    }

    /**
     * Get the authentication tag length of this SRTP cryptographic context
     *
     * @return the authentication tag length of this SRTP cryptographic context
     */
    public int getAuthTagLength()
    {
        return policy.getAuthTagLength();
    }

    /**
     * Get the MKI length of this SRTP cryptographic context
     *
     * @return the MKI length of this SRTP cryptographic context
     */
    public int getMKILength()
    {
        return (mki == null) ? 0 : mki.length;
    }

    /**
     * Get the SSRC of this SRTP cryptographic context
     *
     * @return the SSRC of this SRTP cryptographic context
     */
    public int getSSRC()
    {
        return ssrc;
    }

    /**
     * Transform a RTP packet into a SRTP packet.
     * This method is called when a normal RTP packet ready to be sent.
     *
     * Operations done by the transformation may include: encryption, using
     * either Counter Mode encryption, or F8 Mode encryption, adding
     * authentication tag, currently HMC SHA1 method.
     *
     * Both encryption and authentication functionality can be turned off
     * as long as the SRTPPolicy used in this SRTPCryptoContext is requires no
     * encryption and no authentication. Then the packet will be sent out
     * untouched. However this is not encouraged. If no SRTP feature is enabled,
     * then we shall not use SRTP TransformConnector. We should use the original
     * method (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is going to be sent out
     */
    public boolean transformPacket(RawPacket pkt)
    {
        int seqNo = pkt.getSequenceNumber();

        if (!seqNumSet)
        {
            seqNumSet = true;
            s_l = seqNo;
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed ROC in this.guessedROC
        long guessedIndex = guessIndex(seqNo);

        /*
         * XXX The invocation of the checkReplay method here is not meant as
         * replay protection but as a consistency check of our implementation.
         */
        if (!checkReplay(seqNo, guessedIndex))
            return false;

        switch (policy.getEncType())
        {
        // Encrypt the packet using Counter Mode encryption.
        case SRTPPolicy.AESCM_ENCRYPTION:
        case SRTPPolicy.TWOFISH_ENCRYPTION:
            processPacketAESCM(pkt);
            break;

        // Encrypt the packet using F8 Mode encryption.
        case SRTPPolicy.AESF8_ENCRYPTION:
        case SRTPPolicy.TWOFISHF8_ENCRYPTION:   
            processPacketAESF8(pkt);
            break;
        }

        /* Authenticate the packet. */
        if (policy.getAuthType() != SRTPPolicy.NULL_AUTHENTICATION)
        {
            authenticatePacketHMCSHA1(pkt, guessedROC);
            pkt.append(tagStore, policy.getAuthTagLength());
        }

        // Update the ROC if necessary.
        update(seqNo, guessedIndex);

        return true;
    }

    /**
     * Transform a SRTP packet into a RTP packet.
     * This method is called when a SRTP packet is received.
     *
     * Operations done by the this operation include:
     * Authentication check, Packet replay check and decryption.
     *
     * Both encryption and authentication functionality can be turned off
     * as long as the SRTPPolicy used in this SRTPCryptoContext is requires no
     * encryption and no authentication. Then the packet will be sent out
     * untouched. However this is not encouraged. If no SRTP feature is enabled,
     * then we shall not use SRTP TransformConnector. We should use the original
     * method (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is just received
     * @return <tt>true</tt> if the packet can be accepted; <tt>false</tt> if
     * the packet failed authentication or failed replay check
     */
    public boolean reverseTransformPacket(RawPacket pkt)
    {
        int seqNo = pkt.getSequenceNumber();

        if (!seqNumSet)
        {
            seqNumSet = true;
            s_l = seqNo;
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed ROC in this.guessedROC
        long guessedIndex = guessIndex(seqNo);

        /* Replay control */
        if (!checkReplay(seqNo, guessedIndex))
            return false;

        /* Authenticate the packet */
        if (policy.getAuthType() != SRTPPolicy.NULL_AUTHENTICATION)
        {
            int tagLength = policy.getAuthTagLength();

            // get original authentication and store in tempStore
            pkt.readRegionToBuff(
                    pkt.getLength() - tagLength,
                    tagLength,
                    tempStore);

            pkt.shrink(tagLength);

            // save computed authentication in tagStore
            authenticatePacketHMCSHA1(pkt, guessedROC);

            for (int i = 0; i < tagLength; i++)
            {
                if ((tempStore[i]&0xff) == (tagStore[i]&0xff))
                    continue;
                else
                    return false;
            }
        }

        /*
         * If the specified pkt represents an RTP packet generated by a muted
         * audio source, (maybe) do not waste processing power on decrypting it.
         */
        if (DECRYPT_SILENCE || ((Buffer.FLAG_SILENCE & pkt.getFlags()) == 0))
        {
            switch (policy.getEncType())
            {
            // Decrypt the packet using Counter Mode encryption.
            case SRTPPolicy.AESCM_ENCRYPTION:
            case SRTPPolicy.TWOFISH_ENCRYPTION:
                processPacketAESCM(pkt);
                break;
    
            // Decrypt the packet using F8 Mode encryption.
            case SRTPPolicy.AESF8_ENCRYPTION:
            case SRTPPolicy.TWOFISHF8_ENCRYPTION:
                processPacketAESF8(pkt);
                break;
            }
        }
        else
        {
            /*
             * Buffer.FLAG_SILENCE is set for RTP (as opposed to RTCP) audio (as
             * opposed to video) packets only.
             */
        }

        // Update the rollover counter and highest sequence number if necessary.
        update(seqNo, guessedIndex);

        return true;
    }

    /**
     * Perform Counter Mode AES encryption / decryption
     *
     * @param pkt the RTP packet to be encrypted / decrypted
     */
    public void processPacketAESCM(RawPacket pkt)
    {
        int ssrc = pkt.getSSRC();
        int seqNo = pkt.getSequenceNumber();
        long index = (((long) guessedROC) << 16) | seqNo;

        // byte[] iv = new byte[16];
        ivStore[0] = saltKey[0];
        ivStore[1] = saltKey[1];
        ivStore[2] = saltKey[2];
        ivStore[3] = saltKey[3];

        int i;

        for (i = 4; i < 8; i++)
        {
            ivStore[i] = (byte)
                (
                    (0xFF & (ssrc >> ((7 - i) * 8)))
                    ^
                    saltKey[i]
                );
        }

        for (i = 8; i < 14; i++)
        {
            ivStore[i] = (byte)
                (
                    (0xFF & (byte) (index >> ((13 - i) * 8)))
                    ^
                    saltKey[i]
                );
        }

        ivStore[14] = ivStore[15] = 0;

        int payloadOffset = pkt.getHeaderLength();
        int payloadLength = pkt.getPayloadLength();

        cipherCtr.process(
                cipher,
                pkt.getBuffer(), pkt.getOffset() + payloadOffset, payloadLength,
                ivStore);
    }

    /**
     * Perform F8 Mode AES encryption / decryption
     *
     * @param pkt the RTP packet to be encrypted / decrypted
     */
    public void processPacketAESF8(RawPacket pkt)
    {
        // byte[] iv = new byte[16];

        // 11 bytes of the RTP header are the 11 bytes of the iv
        // the first byte of the RTP header is not used.
        System.arraycopy(pkt.getBuffer(), pkt.getOffset(), ivStore, 0, 12);
        ivStore[0] = 0;

        // set the ROC in network order into IV
        int roc = guessedROC;

        ivStore[12] = (byte) (roc >> 24);
        ivStore[13] = (byte) (roc >> 16);
        ivStore[14] = (byte) (roc >> 8);
        ivStore[15] = (byte) roc;

        int payloadOffset = pkt.getHeaderLength();
        int payloadLength = pkt.getPayloadLength();

        SRTPCipherF8.process(
                cipher,
                pkt.getBuffer(), pkt.getOffset() + payloadOffset, payloadLength,
                ivStore,
                cipherF8);
    }

    /**
     * Authenticate a packet.
     * Calculated authentication tag is returned.
     *
     * @param pkt the RTP packet to be authenticated
     * @param rocIn Roll-Over-Counter
     */
    private void authenticatePacketHMCSHA1(RawPacket pkt, int rocIn)
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
     * Checks if a packet is a replayed based on its sequence number. The method
     * supports a 64 packet history relative the the specified sequence number.
     * The sequence number is guaranteed to be real (i.e. not faked) through
     * authentication.
     *
     * @param seqNo sequence number of the packet
     * @param guessedIndex guessed ROC
     * @return <tt>true</tt> if the specified sequence number indicates that the
     * packet is not a replayed one; <tt>false</tt>, otherwise
     */
    boolean checkReplay(int seqNo, long guessedIndex)
    {
        if (!checkReplay)
            return true;

        // Compute the index of the previously received packet and its delta to
        // the newly received packet.
        long localIndex = (((long) roc) << 16) | s_l;
        long delta = guessedIndex - localIndex;

        if (delta > 0)
        {
            return true; // Packet not received yet.
        }
        else if (-delta > REPLAY_WINDOW_SIZE)
        {
            if (sender)
            {
                logger.error(
                        "Discarding RTP packet with sequence number " + seqNo
                            + ", SSRC " + Long.toString(0xFFFFFFFFL & ssrc)
                            + " because it is outside the replay window! (roc "
                            + roc + ", s_l " + s_l + ", guessedROC "
                            + guessedROC);
            }
            return false; // Packet too old.
        }
        else if (((replayWindow >> (-delta)) & 0x1) != 0)
        {
            if (sender)
            {
                logger.error(
                        "Discarding RTP packet with sequence number " + seqNo
                            + ", SSRC " + Long.toString(0xFFFFFFFFL & ssrc)
                            + " because it has been received already! (roc "
                            + roc + ", s_l " + s_l + ", guessedROC "
                            + guessedROC);
            }
            return false; // Packet received already!
        }
        else
        {
            return true; // Packet not received yet.
        }
    }

    /**
     * Compute the initialization vector, used later by encryption algorithms,
     * based on the label, the packet index, key derivation rate and master
     * salt key.
     *
     * @param label label specified for each type of iv
     * @param index 48bit RTP packet index
     */
    private void computeIv(long label, long index)
    {
        long key_id;

        if (keyDerivationRate == 0)
        {
            key_id = label << 48;
        }
        else
        {
            key_id = ((label << 48) | (index / keyDerivationRate));
        }
        for (int i = 0; i < 7; i++)
        {
            ivStore[i] = masterSalt[i];
        }
        for (int i = 7; i < 14; i++)
        {
            ivStore[i] = (byte)
                (
                    (byte) (0xFF & (key_id >> (8 * (13 - i))))
                    ^
                    masterSalt[i]
                );
        }
        ivStore[14] = ivStore[15] = 0;
    }

    /**
     * Derives the srtp session keys from the master key
     *
     * @param index the 48 bit SRTP packet index
     */
    public void deriveSrtpKeys(long index)
    {
        // compute the session encryption key
        long label = 0;
        computeIv(label, index);

        KeyParameter encryptionKey = new KeyParameter(masterKey);
        cipher.init(true, encryptionKey);
        Arrays.fill(masterKey, (byte)0);

        cipherCtr.getCipherStream(
                cipher,
                encKey,
                policy.getEncKeyLength(),
                ivStore);

        // compute the session authentication key
        if (authKey != null)
        {
            label = 0x01;
            computeIv(label, index);
            cipherCtr.getCipherStream(cipher, authKey,
                    policy.getAuthKeyLength(), ivStore);

            switch ((policy.getAuthType()))
            {
            case SRTPPolicy.HMACSHA1_AUTHENTICATION:
                KeyParameter key =  new KeyParameter(authKey);
                mac.init(key);
                break;

            case SRTPPolicy.SKEIN_AUTHENTICATION:
                // Skein MAC uses number of bits as MAC size, not just bytes
                ParametersForSkein pfs = new ParametersForSkein(
                        new KeyParameter(authKey),
                        ParametersForSkein.Skein512, tagStore.length*8);
                mac.init(pfs);
                break;
            }
        }
        Arrays.fill(authKey, (byte)0);

        // compute the session salt
        label = 0x02;
        computeIv(label, index);
        cipherCtr.getCipherStream(
                cipher,
                saltKey,
                policy.getSaltKeyLength(),
                ivStore);
        Arrays.fill(masterSalt, (byte)0);

        // As last step: initialize cipher with derived encryption key.
        if (cipherF8 != null)
            SRTPCipherF8.deriveForIV(cipherF8, encKey, saltKey);
        encryptionKey = new KeyParameter(encKey);
        cipher.init(true, encryptionKey);
        Arrays.fill(encKey, (byte)0);
    }

    /**
     * For the receiver only, determines/guesses the SRTP index of a received
     * SRTP packet with a specific sequence number.
     *
     * @param seqNo the sequence number of the received SRTP packet
     * @return the SRTP index of the received SRTP packet with the specified
     * <tt>seqNo</tt>
     */
    private long guessIndex(int seqNo)
    {
        if (s_l < 32768)
        {
            if (seqNo - s_l > 32768)
                guessedROC = roc - 1;
            else
                guessedROC = roc;
        }
        else
        {
            if (s_l - 32768 > seqNo)
                guessedROC = roc + 1;
            else
                guessedROC = roc;
        }

        return (((long) guessedROC) << 16) | seqNo;
    }

    /**
     * For the receiver only, updates the rollover counter (i.e. {@link #roc})
     * and highest sequence number (i.e. {@link #s_l}) in this cryptographic
     * context using the SRTP/packet index calculated by
     * {@link #guessIndex(int)} and updates the replay list (i.e.
     * {@link #replayWindow}). This method is called after all checks were
     * successful.
     *
     * @param seqNo the sequence number of the accepted SRTP packet
     * @param guessedIndex the SRTP index of the accepted SRTP packet calculated
     * by <tt>guessIndex(int)</tt>
     */
    private void update(int seqNo, long guessedIndex)
    {
        long delta = guessedIndex - ((((long) roc) << 16) | s_l);

        /* Update the replay bit mask. */
        if (delta > 0)
        {
            replayWindow <<= delta;
            replayWindow |= 1;
        }
        else
        {
            replayWindow |= (1 << -delta);
        }

        if (guessedROC == roc)
        {
            if (seqNo > s_l)
                s_l = seqNo & 0xffff;
        }
        else if (guessedROC == (roc + 1))
        {
            s_l = seqNo & 0xffff;
            roc = guessedROC;
        }
    }

    /**
     * Derive a new SRTPCryptoContext for use with a new SSRC
     *
     * This method returns a new SRTPCryptoContext initialized with the data of
     * this SRTPCryptoContext. Replacing the SSRC, Roll-over-Counter, and the
     * key derivation rate the application cab use this SRTPCryptoContext to
     * encrypt / decrypt a new stream (Synchronization source) inside one RTP
     * session.
     *
     * Before the application can use this SRTPCryptoContext it must call the
     * deriveSrtpKeys method.
     *
     * @param ssrc The SSRC for this context
     * @param roc The Roll-Over-Counter for this context
     * @param deriveRate The key derivation rate for this context
     * @return a new SRTPCryptoContext with all relevant data set.
     */
    public SRTPCryptoContext deriveContext(int ssrc, int roc, long deriveRate)
    {
        return
            new SRTPCryptoContext(
                    sender,
                    ssrc,
                    roc,
                    deriveRate,
                    masterKey,
                    masterSalt,
                    policy);
    }
}
