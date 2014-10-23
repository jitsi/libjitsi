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
import org.bouncycastle.crypto.macs.*;
import org.bouncycastle.crypto.params.*;
import org.jitsi.bccontrib.macs.SkeinMac;
import org.jitsi.bccontrib.params.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

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
public class SRTPCryptoContext
{
    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether protection against replay attacks is to be
     * activated. The default value is <tt>true</tt>.
     */
    public static final String CHECK_REPLAY_PNAME
        = SRTPCryptoContext.class.getName() + ".checkReplay";

    /**
     * The indicator which determines whether protection against replay attacks
     * is to be activated. The default value is <tt>true</tt>.
     */
    private static boolean checkReplay = true;

    /**
     * The <tt>Logger</tt> used by the <tt>SRTPCryptoContext</tt> class and its
     * instances to print out debug information.
     */
    private static final Logger logger
        = Logger.getLogger(SRTPCryptoContext.class);

    /**
     * The indicator which determines whether the method
     * {@link #readConfigurationServicePropertiesOnce()} is to read the values
     * of certain <tt>ConfigurationService</tt> properties of concern to
     * <tt>SRTPCryptoContext</tt> once during the initialization of the first
     * instance.
     */
    private static boolean readConfigurationServicePropertiesOnce = true;

    /**
     * The replay check windows size.
     */
    private static final long REPLAY_WINDOW_SIZE = 64;

    /**
     * Reads the values of certain <tt>ConfigurationService</tt> properties of
     * concern to <tt>SRTPCryptoContext</tt> once during the initialization of
     * the first instance.
     */
    private static synchronized void readConfigurationServicePropertiesOnce()
    {
        if (readConfigurationServicePropertiesOnce)
            readConfigurationServicePropertiesOnce = false;
        else
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
            checkReplay = cfg.getBoolean(CHECK_REPLAY_PNAME, checkReplay);
    }

    /**
     * Derived session authentication key
     */
    private final byte[] authKey;

    /**
     * The symmetric cipher engines we need here
     */
    private final BlockCipher cipher;

    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    private final SRTPCipherCTR cipherCtr = new SRTPCipherCTR();

    /**
     * Used inside F8 mode only
     */
    private final BlockCipher cipherF8;

    /**
     * Derived session encryption key
     */
    private final byte[] encKey;

    /**
     * For the receiver only, the rollover counter guessed from the sequence
     * number of the received packet that is currently being processed (i.e. the
     * value is valid during the execution of
     * {@link #reverseTransformPacket(RawPacket)} only.) RFC 3711 refers to it
     * by the name <tt>v</tt>.
     */
    private int guessedROC;

    /**
     * Temp store.
     */
    private final byte[] ivStore = new byte[16];

    /**
     * Key Derivation Rate, used to derive session keys from master keys
     */
    private final long keyDerivationRate;

    /**
     * The HMAC object we used to do packet authentication
     */
    private final Mac mac; // used for various HMAC computations

    /**
     * Master encryption key
     */
    private final byte[] masterKey;

    /**
     * Master salting key
     */
    private final byte[] masterSalt;

    /**
     * Master key identifier
     */
    private final byte[] mki = null;

    /**
     * Encryption / Authentication policy for this session
     */
    private final SRTPPolicy policy;

    /**
     * Temp store.
     */
    private final byte[] rbStore = new byte[4];

    /**
     * Bit mask for replay check
     */
    private long replayWindow;

    /**
     * RFC 3711: a 32-bit unsigned rollover counter (ROC), which records how
     * many times the 16-bit RTP sequence number has been reset to zero after
     * passing through 65,535.  Unlike the sequence number (SEQ), which SRTP
     * extracts from the RTP packet header, the ROC is maintained by SRTP as
     * described in Section 3.3.1.
     */
    private int roc;

    /**
     * RFC 3711: for the receiver only, a 16-bit sequence number <tt>s_l</tt>,
     * which can be thought of as the highest received RTP sequence number (see
     * Section 3.3.1 for its handling), which SHOULD be authenticated since
     * message authentication is RECOMMENDED.
     */
    private int s_l = 0;

    /**
     * Derived session salting key
     */
    private final byte[] saltKey;

    /**
     * The indicator which determines whether this instance is used by an SRTP
     * sender (<tt>true</tt>) or receiver (<tt>false</tt>).
     */
    private final boolean sender;

    /**
     * The indicator which determines whether {@link #s_l} has seen set i.e.
     * appropriately initialized.
     */
    private boolean seqNumSet = false;

    /**
     * RTP SSRC of this cryptographic context
     */
    private final int ssrc;

    /**
     * Temp store.
     */
    private final byte[] tagStore;

    /**
     * this is a working store, used by some methods to avoid new operations
     * the methods must use this only to store results for immediate processing
     */
    private final byte[] tempStore = new byte[100];

    /**
     * Constructs an empty SRTPCryptoContext using ssrc. The other parameters
     * are set to default null value.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP
     * sender; <tt>false</tt> if the new instance is to be used by an SRTP
     * receiver
     * @param ssrc SSRC of this SRTPCryptoContext
     */
    public SRTPCryptoContext(boolean sender, int ssrc)
    {
        this.sender = sender;
        this.ssrc = ssrc;

        authKey = null;
        cipher = null;
        cipherF8 = null;
        encKey = null;
        keyDerivationRate = 0;
        mac = null;
        masterKey = null;
        masterSalt = null;
        policy = null;
        roc = 0;
        saltKey = null;
        tagStore = null;
    }

    /**
     * Constructs a normal SRTPCryptoContext based on the given parameters.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP
     * sender; <tt>false</tt> if the new instance is to be used by an SRTP
     * receiver
     * @param ssrc the RTP SSRC that this SRTP cryptographic context protects.
     * @param roc the initial Roll-Over-Counter according to RFC 3711. These
     * are the upper 32 bit of the overall 48 bit SRTP packet index. Refer to
     * chapter 3.2.1 of the RFC.
     * @param keyDerivationRate the key derivation rate defines when to
     * recompute the SRTP session keys. Refer to chapter 4.3.1 in the RFC.
     * @param masterK byte array holding the master key for this SRTP
     * cryptographic context. Refer to chapter 3.2.1 of the RFC about the role
     * of the master key.
     * @param masterS byte array holding the master salt for this SRTP
     * cryptographic context. It is used to computer the initialization vector
     * that in turn is input to compute the session key, session authentication
     * key and the session salt.
     * @param policy SRTP policy for this SRTP cryptographic context, defined
     * the encryption algorithm, the authentication algorithm, etc
     */
    @SuppressWarnings("fallthrough")
    public SRTPCryptoContext(
            boolean sender,
            int ssrc,
            int roc,
            long keyDerivationRate,
            byte[] masterK,
            byte[] masterS,
            SRTPPolicy policy)
    {
        this.sender = sender;
        this.ssrc = ssrc;
        this.roc = roc;
        this.keyDerivationRate = keyDerivationRate;
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
            mac = new HMac(SHA1.createDigest());
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
            mac = new HMac(SHA1.createDigest());
            tagStore = null;
            break;
        }
        this.authKey = authKey;
        this.mac = mac;
        this.tagStore = tagStore;

        readConfigurationServicePropertiesOnce();
    }

    /**
     * Authenticates a specific <tt>RawPacket</tt> if the <tt>policy</tt> of
     * this <tt>SRTPCryptoContext</tt> specifies that authentication is to be
     * performed.
     *
     * @param pkt the <tt>RawPacket</tt> to authenticate
     * @return <tt>true</tt> if the <tt>policy</tt> of this
     * <tt>SRTPCryptoContext</tt> specifies that authentication is to not be
     * performed or <tt>pkt</tt> was successfully authenticated; otherwise,
     * <tt>false</tt>
     */
    private boolean authenticatePacket(RawPacket pkt)
    {
        boolean b = true;

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
                if ((tempStore[i] & 0xff) != (tagStore[i] & 0xff))
                {
                    b = false;
                    break;
                }
            }
        }
        return b;
    }

    /**
     * Authenticates a packet. Calculated authentication tag is returned in
     * {@link #tagStore}.
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
     * Closes this crypto context. The close functions deletes key data and
     * performs a cleanup of this crypto context. Clean up key data, maybe this
     * is the second time however, sometimes we cannot know if the CryptoContext
     * was used and the application called deriveSrtpKeys(...).
     */
    public void close()
    {
        Arrays.fill(masterKey, (byte)0);
        Arrays.fill(masterSalt, (byte)0);
    }

    /**
     * Computes the initialization vector, used later by encryption algorithms,
     * based on the label, the packet index, key derivation rate and master salt
     * key.
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
     * Derives a new SRTPCryptoContext for use with a new SSRC. The method
     * returns a new SRTPCryptoContext initialized with the data of this
     * SRTPCryptoContext. Replacing the SSRC, Roll-over-Counter, and the key
     * derivation rate the application cab use this SRTPCryptoContext to
     * encrypt/decrypt a new stream (Synchronization source) inside one RTP
     * session. Before the application can use this SRTPCryptoContext it must
     * call the deriveSrtpKeys method.
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

            Arrays.fill(authKey, (byte)0);
        }

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
     * Performs Counter Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
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
     * Performs F8 Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
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
     * Transforms an SRTP packet into an RTP packet. The method is called when
     * an SRTP packet is received. Operations done by the this operation
     * include: authentication check, packet replay check and decryption. Both
     * encryption and authentication functionality can be turned off as long as
     * the SRTPPolicy used in this SRTPCryptoContext is requires no encryption
     * and no authentication. Then the packet will be sent out untouched.
     * However, this is not encouraged. If no SRTP feature is enabled, then we
     * shall not use SRTP TransformConnector. We should use the original method
     * (RTPManager managed transportation) instead.
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
        // Stores the guessed rollover counter (ROC) in this.guessedROC.
        long guessedIndex = guessIndex(seqNo);
        boolean b = false;

        // Replay control
        if (checkReplay(seqNo, guessedIndex))
        {
            // Authenticate the packet.
            if (authenticatePacket(pkt))
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

                /*
                 * Update the rollover counter and highest sequence number if
                 * necessary.
                 */
                update(seqNo, guessedIndex);

                b = true;
            }
        }

        return b;
    }

    /**
     * Transforms an RTP packet into an SRTP packet. The method is called when a
     * normal RTP packet ready to be sent. Operations done by the transformation
     * may include: encryption, using either Counter Mode encryption, or F8 Mode
     * encryption, adding authentication tag, currently HMC SHA1 method. Both
     * encryption and authentication functionality can be turned off as long as
     * the SRTPPolicy used in this SRTPCryptoContext is requires no encryption
     * and no authentication. Then the packet will be sent out untouched.
     * However, this is not encouraged. If no SRTP feature is enabled, then we
     * shall not use SRTP TransformConnector. We should use the original method
     * (RTPManager managed transportation) instead.
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
}
