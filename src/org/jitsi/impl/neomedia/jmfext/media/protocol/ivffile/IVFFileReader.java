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
     * @param loopFile if true and that the end of the file is reached,
     * this <tt>IVFFileReader</tt> will go back at the beginning of the file
     * and start over the reading of the file.
     * @return the next vp8 frame of the IVF file as a <tt>byte</tt> array.
     * @throws IOException if an error occur during the read, of if EOF is reached.
     */
    public byte[] getNextFrame(boolean loopFile) throws IOException
    {
        if((loopFile == true) && (frameNo >= header.getNumberOfFramesInFile()))
        {
            stream.seek(header.getHeaderLengh());
            frameNo = 0;
        }
        
        byte[] data;
        int frameSizeInBytes;
        
        
        frameSizeInBytes = changeEndianness(stream.readInt());
        stream.skipBytes(8);
        data = new byte[frameSizeInBytes];
        stream.read(data);
        frameNo++;
        
        return data;
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
}