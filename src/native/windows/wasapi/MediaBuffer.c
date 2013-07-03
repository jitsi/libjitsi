/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "MediaBuffer.h"

#include <objbase.h> /* CoTaskMemAlloc */
#include <string.h> /* memcpy */
#include <windows.h> /* InterlockedDecrement, InterlockedIncrement */

#include "Typecasting.h"

struct MediaBuffer
{
    CONST_VTBL IMediaBufferVtbl *lpVtbl;

    BYTE *_buffer;
    DWORD _length;
    DWORD _maxLength;
    LONG _refCount;
    IMediaBufferVtbl _vtbl;
};

static STDMETHODIMP_(ULONG)
MediaBuffer_AddRef(IMediaBuffer *thiz)
{
    return InterlockedIncrement(&(((MediaBuffer *) thiz)->_refCount));
}

static STDMETHODIMP
MediaBuffer_GetBufferAndLength
    (IMediaBuffer *thiz, BYTE **ppBuffer, DWORD *pcbLength)
{
    if (!ppBuffer && !pcbLength)
        return E_POINTER;
    else
    {
        MediaBuffer *thiz_ = (MediaBuffer *) thiz;

        if (ppBuffer)
            *ppBuffer = thiz_->_buffer;
        if (pcbLength)
            *pcbLength = thiz_->_length;
        return S_OK;
    }
}

static STDMETHODIMP
MediaBuffer_GetMaxLength(IMediaBuffer *thiz, DWORD *pcbMaxLength)
{
    if (pcbMaxLength)
    {
        *pcbMaxLength = ((MediaBuffer *) thiz)->_maxLength;
        return S_OK;
    }
    else
        return E_POINTER;
}

static STDMETHODIMP
MediaBuffer_QueryInterface(IMediaBuffer *thiz, REFIID riid, void **ppvObject)
{
    if (ppvObject)
    {
        if (IsEqualIID(__uuidof(IID_IUnknown), riid)
                || IsEqualIID(__uuidof(IID_IMediaBuffer), riid))
        {
            *ppvObject = thiz;
            IMediaObject_AddRef(thiz);
            return 0;
        }
        else
        {
            *ppvObject = NULL;
            return E_NOINTERFACE;
        }
    }
    else
        return E_POINTER;
}

static STDMETHODIMP_(ULONG)
MediaBuffer_Release(IMediaBuffer *thiz)
{
    LONG refCount = InterlockedDecrement(&(((MediaBuffer *) thiz)->_refCount));

    if (refCount == 0)
        CoTaskMemFree(thiz);
    return refCount;
}

static STDMETHODIMP
MediaBuffer_SetLength(IMediaBuffer *thiz, DWORD cbLength)
{
    MediaBuffer *thiz_ = (MediaBuffer *) thiz;

    if (cbLength > thiz_->_maxLength)
        return E_INVALIDARG;
    else
    {
        thiz_->_length = cbLength;
        return S_OK;
    }
}

MediaBuffer *
MediaBuffer_alloc(DWORD maxLength)
{
    size_t sizeofMediaBuffer = sizeof(MediaBuffer);
    MediaBuffer *thiz = CoTaskMemAlloc(sizeofMediaBuffer + maxLength);

    if (thiz)
    {
        IMediaBufferVtbl *lpVtbl = &(thiz->_vtbl);

        lpVtbl->AddRef = MediaBuffer_AddRef;
        lpVtbl->GetBufferAndLength = MediaBuffer_GetBufferAndLength;
        lpVtbl->GetMaxLength = MediaBuffer_GetMaxLength;
        lpVtbl->QueryInterface = MediaBuffer_QueryInterface;
        lpVtbl->Release = MediaBuffer_Release;
        lpVtbl->SetLength = MediaBuffer_SetLength;
        thiz->lpVtbl = lpVtbl;

        thiz->_buffer = ((BYTE *) thiz) + sizeofMediaBuffer;
        thiz->_length = 0;
        thiz->_maxLength = maxLength;
        thiz->_refCount = 1;
    }
    return thiz;
}

DWORD
MediaBuffer_pop(MediaBuffer *thiz, BYTE *buffer, DWORD length)
{
    DWORD i;

    if (buffer)
        memcpy(buffer, thiz->_buffer, length);
    thiz->_length -= length;
    for (i = 0; i < thiz->_length; i++)
        thiz->_buffer[i] = thiz->_buffer[length + i];
    return length;
}

DWORD
MediaBuffer_push(MediaBuffer *thiz, BYTE *buffer, DWORD length)
{
    memcpy(thiz->_buffer + thiz->_length, buffer, length);
    thiz->_length += length;
    return length;
}
