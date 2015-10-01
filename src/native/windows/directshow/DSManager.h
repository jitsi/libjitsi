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
