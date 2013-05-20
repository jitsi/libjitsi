/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.directshow;

/**
 * DirectShow capture device.
 *
 * @author Sebastien Vincent
 */
public class DSCaptureDevice
{
    /**
     * Empty array with <tt>DSFormat</tt> element type. Explicitly defined
     * in order to avoid unnecessary allocations.
     */
    private static final DSFormat EMPTY_FORMATS[] = new DSFormat[0];

    /**
     * Get bytes from <tt>buf</tt> native pointer and copy them
     * to <tt>ptr</tt> byte native pointer.
     *
     * @param ptr pointer to native data
     * @param buf byte native pointer (see ByteBufferPool)
     * @param length length of buf pointed by <tt>ptr</tt>
     * @return length written to <tt>buf</tt>
     */
    public static native int getBytes(long ptr, long buf, int length);

    /**
     * Native pointer of <tt>DSCaptureDevice</tt>.
     *
     * This pointer is hold and will be released by <tt>DSManager</tt>
     * singleton.
     */
    private long ptr = 0;

    /**
     * Constructor.
     *
     * @param ptr native pointer
     */
    public DSCaptureDevice(long ptr)
    {
        /* do not allow 0 pointer value */
        if(ptr == 0)
            throw new IllegalArgumentException("invalid ptr value (0)");

        this.ptr = ptr;
    }

    /**
     * Stop and close the capture device.
     */
    public void close()
    {
        close(ptr);
    }

    /**
     * Native method to close capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     */
    private native void close(long ptr);

    /**
     * Get current format.
     *
     * @return current format used
     */
    public DSFormat getFormat()
    {
        return getFormat(ptr);
    }

    /**
     * Native method to get format on the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return format current format
     */
    private native DSFormat getFormat(long ptr);

    /**
     * Get name of the capture device.
     *
     * @return name of the capture device
     */
    public String getName()
    {
        return getName(ptr).trim();
    }

    /**
     * Native method to get name of the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return name of the capture device
     */
    private native String getName(long ptr);

    /**
     * Get the supported video format this capture device supports.
     *
     * @return array of <tt>DSFormat</tt>
     */
    public DSFormat[] getSupportedFormats()
    {
        DSFormat formats[] = getSupportedFormats(ptr);

        if(formats == null)
        {
            formats = EMPTY_FORMATS;
        }

        return formats;
    }

    /**
     * Native method to get supported formats from capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return array of native pointer corresponding to formats
     */
    private native DSFormat[] getSupportedFormats(long ptr);

    /**
     * Open and initialize the capture device.
     */
    public void open()
    {
        open(ptr);
    }

    /**
     * Native method to open capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     */
    private native void open(long ptr);

    /**
     * Set a delegate to use when a frame is received.
     * @param delegate delegate
     */
    public void setDelegate(GrabberDelegate delegate)
    {
        setDelegate(ptr, delegate);
    }

    /**
     * Native method to set a delegate to use when a frame is received.
     * @param ptr native pointer
     * @param delegate delegate
     */
    public native void setDelegate(long ptr, GrabberDelegate delegate);

    /**
     * Set format to use with this capture device.
     *
     * @param format format to set
     */
    public void setFormat(DSFormat format)
    {
        setFormat(ptr, format);
    }

    /**
     * Native method to set format on the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @param format format to set
     */
    private native void setFormat(long ptr, DSFormat format);

    /**
     * Delegate class to handle grabbing frames.
     */
    public static abstract class GrabberDelegate
    {
        /**
         * Callback method when receiving frames.
         *
         * @param ptr native pointer to data
         * @param length length of data
         */
        public abstract void frameReceived(long ptr, int length);
    }
}
