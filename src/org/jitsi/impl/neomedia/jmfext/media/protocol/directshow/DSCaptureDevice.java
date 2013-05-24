/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

/**
 * DirectShow capture device.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DSCaptureDevice
{
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

    /**
     * Empty array with <tt>DSFormat</tt> element type. Explicitly defined
     * in order to avoid unnecessary allocations.
     */
    private static final DSFormat EMPTY_FORMATS[] = new DSFormat[0];

    public static final int S_FALSE = 1;

    public static final int S_OK = 0;

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
    private final long ptr;

    /**
     * Constructor.
     *
     * @param ptr native pointer
     */
    public DSCaptureDevice(long ptr)
    {
        /* Do not allow 0/NULL pointer value. */
        if (ptr == 0)
            throw new IllegalArgumentException("ptr");

        this.ptr = ptr;
    }

    /**
     * Connects to this DirectShow video capture device.
     */
    public void connect()
    {
        connect(ptr);
    }

    /**
     * Connects to the specified DirectShow video capture device
     *
     * @param ptr a pointer to a native <tt>DSCaptureDevice</tt> to connect to
     */
    private native void connect(long ptr);

    /**
     * Disconnects from this DirectShow video capture device.
     */
    public void disconnect()
    {
        disconnect(ptr);
    }

    /**
     * Disconnects from a specific DirectShow video capture device
     *
     * @param ptr a pointer to a native <tt>DSCaptureDevice</tt> to disconnect
     * from
     */
    private native void disconnect(long ptr);

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
        DSFormat[] formats = getSupportedFormats(ptr);

        return (formats == null) ? EMPTY_FORMATS : formats;
    }

    /**
     * Native method to get supported formats from capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @return array of native pointer corresponding to formats
     */
    private native DSFormat[] getSupportedFormats(long ptr);

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
    private native void setDelegate(long ptr, GrabberDelegate delegate);

    /**
     * Set format to use with this capture device.
     *
     * @param format format to set
     * @return an <tt>HRESULT</tt> value indicating whether the specified
     * <tt>format</tt> was successfully set or describing a failure
     * 
     */
    public int setFormat(DSFormat format)
    {
        return setFormat(ptr, format);
    }

    /**
     * Native method to set format on the capture device.
     *
     * @param ptr native pointer of <tt>DSCaptureDevice</tt>
     * @param format format to set
     * @return an <tt>HRESULT</tt> value indicating whether the specified
     * <tt>format</tt> was successfully set or describing a failure
     */
    private native int setFormat(long ptr, DSFormat format);

    public int start()
    {
        return start(ptr);
    }

    private native int start(long ptr);

    public int stop()
    {
        return stop(ptr);
    }

    private native int stop(long ptr);
}
