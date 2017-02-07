package org.jitsi.impl.neomedia;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class RawPacketTest
{
    private byte[] buildPacketWithAbsSendTime(int absSendTime)
    {
        String sendTime = String.format("%04x", absSendTime);
        String data = "90ff2a156c67726096b37210bede000132" + sendTime + "98ad90a8523ccaa4313550c71e6a01";
        return buildPacket(data);
    }

    private static byte[] buildPacket(String hexString)
    {
        StringReader reader = new StringReader(hexString);
        char[] data = new char[2];
        byte[] buffer = new byte[hexString.length() / 2];
        int i = 0;
        try
        {
            while (reader.read(data, 0, 2) != -1)
            {
                buffer[i++] = (byte)Integer.parseInt(String.valueOf(data), 16);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return buffer;
    }

    @Test
    public void testGetAbsSendTime()
    {
        final int MAGIC_ABS_SEND_TIME = 0x734d77;
        byte[] data = buildPacketWithAbsSendTime(MAGIC_ABS_SEND_TIME);
        RawPacket packet = new RawPacket(data, 0, data.length);
        assertEquals(MAGIC_ABS_SEND_TIME, packet.getAbsSendTime());
    }

    @Test
    public void testGetAbsSendTimeSignExtension()
    {
        final int LEADING_BITS = 0x818283;
        byte[] data = buildPacketWithAbsSendTime(LEADING_BITS);
        RawPacket packet = new RawPacket(data, 0, data.length);
        assertEquals(LEADING_BITS, packet.getAbsSendTime());
    }

    @Test
    public void testGetAbsSentTimeEmptyPacket()
    {
        RawPacket packet = new RawPacket(new byte[] {}, 0, 0);
        assertEquals(-1, packet.getAbsSendTime());
    }

    @Test
    public void testGetAbsSentTimeIncompletePacket()
    {
        final String incompletePacket = "90ff2a156c67726096b37210bede000132734d";
        final byte[] incompletePacketBytes = buildPacket(incompletePacket);
        RawPacket packet = new RawPacket(incompletePacketBytes, 0, 35);
        assertEquals(-1, packet.getAbsSendTime());
    }
}
