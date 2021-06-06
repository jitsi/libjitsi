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

import static org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi.WASAPI.*;

import org.jitsi.utils.logging.*;

/**
 * Defines the native interface of Voice Capture DSP as used by
 * <tt>WASAPISystem</tt> and its associated <tt>CaptureDevice</tt>,
 * <tt>DataSource</tt> and <tt>Renderer</tt> implementations.
 *
 * @author Lyubomir Marinov
 * @author Timothy Price
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

    /**
     * The <tt>Logger</tt> used by the <tt>VoiceCaptureDSP</tt> class to print
     * out debugging information.
     */
    private static final Logger logger
        = Logger.getLogger(VoiceCaptureDSP.class);

    public static final String MEDIASUBTYPE_PCM
        = "{00000001-0000-0010-8000-00AA00389B71}";

    public static final String MEDIATYPE_Audio
        = "{73647561-0000-0010-8000-00aa00389b71}";

    /**
     * Specifies which audio devices the Voice Capture DSP uses for capturing
     * and rendering audio. Set this property if you are using the DSP in source
     * mode. The DSP ignores this property in filter mode. The value of the
     * property is two 16-bit <tt>WORD</tt>s packed into a <tt>DWORD</tt> (i.e.
     * a Java <tt>int</tt>). The upper 16 bits specify the audio rendering
     * device (typically a speaker), and the lower 16 bits specify the capture
     * device (typically a microphone). Each device is specified as an index
     * into the audio device collection. If the index is <tt>-1</tt>, the
     * default device is used.
     */
    public static final long MFPKEY_WMAAECMA_DEVICE_INDEXES;

    /**
     * Specifies whether the Voice Capture DSP uses source mode (<tt>true</tt>)
     * or filter mode (<tt>false</tt>). In source mode, the application does not
     * need to send input data to the DSP, because the DSP automatically pulls
     * data from the audio devices. In filter mode, the application must send
     * the input data to the DSP.
     */
    public static final long MFPKEY_WMAAECMA_DMO_SOURCE_MODE;

    /**
     * Specifies how many times the Voice Capture DSP performs acoustic echo
     * suppression (AES) on the residual signal. The Voice Capture DSP can
     * perform AES on the residual signal after echo cancellation. This
     * <tt>int</tt> property can have the value <tt>0</tt>, <tt>1</tt>, or
     * <tt>2</tt>. The default value is <tt>0</tt>. Before setting this
     * property, you must set the {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property
     * to <tt>true</tt>. The DSP uses this property only when AEC processing is
     * enabled.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_AES;

    /**
     * Specifies whether the Voice Capture DSP performs automatic gain control.
     * Automatic gain control is a digital signal processing (DSP) component
     * that adjusts the gain so that the output level of the signal remains
     * within the same approximate range. The default value of this property is
     * <tt>false</tt> (i.e. disabled). Before setting this property, you must
     * set the {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property to <tt>true</tt>.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_AGC;

    /**
     * Specifies whether the Voice Capture DSP performs center clipping. Center
     * clipping is a process that removes small echo residuals that remain after
     * AEC processing, in single-talk situations (when speech occurs only on one
     * end of the line). The default value of this property is <tt>true</tt>
     * (i.e. enabled). Before setting this property, you must set the
     * {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property to <tt>true</tt>. The DSP
     * uses this property only when AEC processing is enabled.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_CENTER_CLIP;

    /**
     * Specifies the duration of echo that the acoustic echo cancellation (AEC)
     * algorithm can handle, in milliseconds. The AEC algorithm uses an adaptive
     * filter whose length is determined by the duration of the echo. It is
     * recommended that applications use one of the following <tt>int</tt>
     * values: <tt>128</tt>, <tt>256</tt>, <tt>512</tt>, <tt>1024</tt>. The
     * default value is <tt>256</tt> milliseconds, which is sufficient for most
     * office and home environments. Before setting this property, you must set
     * the {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property to <tt>true</tt>. The
     * DSP uses this property only when AEC processing is enabled.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_ECHO_LENGTH;

    /**
     * Specifies whether the Voice Capture DSP performs noise filling. Noise
     * filling adds a small amount of noise to portions of the signal where
     * center clipping has removed the residual echoes. This results in a better
     * experience for the user than leaving silent gaps in the signal. The
     * default value of this property is <tt>true</tt> (i.e. enabled). Before
     * setting this property, you must set the
     * {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property to <tt>true</tt>. The DSP
     * uses this property only when AEC processing is enabled.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_NOISE_FILL;

    /**
     * Specifies whether the Voice Capture DSP performs noise suppression. Noise
     * suppression is a digital signal processing (DSP) component that
     * suppresses or reduces stationary background noise in the audio signal.
     * Noise suppression is applied after the acoustic echo cancellation (AEC)
     * and microphone array processing. The property can have the following
     * <tt>int</tt> values: <tt>0</tt> to disable noise suppression or
     * <tt>1</tt> to enable noise suppression. The default value of this
     * property is <tt>1</tt> (i.e. enabled). Before setting this property, you
     * must set the {@link #MFPKEY_WMAAECMA_FEATURE_MODE} property to
     * <tt>true</tt>.
     */
    public static final long MFPKEY_WMAAECMA_FEATR_NS;

    /**
     * Enables the application to override the default settings on various
     * properties of the Voice Capture DSP. If this property is <tt>true</tt>,
     * the application can set the <tt>MFPKEY_WMAAECMA_FEATR_XXX</tt> properties
     * on the DSP. If this property is <tt>false</tt>, the DSP ignores these
     * properties and uses its default settings. The default value of this
     * property is <tt>false</tt>.
     */
    public static final long MFPKEY_WMAAECMA_FEATURE_MODE;

    /**
     * Specifies whether the Voice Capture DSP applies microphone gain bounding.
     * Microphone gain bounding ensures that the microphone has the correct
     * level of gain. If gain is too high, the captured signal might be
     * saturated and will be clipped. Clipping is a non-linear effect, which
     * will cause the acoustic echo cancellation (AEC) algorithm to fail. If the
     * gain is too low, the signal-to-noise ratio is low, which can also cause
     * the AEC algorithm to fail or not perform well. The default value of this
     * <tt>boolean</tt> property is <tt>true</tt>. Microphone gain bounding is
     * applied only when the DSP operates in source mode. In filter mode, the
     * application must ensure that the microphone has the correct gain level.
     * In order to disable automatic gain control (AGC), microphone gain
     * bounding must also be disabled.
     */
    public static final long MFPKEY_WMAAECMA_MIC_GAIN_BOUNDER;

    /**
     * Specifies the processing mode for the Voice Capture DSP.
     *
     * @see #SINGLE_CHANNEL_AEC
     */
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
        String fmtid = "{6f52c567-0360-4bd2-9617-ccbf1421c939} ";
        String pszString = null;
        long _MFPKEY_WMAAECMA_DEVICE_INDEXES = 0;
        long _MFPKEY_WMAAECMA_DMO_SOURCE_MODE = 0;
        long _MFPKEY_WMAAECMA_SYSTEM_MODE = 0;
        /*
         * XXX The pointer to native memory returned by PSPropertyKeyFromString
         * is to be freed via CoTaskMemFree.
         */
        boolean coTaskMemFree = true;

        try
        {
            pszString = fmtid + "4";
            _MFPKEY_WMAAECMA_DEVICE_INDEXES
                = PSPropertyKeyFromString(pszString);
            if (_MFPKEY_WMAAECMA_DEVICE_INDEXES == 0)
            {
                throw new IllegalStateException(
                        "MFPKEY_WMAAECMA_DEVICE_INDEXES");
            }

            pszString = fmtid + "3";
            _MFPKEY_WMAAECMA_DMO_SOURCE_MODE
                = PSPropertyKeyFromString(pszString);
            if (_MFPKEY_WMAAECMA_DMO_SOURCE_MODE == 0)
            {
                throw new IllegalStateException(
                        "MFPKEY_WMAAECMA_DMO_SOURCE_MODE");
            }

            pszString = fmtid + "2";
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

        MFPKEY_WMAAECMA_DEVICE_INDEXES = _MFPKEY_WMAAECMA_DEVICE_INDEXES;
        MFPKEY_WMAAECMA_DMO_SOURCE_MODE = _MFPKEY_WMAAECMA_DMO_SOURCE_MODE;
        MFPKEY_WMAAECMA_SYSTEM_MODE = _MFPKEY_WMAAECMA_SYSTEM_MODE;

        /*
         * The support for the remaining properties of the Voice Capture DSP is
         * optional at the time of this writing.
         */
        MFPKEY_WMAAECMA_FEATR_AES = maybePSPropertyKeyFromString(fmtid + "10");
        MFPKEY_WMAAECMA_FEATR_AGC = maybePSPropertyKeyFromString(fmtid + "9");
        MFPKEY_WMAAECMA_FEATR_CENTER_CLIP
            = maybePSPropertyKeyFromString(fmtid + "12");
        MFPKEY_WMAAECMA_FEATR_ECHO_LENGTH
            = maybePSPropertyKeyFromString(fmtid + "7");
        MFPKEY_WMAAECMA_FEATR_NOISE_FILL
            = maybePSPropertyKeyFromString(fmtid + "13");
        MFPKEY_WMAAECMA_FEATR_NS = maybePSPropertyKeyFromString(fmtid + "8");
        MFPKEY_WMAAECMA_FEATURE_MODE
            = maybePSPropertyKeyFromString(fmtid + "5");
        MFPKEY_WMAAECMA_MIC_GAIN_BOUNDER
            = maybePSPropertyKeyFromString(fmtid + "21");
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

    /**
     * Invokes {@link WASAPI#PSPropertyKeyFromString(String)} and logs and
     * swallows any <tt>HResultException</tt>.
     *
     * @param pszString the <tt>String</tt> formatted as
     * &quot;<tt>{fmtid} pid</tt>&quot; to be converted to a pointer to a
     * <tt>PROPERTYKEY</tt> structure
     * @return a pointer to a <tt>PROPERTYKEY</tt> structure. To be freed via
     * {@link WASAPI#CoTaskMemFree(long)}.
     */
    private static long maybePSPropertyKeyFromString(String pszString)
    {
        long pkey;

        try
        {
            pkey = PSPropertyKeyFromString(pszString);
        }
        catch (HResultException hre)
        {
            pkey = 0;
            logger.error("PSPropertyKeyFromString " + pszString, hre);
        }
        return pkey;
    }

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
