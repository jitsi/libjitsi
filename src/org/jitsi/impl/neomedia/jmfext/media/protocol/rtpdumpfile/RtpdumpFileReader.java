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
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import java.io.*;

import org.jitsi.service.neomedia.*;

/**
 * This class represent a rtpdump file and provide an API to get the
 * payload of the rtp packet it contains.
 * 
 * rtpdump format : 
 *  - http://www.cs.columbia.edu/irt/software/rtptools/
 *  - https://gist.github.com/Haerezis/18e3ffc2d69c86f8463f#file-rtpdump_file_format
 *      (backup gist)
 * 
 * 
 * rtpdump file can be generated with wireshark from RTP stream, just go to :
 * -> Telephony -> RTP -> Show All Streams
 * then select the RTP stream you want to record, and click on "Save As".
 * 
 * If the RTP menu isn't found in the Telephony menu, maybe you can find it in
 * the "Statistics" menu (in old version of Wireshark).
 *
 * 
 * @author Thomas Kuntz
 */
public class RtpdumpFileReader
{
    /**
     * The size of the first header of the file (in bytes).
     * 
     * The file wireshark/ui/tap-rtp-common.c , more specifically the function
     * rtp_write_header that write the file header, show that this header
     * is 4+4+4+2+2=16 bytes.
     */
    public final static int FILE_HEADER_LENGTH = 4 + 4 + 4 + 2 + 2;

    /**
     * The <tt>RandomAccessFile</tt> used to read the rtpdump file.
     * 
     * <tt>RandomAccessFile</tt> is used because I need to go to the beginning
     * of the file when the loop is activated.
     */
    private RandomAccessFile stream;

    /**
     * Initialize a new instance of <tt>RtpdumpFileReader</tt> that will the
     * rtpdump file located by <tt>filePath</tt>.
     * @param filePath the location of the rtpdump file this
     * <tt>RtpdumpFileReader</tt> will read.
     */
    public RtpdumpFileReader(String filePath)
    {
        try
        {
            stream = new RandomAccessFile(filePath, "r");
            resetFile();
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
     * Get the next rtp packet recorded in the rtpdump file.
     * @param loopFile if true, when the end of the rtpdump file is reached,
     * this <tt>RtpdumpFileReader</tt> will go back at the beginning of the file
     * and get the first packet.
     * @return a <tt>RawPacket</tt> containing all the information and data
     * of the next rtp packet recorded in the rtpdump file
     * @throws IOException if <tt>loopFile</tt> was false and the end of the file
     * is reached.
     */
    public RawPacket getNextPacket(boolean loopFile)
        throws IOException
    {
        if(loopFile && (stream.getFilePointer() >= stream.length()))
        {
            resetFile();
        }

        byte[] rtpdumpPacket;
        int sizeInBytes;

        stream.readShort(); //read away an useless short (2 bytes)
        sizeInBytes = stream.readUnsignedShort();
        rtpdumpPacket = new byte[sizeInBytes];
        stream.readInt(); //read away the rtpdump timestamp

        stream.read(rtpdumpPacket);

        return new RawPacket(rtpdumpPacket, 0, rtpdumpPacket.length);
    }

    /**
     * Go to the beginning of the rtpdump file and
     * skip the first line of ascii (giving the file version) and
     * skip the file header (useless)
     * @throws IOException if an error occur during the seek and reading of the
     * file.
     */
    private void resetFile() throws IOException
    {
        stream.seek(0);
        stream.readLine(); //read the first line that is in ascii
        stream.seek(stream.getFilePointer()
                             + RtpdumpFileReader.FILE_HEADER_LENGTH );
    }
}
