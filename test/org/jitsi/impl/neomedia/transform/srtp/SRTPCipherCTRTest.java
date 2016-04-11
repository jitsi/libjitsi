package org.jitsi.impl.neomedia.transform.srtp;

import static org.junit.Assert.*;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.jitsi.util.OSUtils;
import org.junit.Test;

public class SRTPCipherCTRTest
{
    // RFC 3711 AES CTR Tests vectors
    public static final byte[] TV_Key =
        DatatypeConverter.parseHexBinary("2B7E151628AED2A6ABF7158809CF4F3C");

    public static final byte[] TV_IV_1 =
        DatatypeConverter.parseHexBinary("F0F1F2F3F4F5F6F7F8F9FAFBFCFD0000");

    public static final byte[] TV_Cipher_AES_1 =
        DatatypeConverter.parseHexBinary("E03EAD0935C95E80E166B16DD92B4EB4"
            + "D23513162B02D0F72A43A2FE4A5F97AB"
            + "41E95B3BB0A2E8DD477901E4FCA894C0");

    public static final byte[] TV_IV_2 =
        DatatypeConverter.parseHexBinary("F0F1F2F3F4F5F6F7F8F9FAFBFCFDFEFF");

    public static final byte[] TV_Cipher_AES_2 =
        DatatypeConverter.parseHexBinary("EC8CDF7398607CB0F2D21675EA9EA1E4"
            + "362B7C3C6773516318A077D7FC5073AE"
            + "6A2CC3787889374FBEB4C81B17BA6C44");

    @Test
    public void testJavaCTRAES()
    {
        SRTPCipherCTR cipher = new SRTPCipherCTRJava(new AESFastEngine());
        cipher.init(TV_Key);
        byte[] data = new byte[TV_Cipher_AES_1.length];

        Arrays.fill(data, (byte) 0);
        byte[] iv = Arrays.copyOf(TV_IV_1, TV_IV_1.length);
        cipher.process(data, 0, data.length, iv);
        assertArrayEquals(data, TV_Cipher_AES_1);

        Arrays.fill(data, (byte) 0);
        iv = Arrays.copyOf(TV_IV_2, TV_IV_2.length);
        cipher.process(data, 0, data.length, iv);
        assertArrayEquals(data, TV_Cipher_AES_2);
    }

    @Test
    public void testOpenSSLCTRAES()
    {
        if (!OSUtils.IS_LINUX)
        {
            return;
        }

        SRTPCipherCTR cipher = new SRTPCipherCTROpenSSL();
        cipher.init(TV_Key);
        byte[] data = new byte[TV_Cipher_AES_1.length];

        Arrays.fill(data, (byte) 0);
        byte[] iv = Arrays.copyOf(TV_IV_1, TV_IV_1.length);
        cipher.process(data, 0, data.length, iv);
        assertArrayEquals(data, TV_Cipher_AES_1);

        Arrays.fill(data, (byte) 0);
        iv = Arrays.copyOf(TV_IV_2, TV_IV_2.length);
        cipher.process(data, 0, data.length, iv);
        assertArrayEquals(data, TV_Cipher_AES_2);
    }
}
