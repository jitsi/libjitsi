/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifndef _JAWTRENDERER_H_
#define _JAWTRENDERER_H_

#include <jawt.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

void JAWTRenderer_close
    (JNIEnv *env, jclass clazz, jlong handle, jobject component);
jlong JAWTRenderer_open(JNIEnv *env, jclass clazz, jobject component);
jboolean JAWTRenderer_paint
    (jint version, JAWT_DrawingSurfaceInfo *dsi, jclass clazz, jlong handle,
        jobject g, jint zOrder);
jboolean JAWTRenderer_process
    (JNIEnv *env, jclass clazz,jlong handle, jobject component, jint *data,
        jint length, jint width, jint height);

#ifdef __APPLE__
jstring JAWTRenderer_sysctlbyname(JNIEnv *env, jstring name);
#endif /* #ifdef __APPLE__ */

#ifdef __cplusplus
} /* extern "C" { */
#endif

#endif /* _JAWTRENDERER_H_ */
