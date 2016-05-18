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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_BASICSAMPLEGRABBERCB_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_BASICSAMPLEGRABBERCB_H_

#ifdef _MSC_VER
#include "qedit.h"
#else
#include <qedit.h>
#endif

/**
 * \class BasicSampleGrabberCB
 * \brief Callback when DirectShow device capture frames.
 */
class BasicSampleGrabberCB : public ISampleGrabberCB
{
public:
    /**
     * \brief Constructor.
     */
    BasicSampleGrabberCB() {}

    /**
     * \brief Destructor.
     */
    virtual ~BasicSampleGrabberCB() {}

    /**
     * \brief Method callback when device capture a frame.
     * \param time time when frame was received
     * \param sample media sample
     * \see ISampleGrabberCB
     */
    STDMETHODIMP SampleCB(double time, IMediaSample* sample) { return S_OK; }

    /**
     * \brief Method callback when device buffer a frame.
     * \param time time when frame was received
     * \param buffer raw buffer
     * \param len length of buffer
     * \see ISampleGrabberCB
     */
    STDMETHODIMP BufferCB(double time, BYTE* buffer, long len) { return S_OK; }

    /**
     * \brief Query if this COM object has the interface riid.
     * \param riid interface requested
     * \param ppvObject if method succeed, an object corresponding
     * to the interface requested will be copied in this pointer
     */
    STDMETHODIMP QueryInterface(REFIID riid, void** ppvObject)
    {
        if (ppvObject)
        {
            if (IID_IUnknown == riid)
            {
                *ppvObject = (void *) (IUnknown *) this;
                AddRef();
                return S_OK;
            }
            else if (IID_ISampleGrabberCB == riid)
            {
                *ppvObject = (void *) (ISampleGrabberCB *) this;
                AddRef();
                return S_OK;
            }
            else
                return E_NOINTERFACE;
        }
        else
            return E_POINTER;
    }

    /**
     * \brief Adding a reference.
     * \return number of reference hold
     */
    STDMETHODIMP_(ULONG) AddRef() { return 1; }

    /**
     * \brief Release a reference.
     * \return number of reference hold
     */
    STDMETHODIMP_(ULONG) Release() { return 1; }
};

#endif /* _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_BASICSAMPLEGRABBERCB_H_ */
