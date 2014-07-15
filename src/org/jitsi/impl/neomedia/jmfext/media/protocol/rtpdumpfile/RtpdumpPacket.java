/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;


import org.jitsi.impl.neomedia.*;

/**
 * This class represent a RTP packet (header+payload) of a RTP stream recorded
 * in a rtpdump file
 * 
 * 
 * @author Thomas Kuntz
 */
public class RtpdumpPacket extends RawPacket
{
    /**
     * The rtpdump timestamp (the timestamp of the sending/receiving of the
     * RTP packet).
     */
    private long rtpdump_timestamp;
    
    
    /**
     * Initialize a new instance of <tt>RtpdumpPacket</tt> with the timestamp
     * of the header (in the rtpdump file) of the RTP packet.
     * 
     * @param rtpPacket the data of the RTP packet, as a byte array.
     * @param rtpdump_timestamp the timestamp of the sending/receiving of
     * the RTP packet.
     */
    public RtpdumpPacket(byte[] rtpPacket,long rtpdump_timestamp)
    {
        this(rtpPacket);
        this.rtpdump_timestamp = rtpdump_timestamp;
    }
    
    
    /**
     * Initialize a new instance of <tt>RtpdumpPacket</tt>
     * array <tt>rtpPacket</tt> of the RTP packet.
     * @param rtpPacket the data of the RTP packet, as a byte array.
     */
    public RtpdumpPacket(byte[] rtpPacket)
    {
        super(rtpPacket, 0, rtpPacket.length);
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