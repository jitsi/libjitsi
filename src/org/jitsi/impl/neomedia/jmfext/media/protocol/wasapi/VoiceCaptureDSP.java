/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import org.jitsi.util.*;

/**
 * Defines the native interface of Voice Capture DSP as used by
 * <tt>WASAPISystem</tt> and its associated <tt>CaptureDevice</tt>,
 * <tt>DataSource</tt> and <tt>Renderer</tt> implementations.
 *
 * @author Lyubomir Marinov
 */
public class VoiceCaptureDSP
{
    public static final String CLSID_CWMAudioAEC
        = "{745057c7-f353-4f2d-a7ee-58434477730e}";

    public static final int DMO_E_NOTACCEPTING = 0x80040204;

    public static final int DMO_INPUT_STATUSF_ACCEPT_DATA = 0x1;

    public static final int DMO_OUTPUT_DATA_BUFFERF_INCOMPLETE = 0x01000000;

    public static final int DMO_SET_TYPEF_TEST_ONLY = 0x1;

    public static final String FORMAT_None
        = "{0f6417d6-c318-11d0-a43f-00a0c9223196}";

    public static final String FORMAT_WaveFormatEx
        = "{05589f81-c356-11ce-bf01-00aa0055595a}";

    public static final String IID_IMediaObject
        = "{d8ad0f58-5494-4102-97c5-ec798e59bcf4}";

    public static final String IID_IPropertyStore
        = "{886d8eeb-8cf2-4446-8d02-cdba1dbdcf99}";

    public static final String MEDIASUBTYPE_PCM
        = "{00000001-0000-0010-8000-00AA00389B71}";

    public static final String MEDIATYPE_Audio
        = "{73647561-0000-0010-8000-00aa00389b71}";

    public static final long MFPKEY_WMAAECMA_DMO_SOURCE_MODE;

    public static final long MFPKEY_WMAAECMA_SYSTEM_MODE;

    /**
     * The value of the <tt>AEC_SYSTEM_MODE</tt> enumeration which is used with
     * the <tt>MFPKEY_WMAAECMA_SYSTEM_MODE</tt> property to indicate that the
     * Voice Capture DSP is to operate in acoustic echo cancellation (AEC) only
     * mode.
     */
    public static final int SINGLE_CHANNEL_AEC = 0;

    static
    {
        String pszString = null;
        long _MFPKEY_WMAAECMA_DMO_SOURCE_MODE = 0;
        long _MFPKEY_WMAAECMA_SYSTEM_MODE = 0;
        /*
         * XXX The pointer to native memory returned by PSPropertyKeyFromString
         * is to be freed via CoTaskMemFree.
         */
        boolean coTaskMemFree = true;

        try
        {
            pszString = "{6f52c567-0360-4bd2-9617-ccbf1421c939} 3";
            _MFPKEY_WMAAECMA_DMO_SOURCE_MODE
                = PSPropertyKeyFromString(pszString);
            if (_MFPKEY_WMAAECMA_DMO_SOURCE_MODE == 0)
            {
                throw new IllegalStateException(
                        "MFPKEY_WMAAECMA_DMO_SOURCE_MODE");
            }

            pszString = "{6f52c567-0360-4bd2-9617-ccbf1421c939} 2";
            _MFPKEY_WMAAECMA_SYSTEM_MODE = PSPropertyKeyFromString(pszString);
            if (_MFPKEY_WMAAECMA_SYSTEM_MODE == 0)
                throw new IllegalStateException("MFPKEY_WMAAECMA_SYSTEM_MODE");

            coTaskMemFree = false;
        }
        catch (HResultException hre)
        {
            Logger logger = Logger.getLogger(VoiceCaptureDSP.class);

            logger.error("PSPropertyKeyFromString(" + pszString + ")", hre);
            throw new RuntimeException(hre);
        }
        finally
        {
            /*
             * XXX The pointer to native memory returned by
             * PSPropertyKeyFromString is to be freed via CoTaskMemFree.
             */
            if (coTaskMemFree)
            {
                if (_MFPKEY_WMAAECMA_DMO_SOURCE_MODE != 0)
                {
                    CoTaskMemFree(_MFPKEY_WMAAECMA_DMO_SOURCE_MODE);
                    _MFPKEY_WMAAECMA_DMO_SOURCE_MODE = 0;
                }
                if (_MFPKEY_WMAAECMA_SYSTEM_MODE != 0)
                {
                    CoTaskMemFree(_MFPKEY_WMAAECMA_SYSTEM_MODE);
                    _MFPKEY_WMAAECMA_SYSTEM_MODE = 0;
                }
            }
        }

        MFPKEY_WMAAECMA_DMO_SOURCE_MODE = _MFPKEY_WMAAECMA_DMO_SOURCE_MODE;
        MFPKEY_WMAAECMA_SYSTEM_MODE = _MFPKEY_WMAAECMA_SYSTEM_MODE;
    }

    public static native int DMO_MEDIA_TYPE_fill(
            long thiz,
            String majortype,
            String subtype,
            boolean bFixedSizeSamples,
            boolean bTemporalCompression,
            int lSampleSize,
            String formattype,
            long pUnk,
            int cbFormat,
            long pbFormat)
        throws HResultException;

    public static native void DMO_MEDIA_TYPE_setCbFormat(
            long thiz,
            int cbFormat);

    public static native int DMO_MEDIA_TYPE_setFormattype(
            long thiz,
            String formattype)
        throws HResultException;

    public static native void DMO_MEDIA_TYPE_setLSampleSize(
            long thiz,
            int lSampleSize);

    public static native void DMO_MEDIA_TYPE_setPbFormat(
            long thiz,
            long pbFormat);

    public static native long DMO_OUTPUT_DATA_BUFFER_alloc(
            long pBuffer,
            int dwStatus,
            long rtTimestamp,
            long rtTimelength);

    public static native int DMO_OUTPUT_DATA_BUFFER_getDwStatus(long thiz);

    public static native void DMO_OUTPUT_DATA_BUFFER_setDwStatus(
            long thiz,
            int dwStatus);

    public static native int IMediaBuffer_AddRef(long thiz);

    public static native long IMediaBuffer_GetBuffer(long thiz)
        throws HResultException;

    public static native int IMediaBuffer_GetLength(long thiz)
        throws HResultException;

    public static native int IMediaBuffer_GetMaxLength(long thiz)
        throws HResultException;

    public static native int IMediaBuffer_Release(long thiz);

    public static native void IMediaBuffer_SetLength(long thiz, int cbLength)
        throws HResultException;

    public static native int IMediaObject_Flush(long thiz)
        throws HResultException;

    public static native int IMediaObject_GetInputStatus(
            long thiz,
            int dwInputStreamIndex)
        throws HResultException;

    public static native int IMediaObject_ProcessInput(
            long thiz,
            int dwInputStreamIndex,
            long pBuffer,
            int dwFlags,
            long rtTimestamp,
            long rtTimelength)
        throws HResultException;

    public static native int IMediaObject_ProcessOutput(
            long thiz,
            int dwFlags,
            int cOutputBufferCount,
            long pOutputBuffers)
        throws HResultException;

    public static native long IMediaObject_QueryInterface(long thiz, String iid)
        throws HResultException;

    public static native void IMediaObject_Release(long thiz);

    public static native int IMediaObject_SetInputType(
            long thiz,
            int dwInputStreamIndex,
            long pmt,
            int dwFlags)
        throws HResultException;

    public static native int IMediaObject_SetOutputType(
            long thiz,
            int dwOutputStreamIndex,
            long pmt,
            int dwFlags)
        throws HResultException;

    public static native int IPropertyStore_SetValue(
            long thiz,
            long key, boolean value)
        throws HResultException;

    public static native int IPropertyStore_SetValue(
            long thiz,
            long key, int value)
        throws HResultException;

    public static native long MediaBuffer_alloc(int maxLength);

    public static native int MediaBuffer_pop(
            long thiz,
            byte[] buffer, int offset, int length)
        throws HResultException;

    public static native int MediaBuffer_push(
            long thiz,
            byte[] buffer, int offset, int length)
        throws HResultException;

    public static native long MoCreateMediaType(int cbFormat)
        throws HResultException;

    public static native void MoDeleteMediaType(long pmt)
        throws HResultException;

    public static native void MoFreeMediaType(long pmt)
        throws HResultException;

    public static native void MoInitMediaType(long pmt, int cbFormat)
        throws HResultException;

    /** Prevents the initialization of <tt>VoiceCaptureDSP</tt> instances. */
    private VoiceCaptureDSP() {}
}
