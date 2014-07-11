/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import java.util.Arrays;

/**
 * This class represent a RTP packet (header+payload) of a RTP stream recorded
 * in a rtpdump file
 * 
 * 
 * @author Thomas Kuntz
 */
public class RTPPacket
{
    /**
     * The version of RTP of the packet
     */
    private short version;

    /**
     * The padding flag of the RTP packet
     * (if true, the flag is set, else it's not)
     */
    private boolean padding;

    /**
     * The extension flag of the RTP packet
     * (if true, the flag is set, else it's not)
     */
    private boolean extension;

    /**
     * The number of CSRC identifiers that follow the fixed header.
     */
    private short CSRCCount;

    /**
     * The marker flag of the RTP packet
     * (if true, the flag is set, else it's not)
     */
    private boolean marker;

    /**
     * The payload type of the RTP packet.
     * It identifies the format of the RTP payload.
     */
    private short payloadType;
    
    /**
     * The sequence number of the RTP packet.
     */
    private int sequenceNumber;

    /**
     * The timestamp of the RTP packet.
     */
    private long timestamp;

    /**
     * The ssrc of the RTP packet.
     */
    private long ssrc;

    /**
     * The list of CSRC contained in the RTP packet.
     */
    private long[] CSRC;

    /**
     * The payload of the RTP packet.
     */
    private byte[] payload;

    /**
     * The rtpdump timestamp (the timestamp of the sending/receiving of the
     * RTP packet).
     */
    private long rtpdump_timestamp;
    
    
    /**
     * Initialize a new instance of <tt>RTPPacket</tt>, by parsing the byte
     * array <tt>rtpPacket</tt> of the RTP packet.
     * @param rtpPacket the data of the RTP packet, as a byte array.
     * @param rtpdump_timestamp the timestamp of the sending/receiving of
     * the RTP packet.
     */
    public RTPPacket(byte[] rtpPacket,long rtpdump_timestamp)
    {
        this(rtpPacket);
        this.rtpdump_timestamp = rtpdump_timestamp;
    }
    
    
    /**
     * Initialize a new instance of <tt>RTPPacket</tt>, by parsing the byte
     * array <tt>rtpPacket</tt> of the RTP packet.
     * @param rtpPacket the data of the RTP packet, as a byte array.
     */
    public RTPPacket(byte[] rtpPacket)
    {
        version = (short) ((rtpPacket[0] & 0xC0) >> 6);
        padding = ((rtpPacket[0] & 0x20) != 0);
        extension = ((rtpPacket[0] & 0x10) != 0);
        CSRCCount = (short) ((rtpPacket[0] & 0x0F));
        
        marker = ((rtpPacket[1] & 0x80) != 0);
        payloadType = (short) ((rtpPacket[1] & 0x7F));
        
        
        sequenceNumber = ((rtpPacket[2] & 0xFF) << 8) | (rtpPacket[3] & 0xFF);
        
        
        
        timestamp = 
                ((rtpPacket[4] & 0xFF) << 24) |
                ((rtpPacket[5] & 0xFF) << 16)  |
                ((rtpPacket[6] & 0xFF) << 8)  |
                ((rtpPacket[7] & 0xFF) << 0);
        
        ssrc =  
                ((rtpPacket[8] & 0xFF) << 24) |
                ((rtpPacket[9] & 0xFF) << 16) |
                ((rtpPacket[10] & 0xFF) << 8) |
                ((rtpPacket[11] & 0xFF) << 0);
        
        
        
        CSRC = new long[CSRCCount];
        for(int i = 0; i< CSRCCount ; i++)
        {
            CSRC[i] =   
                    ((rtpPacket[12 + i*4 + 0] & 0xFF) << 24) |
                    ((rtpPacket[12 + i*4 + 1] & 0xFF) << 16)  |
                    ((rtpPacket[12 + i*4 + 2] & 0xFF) << 8)  |
                    ((rtpPacket[12 + i*4 + 3] & 0xFF) << 0);
        }
        
        payload = Arrays.copyOfRange(
                rtpPacket,
                12 + CSRCCount*4,
                rtpPacket.length);
    }
    
    /**
     * Get the version of the RTP packet.
     * @return the version of the RTP packet.
     */
    public short getVersion()
    {
        return version;
    }
    
    /**
     * Indicate if the RTP packet has the padding flag set.
     * @return true if the padding flag was set in the header, false if not.
     */
    public boolean hasPadding()
    {
        return padding;
    }
    
    /**
     * Indicate if the RTP packet has the extension flag set.
     * @return true if the extension flag was set in the header, false if not.
     */
    public boolean hasExtension()
    {
        return extension;
    }
    
    /**
     * Get the number of CSRC identifiers that follow the fixed header.
     * @return the number of CSRC identifiers that follow the fixed header.
     */
    public short getCSRCCount()
    {
        return CSRCCount;
    }
    
    /**
     * Indicate if the RTP packet has the marker flag set.
     * @return true if the marker flag was set in the header, false if not.
     */
    public boolean hasMarker()
    {
        return marker;
    }
    
    /**
     * Get the payload type of the RTP packet.
     * @return the payload type of the RTP packet.
     */
    public short getPayloadType()
    {
        return payloadType;
    }
    
    /**
     * Get the sequence number of the RTP packet.
     * @return the sequence number of the RTP packet.
     */
    public int getSequenceNumber()
    {
        return sequenceNumber;
    }
    
    /**
     * Get the timestamp of the RTP packet.
     * @return the timestamp type of the RTP packet.
     */
    public long getTimestamp()
    {
        return timestamp;
    }
    
    /**
     * Get the SSRC of the RTP packet.
     * @return the SSRC of the RTP packet.
     */
    public long getSSRC()
    {
        return ssrc;
    }
    
    /**
     * Get the array of CSRC this RTP packet contains.
     * @return the array of CSRC this RTP packet contains.
     */
    public long[] getCSRC()
    {
        return CSRC;
    }
    
    /**
     * Get the payload as a byte array of the RTP packet.
     * @return the payload of the RTP packet.
     */
    public byte[] getPayload()
    {
        return payload;
    }

    /**
     * Get the rtpdump timestamp (the timestamp of the sending/receiving).
     * @return the rtpdump timestamp (the timestamp of the sending/receiving).
     */
    public long getRtpdumpTimestamp()
    {
        return rtpdump_timestamp;
    }
}