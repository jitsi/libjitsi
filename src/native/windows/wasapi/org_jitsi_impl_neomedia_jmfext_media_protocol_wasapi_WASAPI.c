/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI.h"

#ifdef INITGUID
    #include <mmdeviceapi.h>
#else /* #ifdef INITGUID */
    #define INITGUID
    #include <mmdeviceapi.h>
    #undef INITGUID
#endif /* #ifdef INITGUID */

#include <audioclient.h> /* IAudioClient */
#include <mmreg.h> /* WAVEFORMATEX */
#include <objbase.h>
#include <stdint.h> /* intptr_t */
#include <string.h>
#include <windows.h> /* LoadLibrary, GetProcAddress */

#include "HResultException.h"
#include "Typecasting.h"

#define PSPropertyKeyFromString(pszString,pkey) \
    WASAPI_pPSPropertyKeyFromString(pszString,pkey)

typedef HRESULT (WINAPI *LPPSPropertyKeyFromString)(LPCWSTR,PROPERTYKEY*);

ULONG STDMETHODCALLTYPE MMNotificationClient_AddRef
    (IMMNotificationClient* thiz);
static HRESULT MMNotificationClient_invoke
    (jmethodID methodID, jint a, jint b, LPCWSTR c, jint d, jlong e);
HRESULT STDMETHODCALLTYPE MMNotificationClient_OnDefaultDeviceChanged
    (IMMNotificationClient* thiz, EDataFlow flow, ERole role,
        LPCWSTR pwstrDeviceId);
HRESULT STDMETHODCALLTYPE MMNotificationClient_OnDeviceAdded
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId);
HRESULT STDMETHODCALLTYPE MMNotificationClient_OnDeviceRemoved
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId);
HRESULT STDMETHODCALLTYPE MMNotificationClient_OnDeviceStateChanged
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId, DWORD dwNewState);
HRESULT STDMETHODCALLTYPE MMNotificationClient_OnPropertyValueChanged
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId, const PROPERTYKEY key);
HRESULT STDMETHODCALLTYPE MMNotificationClient_QueryInterface
    (IMMNotificationClient *thiz, REFIID riid, void **ppvObject);
ULONG STDMETHODCALLTYPE MMNotificationClient_Release
    (IMMNotificationClient* thiz);
static UINT32 WASAPI_audiocopy
    (void *src, jint srcSampleSize, jint srcChannels, void *dst,
        jint dstSampleSize, jint dstChannels, UINT32 numFramesRequested);

#ifdef _MSC_VER
    DEFINE_GUID(IID_IMMDeviceEnumerator,0xa95664d2,0x9614,0x4f35,0xa7,0x46,0xde,0x8d,0xb6,0x36,0x17,0xe6);
    DEFINE_GUID(IID_IMMNotificationClient,0x7991eec9,0x7e89,0x4d85,0x83,0x90,0x6c,0x70,0x3c,0xec,0x60,0xc0);
#endif /* #ifdef _MSC_VER */

static jclass MMNotificationClient_class = 0;
static jmethodID MMNotificationClient_onDefaultDeviceChangedMethodID = 0;
static jmethodID MMNotificationClient_onDeviceAddedMethodID = 0;
static jmethodID MMNotificationClient_onDeviceRemovedMethodID = 0;
static jmethodID MMNotificationClient_onDeviceStateChangedMethodID = 0;
static jmethodID MMNotificationClient_onPropertyValueChangedMethodID = 0;
/**
 * The single IMMNotificationClient instance/implementation which is to be
 * registered with every IMMDeviceEnumerator instance.
 */
static IMMNotificationClientVtbl WASAPI_iMMNotificationClientVtbl
    = {
        MMNotificationClient_QueryInterface,
        MMNotificationClient_AddRef,
        MMNotificationClient_Release,
        MMNotificationClient_OnDeviceStateChanged,
        MMNotificationClient_OnDeviceAdded,
        MMNotificationClient_OnDeviceRemoved,
        MMNotificationClient_OnDefaultDeviceChanged,
        MMNotificationClient_OnPropertyValueChanged
    };
static IMMNotificationClientVtbl *WASAPI_iMMNotificationClient
    = &WASAPI_iMMNotificationClientVtbl;
/**
 * The pointer to the function PSPropertyKeyFromString implemented in the
 * library propsys.dll. MinGW-w64/TDM-GCC declares the function
 * PSPropertyKeyFromString but, unfortunately, does not provide a static library
 * to link to at this time.
 */
static LPPSPropertyKeyFromString WASAPI_pPSPropertyKeyFromString = NULL;
static JavaVM *WASAPI_vm = NULL;

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CloseHandle
    (JNIEnv *env, jclass clazz, jlong hObject)
{
    BOOL b = CloseHandle((HANDLE) (intptr_t) hObject);

    if (!b)
    {
        WASAPI_throwNewHResultException(
                env,
                HRESULT_FROM_WIN32(GetLastError()),
                __func__, __LINE__);
    }
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CoCreateGuid
    (JNIEnv *env, jclass clazz)
{
    HRESULT hr;
    GUID guid;
    jstring str;

    hr = CoCreateGuid(&guid);
    if (SUCCEEDED(hr))
    {
        WCHAR
            sz[
                2 /* braces */
                    + 4 /* hyphens */
                    + 128 /* bits */ / 4 /* bits per hex digit */
                    + 1 /* null terminator */];
        int toWrite = sizeof(sz) / sizeof(WCHAR);
        int written = StringFromGUID2(&guid, sz, toWrite);

        if (written == 0)
        {
            str = NULL;
            WASAPI_throwNewHResultException(
                    env,
                    E_OUTOFMEMORY,
                    __func__, __LINE__);
        }
        else if (written == toWrite)
        {
            str = (*env)->NewString(env, sz, wcslen(sz));
        }
        else
        {
            str = NULL;
            WASAPI_throwNewHResultException(
                    env,
                    E_FAIL,
                    __func__, __LINE__);
        }
    }
    else
    {
        str = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return str;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CoCreateInstance
    (JNIEnv *env, jclass clazz, jstring clsid, jlong pUnkOuter,
        jint dwClsContext, jstring iid)
{
    HRESULT hr;
    CLSID clsid_;
    LPVOID pv;

    if (clsid)
    {
        const jchar *szClsid = (*env)->GetStringChars(env, clsid, NULL);

        if (szClsid)
        {
            hr = CLSIDFromString((LPOLESTR) szClsid, &clsid_);
            (*env)->ReleaseStringChars(env, clsid, szClsid);
            if (FAILED(hr))
                WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
        else
            hr = E_OUTOFMEMORY;
    }
    else
        hr = S_OK;
    if (SUCCEEDED(hr))
    {
        IID iid_;

        hr = WASAPI_iidFromString(env, iid, &iid_);
        if (SUCCEEDED(hr))
        {
            hr
                = CoCreateInstance(
                        &clsid_,
                        (LPUNKNOWN) (intptr_t) pUnkOuter,
                        (DWORD) dwClsContext,
                        &iid_,
                        &pv);
            if (SUCCEEDED(hr))
            {
                /*
                 * There is statically allocated IMMNotificationClient instance
                 * which is to be registered with every IMMDeviceEnumerator
                 * instance.
                 */
                if (IsEqualIID(__uuidof(IID_IMMDeviceEnumerator), &iid_))
                {
                    IMMDeviceEnumerator_RegisterEndpointNotificationCallback(
                            (IMMDeviceEnumerator *) pv,
                            (IMMNotificationClient *)
                                &WASAPI_iMMNotificationClient);
                }
            }
            else
            {
                pv = NULL;
                WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
            }
        }
        else
            pv = NULL;
    }
    else
        pv = NULL;
    return (jlong) (intptr_t) pv;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CoInitializeEx
    (JNIEnv *env, jclass clazz, jlong pvReserved, jint dwCoInit)
{
    HRESULT hr
        = CoInitializeEx((LPVOID) (intptr_t) pvReserved, (DWORD) dwCoInit);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CoTaskMemFree
    (JNIEnv *env, jclass clazz, jlong pv)
{
    CoTaskMemFree((LPVOID) (intptr_t) pv);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CoUninitialize
    (JNIEnv *env, jclass clazz)
{
    CoUninitialize();
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_CreateEvent
    (JNIEnv *env, jclass clazz, jlong lpEventAttributes, jboolean bManualReset,
        jboolean bInitialState, jstring lpName)
{
    const jchar *lpName_;
    HRESULT hr;
    HANDLE ev;

    if (lpName)
    {
        lpName_ = (*env)->GetStringChars(env, lpName, NULL);
        hr = lpName_ ? S_OK : E_OUTOFMEMORY;
    }
    else
    {
        lpName_ = NULL;
        hr = S_OK;
    }
    if (SUCCEEDED(hr))
    {
        ev
            = CreateEventW(
                    (LPSECURITY_ATTRIBUTES) (intptr_t) lpEventAttributes,
                    (JNI_TRUE == bManualReset) ? TRUE : FALSE,
                    (JNI_TRUE == bInitialState) ? TRUE : FALSE,
                    lpName_);
        if (lpName_)
            (*env)->ReleaseStringChars(env, lpName, lpName_);
        if (!ev)
        {
            WASAPI_throwNewHResultException(
                    env,
                    HRESULT_FROM_WIN32(GetLastError()),
                    __func__, __LINE__);
        }
    }
    else
    {
        ev = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) ev;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioCaptureClient_1GetNextPacketSize
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    UINT32 numFramesInNextPacket;

    hr
        = IAudioCaptureClient_GetNextPacketSize(
                (IAudioCaptureClient *) (intptr_t) thiz,
                &numFramesInNextPacket);
    if (FAILED(hr))
    {
        numFramesInNextPacket = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) numFramesInNextPacket;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioCaptureClient_1Read
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray data, jint offset,
        jint length, jint srcSampleSize, jint srcChannels, jint dstSampleSize,
        jint dstChannels)
{
    HRESULT hr;
    IAudioCaptureClient *iAudioCaptureClient
        = (IAudioCaptureClient *) (intptr_t) thiz;
    BYTE *pData;
    UINT32 numFramesToRead;
    DWORD dwFlags;
    jint read;

    hr
        = IAudioCaptureClient_GetBuffer(
                iAudioCaptureClient,
                &pData,
                &numFramesToRead,
                &dwFlags,
                NULL,
                NULL);
    if (SUCCEEDED(hr))
    {
        UINT32 numFramesRead;
        jint dstFrameSize = dstSampleSize * dstChannels;

        if ((numFramesToRead == 0) || (hr == AUDCLNT_S_BUFFER_EMPTY))
            numFramesRead = 0;
        else
        {
            if (length < numFramesToRead * dstFrameSize)
            {
                numFramesRead = 0;
                WASAPI_throwNewHResultException(
                        env,
                        E_INVALIDARG,
                        __func__, __LINE__);
            }
            else
            {
                jbyte *data_
                    = (*env)->GetPrimitiveArrayCritical(env, data, NULL);

                if (data_)
                {
                    numFramesRead
                        = WASAPI_audiocopy(
                                pData, srcSampleSize, srcChannels,
                                data_ + offset, dstSampleSize, dstChannels,
                                numFramesToRead);
                    (*env)->ReleasePrimitiveArrayCritical(env, data, data_, 0);
                }
                else
                    numFramesRead = 0; /* An OutOfMemoryError has been thrown. */
            }
        }
        hr
            = IAudioCaptureClient_ReleaseBuffer(
                    iAudioCaptureClient,
                    numFramesRead);
        read = numFramesRead * dstFrameSize;
        if (FAILED(hr))
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    else
    {
        read = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return read;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioCaptureClient_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IAudioCaptureClient_Release((IAudioCaptureClient *) (intptr_t) thiz);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1GetBufferSize
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    UINT32 numBufferFrames;

    hr
        = IAudioClient_GetBufferSize(
                (IAudioClient *) (intptr_t) thiz,
                &numBufferFrames);
    if (FAILED(hr))
    {
        numBufferFrames = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) numBufferFrames;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1GetCurrentPadding
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    UINT32 numPaddingFrames;

    hr
        = IAudioClient_GetCurrentPadding(
                (IAudioClient *) (intptr_t) thiz,
                &numPaddingFrames);
    if (FAILED(hr))
    {
        numPaddingFrames = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) numPaddingFrames;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1GetDefaultDevicePeriod
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    REFERENCE_TIME hnsDefaultDevicePeriod;
    REFERENCE_TIME hnsMinimumDevicePeriod;

    hr
        = IAudioClient_GetDevicePeriod(
                (IAudioClient *) (intptr_t) thiz,
                &hnsDefaultDevicePeriod,
                &hnsMinimumDevicePeriod);
    if (FAILED(hr))
    {
        hnsDefaultDevicePeriod = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) hnsDefaultDevicePeriod;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1GetMinimumDevicePeriod
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    REFERENCE_TIME hnsDefaultDevicePeriod;
    REFERENCE_TIME hnsMinimumDevicePeriod;

    hr
        = IAudioClient_GetDevicePeriod(
                (IAudioClient *) (intptr_t) thiz,
                &hnsDefaultDevicePeriod,
                &hnsMinimumDevicePeriod);
    if (FAILED(hr))
    {
        hnsMinimumDevicePeriod = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) hnsMinimumDevicePeriod;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1GetService
    (JNIEnv *env, jclass clazz, jlong thiz, jstring iid)
{
    HRESULT hr;
    IID iid_;
    void *pv;

    hr = WASAPI_iidFromString(env, iid, &iid_);
    if (SUCCEEDED(hr))
    {
        hr
            = IAudioClient_GetService(
                    (IAudioClient *) (intptr_t) thiz,
                    &iid_,
                    &pv);
        if (FAILED(hr))
        {
            pv = NULL;
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
    }
    else
        pv = NULL;
    return (jlong) (intptr_t) pv;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1Initialize
    (JNIEnv *env, jclass clazz, jlong thiz, jint shareMode, jint streamFlags,
        jlong hnsBufferDuration, jlong hnsPeriodicity, jlong pFormat,
        jstring audioSessionGuid)
{
    HRESULT hr;
    IID audioSessionGuid_;

    hr = WASAPI_iidFromString(env, audioSessionGuid, &audioSessionGuid_);
    if (SUCCEEDED(hr))
    {
        hr
            = IAudioClient_Initialize(
                    (IAudioClient *) (intptr_t) thiz,
                    (AUDCLNT_SHAREMODE) shareMode,
                    (DWORD) streamFlags,
                    (REFERENCE_TIME) hnsBufferDuration,
                    (REFERENCE_TIME) hnsPeriodicity,
                    (const WAVEFORMATEX *) (intptr_t) pFormat,
                    &audioSessionGuid_);
        if (FAILED(hr))
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) hr;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1IsFormatSupported
    (JNIEnv *env, jclass clazz, jlong thiz, jint shareMode, jlong pFormat)
{
    HRESULT hr;
    WAVEFORMATEX *pFormat_ = (WAVEFORMATEX *) (intptr_t) pFormat;
    WAVEFORMATEX *pClosestMatch = NULL;

    hr
        = IAudioClient_IsFormatSupported(
                (IAudioClient *) (intptr_t) thiz,
                (AUDCLNT_SHAREMODE) shareMode,
                pFormat_,
                &pClosestMatch);
    switch (hr)
    {
    case S_OK:
        /* Succeeded and the IAudioClient supports the specified format. */
        if (!pClosestMatch)
            pClosestMatch = pFormat_;
        break;

    case AUDCLNT_E_UNSUPPORTED_FORMAT:
        /* Succeeded but the specified format is not supported. */
    case S_FALSE:
        /* Succeeded with a closest match to the specified format. */
        break;

    default:
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pClosestMatch;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IAudioClient_Release((IAudioClient *) (intptr_t) thiz);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1SetEventHandle
    (JNIEnv *env, jclass clazz, jlong thiz, jlong eventHandle)
{
    HRESULT hr
        = IAudioClient_SetEventHandle(
                (IAudioClient *) (intptr_t) thiz,
                (HANDLE) (intptr_t) eventHandle);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1Start
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr = IAudioClient_Start((IAudioClient *) (intptr_t) thiz);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioClient_1Stop
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr = IAudioClient_Stop((IAudioClient *) (intptr_t) thiz);

    if (FAILED(hr))
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    return (jint) hr;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioRenderClient_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IAudioRenderClient_Release((IAudioRenderClient *) (intptr_t) thiz);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IAudioRenderClient_1Write
    (JNIEnv *env, jclass clazz, jlong thiz, jbyteArray data, jint offset,
        jint length, jint srcSampleSize, jint srcChannels, jint dstSampleSize,
        jint dstChannels)
{
    jint srcFrameSize;
    UINT32 numFramesRequested;
    HRESULT hr;
    IAudioRenderClient *iAudioRenderClient
        = (IAudioRenderClient *) (intptr_t) thiz;
    BYTE *pData;
    jint written;

    srcFrameSize = srcSampleSize * srcChannels;
    numFramesRequested = length / srcFrameSize;
    hr
        = IAudioRenderClient_GetBuffer(
                iAudioRenderClient,
                numFramesRequested,
                &pData);
    if (SUCCEEDED(hr))
    {
        jbyte *data_;
        UINT32 numFramesWritten;

        data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
        if (data_)
        {
            numFramesWritten
                = WASAPI_audiocopy(
                        data_ + offset, srcSampleSize, srcChannels,
                        pData, dstSampleSize, dstChannels,
                        numFramesRequested);
            (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
        }
        else
            numFramesWritten = 0; /* An OutOfMemoryError has been thrown. */
        hr
            = IAudioRenderClient_ReleaseBuffer(
                    iAudioRenderClient,
                    numFramesWritten,
                    /* dwFlags */ 0);
        written = numFramesWritten * srcFrameSize;
        if (FAILED(hr))
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    else
    {
        written = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return written;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1Activate
    (JNIEnv *env, jclass clazz, jlong thiz, jstring iid, jint dwClsCtx,
        jlong pActivationParams)
{
    HRESULT hr;
    IID iid_;
    void *pInterface;

    hr = WASAPI_iidFromString(env, iid, &iid_);
    if (SUCCEEDED(hr))
    {
        hr
            = IMMDevice_Activate(
                    (IMMDevice *) (intptr_t) thiz,
                    &iid_,
                    (DWORD) dwClsCtx,
                    (PROPVARIANT *) (intptr_t) pActivationParams,
                    &pInterface);
        if (FAILED(hr))
        {
            pInterface = NULL;
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
    }
    else
        pInterface = NULL;
    return (jlong) (intptr_t) pInterface;
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1GetId
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    LPWSTR pstrId;
    HRESULT hr = IMMDevice_GetId((IMMDevice *) (intptr_t) thiz, &pstrId);
    jstring ret;

    if (SUCCEEDED(hr))
    {
        if (pstrId)
        {
            ret = (*env)->NewString(env, pstrId, wcslen(pstrId));
            CoTaskMemFree(pstrId);
        }
        else
            ret = NULL;
    }
    else
    {
        ret = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1GetState
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    DWORD dwState;
    HRESULT hr = IMMDevice_GetState((IMMDevice *) (intptr_t) thiz, &dwState);

    if (FAILED(hr))
    {
        dwState = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) dwState;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1OpenPropertyStore
    (JNIEnv *env, jclass clazz, jlong thiz, jint stgmAccess)
{
    HRESULT hr;
    IPropertyStore *pProperties;

    hr
        = IMMDevice_OpenPropertyStore(
                (IMMDevice *) (intptr_t) thiz,
                (DWORD) stgmAccess,
                &pProperties);
    if (FAILED(hr))
    {
        pProperties = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pProperties;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1QueryInterface
    (JNIEnv *env, jclass clazz, jlong thiz, jstring iid)
{
    HRESULT hr;
    IID iid_;
    void *pvObject;

    hr = WASAPI_iidFromString(env, iid, &iid_);
    if (SUCCEEDED(hr))
    {
        hr
            = IMMDevice_QueryInterface(
                    (IMMDevice *) (intptr_t) thiz,
                    &iid_,
                    &pvObject);
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
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDevice_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IMMDevice_Release((IMMDevice *) (intptr_t) thiz);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceCollection_1GetCount
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    UINT cDevices;
    HRESULT hr
        = IMMDeviceCollection_GetCount(
                (IMMDeviceCollection *) (intptr_t) thiz,
                &cDevices);

    if (FAILED(hr))
    {
        cDevices = 0;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) cDevices;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceCollection_1Item
    (JNIEnv *env, jclass clazz, jlong thiz, jint nDevice)
{
    IMMDevice *pDevice;
    HRESULT hr
        = IMMDeviceCollection_Item(
                (IMMDeviceCollection *) (intptr_t) thiz,
                nDevice,
                &pDevice);

    if (FAILED(hr))
    {
        pDevice = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pDevice;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceCollection_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IMMDeviceCollection_Release((IMMDeviceCollection *) (intptr_t) thiz);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceEnumerator_1EnumAudioEndpoints
    (JNIEnv *env, jclass clazz, jlong thiz, jint dataFlow, jint dwStateMask)
{
    IMMDeviceCollection *pDevices;
    HRESULT hr
        = IMMDeviceEnumerator_EnumAudioEndpoints(
                (IMMDeviceEnumerator *) (intptr_t) thiz,
                (EDataFlow) dataFlow,
                (DWORD) dwStateMask,
                &pDevices);

    if (FAILED(hr))
    {
        pDevices = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jlong) (intptr_t) pDevices;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceEnumerator_1GetDevice
    (JNIEnv *env, jclass clazz, jlong thiz, jstring pwstrId)
{
    const jchar *pwstrId_;
    HRESULT hr;
    IMMDevice *pDevice;

    if (pwstrId)
    {
        pwstrId_ = (*env)->GetStringChars(env, pwstrId, NULL);
        hr = pwstrId_ ? S_OK : E_OUTOFMEMORY;
    }
    else
    {
        pwstrId_ = NULL;
        hr = S_OK;
    }
    if (SUCCEEDED(hr))
    {
        hr
            = IMMDeviceEnumerator_GetDevice(
                    (IMMDeviceEnumerator *) (intptr_t) thiz,
                    (LPCWSTR) pwstrId_,
                    &pDevice);
        if (pwstrId_)
            (*env)->ReleaseStringChars(env, pwstrId, pwstrId_);
        if (FAILED(hr))
        {
            pDevice = NULL;
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
        }
    }
    else
        pDevice = NULL;
    return (jlong) (intptr_t) pDevice;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMDeviceEnumerator_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IMMDeviceEnumerator *iMMDeviceEnumerator
        = (IMMDeviceEnumerator *) (intptr_t) thiz;

    /*
     * There is statically allocated IMMNotificationClient instance which is to
     * be registered with every IMMDeviceEnumerator instance.
     */
    IMMDeviceEnumerator_UnregisterEndpointNotificationCallback(
            iMMDeviceEnumerator,
            (IMMNotificationClient *) &WASAPI_iMMNotificationClient);
    IMMDeviceEnumerator_Release(iMMDeviceEnumerator);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMEndpoint_1GetDataFlow
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    HRESULT hr;
    EDataFlow dataFlow;

    hr = IMMEndpoint_GetDataFlow((IMMEndpoint *) (intptr_t) thiz, &dataFlow);
    if (FAILED(hr))
    {
        dataFlow = EDataFlow_enum_count;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return (jint) dataFlow;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IMMEndpoint_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IMMEndpoint_Release((IMMEndpoint *) (intptr_t) thiz);
}

JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IPropertyStore_1GetString
    (JNIEnv *env, jclass clazz, jlong thiz, jlong key)
{
    PROPVARIANT v;
    HRESULT hr;
    jstring ret;

    PropVariantInit(&v);
    hr
        = IPropertyStore_GetValue(
                (IPropertyStore *) (intptr_t) thiz,
                (REFPROPERTYKEY) (intptr_t) key,
                &v);
    if (SUCCEEDED(hr))
    {
        if (v.vt == VT_LPWSTR)
        {
            ret
                = v.pwszVal
                    ? (*env)->NewString(env, v.pwszVal, wcslen(v.pwszVal))
                    : NULL;
        }
        else
        {
            ret = NULL;
            WASAPI_throwNewHResultException(
                    env,
                    E_UNEXPECTED,
                    __func__, __LINE__);
        }
        PropVariantClear(&v);
    }
    else
    {
        ret = NULL;
        WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    return ret;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_IPropertyStore_1Release
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    IPropertyStore_Release((IPropertyStore *) (intptr_t) thiz);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_PSPropertyKeyFromString
    (JNIEnv *env, jclass clazz, jstring pszString)
{
    const jchar *pszString_ = (*env)->GetStringChars(env, pszString, NULL);
    PROPERTYKEY *pkey;

    if (pszString_)
    {
        HRESULT hr;

        pkey = CoTaskMemAlloc(sizeof(PROPERTYKEY));
        if (pkey)
        {
            hr = PSPropertyKeyFromString((LPCWSTR) pszString_, pkey);
            if (FAILED(hr))
            {
                CoTaskMemFree(pkey);
                pkey = NULL;
            }
        }
        else
            hr = E_OUTOFMEMORY;
        (*env)->ReleaseStringChars(env, pszString, pszString_);
        if (FAILED(hr))
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    else
        pkey = NULL;
    return (jlong) (intptr_t) pkey;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_ResetEvent
    (JNIEnv *env, jclass clazz, jlong hEvent)
{
    BOOL b = ResetEvent((HANDLE) (intptr_t) hEvent);

    if (!b)
    {
        WASAPI_throwNewHResultException(
                env,
                HRESULT_FROM_WIN32(GetLastError()),
                __func__, __LINE__);
    }
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WaitForSingleObject
    (JNIEnv *env, jclass clazz, jlong hHandle, jlong dwMilliseconds)
{
    DWORD ret
        = WaitForSingleObject(
                (HANDLE) (intptr_t) hHandle,
                (DWORD) dwMilliseconds);

    if (WAIT_FAILED == ret)
    {
        WASAPI_throwNewHResultException(
                env,
                HRESULT_FROM_WIN32(GetLastError()),
                __func__, __LINE__);
    }
    return ret;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1alloc
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) CoTaskMemAlloc(sizeof(WAVEFORMATEX));
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1fill
    (JNIEnv *env, jclass clazz, jlong thiz, jchar wFormatTag, jchar nChannels,
        jint nSamplesPerSec, jint nAvgBytesPerSec, jchar nBlockAlign,
        jchar wBitsPerSample, jchar cbSize)
{
    WAVEFORMATEX *thiz_ = (WAVEFORMATEX *) (intptr_t) thiz;

    thiz_->wFormatTag = (WORD) wFormatTag;
    thiz_->nChannels = (WORD) nChannels;
    thiz_->nSamplesPerSec = (DWORD) nSamplesPerSec;
    thiz_->nAvgBytesPerSec = (DWORD) nAvgBytesPerSec;
    thiz_->nBlockAlign = (WORD) nBlockAlign;
    thiz_->wBitsPerSample = (WORD) wBitsPerSample;
    thiz_->cbSize = (WORD) cbSize;
}

JNIEXPORT jchar JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getCbSize
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jchar) ((WAVEFORMATEX *) (intptr_t) thiz)->cbSize;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getNAvgBytesPerSec
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jint) ((WAVEFORMATEX *) (intptr_t) thiz)->nAvgBytesPerSec;
}

JNIEXPORT jchar JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getNBlockAlign
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jchar) ((WAVEFORMATEX *) (intptr_t) thiz)->nBlockAlign;
}

JNIEXPORT jchar JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getNChannels
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jchar) ((WAVEFORMATEX *) (intptr_t) thiz)->nChannels;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getNSamplesPerSec
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jint) ((WAVEFORMATEX *) (intptr_t) thiz)->nSamplesPerSec;
}

JNIEXPORT jchar JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getWBitsPerSample
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jchar) ((WAVEFORMATEX *) (intptr_t) thiz)->wBitsPerSample;
}

JNIEXPORT jchar JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1getWFormatTag
    (JNIEnv *env, jclass clazz, jlong thiz)
{
    return (jchar) ((WAVEFORMATEX *) (intptr_t) thiz)->wFormatTag;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setCbSize
    (JNIEnv *env, jclass clazz, jlong thiz, jchar cbSize)
{
   ((WAVEFORMATEX *) (intptr_t) thiz)->cbSize = (WORD) cbSize;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setNAvgBytesPerSec
    (JNIEnv *env, jclass clazz, jlong thiz, jint nAvgBytesPerSec)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->nAvgBytesPerSec
        = (DWORD) nAvgBytesPerSec;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setNBlockAlign
    (JNIEnv *env, jclass clazz, jlong thiz, jchar nBlockAlign)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->nBlockAlign = (WORD) nBlockAlign;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setNChannels
    (JNIEnv *env, jclass clazz, jlong thiz, jchar nChannels)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->nChannels = (WORD) nChannels;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setNSamplesPerSec
  (JNIEnv *env, jclass clazz, jlong thiz, jint nSamplesPerSec)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->nSamplesPerSec = (DWORD) nSamplesPerSec;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setWBitsPerSample
  (JNIEnv *env, jclass clazz, jlong thiz, jchar wBitsPerSample)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->wBitsPerSample = (WORD) wBitsPerSample;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1setWFormatTag
  (JNIEnv *env, jclass clazz, jlong thiz, jchar wFormatTag)
{
    ((WAVEFORMATEX *) (intptr_t) thiz)->wFormatTag = (WORD) wFormatTag;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_protocol_wasapi_WASAPI_WAVEFORMATEX_1sizeof
    (JNIEnv *env, jclass clazz)
{
    return sizeof(WAVEFORMATEX);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    /*
     * MinGW-w64/TDM-GCC declares the function PSPropertyKeyFromString but,
     * unfortunately, does not provide a static library to link to at this time.
     */
    HMODULE hPropSys = LoadLibrary(TEXT("propsys.dll"));
    jint ret;

    if (hPropSys)
    {
        WASAPI_pPSPropertyKeyFromString
            = (LPPSPropertyKeyFromString)
                GetProcAddress(hPropSys, "PSPropertyKeyFromString");
        ret = WASAPI_pPSPropertyKeyFromString ? JNI_VERSION_1_4 : JNI_ERR;
    }
    else
        ret = JNI_ERR;
    if (JNI_ERR != ret)
    {
        /*
         * Assert that the library will be able to initialize new
         * HResultException instances.
         */
        JNIEnv *env;

        if (JNI_OK == (*vm)->GetEnv(vm, (void **) &env, ret))
        {
            jclass clazz
                = (*env)->FindClass(
                        env,
                        "org/jitsi/impl/neomedia/jmfext/media/protocol/wasapi/HResultException");

            if (clazz)
            {
                clazz = (*env)->NewGlobalRef(env, clazz);
                if (clazz)
                {
                    jmethodID methodID
                        = (*env)->GetMethodID(env, clazz, "<init>", "(I)V");

                    if (methodID)
                    {
                        WASAPI_hResultExceptionClass = clazz;
                        WASAPI_hResultExceptionMethodID = methodID;
                    }
                    else
                    {
                        (*env)->DeleteGlobalRef(env, clazz);
                        ret = JNI_ERR;
                    }
                }
                else
                    ret = JNI_ERR;
            }
            else
                ret = JNI_ERR;

            /*
             * Enable the forwarding of invocations of methods on the statically
             * allocated IMMNotificationClient instance to the respective Java
             * counterpart. The feature is implemented as optional at the time
             * of this writing.
             */
            if (JNI_ERR != ret)
            {
                clazz
                    = (*env)->FindClass(
                            env,
                            "org/jitsi/impl/neomedia/jmfext/media/protocol/wasapi/MMNotificationClient");
                if (clazz)
                {
                    clazz = (*env)->NewGlobalRef(env, clazz);
                    if (clazz)
                    {
                        MMNotificationClient_onDefaultDeviceChangedMethodID
                            = (*env)->GetStaticMethodID(
                                    env,
                                    clazz,
                                    "OnDefaultDeviceChanged",
                                    "(IILjava/lang/String;)V");
                        if (MMNotificationClient_onDefaultDeviceChangedMethodID)
                        {
                            MMNotificationClient_onDeviceAddedMethodID
                                = (*env)->GetStaticMethodID(
                                        env,
                                        clazz,
                                        "OnDeviceAdded",
                                        "(Ljava/lang/String;)V");
                        }
                        if (MMNotificationClient_onDeviceAddedMethodID)
                        {
                            MMNotificationClient_onDeviceRemovedMethodID
                                = (*env)->GetStaticMethodID(
                                        env,
                                        clazz,
                                        "OnDeviceRemoved",
                                        "(Ljava/lang/String;)V");
                        }
                        if (MMNotificationClient_onDeviceRemovedMethodID)
                        {
                            MMNotificationClient_onDeviceStateChangedMethodID
                                = (*env)->GetStaticMethodID(
                                        env,
                                        clazz,
                                        "OnDeviceStateChanged",
                                        "(Ljava/lang/String;I)V");
                        }
                        if (MMNotificationClient_onDeviceStateChangedMethodID)
                        {
                            MMNotificationClient_onPropertyValueChangedMethodID
                                = (*env)->GetStaticMethodID(
                                        env,
                                        clazz,
                                        "OnPropertyValueChanged",
                                        "(Ljava/lang/String;J)V");
                        }
                        if (MMNotificationClient_onPropertyValueChangedMethodID)
                            MMNotificationClient_class = clazz;
                    }
                }
            }
        }
        else
            ret = JNI_ERR;
    }
    /*
     * Eventually, grant the whole WASAPI module/library access to the JavaVM
     * instance.
     */
    if (JNI_ERR != ret)
        WASAPI_vm = vm;
    return ret;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *vm, void *reserved)
{
    JNIEnv *env;

    if (JNI_OK == (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4))
    {
        if (WASAPI_hResultExceptionClass)
        {
            (*env)->DeleteGlobalRef(env, WASAPI_hResultExceptionClass);
            WASAPI_hResultExceptionClass = 0;
            WASAPI_hResultExceptionMethodID = 0;
        }
        if (MMNotificationClient_class)
        {
            (*env)->DeleteGlobalRef(env, MMNotificationClient_class);
            MMNotificationClient_class = 0;
            MMNotificationClient_onDefaultDeviceChangedMethodID = 0;
            MMNotificationClient_onDeviceAddedMethodID = 0;
            MMNotificationClient_onDeviceRemovedMethodID = 0;
            MMNotificationClient_onDeviceStateChangedMethodID = 0;
            MMNotificationClient_onPropertyValueChangedMethodID = 0;
        }
    }

    /*
     * Eventually, revoke the access to the JavaVM instance from the whole
     * WASAPI module/library.
     */
    WASAPI_vm = NULL;
}

ULONG STDMETHODCALLTYPE
MMNotificationClient_AddRef(IMMNotificationClient* thiz)
{
    /*
     * There is a single IMMNotificationClient instance which is statically
     * allocated. The function/method is not likely to be invoked.
     */
    return 1;
}

static HRESULT
MMNotificationClient_invoke
(jmethodID methodID, jint a, jint b, LPCWSTR c, jint d, jlong e)
{
    HRESULT hr;

    if (methodID)
    {
        jclass clazz = MMNotificationClient_class;

        if (clazz)
        {
            JavaVM *vm = WASAPI_vm;
            JNIEnv *env;

            if (vm
                    && ((*vm)->AttachCurrentThreadAsDaemon(
                                vm,
                                (void **) &env,
                                NULL))
                            == 0)
            {
                jstring c_;

                if (c)
                {
                    c_ = (*env)->NewString(env, c, wcslen(c));
                    hr = c_ ? S_OK : E_OUTOFMEMORY;
                }
                else
                {
                    c_ = NULL;
                    hr = S_OK;
                }
                if (SUCCEEDED(hr))
                {
                    if ((MMNotificationClient_onDeviceAddedMethodID == methodID)
                            || (MMNotificationClient_onDeviceRemovedMethodID
                                    == methodID))
                    {
                        (*env)->CallStaticVoidMethod(
                                env, clazz, methodID,
                                c_);
                    }
                    else if (MMNotificationClient_onDeviceStateChangedMethodID
                            == methodID)
                    {
                        (*env)->CallStaticVoidMethod(
                                env, clazz, methodID,
                                c_, d);
                    }
                    else
                        hr = E_NOTIMPL;
                    if (SUCCEEDED(hr)
                            && (JNI_TRUE == (*env)->ExceptionCheck(env)))
                        hr = E_FAIL;
                }
                (*env)->ExceptionClear(env);
            }
            else
                hr = E_UNEXPECTED;
        }
        hr = E_NOTIMPL;
    }
    else
        hr = E_NOTIMPL;
    return hr;
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_OnDefaultDeviceChanged
    (IMMNotificationClient* thiz, EDataFlow flow, ERole role,
        LPCWSTR pwstrDeviceId)
{
    return
        MMNotificationClient_invoke(
                MMNotificationClient_onDefaultDeviceChangedMethodID,
                (jint) flow,
                (jint) role,
                pwstrDeviceId,
                /* unused */ 0,
                /* unused */ 0);
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_OnDeviceAdded
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId)
{
    return
        MMNotificationClient_invoke(
                MMNotificationClient_onDeviceAddedMethodID,
                /* unused */ 0,
                /* unused */ 0,
                pwstrDeviceId,
                /* unused */ 0,
                /* unused */ 0);
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_OnDeviceRemoved
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId)
{
    return
        MMNotificationClient_invoke(
                MMNotificationClient_onDeviceRemovedMethodID,
                /* unused */ 0,
                /* unused */ 0,
                pwstrDeviceId,
                /* unused */ 0,
                /* unused */ 0);
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_OnDeviceStateChanged
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId, DWORD dwNewState)
{
    return
        MMNotificationClient_invoke(
                MMNotificationClient_onDeviceStateChangedMethodID,
                /* unused */ 0,
                /* unused */ 0,
                pwstrDeviceId,
                (jint) dwNewState,
                /* unused */ 0);
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_OnPropertyValueChanged
    (IMMNotificationClient* thiz, LPCWSTR pwstrDeviceId, const PROPERTYKEY key)
{
    return
        MMNotificationClient_invoke(
                MMNotificationClient_onPropertyValueChangedMethodID,
                /* unused */ 0,
                /* unused */ 0,
                pwstrDeviceId,
                /* unused */ 0,
                (jlong) (intptr_t) &key);
}

HRESULT STDMETHODCALLTYPE
MMNotificationClient_QueryInterface
    (IMMNotificationClient *thiz, REFIID riid, void **ppvObject)
{
    /*  The function/method is not likely to be invoked. */
    HRESULT hr;

    if (ppvObject)
    {
        if (IsEqualIID(__uuidof(IID_IUnknown), riid)
                || IsEqualIID(__uuidof(IID_IMMNotificationClient), riid))
        {
            *ppvObject = thiz;
            hr = S_OK;
        }
        else
        {
            *ppvObject = NULL;
            hr = E_NOINTERFACE;
        }
    }
    else
        hr = E_POINTER;
    return hr;
}

ULONG STDMETHODCALLTYPE
MMNotificationClient_Release(IMMNotificationClient* thiz)
{
    /*
     * There is a single IMMNotificationClient instance which is statically
     * allocated. The function/method is not likely to be invoked.
     */
    return 1;
}

static UINT32
WASAPI_audiocopy
    (void *src, jint srcSampleSize, jint srcChannels, void *dst,
        jint dstSampleSize, jint dstChannels, UINT32 numFramesRequested)
{
    UINT32 numFramesWritten;

    if (srcChannels == dstChannels)
    {
        if (srcSampleSize == dstSampleSize)
        {
            memcpy(dst, src, numFramesRequested * dstSampleSize * dstChannels);
            numFramesWritten = numFramesRequested;
        }
        else
        {
            // TODO Auto-generated method stub
            numFramesWritten = 0;
        }
    }
    else if ((srcSampleSize == 2) && (dstSampleSize == 2))
    {
        int16_t *s = (int16_t *) src;
        int16_t *d = (int16_t *) dst;

        if (srcChannels == 1)
        {
            /* Convert from mono to stereo. */
            UINT32 i;

            for (i = 0; i < numFramesRequested; i++)
            {
                int16_t srcSample = *s++;

                *d++ = srcSample;
                *d++ = srcSample;
            }
        }
        else
        {
            /* Convert from stereo to mono. */
            UINT32 i;

            for (i = 0; i < numFramesRequested; i++)
            {
                int32_t srcSampleL = *s++;
                int32_t srcSampleR = *s++;

                *d++ = (int16_t) ((srcSampleL + srcSampleR) / 2);
            }
        }
        numFramesWritten = numFramesRequested;
    }
    else
    {
        // TODO Auto-generated method stub
        numFramesWritten = 0;
    }
    return numFramesWritten;
}
