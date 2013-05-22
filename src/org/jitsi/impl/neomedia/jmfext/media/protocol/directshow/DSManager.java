/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.directshow;

/**
 * DirectShow capture device manager.
 *
 * <code>
 * DSManager manager = new DSManager();
 * DSCaptureDevice[] devices = manager.getCaptureDevices();
 *
 * // Utilize any DSCaptureDevice instance obtained through manager.
 *
 * manager.dispose();
 *
 * // Do NOT utilize any DSCaptureDevice instance obtained through manager!
 * </code>
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 */
public class DSManager
{
    /**
     * Empty array of <tt>DSCaptureDevice</tt>s. Explicitly defined in order to
     * avoid unnecessary allocations.
     */
    private static DSCaptureDevice[] EMPTY_DEVICES = new DSCaptureDevice[0];

    static
    {
        System.loadLibrary("jndirectshow");
    }

    /**
     * Delete native pointer.
     *
     * @param ptr native pointer to delete
     */
    private static native void destroy(long ptr);

    /**
     * Initialize and gather existing capture device.
     *
     * @return native pointer
     */
    private static native long init();

    /**
     * Array of all <tt>DSCaptureDevice</tt>s found on the OS.
     */
    private DSCaptureDevice[] captureDevices;

    /**
     * Native pointer.
     */
    private final long ptr;

    /**
     * Constructor.
     */
    public DSManager()
    {
        ptr = init();
        if (ptr == 0)
            throw new IllegalStateException("ptr");
    }

    /**
     * Dispose the object.
     */
    public void dispose()
    {
        destroy(ptr);
    }

    /**
     * Get the array of capture devices.
     *
     * @return array of <tt>DSCaptureDevice</tt>s
     */
    public DSCaptureDevice[] getCaptureDevices()
    {
        if (captureDevices == null)
        {
            long ptrs[] = getCaptureDevices(ptr);

            if ((ptrs != null) && (ptrs.length != 0))
            {
                captureDevices = new DSCaptureDevice[ptrs.length];
                for (int i = 0 ; i < ptrs.length ; i++)
                    captureDevices[i] = new DSCaptureDevice(ptrs[i]);
            }
            else
                captureDevices = EMPTY_DEVICES;
        }
        return captureDevices;
    }

    /**
     * Native method to get capture devices pointers.
     *
     * @param ptr native pointer of DSManager
     * @return array of native pointer to DSCaptureDevice
     */
    private native long[] getCaptureDevices(long ptr);
}
