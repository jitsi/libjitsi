/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP.h"

#include <objbase.h> /* CoTaskMemAlloc */
#include <propsys.h> /* IPropertyStore */
#include <stdint.h> /* intptr_t */

#include "HResultException.h"
#include "MediaBuffer.h"
#include "MinGW_dmo.h" /* DMO_MEDIA_TYPE, IMediaObject */
#include "Typecasting.h"

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1fill
    (JNIEnv *env, jclass clazz, jlong thiz, jstring majortype, jstring subtype,
        jboolean bFixedSizeSamples, jboolean bTemporalCompression,
        jint lSampleSize, jstring formattype, jlong pUnk, jint cbFormat,
        jlong pbFormat)
{
    HRESULT hr;
    DMO_MEDIA_TYPE *thiz_ = (DMO_MEDIA_TYPE *) (intptr_t) thiz;

    hr = WASAPI_iidFromString(env, majortype, &(thiz_->majortype));
    if (SUCCEEDED(hr))
    {
        hr = WASAPI_iidFromString(env, subtype, &(thiz_->subtype));
        if (SUCCEEDED(hr))
        {
            hr = WASAPI_iidFromString(env, formattype, &(thiz_->formattype));
            if (SUCCEEDED(hr))
            {
                thiz_->bFixedSizeSamples
                    = (JNI_TRUE == bFixedSizeSamples) ? TRUE : FALSE;
                thiz_->bTemporalCompression
                    = (JNI_TRUE == bTemporalCompression) ? TRUE : FALSE;
                thiz_->lSampleSize = (ULONG) lSampleSize;
                thiz_->pUnk = (IUnknown *) (intptr_t) pUnk;
                thiz_->cbFormat = (ULONG) cbFormat;
                thiz_->pbFormat = (BYTE *) (intptr_t) pbFormat;
            }
        }
    }
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1setCbFormat
    (JNIEnv *env, jclass clazz, jlong thiz, jint cbFormat)
{
    ((DMO_MEDIA_TYPE *) (intptr_t) thiz)->cbFormat = (ULONG) cbFormat;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1setFormattype
    (JNIEnv *env, jclass clazz, jlong thiz, jstring formattype)
{
    return
        WASAPI_iidFromString(
                env,
                formattype,
                &(((DMO_MEDIA_TYPE *) (intptr_t) thiz)->formattype));
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1setLSampleSize
    (JNIEnv *env, jclass clazz, jlong thiz, jint lSampleSize)
{
    ((DMO_MEDIA_TYPE *) (intptr_t) thiz)->lSampleSize = (ULONG) lSampleSize;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1setPbFormat
    (JNIEnv *env, jclass clazz, jlong thiz, jlong pbFormat)
{
    ((DMO_MEDIA_TYPE *) (intptr_t) thiz)->pbFormat = (BYTE *) (intptr_t) pbFormat;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1OUTPUT_1DATA_1BUFFER_1alloc
    (JNIEnv *env, jclass clazz, jlong pBuffer, jint dwStatus, jlong rtTimestamp,
        jlong rtTimelength)
{
    DMO_OUTPUT_DATA_BUFFER *thiz = CoTaskMemAlloc(sizeof(DMO_OUTPUT_DATA_BUFFER));

    if (thiz)
    {
        thiz->pBuffer = (IMediaBuffer *) (intptr_t) pBuffer;
        thiz->dwStatus = (DWORD) dwStatus;
        thiz->rtTimestamp = (REFERENCE_TIME) rtTimestamp;
        thiz->rtTimelength = (REFERENCE_TIME) rtTimelength;
    }
    return (jlong) (intptr_t) thiz;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1OUTPUT_1DATA_1BUFFER_1getDwStatus
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jint) (((DMO_OUTPUT_DATA_BUFFER *) (intptr_t) thiz)->dwStatus);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1OUTPUT_1DATA_1BUFFER_1setDwStatus
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwStatus)
{
    ((DMO_OUTPUT_DATA_BUFFER *) (intptr_t) thiz)->dwStatus = (DWORD) dwStatus;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1AddRef
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jint) IMediaBuffer_AddRef((IMediaBuffer *) (intptr_t) thiz);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1GetBuffer
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    BYTE *pBuffer;
    HRESULT hr
        = IMediaBuffer_GetBufferAndLength(
                (IMediaBuffer *) (intptr_t) thiz,
                &pBuffer, NULL);

    if (FAILED(hr))
    {
        pBuffer = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pBuffer;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1GetLength
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    DWORD cbLength;
    HRESULT hr
        = IMediaBuffer_GetBufferAndLength(
                (IMediaBuffer *) (intptr_t) thiz,
                NULL, &cbLength);

    if (FAILED(hr))
    {
        cbLength = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) cbLength;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1GetMaxLength
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    DWORD cbMaxLength;
    HRESULT hr
        = IMediaBuffer_GetMaxLength(
                (IMediaBuffer *) (intptr_t) thiz,
                &cbMaxLength);

    if (FAILED(hr))
    {
        cbMaxLength = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) cbMaxLength;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jint) IMediaBuffer_Release((IMediaBuffer *) (intptr_t) thiz);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1SetLength
    (JNIEnv *env, jclass clazz, jlong thiz, jint cbLength)
{
    HRESULT hr
        = IMediaBuffer_SetLength(
                (IMediaBuffer *) (intptr_t) thiz,
                (DWORD) cbLength);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1Flush
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr = IMediaObject_Flush((IMediaObject *) (intptr_t) thiz);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1GetInputStatus
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwInputStreamIndex)
{
    DWORD dwFlags;
    HRESULT hr
        = IMediaObject_GetInputStatus(
                (IMediaObject *) (intptr_t) thiz,
                (DWORD) dwInputStreamIndex,
                &dwFlags);

    if (FAILED(hr))
    {
        dwFlags = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) dwFlags;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1ProcessInput
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwInputStreamIndex,
        jlong pBuffer, jint dwFlags, jlong rtTimestamp, jlong rtTimelength)
{
    HRESULT hr
        = IMediaObject_ProcessInput(
                (IMediaObject *) (intptr_t) thiz,
                (DWORD) dwInputStreamIndex,
                (IMediaBuffer *) (intptr_t) pBuffer,
                (DWORD) dwFlags,
                (REFERENCE_TIME) rtTimestamp,
                (REFERENCE_TIME) rtTimelength);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1ProcessOutput
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwFlags,
        jint cOutputBufferCount, jlong pOutputBuffers)
{
    DWORD dwStatus;
    HRESULT hr
        = IMediaObject_ProcessOutput(
                (IMediaObject *) (intptr_t) thiz,
                (DWORD) dwFlags,
                (DWORD) cOutputBufferCount,
                (DMO_OUTPUT_DATA_BUFFER *) (intptr_t) pOutputBuffers,
                &dwStatus);

    if (FAILED(hr))
    {
        dwStatus = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) dwStatus;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1QueryInterface
    (JNIEnv *env, jclass clazz, jlong thiz, jstring iid)
{
    HRESULT hr;
    IID iid_;
    void *pvObject;

    hr = WASAPI_iidFromString(env, iid, &iid_);
    if (SUCCEEDED(hr))
    {
        hr = IMediaObject_QueryInterface((IMediaObject *) (intptr_t) thiz, &iid_, &pvObject);
        if (FAILED(hr))
        {
            pvObject = NULL;
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
    }
    else
        pvObject = NULL;
    return (jlong) (intptr_t) pvObject;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IMediaObject_Release((IMediaObject *) (intptr_t) thiz);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1SetInputType
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwInputStreamIndex, jlong pmt,
        jint dwFlags)
{
    HRESULT hr
        = IMediaObject_SetInputType(
                (IMediaObject *) (intptr_t) thiz,
                (DWORD) dwInputStreamIndex,
                (const DMO_MEDIA_TYPE *) (intptr_t) pmt,
                (DWORD) dwFlags);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1SetOutputType
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwOutputStreamIndex, jlong pmt,
        jint dwFlags)
{
    HRESULT hr
        = IMediaObject_SetOutputType(
                (IMediaObject *) (intptr_t) thiz,
                (DWORD) dwOutputStreamIndex,
                (const DMO_MEDIA_TYPE *) (intptr_t) pmt,
                (DWORD) dwFlags);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IPropertyStore_1SetValue__JJZ
    (JNIEnv *env, jclass clazz, jlong thiz, jlong key, jboolean value)
{
    PROPVARIANT propvar;
    HRESULT hr;

    PropVariantInit(&propvar);
    propvar.boolVal = (JNI_TRUE == value) ? VARIANT_TRUE : VARIANT_FALSE;
    propvar.vt = VT_BOOL;
    hr
        = IPropertyStore_SetValue(
                (IPropertyStore *) (intptr_t) thiz,
                (REFPROPERTYKEY) (intptr_t) key,
                &propvar);
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IPropertyStore_1SetValue__JJI
    (JNIEnv *env, jclass clazz, jlong thiz, jlong key, jint value)
{
    PROPVARIANT propvar;
    HRESULT hr;

    PropVariantInit(&propvar);
    propvar.lVal = value;
    propvar.vt = VT_I4;
    hr
        = IPropertyStore_SetValue(
                (IPropertyStore *) (intptr_t) thiz,
                (REFPROPERTYKEY) (intptr_t) key,
                &propvar);
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1alloc
    (JNIEnv *env, jclass clazz, jint maxLength)
{
    return (jlong) (intptr_t) MediaBuffer_alloc((DWORD) maxLength);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1pop
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray buffer, jint offset,
        jint length)
{
    jint read;

    if (buffer)
    {
        jbyte *buffer_ = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);

        if (buffer_)
        {
            read
                = MediaBuffer_pop(
                        (MediaBuffer *) (intptr_t) thiz,
                        ((BYTE *) buffer_) + offset,
                        (DWORD) length);
            (*env)->ReleasePrimitiveArrayCritical(env, buffer, buffer_, 0);
        }
        else
            read = 0;
    }
    else if (length)
    {
        read
            = (jint) MediaBuffer_pop(
                    (MediaBuffer *) (intptr_t) thiz,
                    NULL,
                    length);
    }
    else
        read = 0;
    return read;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1push
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray buffer, jint offset,
        jint length)
{
    jbyte *buffer_ = (*env)->GetPrimitiveArrayCritical(env, buffer, NULL);
    jint written;

    if (buffer_)
    {
        written
            = (jint)
                MediaBuffer_push(
                        (MediaBuffer *) (intptr_t) thiz,
                        ((BYTE *) buffer_) + offset,
                        (DWORD) length);
        (*env)->ReleasePrimitiveArrayCritical(env, buffer, buffer_, JNI_ABORT);
    }
    else
        written = 0;
    return written;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MoCreateMediaType
    (JNIEnv *env, jclass clazz, jint cbFormat)
{
    DMO_MEDIA_TYPE *pmt;
    HRESULT hr = MoCreateMediaType(&pmt, (DWORD) cbFormat);

    if (FAILED(hr))
    {
        pmt = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pmt;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MoDeleteMediaType
    (JNIEnv *env, jclass clazz, jlong pmt)
{
    HRESULT hr = MoDeleteMediaType((DMO_MEDIA_TYPE *) (intptr_t) pmt);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MoFreeMediaType
    (JNIEnv *env, jclass clazz, jlong pmt)
{
    HRESULT hr = MoFreeMediaType((DMO_MEDIA_TYPE *) (intptr_t) pmt);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MoInitMediaType
    (JNIEnv *env, jclass clazz, jlong pmt, jint cbFormat)
{
    HRESULT hr
        = MoInitMediaType((DMO_MEDIA_TYPE *) (intptr_t) pmt, (DWORD) cbFormat);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}
