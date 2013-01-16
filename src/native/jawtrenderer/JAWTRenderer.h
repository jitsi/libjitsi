/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#ifndef _JAWTRENDERER_H_
#define _JAWTRENDERER_H_

#ifndef __ANDROID__
    #include <jawt.h>
#else /* #ifndef __ANDROID__ */
    typedef void JAWT_DrawingSurfaceInfo;
#endif /* #ifndef __ANDROID__ */
#include <jni.h>

#ifndef NULL
#define NULL 0
#endif /* #ifndef NULL */

#ifdef __cplusplus
extern "C" {
#endif /* #ifdef __cplusplus */

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
#endif /* #ifdef __cplusplus */

#endif /* _JAWTRENDERER_H_ */
