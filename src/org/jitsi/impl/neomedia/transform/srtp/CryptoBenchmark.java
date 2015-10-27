/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import java.nio.*;
import java.security.*;
import java.util.*;

import javax.crypto.*;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.*;
import org.bouncycastle.crypto.engines.*;
import org.bouncycastle.crypto.macs.*;
import org.bouncycastle.crypto.params.*;

public class CryptoBenchmark
{
    public static void main(String[] args)
        throws Exception
    {
        boolean benchmarkJavaxCryptoCipher = false;
        boolean benchmarkNIOBlockCipher = false;

        for (String arg : args)
        {
            if ("-javax-crypto-cipher".equalsIgnoreCase(arg))
                benchmarkJavaxCryptoCipher = true;
            else if ("-nio-block-cipher".equalsIgnoreCase(arg))
                benchmarkNIOBlockCipher = true;
        }

        Provider sunPKCS11
            = new sun.security.pkcs11.SunPKCS11(
                    "--name=CryptoBenchmark\\n"
                        + "nssDbMode=noDb\\n"
                        + "attributes=compatibility");
        Provider sunJCE = Security.getProvider("SunJCE");

//        for (Provider provider : new Provider[] { sunPKCS11, sunJCE })
//            for (Provider.Service service : provider.getServices())
//                if ("Cipher".equalsIgnoreCase(service.getType()))
//                    System.err.println(service);

        // org.bouncycastle.crypto.Digest & java.security.MessageDigest
        Digest[] digests
            = {
                new SHA1Digest(),
                new OpenSSLDigest(OpenSSLDigest.SHA1)
            };
        MessageDigest[] messageDigests
            = {
                MessageDigest.getInstance("SHA-1"),
                MessageDigest.getInstance("SHA-1", sunPKCS11)
            };
        int maxDigestSize = 0;
        int maxByteLength = 0;

        for (Digest digest : digests)
        {
            int digestSize = digest.getDigestSize();

            if (maxDigestSize < digestSize)
                maxDigestSize = digestSize;

            int byteLength
                = (digest instanceof ExtendedDigest)
                    ? ((ExtendedDigest) digest).getByteLength()
                    : 64;

            if (maxByteLength < byteLength)
                maxByteLength = byteLength;

            System.err.println(
                    digest.getClass().getName() + ": digestSize " + digestSize
                        + ", byteLength " + byteLength + ".");
        }
        for (MessageDigest messageDigest : messageDigests)
        {
            int digestLength = messageDigest.getDigestLength();

            if (maxDigestSize < digestLength)
                maxDigestSize = digestLength;

            System.err.println(
                    messageDigest.getProvider().getClass().getName()
                        + ": digestLength " + digestLength + ".");
        }

        // org.bouncycastle.crypto.BlockCipher
        BlockCipher[] ciphers
            = {
                new AESFastEngine(),
                new BlockCipherAdapter(
                        Cipher.getInstance("AES_128/ECB/NoPadding", sunPKCS11)),
                new BlockCipherAdapter(
                        Cipher.getInstance("AES_128/ECB/NoPadding", sunJCE)),
                new OpenSSLBlockCipher(OpenSSLBlockCipher.AES_128_ECB)
            };

        for (BlockCipher cipher : ciphers)
        {
            Class<?> clazz;

            if (cipher instanceof BlockCipherAdapter)
            {
                clazz
                    = ((BlockCipherAdapter) cipher).getCipher().getProvider()
                        .getClass();
            }
            else
            {
                clazz = cipher.getClass();
            }
            System.err.println(
                    clazz.getName() + ": blockSize " + cipher.getBlockSize());
        }

        // org.bouncycastle.crypto.Mac
        Mac[] macs
            = {
                new HMac(new SHA1Digest()),
                new HMac(new OpenSSLDigest(OpenSSLDigest.SHA1)),
                new OpenSSLHMAC(OpenSSLDigest.SHA1)
            };

        Random random = new Random(System.currentTimeMillis());
        byte[] in = new byte[1024 * maxByteLength];
        ByteBuffer inNIO = ByteBuffer.allocateDirect(in.length);
        byte[] out = new byte[maxDigestSize];
        ByteBuffer outNIO = ByteBuffer.allocateDirect(out.length);
        long time0 = 0;
        int dMax = Math.max(digests.length, messageDigests.length);
        final int iEnd = 1000, jEnd = 1000;
//        Base64.Encoder byteEncoder = Base64.getEncoder().withoutPadding();

        inNIO.order(ByteOrder.nativeOrder());
        outNIO.order(ByteOrder.nativeOrder());

        for (int i = 0; i < iEnd; ++i)
        {
            System.err.println("========================================");

            random.nextBytes(in);
            inNIO.clear();
            inNIO.put(in);

            // org.bouncycastle.crypto.BlockCipher
            time0 = 0;
            for (BlockCipher blockCipher : ciphers)
            {
                NIOBlockCipher nioBlockCipher
                    = (blockCipher instanceof NIOBlockCipher)
                        ? (NIOBlockCipher) blockCipher
                        : null;
                Cipher cipher;
                Class<?> clazz;

                if (blockCipher instanceof BlockCipherAdapter)
                {
                    cipher = ((BlockCipherAdapter) blockCipher).getCipher();
                    clazz = cipher.getProvider().getClass();
                }
                else
                {
                    cipher = null;
                    clazz = blockCipher.getClass();
                }

                int blockSize = blockCipher.getBlockSize();

                blockCipher.init(true, new KeyParameter(in, 0, blockSize));

                long startTime, endTime;
                int offEnd = in.length - blockSize;

                if (nioBlockCipher != null && benchmarkNIOBlockCipher)
                {
                    inNIO.clear();
                    outNIO.clear();

                    startTime = System.nanoTime();
                    for (int j = 0; j < jEnd; ++j)
                    {
                        for (int off = 0; off < offEnd;)
                        {
                            nioBlockCipher.processBlock(inNIO, off, outNIO, 0);
                            off += blockSize;
                        }
//                        nioBlockCipher.reset();
                    }
                    endTime = System.nanoTime();

                    outNIO.get(out);
                }
                else if (cipher != null && benchmarkJavaxCryptoCipher)
                {
                    startTime = System.nanoTime();
                    for (int j = 0; j < jEnd; ++j)
                    {
                        for (int off = 0; off < offEnd;)
                        {
                            int nextOff = off + blockSize;

                            inNIO.limit(nextOff);
                            inNIO.position(off);
                            outNIO.clear();
                            cipher.update(inNIO, outNIO);
                            off = nextOff;
                        }
//                        cipher.doFinal();
                    }
                    endTime = System.nanoTime();

                    outNIO.clear();
                    outNIO.get(out);
                }
                else
                {
                    startTime = System.nanoTime();
                    for (int j = 0; j < jEnd; ++j)
                    {
                        for (int off = 0; off < offEnd;)
                        {
                            blockCipher.processBlock(in, off, out, 0);
                            off += blockSize;
                        }
//                        blockCipher.reset();
                    }
                    endTime = System.nanoTime();
                }

                long time = endTime - startTime;

                if (time0 == 0)
                    time0 = time;
                Arrays.fill(out, blockSize, out.length, (byte) 0);
                System.err.println(
                        clazz.getName() + ": ratio "
                            + String.format("%.2f", time / (double) time0)
                            + ", time " + time + ", out "
                            /*+ byteEncoder.encodeToString(out)*/ + ".");
            }

            // org.bouncycastle.crypto.Digest & java.security.MessageDigest
            System.err.println("----------------------------------------");

            time0 = 0;
            for (int d = 0; d < dMax; ++d)
            {
                Arrays.fill(out, (byte) 0);

                // org.bouncycastle.crypto.Digest
                Digest digest = (d < digests.length) ? digests[d] : null;
                int byteLength
                    = (digest instanceof ExtendedDigest)
                        ? ((ExtendedDigest) digest).getByteLength()
                        : 64;
                long startTime, endTime;
                int offEnd = in.length - byteLength;

                if (digest != null)
                {
                    startTime = System.nanoTime();
                    for (int j = 0; j < jEnd; ++j)
                    {
                        for (int off = 0; off < offEnd;)
                        {
                            digest.update(in, off, byteLength);
                            off += byteLength;
                        }
                        digest.doFinal(out, 0);
                    }
                    endTime = System.nanoTime();

                    long time = endTime - startTime;

                    if (time0 == 0)
                        time0 = time;
                    System.err.println(
                            digest.getClass().getName() + ": ratio "
                                + String.format("%.2f", time / (double) time0)
                                + ", time " + time + ", digest "
                                /*+ byteEncoder.encodeToString(out)*/ + ".");
                }

                // java.security.MessageDigest
                MessageDigest messageDigest
                    = (d < messageDigests.length) ? messageDigests[d] : null;

                if (messageDigest != null)
                {
                    @SuppressWarnings("unused")
                    byte[] t = null;

                    startTime = System.nanoTime();
                    for (int j = 0; j < jEnd; ++j)
                    {
                        for (int off = 0; off < offEnd;)
                        {
                            messageDigest.update(in, off, byteLength);
                            off += byteLength;
                        }
                        t = messageDigest.digest();
                    }
                    endTime = System.nanoTime();

                    long time = endTime - startTime;

                    if (time0 == 0)
                        time0 = time;
                    System.err.println(
                            messageDigest.getProvider().getClass().getName()
                                + ": ratio "
                                + String.format("%.2f", time / (double) time0)
                                + ", time " + (endTime - startTime)
                                + ", digest " /*+ byteEncoder.encodeToString(t)*/
                                + ".");
                }
            }

            // org.bouncycastle.crypto.Mac
            System.err.println("----------------------------------------");

            time0 = 0;
            for (Mac mac : macs)
            {
                mac.init(new KeyParameter(in, 0, maxByteLength));

                long startTime, endTime;
                int offEnd = in.length - maxByteLength;

                startTime = System.nanoTime();
                for (int j = 0; j < jEnd; ++j)
                {
                    for (int off = 0; off < offEnd; off = off + maxByteLength)
                        mac.update(in, off, maxByteLength);
                    mac.doFinal(out, 0);
                }
                endTime = System.nanoTime();

                int macSize = mac.getMacSize();
                long time = endTime - startTime;

                if (time0 == 0)
                    time0 = time;
                Arrays.fill(out, macSize, out.length, (byte) 0);
                System.err.println(
                        mac.getClass().getName() + ": ratio "
                            + String.format("%.2f", time / (double) time0)
                            + ", time " + time + ", out "
                            /*+ byteEncoder.encodeToString(out)*/ + ".");
            }
        }
    }
}
