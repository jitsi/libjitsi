/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

/**
 * Implements an <tt>Exception</tt> which represents an <tt>HRESULT</tt> value.
 *
 * @author Lyubomir Marinov
 */
public class HResultException
    extends Exception
{
    /**
     * The <tt>HRESULT</tt> value represented by this instance.
     */
    private final int hresult;

    /**
     * Initializes a new <tt>HResultException</tt> which is to represent a
     * specific <tt>HRESULT</tt> value.
     *
     * @param hresult the <tt>HRESULT</tt> value to be represented by the new
     * instance
     */
    public HResultException(int hresult)
    {
        this.hresult = hresult;
    }

    /**
     * Gets the <tt>HRESULT</tt> value represented by this instance.
     *
     * @return the <tt>HRESULT</tt> value represented by this instance
     */
    public int getHResult()
    {
        return hresult;
    }
}
