package org.jitsi.impl.neomedia.transform.srtp;

import static org.junit.Assert.*;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;
import org.bouncycastle.crypto.engines.*;
import org.junit.*;

public class SRTPCipherF8Test
{
    // RFC 3711 AES F8 Tests vectors
    public static final byte[] TV_Key =
        DatatypeConverter.parseHexBinary("234829008467be186c3de14aae72d62c");

    public static final byte[] TV_Salt =
        DatatypeConverter.parseHexBinary("32f2870d");

    public static final byte[] TV_IV =
        DatatypeConverter.parseHexBinary("006e5cba50681de55c621599d462564a");

    public static final byte[] TV_Plain =
        DatatypeConverter.parseHexBinary("70736575646f72616e646f6d6e657373"
            + "20697320746865206e65787420626573" + "74207468696e67");

    public static final byte[] TV_Cipher_AES =
        DatatypeConverter.parseHexBinary("019ce7a26e7854014a6366aa95d4eefd"
            + "1ad4172a14f9faf455b7f1d4b62bd08f" + "562c0eef7c4802");

    // Generated with our own implementation
    public static final byte[] TV_Cipher_TwoFish =
        DatatypeConverter.parseHexBinary("346d91e0d4c3908c476ba25f2792fbb6"
            + "5456f2d90736f40353da7865a8989f01" + "947f6f09385fb5");

    /**
     * Validate our F8 mode implementation with tests vectors provided in
     * RFC3711
     * 
     * @throws Exception
     */
    @Test
    public void testAES() throws Exception
    {
        SRTPCipherF8 cipher = new SRTPCipherF8(new AESFastEngine());
        cipher.init(TV_Key, TV_Salt);
        byte[] data = Arrays.copyOf(TV_Plain, TV_Plain.length);
        byte[] iv = Arrays.copyOf(TV_IV, TV_IV.length);
        cipher.process(data, 0, data.length, iv);

        assertArrayEquals(data, TV_Cipher_AES);
    }

    /**
     * Validate our F8 mode implementation work with TwoFish
     * 
     * @throws Exception
     */
    @Test
    public void testTwoFish() throws Exception
    {
        SRTPCipherF8 cipher = new SRTPCipherF8(new TwofishEngine());
        cipher.init(TV_Key, TV_Salt);
        byte[] data = Arrays.copyOf(TV_Plain, TV_Plain.length);
        byte[] iv = Arrays.copyOf(TV_IV, TV_IV.length);
        cipher.process(data, 0, data.length, iv);

        assertArrayEquals(data, TV_Cipher_TwoFish);
    }
}
