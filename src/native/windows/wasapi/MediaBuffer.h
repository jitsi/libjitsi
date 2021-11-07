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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_

#include <dmo.h>

class CMediaBuffer final : public IMediaBuffer {
private:
    LONG ref_count_;
    DWORD max_length_;
    DWORD length_;
    BYTE *buffer_;

    explicit CMediaBuffer(DWORD maxLength) :
            ref_count_(1),
            max_length_(maxLength),
            length_(0),
            buffer_(nullptr) {
        buffer_ = new BYTE[maxLength];
    }

    ~CMediaBuffer() {
        delete[] buffer_;
    }

public:
    static STDMETHODIMP Create(long maxLen, IMediaBuffer **ppBuffer);

    STDMETHODIMP QueryInterface(const IID &riid, void **ppvObject) override;

    STDMETHODIMP_(ULONG) CMediaBuffer::AddRef() override;

    STDMETHODIMP_(ULONG) CMediaBuffer::Release() override;

    STDMETHODIMP CMediaBuffer::GetBufferAndLength(BYTE **ppBuffer, DWORD *pcbLength) override;

    STDMETHODIMP CMediaBuffer::GetMaxLength(DWORD *pcbMaxLength) override;

    STDMETHODIMP CMediaBuffer::SetLength(DWORD cbLength) override;

    DWORD pop(BYTE *buffer, DWORD length);

    DWORD push(BYTE *buffer, DWORD length);
};

typedef struct MediaBuffer MediaBuffer;

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_WASAPI_MEDIABUFFER_ */
