/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file hw_renderer.h
 * \brief Hardware renderer.
 * \author Sebastien Vincent
 * \date 2013
 */

#include <jni.h>
#include <jawt.h>

#ifdef __cplusplus
extern "C" { /* } */
#endif

void hw_renderer_close(JNIEnv* env, jclass clazz, jlong handle,
    jobject component);

jlong hw_renderer_open(JNIEnv* env, jclass clazz, jobject component);

jboolean hw_renderer_paint(JAWT_DrawingSurfaceInfo* dsi, jclass clazz,
    jlong handle, jobject graphic);

jboolean hw_renderer_process(JNIEnv* env, jclass clazz, jlong handle,
    jobject component, jlong data, jint offset, jint length, jint width,
    jint height);

#ifdef __cplusplus
}
#endif

