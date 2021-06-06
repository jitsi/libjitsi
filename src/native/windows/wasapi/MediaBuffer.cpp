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

#include "MediaBuffer.h"

#include <cstring>
#include <new>

STDMETHODIMP CMediaBuffer::Create(long maxLen, IMediaBuffer **ppBuffer) {
    if (ppBuffer == nullptr) {
        return E_POINTER;
    }

    try {
        auto *pBuffer = new CMediaBuffer(maxLen);
        pBuffer->AddRef();
        *ppBuffer = pBuffer;
        return S_OK;
    }
    catch (const std::bad_alloc &) {
        return E_OUTOFMEMORY;
    }
}

STDMETHODIMP CMediaBuffer::QueryInterface(REFIID riid, void **ppvObject) {
    if (ppvObject) {
        if (riid == IID_IUnknown || riid == IID_IMediaBuffer) {
            *ppvObject = this;
            AddRef();
            return 0;
        } else {
            *ppvObject = nullptr;
            return E_NOINTERFACE;
        }
    } else {
        return E_POINTER;
    }
}

STDMETHODIMP_(ULONG) CMediaBuffer::AddRef() {
    return InterlockedIncrement(&ref_count_);
}

STDMETHODIMP_(ULONG) CMediaBuffer::Release() {
    LONG refCount = InterlockedDecrement(&ref_count_);
    if (refCount == 0)
        delete this;
    return refCount;
}

STDMETHODIMP CMediaBuffer::GetBufferAndLength(BYTE **ppBuffer, DWORD *pcbLength) {
    if (!ppBuffer && !pcbLength)
        return E_POINTER;
    else {
        if (ppBuffer)
            *ppBuffer = buffer_;
        if (pcbLength)
            *pcbLength = length_;
        return S_OK;
    }
}

STDMETHODIMP CMediaBuffer::GetMaxLength(DWORD *pcbMaxLength) {
    if (pcbMaxLength) {
        *pcbMaxLength = max_length_;
        return S_OK;
    } else
        return E_POINTER;
}

STDMETHODIMP CMediaBuffer::SetLength(DWORD cbLength) {
    if (cbLength > max_length_)
        return E_INVALIDARG;
    else {
        length_ = cbLength;
        return S_OK;
    }
}

DWORD CMediaBuffer::pop(BYTE *buffer, DWORD length) {
    if (buffer)
        memcpy(buffer, buffer_, length);
    length_ -= length;
    for (DWORD i = 0; i < length_; i++)
        buffer_[i] = buffer_[length + i];
    return length;
}

DWORD CMediaBuffer::push(BYTE *buffer, DWORD length) {
    memcpy(buffer_ + length_, buffer, length);
    length_ += length;
    return length;
}
