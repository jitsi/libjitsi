/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager.cpp
 * \brief JNI part of DSManager.
 * \author Sebastien Vincent
 * \author Lyubomir Marinov
 */

#include "DSManager.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager.h"

#include <stdint.h>
/**
 * \brief Initialize DSManager singleton.
 * \param env JNI environment
 * \param clazz DSManager class
 * \return native pointer on DSManager singleton instance
 */
JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager_destroy
  (JNIEnv* env, jclass clazz, jlong ptr)
{
    DSManager *thiz = reinterpret_cast<DSManager *>(ptr);

    delete thiz;
}

/**
 * \brief Initialize DSManager singleton.
 * \param env JNI environment
 * \param clazz DSManager class
 * \return native pointer on DSManager singleton instance
 */
JNIEXPORT jlong JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager_init
  (JNIEnv* env, jclass clazz)
{
    return (jlong) (intptr_t) new DSManager();
}

/**
 * \brief Get all capture devices.
 * \param env JNI environment
 * \param obj DSManager object
 * \param jlong native pointer of DSManager
 * \return array of native DSCaptureDevice pointers
 */
JNIEXPORT jlongArray JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_protocol_directshow_DSManager_getCaptureDevices
  (JNIEnv* env, jobject obj, jlong ptr)
{
    jlongArray ret = NULL;
    DSManager* manager = reinterpret_cast<DSManager*>(ptr);
    std::list<DSCaptureDevice*> devices;
    jsize i = 0;

    devices = manager->getDevices();

    ret = env->NewLongArray(static_cast<jsize>(devices.size()));
    if(!ret)
        return NULL;

    for(std::list<DSCaptureDevice*>::iterator it = devices.begin() ; it != devices.end() ; ++it)
    {
        jlong dPtr = reinterpret_cast<jlong>((*it));

        env->SetLongArrayRegion(ret, i, 1, &dPtr);
        i++;
    }

    return ret;
}

#ifdef __cplusplus
} /* extern "C" { */
#endif
