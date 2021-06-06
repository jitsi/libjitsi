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
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * Defines the native interface to Windows Audio Session API (WASAPI) and
 * related Core Audio APIs such as Multimedia Device (MMDevice) API as used by
 * <tt>WASAPISystem</tt> and its associated <tt>CaptureDevice</tt>,
 * <tt>DataSource</tt> and <tt>Renderer</tt> implementations.
 *
 * @author Lyubomir Marinov
 */
public class WASAPI
{
    public static final int AUDCLNT_E_NOT_STOPPED;

    public static final int AUDCLNT_SHAREMODE_SHARED = 0;

    public static final int AUDCLNT_STREAMFLAGS_EVENTCALLBACK = 0x00040000;

    public static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;

    public static final int AUDCLNT_STREAMFLAGS_NOPERSIST = 0x00080000;

    public static final int CLSCTX_ALL
        = /* CLSCTX_INPROC_SERVER */ 0x1
            | /* CLSCTX_INPROC_HANDLER */ 0x2
            | /* CLSCTX_LOCAL_SERVER */ 0x4
            | /* CLSCTX_REMOTE_SERVER */ 0x10;

    public static final String CLSID_MMDeviceEnumerator
        = "{bcde0395-e52f-467c-8e3d-c4579291692e}";

    public static final int COINIT_MULTITHREADED = 0x0;

    public static final int DEVICE_STATE_ACTIVE = 0x1;

    public static final int eAll = 2;

    public static final int eCapture = 1;

    public static final int eRender = 0;

    private static final int FACILIY_AUDCLNT = 0x889;

    public static final String IID_IAudioCaptureClient
        = "{c8adbd64-e71e-48a0-a4de-185c395cd317}";

    public static final String IID_IAudioClient
        = "{1cb9ad4c-dbfa-4c32-b178-c2f568a703b2}";

    public static final String IID_IAudioRenderClient
        = "{f294acfc-3146-4483-a7bf-addca7c260e2}";

    public static final String IID_IMMDeviceEnumerator
        = "{a95664d2-9614-4f35-a746-de8db63617e6}";

    public static final String IID_IMMEndpoint
        = "{1be09788-6894-4089-8586-9a2a6c265ac5}";

    public static final long PKEY_Device_FriendlyName;

    public static final int RPC_E_CHANGED_MODE = 0x80010106;

    public static final int S_FALSE = 1;

    public static final int S_OK = 0;

    private static final int SEVERITY_ERROR = 1;

    private static final int SEVERITY_SUCCESS = 0;

    public static final int STGM_READ = 0x0;

    /**
     * The return value of {@link #WaitForSingleObject(long, long)} which
     * indicates that the specified object is a mutex that was not released by
     * the thread that owned the mutex before the owning thread terminated.
     * Ownership of the mutex is granted to the calling thread and the mutex
     * state is set to non-signaled.
     */
    public static final int WAIT_ABANDONED = 0x00000080;

    /**
     * The return value of {@link #WaitForSingleObject(long, long)} which
     * indicates that the function has failed. Normally, the function will throw
     * an {@link HResultException} in the case and
     * {@link HResultException#getHResult()} will return <tt>WAIT_FAILED</tt>.
     */
    public static final int WAIT_FAILED = 0xffffffff;

    /**
     * The return value of {@link #WaitForSingleObject(long, long)} which
     * indicates that the specified object is signaled.
     */
    public static final int WAIT_OBJECT_0 = 0x00000000;

    /**
     * The return value of {@link #WaitForSingleObject(long, long)} which
     * indicates that the specified time-out interval has elapsed and the state
     * of the specified object is non-signaled.
     */
    public static final int WAIT_TIMEOUT = 0x00000102;

    public static final char WAVE_FORMAT_PCM = 1;

    static
    {
        JNIUtils.loadLibrary("jnwasapi", WASAPI.class);

        AUDCLNT_E_NOT_STOPPED
            = MAKE_HRESULT(SEVERITY_ERROR, FACILIY_AUDCLNT, 5);

        /*
         * XXX The pointer to native memory returned by PSPropertyKeyFromString
         * is to be freed via CoTaskMemFree.
         */
        String pszString = null;

        try
        {
            pszString = "{a45c254e-df1c-4efd-8020-67d146a850e0} 14";
            PKEY_Device_FriendlyName = PSPropertyKeyFromString(pszString);
            if (PKEY_Device_FriendlyName == 0)
                throw new IllegalStateException("PKEY_Device_FriendlyName");
        }
        catch (HResultException hre)
        {
            Logger logger = Logger.getLogger(WASAPI.class);

            logger.error("PSPropertyKeyFromString(" + pszString + ")", hre);
            throw new RuntimeException(hre);
        }
    }

    public static native void CloseHandle(long hObject)
        throws HResultException;

    public static native String CoCreateGuid()
        throws HResultException;

    public static native long CoCreateInstance(
            String clsid,
            long pUnkOuter,
            int dwClsContext,
            String iid)
        throws HResultException;

    public static native int CoInitializeEx(long pvReserved, int dwCoInit)
        throws HResultException;

    public static native void CoTaskMemFree(long pv);

    public static native void CoUninitialize();

    public static native long CreateEvent(
            long lpEventAttributes,
            boolean bManualReset,
            boolean bInitialState,
            String lpName)
        throws HResultException;

    /**
     * Determines whether a specific <tt>HRESULT</tt> value indicates failure.
     *
     * @param hresult the <tt>HRESULT</tt> value to be checked whether it
     * indicates failure
     * @return <tt>true</tt> if the specified <tt>hresult</tt> indicates
     * failure; otherwise, <tt>false</tt>
     */
    public static boolean FAILED(int hresult)
    {
        return (hresult < 0);
    }

    public static native int IAudioCaptureClient_GetNextPacketSize(long thiz)
        throws HResultException;

    public static native int IAudioCaptureClient_Read(
            long thiz,
            byte[] data, int offset, int length,
            int srcSampleSize, int srcChannels,
            int dstSampleSize, int dstChannels)
        throws HResultException;

    public static native void IAudioCaptureClient_Release(long thiz);

    public static native int IAudioClient_GetBufferSize(long thiz)
        throws HResultException;

    public static native int IAudioClient_GetCurrentPadding(long thiz)
        throws HResultException;

    public static native long IAudioClient_GetDefaultDevicePeriod(long thiz)
        throws HResultException;

    public static native long IAudioClient_GetMinimumDevicePeriod(long thiz)
        throws HResultException;

    public static native long IAudioClient_GetService(long thiz, String iid)
        throws HResultException;

    public static native int IAudioClient_Initialize(
            long thiz,
            int shareMode,
            int streamFlags,
            long hnsBufferDuration,
            long hnsPeriodicity,
            long pFormat,
            String audioSessionGuid)
        throws HResultException;

    public static native long IAudioClient_IsFormatSupported(
            long thiz,
            int shareMode,
            long pFormat)
        throws HResultException;

    public static native void IAudioClient_Release(long thiz);

    public static native void IAudioClient_SetEventHandle(
            long thiz,
            long eventHandle)
        throws HResultException;

    public static native int IAudioClient_Start(long thiz)
        throws HResultException;

    public static native int IAudioClient_Stop(long thiz)
        throws HResultException;

    public static native void IAudioRenderClient_Release(long thiz);

    /**
     * Writes specific audio data into the rendering endpoint buffer of a
     * specific <tt>IAudioRenderClient</tt>. If the sample sizes and/or the
     * numbers of channels of the specified audio <tt>data</tt> and the
     * specified rendering endpoint buffer differ, the method may be able to
     * perform the necessary conversions.
     *
     * @param thiz the <tt>IAudioRenderClient</tt> which abstracts the rendering
     * endpoint buffer into which the specified audio <tt>data</tt> is to be
     * written
     * @param data the bytes of the audio samples to be written into the
     * specified rendering endpoint buffer
     * @param offset the offset in bytes within <tt>data</tt> at which valid
     * audio samples begin
     * @param length the number of bytes of valid audio samples in <tt>data</tt>
     * @param srcSampleSize the size in bytes of an audio sample in
     * <tt>data</tt>
     * @param srcChannels the number of channels of the audio signal provided
     * in <tt>data</tt>
     * @param dstSampleSize the size in bytes of an audio sample in the
     * rendering endpoint buffer
     * @param dstChannels the number of channels with which the rendering
     * endpoint buffer has been initialized
     * @return the number of bytes which have been read from <tt>data</tt>
     * (beginning at <tt>offset</tt>, of course) and successfully written into
     * the rendering endpoint buffer
     * @throws HResultException if an HRESULT value indicating an error is
     * returned by a function invoked by the method implementation or an I/O
     * error is encountered during the execution of the method
     */
    public static native int IAudioRenderClient_Write(
            long thiz,
            byte[] data, int offset, int length,
            int srcSampleSize, int srcChannels,
            int dstSampleSize, int dstChannels)
        throws HResultException;

    public static native long IMMDevice_Activate(
            long thiz,
            String iid,
            int dwClsCtx,
            long pActivationParams)
        throws HResultException;

    public static native String IMMDevice_GetId(long thiz)
        throws HResultException;

    public static native int IMMDevice_GetState(long thiz)
        throws HResultException;

    public static native long IMMDevice_OpenPropertyStore(
            long thiz,
            int stgmAccess)
        throws HResultException;

    public static native long IMMDevice_QueryInterface(long thiz, String iid)
        throws HResultException;

    public static native void IMMDevice_Release(long thiz);

    public static native int IMMDeviceCollection_GetCount(long thiz)
        throws HResultException;

    public static native long IMMDeviceCollection_Item(long thiz, int nDevice)
        throws HResultException;

    public static native void IMMDeviceCollection_Release(long thiz);

    public static native long IMMDeviceEnumerator_EnumAudioEndpoints(
            long thiz,
            int dataFlow,
            int dwStateMask)
        throws HResultException;

    public static native long IMMDeviceEnumerator_GetDevice(
            long thiz,
            String pwstrId)
        throws HResultException;

    public static native void IMMDeviceEnumerator_Release(long thiz);

    public static native int IMMEndpoint_GetDataFlow(long thiz)
        throws HResultException;

    public static native void IMMEndpoint_Release(long thiz);

    public static native String IPropertyStore_GetString(long thiz, long key)
        throws HResultException;

    public static native void IPropertyStore_Release(long thiz);

    private static int MAKE_HRESULT(int sev, int fac, int code)
    {
        return ((sev & 0x1) << 31) | ((fac & 0x7fff) << 16) | (code & 0xffff);
    }

    public static native long PSPropertyKeyFromString(String pszString)
        throws HResultException;

    public static native void ResetEvent(long hEvent)
        throws HResultException;

    /**
     * Determines whether a specific <tt>HRESULT</tt> value indicates success.
     *
     * @param hresult the <tt>HRESULT</tt> value to be checked whether it
     * indicates success
     * @return <tt>true</tt> if the specified <tt>hresult</tt> indicates
     * success; otherwise, <tt>false</tt>
     */
    public static boolean SUCCEEDED(int hresult)
    {
        return (hresult >= 0);
    }

    /**
     * Waits until the specified object is in the signaled state or the
     * specified time-out interval elapses.
     *
     * @param hHandle a <tt>HANDLE</tt> to the object to wait for
     * @param dwMilliseconds the time-out interval in milliseconds to wait. If a
     * nonzero value is specified, the function waits until the specified object
     * is signaled or the specified time-out interval elapses. If
     * <tt>dwMilliseconds</tt> is zero, the function does not enter a wait state
     * if the specified object is not signaled; it always returns immediately.
     * If <tt>dwMilliseconds</tt> is <tt>INFINITE</tt>, the function will return
     * only when the specified object is signaled.
     * @return one of the <tt>WAIT_XXX</tt> constant values defined by the
     * <tt>WASAPI</tt> class to indicate the event that caused the function to
     * return
     * @throws HResultException if the return value is {@link #WAIT_FAILED}
     */
    public static native int WaitForSingleObject(
            long hHandle,
            long dwMilliseconds)
        throws HResultException;

    public static native long WAVEFORMATEX_alloc();

    public static native void WAVEFORMATEX_fill(
            long thiz,
            char wFormatTag,
            char nChannels,
            int nSamplesPerSec,
            int nAvgBytesPerSec,
            char nBlockAlign,
            char wBitsPerSample,
            char cbSize);

    public static native char WAVEFORMATEX_getCbSize(long thiz);

    public static native int WAVEFORMATEX_getNAvgBytesPerSec(long thiz);

    public static native char WAVEFORMATEX_getNBlockAlign(long thiz);

    public static native char WAVEFORMATEX_getNChannels(long thiz);

    public static native int WAVEFORMATEX_getNSamplesPerSec(long thiz);

    public static native char WAVEFORMATEX_getWBitsPerSample(long thiz);

    public static native char WAVEFORMATEX_getWFormatTag(long thiz);

    public static native void WAVEFORMATEX_setCbSize(long thiz, char cbSize);

    public static native void WAVEFORMATEX_setNAvgBytesPerSec(
            long thiz,
            int nAvgBytesPerSec);

    public static native void WAVEFORMATEX_setNBlockAlign(
            long thiz,
            char nBlockAlign);

    public static native void WAVEFORMATEX_setNChannels(
            long thiz,
            char nChannels);

    public static native void WAVEFORMATEX_setNSamplesPerSec(
            long thiz,
            int nSamplesPerSec);

    public static native void WAVEFORMATEX_setWBitsPerSample(
            long thiz,
            char wBitsPerSample);

    public static native void WAVEFORMATEX_setWFormatTag(
            long thiz,
            char wFormatTag);

    public static native int WAVEFORMATEX_sizeof();

    /** Prevents the initialization of <tt>WASAPI</tt> instances. */
    private WASAPI() {}
}
