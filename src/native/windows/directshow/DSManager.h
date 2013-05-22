/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file DSManager.h
 * \brief DirectShow capture devices manager.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 * \date 2010
 */

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSMANAGER_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSMANAGER_H_

#include <list>

#include <dshow.h>

class DSCaptureDevice;

/**
 * \class DSManager
 * \brief DirectShow capture device manager (singleton).
 */
class DSManager
{
public:
    /**
     * \brief Constructor.
     */
    DSManager();

    /**
     * \brief Destructor.
     */
    ~DSManager();

    /**
     * \brief Get all available capture video devices.
     * \return devices list
     */
    std::list<DSCaptureDevice*> getDevices() const;

private:
    /**
     * \brief Get and initialize video capture devices.
     */
    void initCaptureDevices();

    /**
     * \brief Available devices list.
     */
    std::list<DSCaptureDevice*> m_devices;

    /**
     * If COM backend is initialized.
     */
    bool _coUninitialize;
};

#endif /* _ORG_JITSI_IMPL_NEOMEDIA_JMFEXT_MEDIA_PROTOCOL_DIRECTSHOW_DSMANAGER_H_ */
