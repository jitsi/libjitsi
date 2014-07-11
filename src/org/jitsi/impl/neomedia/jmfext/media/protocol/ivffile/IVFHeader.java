/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import java.awt.*;
import java.io.*;

/**
 * This class represent the 32 bytes header of an IVF file.
 * http://wiki.multimedia.cx/index.php?title=IVF
 * 
 * 
 * @author Thomas Kuntz
 */
public class IVFHeader
{
    /**
     * The signature of the ivf file (should be "DKIF").
     */
    private String signature;
    
    /**
     * The version of the ivf file (should be 0).
     */
    private short version;
    
    /**
     * Length of header in bytes of the ivf file.
     */
    private short headerLengh;
    
    /**
     * Codec of the ivf file (should be "VP80").
     */
    private String codec;
    
    /**
     * Width of the video of the ivf file.
     */
    private short width;
    
    /**
     * Height of the video of the ivf file.
     */
    private short height;
    
    /**
     * Framerate of the video of the ivf file.
     */
    private int framerate;
    
    /**
     * Timescale of the video of the ivf file.
     * The real framerate is obtained by dividing the header framerate
     * by the header timescale.
     */
    private int timeScale;
    
    /**
     * The number of frame in the ivf file.
     */
    private int numberOfFramesInFile;
    
    /**
     * Initialize a new instance of a <tt>IVFHeader</tt> from an ivf file, by
     * reading and parsing the header of the ivf file.
     * 
     * @param filePath the location of the ivf file from which you want to
     * parse the header.
     */
    public IVFHeader(String filePath)
    {
        try
        {
            InputStream input = new FileInputStream(filePath);
            DataInputStream stream =
                    new DataInputStream(input);
            
            signature = "" +
                    (char)stream.readByte() +
                    (char)stream.readByte() +
                    (char)stream.readByte() +
                    (char)stream.readByte();
            version = IVFFileReader.changeEndianness(stream.readShort());
            headerLengh = IVFFileReader.changeEndianness(stream.readShort());
            codec = "" +
                    (char)stream.readByte() +
                    (char)stream.readByte() +
                    (char)stream.readByte() +
                    (char)stream.readByte();
            width = IVFFileReader.changeEndianness(stream.readShort());
            height = IVFFileReader.changeEndianness(stream.readShort());
            framerate = IVFFileReader.changeEndianness(stream.readInt());
            timeScale = IVFFileReader.changeEndianness(stream.readInt());
            numberOfFramesInFile = IVFFileReader.changeEndianness(stream.readInt());
            
            stream.close();
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
     * Get the signature of the ivf file
     * @return the signature of the ivf file
     */
    public String getSignature()
    {
        return signature;
    }
    
    /**
     * Get the version of the ivf file
     * @return the version of the ivf file
     */
    public short getVersion()
    {
        return version;
    }
    
    /**
     * Get the header length of the ivf file
     * @return the header length of the ivf file
     */
    public short getHeaderLengh()
    {
        return headerLengh;
    }
    
    /**
     * Get the codec of the ivf file
     * @return the codec of the ivf file
     */
    public String getCodec()
    {
        return codec;
    }
    
    /**
     * Get the width of the video of the ivf file
     * @return the width of the video of the ivf file
     */
    public short getWidth()
    {
        return width;
    }
    
    /**
     * Get the height of the video of the ivf file
     * @return the height of the video of the ivf file
     */
    public short getHeight()
    {
        return height;
    }
    
    /**
     * Get the dimension (height x width) of the video of the ivf file.
     * @return the dimension (height x width) of the video of the ivf file.
     */
    public Dimension getDimension()
    {
        return new Dimension(width,height);
    }
    
    /**
     * Get the framerate declared by the header of the ivf file
     * @return the framerate declared by the header of the ivf file
     */
    public int getFramerate()
    {
        return framerate;
    }
    
    /**
     * Get the timescale declared by the header of the ivf file
     * @return the timescale declared by the header of the video of the ivf file
     */
    public int getTimeScale()
    {
        return timeScale;
    }
    
    /**
     * Get the number of frame contained in the ivf file
     * @return the number of frame contained in the ivf file
     */
    public int getNumberOfFramesInFile()
    {
        return numberOfFramesInFile;
    }
}