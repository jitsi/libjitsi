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

#include <org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP.h>

#include <objbase.h> /* CoTaskMemAlloc */
#include <propsys.h> /* IPropertyStore */
#include <cstdint> /* intptr_t */

#include "HResultException.h"
#include "MediaBuffer.h"
#include "Typecasting.h"

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_DMO_1MEDIA_1TYPE_1fill
    (JNIEnv *env, jclass clazz, jlong thiz, jstring majortype, jstring subtype,
        jboolean bFixedSizeSamples, jboolean bTemporalCompression,
        jint lSampleSize, jstring formattype, jlong pUnk, jint cbFormat,
        jlong pbFormat)
{
    HRESULT hr;
    auto *thiz_ = (DMO_MEDIA_TYPE *) (intptr_t) thiz;

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
    auto *thiz = (DMO_OUTPUT_DATA_BUFFER *) CoTaskMemAlloc(sizeof(DMO_OUTPUT_DATA_BUFFER));
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
    return (jint) ((IMediaBuffer *) (intptr_t) thiz)->AddRef();
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1GetBuffer
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    BYTE *pBuffer = nullptr;
    HRESULT hr = ((IMediaBuffer *) (intptr_t) thiz)->GetBufferAndLength(&pBuffer, nullptr);
    if (FAILED(hr))
    {
        pBuffer = nullptr;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pBuffer;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1GetLength
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    DWORD cbLength;
    HRESULT hr = ((IMediaBuffer *) (intptr_t) thiz)->GetBufferAndLength(nullptr, &cbLength);
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
    HRESULT hr = ((IMediaBuffer *) (intptr_t) thiz)->GetMaxLength(&cbMaxLength);
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
    return (jint) ((IMediaBuffer *) (intptr_t) thiz)->Release();
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaBuffer_1SetLength
    (JNIEnv *env, jclass clazz, jlong thiz, jint cbLength)
{
    HRESULT hr = ((IMediaBuffer *) (intptr_t) thiz)->SetLength((DWORD) cbLength);
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1Flush
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->Flush();
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1GetInputStatus
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwInputStreamIndex)
{
    DWORD dwFlags;
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->GetInputStatus((DWORD) dwInputStreamIndex, &dwFlags);
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
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->ProcessInput(
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
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->ProcessOutput(
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
        hr = ((IMediaObject *) (intptr_t) thiz)->QueryInterface(iid_, &pvObject);
        if (FAILED(hr))
        {
            pvObject = nullptr;
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
    }
    else
        pvObject = nullptr;
    return (jlong) (intptr_t) pvObject;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    ((IMediaObject *) (intptr_t) thiz)->Release();
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IMediaObject_1SetInputType
    (JNIEnv *env, jclass clazz, jlong thiz, jint dwInputStreamIndex, jlong pmt,
        jint dwFlags)
{
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->SetInputType(
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
    HRESULT hr = ((IMediaObject *) (intptr_t) thiz)->SetOutputType(
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

    PropVariantInit(&propvar);
    propvar.boolVal = (JNI_TRUE == value) ? VARIANT_TRUE : VARIANT_FALSE;
    propvar.vt = VT_BOOL;
    HRESULT hr = ((IPropertyStore *) (intptr_t) thiz)->SetValue(
                *((PROPERTYKEY *)(intptr_t)key),
                propvar);
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_IPropertyStore_1SetValue__JJI
    (JNIEnv *env, jclass clazz, jlong thiz, jlong key, jint value)
{
    PROPVARIANT propvar;

    PropVariantInit(&propvar);
    propvar.lVal = value;
    propvar.vt = VT_I4;
    HRESULT hr = ((IPropertyStore *) (intptr_t) thiz)->SetValue(
                *((PROPERTYKEY *)(intptr_t)key),
                propvar);
    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1alloc
    (JNIEnv *env, jclass clazz, jint maxLength)
{
    IMediaBuffer *buffer = nullptr;
    HRESULT hr = CMediaBuffer::Create((DWORD) maxLength, &buffer);
    if (SUCCEEDED(hr))
    {
        return (jlong) (intptr_t) buffer;
    }

    return (jlong) (intptr_t) nullptr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1pop
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray buffer, jint offset,
        jint length)
{
    DWORD read = 0;

    if (buffer)
    {
        auto *buffer_ = (jbyte*) env->GetPrimitiveArrayCritical(buffer, nullptr);
        if (buffer_)
        {
            read = ((CMediaBuffer *) (intptr_t) thiz)->pop(((BYTE *) buffer_) + offset, (DWORD) length);
            env->ReleasePrimitiveArrayCritical(buffer, buffer_, 0);
        }
    }
    else if (length)
    {
        read = ((CMediaBuffer *) (intptr_t) thiz)->pop(nullptr, length);
    }
    return (jint) read;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_VoiceCaptureDSP_MediaBuffer_1push
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray buffer, jint offset,
        jint length)
{
    auto *buffer_ = (jbyte*) env->GetPrimitiveArrayCritical(buffer, nullptr);
    jint written = 0;
    if (buffer_)
    {
        written = (jint)((CMediaBuffer *) (intptr_t) thiz)->push(((BYTE *) buffer_) + offset, (DWORD) length);
        env->ReleasePrimitiveArrayCritical(buffer, buffer_, JNI_ABORT);
    }
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
        pmt = nullptr;
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
