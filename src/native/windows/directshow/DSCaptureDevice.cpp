/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file DSCaptureDevice.cpp
 * \brief DirectShow capture device.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 * \date 2010
 */

#include "DSCaptureDevice.h"

DEFINE_GUID(CLSID_SampleGrabber, 0xc1f400a0, 0x3f08, 0x11d3, 0x9f, 0x0b, 0x00, 0x60, 0x08, 0x03, 0x9e, 0x37);
DEFINE_GUID(CLSID_NullRenderer, 0xc1f400a4, 0x3f08, 0x11d3, 0x9f, 0x0b, 0x00, 0x60, 0x08, 0x03, 0x9e, 0x37);

static void
_DeleteMediaType(AM_MEDIA_TYPE *mt)
{
    if (mt)
    {
        if (mt->cbFormat && mt->pbFormat)
        {
            ::CoTaskMemFree((LPVOID) mt->pbFormat);
            mt->cbFormat = 0;
            mt->pbFormat = NULL;
        }
        if (mt->pUnk)
        {
            mt->pUnk->Release();
            mt->pUnk = NULL;
        }
        ::CoTaskMemFree(mt);
    }
}

DSCaptureDevice::DSCaptureDevice(const WCHAR* name)
{
    if(name)
        m_name = wcsdup(name);

    m_callback = NULL;

    m_filterGraph = NULL;
    m_captureGraphBuilder = NULL;
    m_graphController = NULL;

    m_srcFilter = NULL;
    m_sampleGrabberFilter = NULL;
    m_sampleGrabber = NULL;
    m_renderer = NULL;
}

DSCaptureDevice::~DSCaptureDevice()
{
    if(m_filterGraph)
    {
        /* remove all added filters from filter graph */
        if(m_srcFilter)
            m_filterGraph->RemoveFilter(m_srcFilter);

        if(m_renderer)
            m_filterGraph->RemoveFilter(m_renderer);

        if(m_sampleGrabberFilter)
            m_filterGraph->RemoveFilter(m_sampleGrabberFilter);
    }

    /* clean up COM stuff */
    if(m_renderer)
        m_renderer->Release();

    if(m_sampleGrabber)
        m_sampleGrabber->Release();

    if(m_sampleGrabberFilter)
        m_sampleGrabberFilter->Release();

    if(m_srcFilter)
        m_srcFilter->Release();

    if(m_captureGraphBuilder)
        m_captureGraphBuilder->Release();

    if(m_filterGraph)
        m_filterGraph->Release();

    if(m_name)
        free(m_name);
}

const WCHAR* DSCaptureDevice::getName() const
{
    return m_name;
}

HRESULT DSCaptureDevice::setFormat(const DSFormat& format)
{
    HRESULT hr;
    IAMStreamConfig* streamConfig = NULL;

    /* get the right interface to change capture settings */
    hr
        = m_captureGraphBuilder->FindInterface(
                &PIN_CATEGORY_CAPTURE,
                &MEDIATYPE_Video,
                m_srcFilter,
                IID_IAMStreamConfig,
                (void**) &streamConfig);
    if(SUCCEEDED(hr))
    {
        int nb = 0;
        int size = 0;
        AM_MEDIA_TYPE* mediaType = NULL;
        size_t bitCount = 0;

        hr = streamConfig->GetNumberOfCapabilities(&nb, &size);
        if (SUCCEEDED(hr) && nb)
        {
            BYTE* scc = new BYTE[size];

            if (scc)
            {
                DWORD pixfmt = format.pixelFormat;

                for (int i = 0 ; i < nb ; i++)
                {
                    AM_MEDIA_TYPE* mt;

                    if (streamConfig->GetStreamCaps(i, &mt, scc) == S_OK)
                    {
                        VIDEOINFOHEADER* hdr = (VIDEOINFOHEADER*) mt->pbFormat;

                        if (hdr
                                && (mt->subtype.Data1 == pixfmt)
                                && ((long) format.height
                                        == hdr->bmiHeader.biHeight)
                                && ((long) format.width
                                        == hdr->bmiHeader.biWidth))
                        {
                            mediaType = mt;
                            if ((pixfmt == MEDIASUBTYPE_ARGB32.Data1)
                                    || (pixfmt == MEDIASUBTYPE_RGB32.Data1))
                                bitCount = 32;
                            else if (pixfmt == MEDIASUBTYPE_RGB24.Data1)
                                bitCount = 24;
                            else
                                bitCount = hdr->bmiHeader.biBitCount;
                            break;
                        }
                        else
                            _DeleteMediaType(mt);
                    }
                }

                delete[] scc;
            }
            else
                hr = E_OUTOFMEMORY;
        }

        if (mediaType)
        {
            hr = streamConfig->SetFormat(mediaType);
            if (SUCCEEDED(hr))
            {
                m_bitPerPixel = bitCount;
                m_format = format;
                m_format.mediaType = mediaType->subtype;
            }
            _DeleteMediaType(mediaType);
        }
        else if (SUCCEEDED(hr))
            hr = E_FAIL;

        streamConfig->Release();
    }

    return hr;
}

void DSCaptureDevice::setCallback(BasicSampleGrabberCB *callback)
{
    m_callback = callback;
    m_sampleGrabber->SetCallback(callback, 0);
}

HRESULT DSCaptureDevice::initDevice(IMoniker* moniker)
{
    HRESULT ret = 0;

    if(!m_name || !moniker)
        return false;
 
    if(m_filterGraph)
        return false; /* This instance has already been initialized. */

    /* create the filter and capture graph */
    ret = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
        IID_IFilterGraph2, (void**)&m_filterGraph);

    if(FAILED(ret))
        return false;

    ret = CoCreateInstance(CLSID_CaptureGraphBuilder2, NULL,
        CLSCTX_INPROC_SERVER, IID_ICaptureGraphBuilder2, 
        (void**)&m_captureGraphBuilder);

    if(FAILED(ret))
        return false;

    m_captureGraphBuilder->SetFiltergraph(m_filterGraph);

    /* get graph controller */
    ret = m_filterGraph->QueryInterface(IID_IMediaControl, (void**)&m_graphController);

    if(FAILED(ret))
        return false;

    /* add source filter to the filter graph */
    ret = moniker->BindToObject(NULL, NULL, IID_IBaseFilter, (void**)&m_srcFilter);
    if(ret != S_OK)
        return false;

    WCHAR* name = wcsdup(m_name);
    ret = m_filterGraph->AddFilter(m_srcFilter, name);
    free(name);
    if(ret != S_OK)
        return false;

    ret = CoCreateInstance(CLSID_SampleGrabber, NULL, CLSCTX_INPROC_SERVER, IID_IBaseFilter,
        (void**)&m_sampleGrabberFilter);

    if(ret != S_OK)
        return false;

    /* get sample grabber */
    ret = m_sampleGrabberFilter->QueryInterface(IID_ISampleGrabber, (void**)&m_sampleGrabber);

    if(ret != S_OK)
        return false;

    /* and sample grabber to the filter graph */
    ret = m_filterGraph->AddFilter(m_sampleGrabberFilter, L"SampleGrabberFilter");

    /* set media type */
/*
    AM_MEDIA_TYPE mediaType;
    memset(&mediaType, 0x00, sizeof(AM_MEDIA_TYPE));
    mediaType.majortype = MEDIATYPE_Video;
    mediaType.subtype = MEDIASUBTYPE_RGB24;
    ret = m_sampleGrabber->SetMediaType(&mediaType);
*/
    /* set the callback handler */

    if(ret != S_OK)
        return false;

    /* set renderer */
    ret = CoCreateInstance(CLSID_NullRenderer, NULL, CLSCTX_INPROC_SERVER,
        IID_IBaseFilter, (void**)&m_renderer);

    if(ret != S_OK)
        return false;

    /* add renderer to the filter graph */
    m_filterGraph->AddFilter(m_renderer, L"NullRenderer");

    /* initialize the list of formats this device supports */
    initSupportedFormats();

    /* see if camera support flipping */
    IAMVideoControl* videoControl = NULL;
    long caps = 0;

    ret = m_captureGraphBuilder->FindInterface(&PIN_CATEGORY_CAPTURE, &MEDIATYPE_Video,
        m_srcFilter, IID_IAMVideoControl, (void**)&videoControl);

    if(!FAILED(ret))
    {
         IPin* pin = NULL;

         ret = m_captureGraphBuilder->FindPin(
             m_srcFilter, PINDIR_OUTPUT, &PIN_CATEGORY_CAPTURE, NULL, FALSE, 0, &pin);    

        if(!FAILED(ret))
        {
            if(!FAILED(videoControl->GetCaps(pin, &caps)))
            {
                if ((caps & VideoControlFlag_FlipVertical) > 0)
                    caps = caps & ~(VideoControlFlag_FlipVertical);
                if ((caps & VideoControlFlag_FlipHorizontal) != 0)
                    caps = caps & ~(VideoControlFlag_FlipHorizontal);

                videoControl->SetMode(pin, caps);
            }
            pin->Release();
        }

        videoControl->Release();
    }

    return S_OK;
}

void DSCaptureDevice::initSupportedFormats()
{
    HRESULT ret;
    IAMStreamConfig* streamConfig = NULL;
    AM_MEDIA_TYPE* mediaType = NULL;

    ret = m_captureGraphBuilder->FindInterface(&PIN_CATEGORY_CAPTURE, &MEDIATYPE_Video,
        m_srcFilter, IID_IAMStreamConfig, (void**)&streamConfig);

    /* get to find all supported formats */
    if(!FAILED(ret))
    {
        int nb = 0;
        int size = 0;
        BYTE* allocBytes = NULL;

        streamConfig->GetNumberOfCapabilities(&nb, &size);
        allocBytes = new BYTE[size];
 
        for(int i = 0 ; i < nb ; i++)
        {
            if(streamConfig->GetStreamCaps(i, &mediaType, allocBytes) == S_OK)
            {
                struct DSFormat format;
                VIDEOINFOHEADER* hdr = (VIDEOINFOHEADER*)mediaType->pbFormat;

                if(hdr)
                {
                    format.height = hdr->bmiHeader.biHeight;
                    format.width = hdr->bmiHeader.biWidth;
                    format.pixelFormat = mediaType->subtype.Data1;
                    format.mediaType = mediaType->subtype;

                    m_formats.push_back(format);
                }
            }
        }

        delete allocBytes;
    }
}

std::list<DSFormat> DSCaptureDevice::getSupportedFormats() const
{
    return m_formats;
}

bool DSCaptureDevice::buildGraph()
{
    HRESULT hr
        = m_captureGraphBuilder->RenderStream(
                &PIN_CATEGORY_PREVIEW,
                &MEDIATYPE_Video,
                m_srcFilter,
                m_sampleGrabberFilter,
                m_renderer);

    if (SUCCEEDED(hr))
    {
        REFERENCE_TIME start = 0;
        REFERENCE_TIME stop = MAXLONGLONG;

        hr
            = m_captureGraphBuilder->ControlStream(
                    &PIN_CATEGORY_PREVIEW,
                    &MEDIATYPE_Video,
                    m_srcFilter,
                    &start, &stop,
                    1, 2);
        return SUCCEEDED(hr);
    }
    else
        return false;
}

HRESULT DSCaptureDevice::start()
{
    return m_graphController ? m_graphController->Run() : E_FAIL;
}

HRESULT DSCaptureDevice::stop()
{
    return m_graphController ? m_graphController->Stop() : E_FAIL;
}

DSFormat DSCaptureDevice::getFormat() const
{
    return m_format;
}

size_t DSCaptureDevice::getBitPerPixel()
{
    return m_bitPerPixel;
}
