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

#include "org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer.h"
#include "JAWTRenderer.h"

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    addNotify
 * Signature: (JLjava/awt/Component;)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_addNotify
    (JNIEnv *env, jclass clazz, jlong handle, jobject component)
{
#ifdef __APPLE__
    JAWTRenderer_addNotify(env, clazz, handle, component);
#endif /* #ifdef __APPLE__ */
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    close
 * Signature: (JLjava/awt/Component;)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_close
    (JNIEnv *env, jclass clazz, jlong handle, jobject component)
{
    JAWTRenderer_close(env, clazz, handle, component);
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    open
 * Signature: (Ljava/awt/Component;)J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_open
    (JNIEnv *env, jclass clazz, jobject component)
{
    return JAWTRenderer_open(env, clazz, component);
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    paint
 * Signature: (JLjava/awt/Component;Ljava/awt/Graphics;I)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_paint
    (JNIEnv *env, jclass clazz, jlong handle, jobject component, jobject g,
        jint zOrder)
{
#ifdef __ANDROID__
    return JAWTRenderer_paint(0, NULL, clazz, handle, g, zOrder);
#else /* #ifdef __ANDROID__ */
    JAWT awt;
    jboolean awtIsAvailable;
    jboolean wantsPaint;

    awt.version = JAWT_VERSION_1_4;
#ifdef __APPLE__
#ifndef JAWT_MACOSX_USE_CALAYER
#define JAWT_MACOSX_USE_CALAYER 0x80000000
#endif /* #ifndef JAWT_MACOSX_USE_CALAYER */

    awt.version |= JAWT_MACOSX_USE_CALAYER;
    awtIsAvailable = JAWT_GetAWT(env, &awt);
    /*
     * We do not know whether JAWT_GetAWT will fail when JAWT_MACOSX_USE_CALAYER
     * is specified and not supported or it will rather remove the flag from the
     * version field of JAWT. That's why we will call the function in question
     * again in case of failure with the flag removed.
     */
    if (JNI_FALSE == awtIsAvailable)
    {
        awt.version &= ~JAWT_MACOSX_USE_CALAYER;
        awtIsAvailable = JAWT_GetAWT(env, &awt);
    }
#else /* #ifdef __APPLE__ */
    awtIsAvailable = JAWT_GetAWT(env, &awt);
#endif /* #ifdef __APPLE__ */
    wantsPaint = JNI_TRUE;
    if (JNI_TRUE == awtIsAvailable)
    {
        JAWT_DrawingSurface *ds;

        ds = awt.GetDrawingSurface(env, component);
        if (ds)
        {
            jint dsLock;

            dsLock = ds->Lock(ds);
            if (0 == (dsLock & JAWT_LOCK_ERROR))
            {
                JAWT_DrawingSurfaceInfo *dsi;

                dsi = ds->GetDrawingSurfaceInfo(ds);
                if (dsi && dsi->platformInfo)
                {
                    /*
                     * The function arguments env and component are now
                     * available as the fields env and target, respectively, of
                     * the JAWT_DrawingSurface which is itself the value of the
                     * field ds of the JAWT_DrawingSurfaceInfo.
                     */
                    wantsPaint
                        = JAWTRenderer_paint(
                            awt.version,
                            dsi,
                            clazz,
                            handle,
                            g,
                            zOrder);
                    ds->FreeDrawingSurfaceInfo(dsi);
                }
                ds->Unlock(ds);
            }
            awt.FreeDrawingSurface(ds);
        }
    }
    return wantsPaint;
#endif /* #ifdef __ANDROID__ */
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    process
 * Signature: (JLjava/awt/Component;[IIIII)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_process
    (JNIEnv *env, jclass clazz, jlong handle, jobject component, jintArray data,
        jint offset, jint length, jint width, jint height)
{
    jint *dataPtr;
    jboolean processed;

    dataPtr = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (dataPtr)
    {
        processed
            = JAWTRenderer_process(
                    env, clazz,
                    handle, component,
                    dataPtr + offset, length,
                    width, height);
        (*env)->ReleasePrimitiveArrayCritical(
                env,
                data, dataPtr,
                JNI_ABORT);
    }
    else
        processed = JNI_FALSE;
    return processed;
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    removeNotify
 * Signature: (JLjava/awt/Component;)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_removeNotify
    (JNIEnv *env, jclass clazz, jlong handle, jobject component)
{
#ifdef __APPLE__
    JAWTRenderer_removeNotify(env, clazz, handle, component);
#endif /* #ifdef __APPLE__ */
}

/*
 * Class:     org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer
 * Method:    sysctlbyname
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer_sysctlbyname
    (JNIEnv *env, jclass clazz, jstring name)
{
#ifdef __APPLE__
    return JAWTRenderer_sysctlbyname(env, name);
#else /* #ifdef __APPLE__ */
    return NULL;
#endif /* #ifdef __APPLE__ */
}
