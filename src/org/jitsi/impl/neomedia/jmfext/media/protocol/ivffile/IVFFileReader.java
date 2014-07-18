/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import java.io.*;

/**
 * This class represent an IVF file and provide an API to get the vp8 video
 * frames it contains.
 * 
 * 
 * @author Thomas Kuntz
 */
public class IVFFileReader
{
    /**
     * A <tt>IVFHeader</tt> representing the global header of the IVF
     * file this <tt>IVFFileReader</tt> will read.
     * This header contains informations like the dimension of the frame,
     * the framerate, the number of frame in the file,etc...
     */
    private IVFHeader header;
    
    /**
     * The number of the next frame that will be read.
     * It is used to know if we reached the last frame of the file or not.
     */
    private int frameNo = 0;
    
    /**
     * The <tt>RandomAccessFile</tt> used to read the IVF file.
     */
    private RandomAccessFile stream;
    
    /**
     * Initialize a new instance of <tt>IVFFileReader</tt> that will read
     * the IVF file located by <tt>filePath</tt>
     * @param filePath the location of the IVF file this <tt>IVFFileReader</tt>
     * will read.
     */
    public IVFFileReader(String filePath)
    {
        header = new IVFHeader(filePath);
        
        try
        {
            stream = new RandomAccessFile(filePath,"r");
            stream.seek(32);
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
     * @param loopFile if true and that the end of the file is reached,
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
     * informations and data of the next frame read.
     * @param loopFile if true and that the end of the file is reached,
     * this <tt>IVFFileReader</tt> will go back at the beginning of the file
     * and start over the reading of the file.
     * @return the next vp8 frame of the IVF file as a <tt>byte</tt> array.
     * @throws IOException if an error occur during the read, of if EOF is reached.
     */
    public void getNextFrame(VP8Frame frame,boolean loopFile) throws IOException
    {
        if((loopFile == true) && (stream.getFilePointer() >= stream.length()))
        {
            stream.seek(header.getHeaderLengh());
            frameNo = 0;
        }
        
        byte[] data;
        int frameSizeInBytes;
        long timestamp;
        
        
        frameSizeInBytes = changeEndianness(stream.readInt());
        timestamp = changeEndianness(stream.readLong());
        data = new byte[frameSizeInBytes];
        stream.read(data);
        frameNo++;
        
        frame.set(timestamp,frameSizeInBytes,data);
    }
    
    /**
     * Change the endianness of a 32bits int.
     * @param value the value which you want to change the endianness.
     * @return the <tt>value</tt> with a changed endianness
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