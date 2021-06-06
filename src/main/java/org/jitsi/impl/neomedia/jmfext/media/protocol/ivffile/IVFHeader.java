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

import java.awt.*;
import java.io.*;

/**
 * This class represent the 32 bytes header of an IVF file.
 * http://wiki.multimedia.cx/index.php?title=IVF
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
    private short headerLength;

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
     * IVF being kind of a debug/toy format used to test VP8/VP9,
     * the format isn't well known.
     * In ffmppeg this field of the header will be the duration of the
     * video (weirdly enough, it's always 0), but mkvextract set the number
     * of frames of the video. 
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
            headerLength = IVFFileReader.changeEndianness(stream.readShort());
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

        // TODO sanity check for the fields?
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
    public short getHeaderLength()
    {
        return headerLength;
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
        return new Dimension(width, height);
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
     * Get the 'number of frames' field from the IVF file header.
     * @return the 'number of frames' field from the IVF file header.
     */
    public int getNumberOfFramesInFile()
    {
        return numberOfFramesInFile;
    }
}
