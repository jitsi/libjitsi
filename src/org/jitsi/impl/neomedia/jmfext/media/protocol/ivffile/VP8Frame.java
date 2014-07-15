/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

/**
 * @author Thomas Kuntz
 * 
 * This class represents a vp8 frame read in an IVF file.
 * http://wiki.multimedia.cx/index.php?title=IVF
 */
public class VP8Frame
{
    /**
     * The timestamp of the frame (of the frame's header in the ivf file)
     */
    private long timestamp;
    
    /**
     * The length (in byte) of the frame.
     */
    private int frameLength;
    
    /**
     * The data of the frame.
     */
    private byte[] frameData;
    
    /**
     * Create an empty <tt>VP8Frame</tt> that need to be set later.
     */
    public VP8Frame() {}
    
    /**
     * Create a <tt>VP8Frame</tt> filled with the data of the frame, its
     * timestamp and the length of the data (in bytes)
     * 
     * @param timestamp the timestamp of the frame.
     * @param frameLength the length of the frame declared in its header.
     * @param frameData the data of the frame.
     */
    public VP8Frame(long timestamp, int frameLength,byte[] frameData)
    {
        this.set(timestamp,frameLength,frameData);
    }
    
    /**
     * Get the timestamp of the frame as declared in its header.
     * @return the timestamp of the frame.
     */
    public long getTimestamp()
    {
        return timestamp;
    }
    
    /**
     * Get the length of the frame (in bytes) as declared in its header.
     * @return the length of the frame.
     */
    public int getFrameLength()
    {
        return frameLength;
    }
    
    /**
     * Get the data composing the VP8 frame.
     * @return the data of the VP8 frame.
     */
    public byte[] getFrameData()
    {
        return frameData;
    }

    /**
     * Set all the attributes of the <tt>VP8Frame</tt>.
     * 
     * @param timestamp the timestamp of the frame.
     * @param frameLength the length of the frame.
     * @param frameData the data of the length.
     */
    public void set(long timestamp, int frameLength,byte[] frameData)
    {
        this.timestamp = timestamp;
        this.frameLength = frameLength;
        this.frameData = frameData;
    }
}

