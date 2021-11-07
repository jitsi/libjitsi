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

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import java.io.*;

/**
 * This class represent an IVF file and provide an API to get the vp8 video
 * frames it contains.
 *
 * @author Thomas Kuntz
 */
public class IVFFileReader
{
    /**
     * The length in bytes of the IVF file header.
     */
    private static int IVF_HEADER_LENGTH = 32;

    /**
     * A <tt>IVFHeader</tt> representing the global header of the IVF
     * file which this <tt>IVFFileReader</tt> will read.
     * This header contains information like the dimension of the frame,
     * the framerate, the number of frame. in the file, etc.
     */
    private IVFHeader header;

    /**
     * The <tt>RandomAccessFile</tt> used to read the IVF file.
     */
    private RandomAccessFile stream;

    /**
     * Initialize a new instance of <tt>IVFFileReader</tt> that will read
     * the IVF file located by <tt>filePath</tt>.
     * @param filePath the location of the IVF file this <tt>IVFFileReader</tt>
     * will read.
     */
    public IVFFileReader(String filePath)
    {
        header = new IVFHeader(filePath);

        try
        {
            stream = new RandomAccessFile(filePath,"r");
            stream.seek(IVF_HEADER_LENGTH);
        }
        catch (FileNotFoundException e) 
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Get the header of the IVF file.
     * @return the header of the IVF file represented by a <tt>IVFHeader</tt>.
     */
    public IVFHeader getHeader()
    {
        return header;
    }

    /**
     * Get the next vp8 frame of the IVF file as a <tt>byte</tt> array.
     * A VP8Frame is allocated for each call to this function.
     * 
     * @param loopFile if true and the end of the file is reached,
     * this <tt>IVFFileReader</tt> will go back at the beginning of the file
     * and start over the reading of the file.
     * @return the next vp8 frame of the IVF file as a <tt>byte</tt> array.
     * @throws IOException if an error occur during the read, of if EOF is reached.
     */
    public VP8Frame getNextFrame(boolean loopFile) throws IOException
    {
        VP8Frame frame = new VP8Frame();
        getNextFrame(frame, loopFile);
        return frame;
    }

    /**
     * Get the next vp8 frame of the IVF file as a <tt>byte</tt> array.
     * You should use this function if you don't want to allocate a new VP8Frame
     * for each call.
     * 
     * @param frame the <tt>VP8Frame</tt> that will be filled with the
     * next frame from the file.
     * @param loopFile if true and the end of the file is reached,
     * this <tt>IVFFileReader</tt> will go back at the beginning of the file
     * and start over the reading of the file.
     * @throws IOException if an error occur during the read, of if EOF is reached.
     */
    public void getNextFrame(VP8Frame frame,boolean loopFile) throws IOException
    {
        if(loopFile && (stream.getFilePointer() >= stream.length()))
        {
            stream.seek(header.getHeaderLength());
        }

        byte[] data;
        int frameSizeInBytes;
        long timestamp;

        frameSizeInBytes = changeEndianness(stream.readInt());
        timestamp = changeEndianness(stream.readLong());
        data = new byte[frameSizeInBytes];
        stream.read(data);

        frame.set(timestamp, frameSizeInBytes, data);
    }

    /**
     * Change the endianness of a 32bits int.
     * @param value the value which you want to change the endianness.
     * @return the <tt>value</tt> with a changed endianness.
     */
    public static int changeEndianness(int value)
    {
        return 
            (((value << 24) & 0xFF000000) |
             ((value << 8) & 0x00FF0000) |
             ((value >> 8) & 0x0000FF00) |
             ((value >> 24) & 0x000000FF));
    }

    /**
     * Change the endianness of a 16bits short.
     * @param value the value which you want to change the endianness.
     * @return the <tt>value</tt> with a changed endianness
     */
    public static short changeEndianness(short value)
    {
        return (short) (
            ((value << 8) & 0xFF00) |
            ((value >> 8) & 0x00FF) );
    }

    /**
     * Change the endianness of a 64bits long.
     * @param value the value which you want to change the endianness.
     * @return the <tt>value</tt> with a changed endianness
     */
    public static long changeEndianness(long value)
    {
      long b1 = (value >>  0) & 0xff;
      long b2 = (value >>  8) & 0xff;
      long b3 = (value >> 16) & 0xff;
      long b4 = (value >> 24) & 0xff;
      long b5 = (value >> 32) & 0xff;
      long b6 = (value >> 40) & 0xff;
      long b7 = (value >> 48) & 0xff;
      long b8 = (value >> 56) & 0xff;

      return b1 << 56 | b2 << 48 | b3 << 40 | b4 << 32 |
             b5 << 24 | b6 << 16 | b7 <<  8 | b8 <<  0;
    }
}
