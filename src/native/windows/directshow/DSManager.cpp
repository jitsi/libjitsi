/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file DSManager.cpp
 * \brief DirectShow capture devices manager.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 * \date 2010
 */

#include <cstdlib>

#include "DSManager.h"
#include "DSCaptureDevice.h"
#include <qedit.h>
#include <stdio.h>

DSManager::DSManager()
{
    HRESULT hr = ::CoInitializeEx(NULL, COINIT_MULTITHREADED);

    if (SUCCEEDED(hr))
        initCaptureDevices();

    /*
     * Each successful call to CoInitializeEx must be balanced by a
     * corresponding call to CoUninitialize in order to close the COM library
     * gracefully on a thread. Unfortunately, the multithreaded architectures of
     * FMJ and libjitsi do not really guarantee that the destructor of this
     * DSManager will be invoked on the same thread on which the constructor of
     * this DSManager has been invoked in the first place.
     */
    _coUninitialize = false;
}

DSManager::~DSManager()
{
    for(std::list<DSCaptureDevice*>::iterator it = m_devices.begin() ; it != m_devices.end() ; ++it)
        delete *it;
    m_devices.clear();

    /*
     * Each successful call to CoInitializeEx must be balanced by a
     * corresponding call to CoUninitialize in order to close the COM library
     * gracefully on a thread.
     */
    if (_coUninitialize)
        ::CoUninitialize();
}

std::list<DSCaptureDevice*> DSManager::getDevices() const
{
    return m_devices;
}

void DSManager::initCaptureDevices()
{
    HRESULT ret = 0;
    VARIANT name;
    ICreateDevEnum* devEnum = NULL;
    IEnumMoniker* monikerEnum = NULL;
    IMoniker* moniker = NULL;

    if(m_devices.size() > 0)
    {
        /* clean up our list in case of reinitialization */
        for(std::list<DSCaptureDevice*>::iterator it = m_devices.begin() ; it != m_devices.end() ; ++it)
            delete *it;
        m_devices.clear();
    }

    /* get the available devices list */
    ret
        = CoCreateInstance(
                CLSID_SystemDeviceEnum,
                NULL,
                CLSCTX_INPROC_SERVER,
                IID_ICreateDevEnum,
                (void**) &devEnum);
    if(FAILED(ret))
        return;

    ret
        = devEnum->CreateClassEnumerator(
                CLSID_VideoInputDeviceCategory,
                &monikerEnum,
                0);
    /* error or no devices */
    if(FAILED(ret) || ret == S_FALSE)
    {
        devEnum->Release();
        return;
    }

    /* loop and initialize all available capture devices */
    while(monikerEnum->Next(1, &moniker, 0) == S_OK)
    {
        DSCaptureDevice* captureDevice = NULL;
        IPropertyBag* propertyBag = NULL;

        {
          IBaseFilter* cp = NULL;
          if(!FAILED(moniker->BindToObject(0, 0, IID_IBaseFilter, (void**)&cp)))
          {
            IAMVfwCaptureDialogs* vfw = NULL;
            if(!FAILED(
                  cp->QueryInterface(IID_IAMVfwCaptureDialogs, (void**)&vfw)))
            {
              if(vfw)
              {
                vfw->Release();
                cp->Release();
                continue;
              }
            }
          }
        }

        /* get properties of the device */
        ret = moniker->BindToStorage(0, 0, IID_IPropertyBag, (void**)&propertyBag);
        if(!FAILED(ret))
        {
            VariantInit(&name);
            ret = propertyBag->Read(L"FriendlyName", &name, 0);
            if(FAILED(ret))
            {
                VariantClear(&name);
                propertyBag->Release();
                moniker->Release();
                continue;
            }

            /*
             * Initialize a new DSCaptureDevice instance and add it to the list
             * of DSCaptureDevice instances.
             */
            captureDevice = new DSCaptureDevice(name.bstrVal);
            if(captureDevice && SUCCEEDED(captureDevice->initDevice(moniker)))
                m_devices.push_back(captureDevice);
            else
                delete captureDevice;

            /* clean up */
            VariantClear(&name);
            propertyBag->Release();
        }
        moniker->Release();
    }

    /* cleanup */
    monikerEnum->Release();
    devEnum->Release();
}
