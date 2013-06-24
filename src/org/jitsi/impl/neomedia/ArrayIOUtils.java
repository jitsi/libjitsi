/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

/**
 * Implements functionality aiding the reading and writing in little endian of
 * <tt>byte</tt> arrays and primitive types such as <tt>short</tt>.
 *
 * @author Lyubomir Marinov
 */
public class ArrayIOUtils
{

    /**
     * Reads an integer from a specific series of bytes starting the reading at
     * a specific offset in it.
     *
     * @param in the series of bytes to read an integer from
     * @param inOffset the offset in <tt>in</tt> at which the reading of the
     * integer is to start
     * @return an integer read from the specified series of bytes starting at
     * the specified offset in it
     */
    public static int readInt(byte[] in, int inOffset)
    {
        return
            (in[inOffset + 3] << 24)
                | ((in[inOffset + 2] & 0xFF) << 16)
                | ((in[inOffset + 1] & 0xFF) << 8)
                | (in[inOffset] & 0xFF);
    }

    /**
     * Reads a short integer from a specific series of bytes starting the
     * reading at a specific offset in it. The difference with
     * {@link #readShort(byte[], int)} is that the read short integer is an
     * <tt>int</tt> which has been formed by reading two bytes, not a
     * <tt>short</tt>.
     *
     * @param in the series of bytes to read the short integer from
     * @param inOffset the offset in <tt>in</tt> at which the reading of the
     * short integer is to start
     * @return a short integer in the form of <tt>int</tt> read from the
     * specified series of bytes starting at the specified offset in it
     */
    public static int readInt16(byte[] in, int inOffset)
    {
        return ((in[inOffset + 1] << 8) | (in[inOffset] & 0x00FF));
    }

    /**
     * Reads a short integer from a specific series of bytes starting the
     * reading at a specific offset in it.
     *
     * @param in the series of bytes to read the short integer from
     * @param inOffset the offset in <tt>in</tt> at which the reading of the
     * short integer is to start
     * @return a short integer in the form of <tt>short</tt> read from the
     * specified series of bytes starting at the specified offset in it
     */
    public static short readShort(byte[] in, int inOffset)
    {
        return (short) ((in[inOffset + 1] << 8) | (in[inOffset] & 0x00FF));
    }

    /**
     * Converts an integer to a series of bytes and writes the result into a
     * specific output array of bytes starting the writing at a specific offset
     * in it.
     *
     * @param in the integer to be written out as a series of bytes
     * @param out the output to receive the conversion of the specified
     * integer to a series of bytes
     * @param outOffset the offset in <tt>out</tt> at which the writing of the
     * result of the conversion is to be started
     */
    public static void writeInt(int in, byte[] out, int outOffset)
    {
        out[outOffset] = (byte) (in & 0xFF);
        out[outOffset + 1] = (byte) ((in >>> 8) & 0xFF);
        out[outOffset + 2] = (byte) ((in >>> 16) & 0xFF);
        out[outOffset + 3] = (byte) (in >> 24);
    }

    /**
     * Converts a short integer to a series of bytes and writes the result into
     * a specific output array of bytes starting the writing at a specific
     * offset in it. The difference with {@link #writeShort(short, byte[], int)}
     * is that the input is an <tt>int</tt> and just two bytes of it are
     * written.
     *
     * @param in the short integer to be written out as a series of bytes
     * specified as an integer i.e. the value to be converted is contained in
     * only two of the four bytes made available by the integer
     * @param out the output to receive the conversion of the specified short
     * integer to a series of bytes
     * @param outOffset the offset in <tt>out</tt> at which the writing of the
     * result of the conversion is to be started
     */
    public static void writeInt16(int in, byte[] out, int outOffset)
    {
        out[outOffset] = (byte) (in & 0xFF);
        out[outOffset + 1] = (byte) (in >> 8);
    }

    /**
     * Converts a short integer to a series of bytes and writes the result into
     * a specific output array of bytes starting the writing at a specific
     * offset in it.
     *
     * @param in the short integer to be written out as a series of bytes
     * specified as <tt>short</tt>
     * @param out the output to receive the conversion of the specified short
     * integer to a series of bytes
     * @param outOffset the offset in <tt>out</tt> at which the writing of
     * the result of the conversion is to be started
     */
    public static void writeShort(short in, byte[] out, int outOffset)
    {
        writeInt16(in, out, outOffset);
    }
}
