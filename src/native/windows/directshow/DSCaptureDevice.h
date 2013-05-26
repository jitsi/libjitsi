/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file DSCaptureDevice.h
 * \brief DirectShow capture device.
 * \author Sebastien Vincent
 * \date 2010
 */

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSCAPTUREDEVICE_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSCAPTUREDEVICE_H_

#include <list>

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <dshow.h>

#include "BasicSampleGrabberCB.h"
#include "DSFormat.h"

/**
 * \class DSCaptureDevice
 * \brief DirectShow capture device.
 *
 * Once a DSCapture has been obtained by DSManager, do not
 * forget to build the graph and optionally set a format.
 */
class DSCaptureDevice
{
public:
    /**
     * \brief Constructor.
     * \param name name of the capture device
     */
    DSCaptureDevice(const WCHAR* name);

    /**
     * \brief Destructor.
     */
    ~DSCaptureDevice();

    /**
     * \brief Get name of the capture device.
     * \return name of the capture device
     */
    const WCHAR* getName() const;

    /**
     * \brief Initialize the device.
     * \param moniker moniker of the capture device
     * \return S_OK or S_FALSE on success or an HRESULT value describing a
     * failure
     */
    HRESULT initDevice(IMoniker* moniker);

    /**
     * \brief Set video format.
     * \param format video format
     * \return S_OK or S_FALSE on success or an HRESULT value describing a
     * failure
     */
    HRESULT setFormat(const DSFormat& format);

    /**
     * \brief Get list of supported formats.
     * \return list of supported formats.
     */
    std::list<DSFormat> getSupportedFormats() const;

    /**
     * \brief Build the filter graph for this capture device.
     * \return true if success, false otherwise
     * \note Call this method before start().
     */
    bool buildGraph();

    /**
     * \brief get callback object.
     * \return callback
     */
    BasicSampleGrabberCB* getCallback() { return m_callback; }

    /**
     * \brief Set callback object when receiving new frames.
     * \param callback callback object to set
     */
    void setCallback(BasicSampleGrabberCB *callback);

    /**
     * \brief Start capture device.
     * \return S_OK or S_FALSE on success or an HRESULT value describing a
     * failure
     */
    HRESULT start();

    /**
     * \brief Stop capture device.
     * \return S_OK or S_FALSE on success or an HRESULT value describing a
     * failure
     */
    HRESULT stop();

    /**
     * \brief Get current format.
     * \return current format
     */
    DSFormat getFormat() const;

    /**
     * \brief Get current bit per pixel.
     * \return bit per pixel of images
     */
    size_t getBitPerPixel();

private:
    /**
     * \brief Initialize list of supported size.
     */
    void initSupportedFormats();
    
    /**
     * \brief Name of the capture device.
     */
    WCHAR* m_name;

    /**
     * \brief Callback.
     */
    BasicSampleGrabberCB* m_callback;

    /**
     * \brief List of DSFormat.
     */
    std::list<DSFormat> m_formats;
    
    /**
     * \brief Reference of the filter graph.
     */
    IFilterGraph2* m_filterGraph;

    /**
     * \brief Reference of the capture graph builder.
     */
    ICaptureGraphBuilder2* m_captureGraphBuilder;

    /**
     * \brief Controller of the graph.
     */
    IMediaControl* m_graphController;

    /**
     * \brief Source filter.
     */
    IBaseFilter* m_srcFilter;
    
    /**
     * \brief Sample grabber filter.
     */
    IBaseFilter* m_sampleGrabberFilter;

    /**
     * \brief The null renderer.
     */
    IBaseFilter* m_renderer;

    /**
     * \brief The sample grabber.
     */
    ISampleGrabber* m_sampleGrabber;

    /**
     * \brief Current format.
     */
    DSFormat m_format;

    /**
     * \brief Current bit per pixel.
     */
    size_t m_bitPerPixel;
};

#endif /* _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSCAPTUREDEVICE_H_ */
