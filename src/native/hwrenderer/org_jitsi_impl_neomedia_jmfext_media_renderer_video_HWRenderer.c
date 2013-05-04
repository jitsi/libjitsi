/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_jmfext_media_renderer_video_HWRenderer.h"

#include "hw_renderer.h"

JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_HWRenderer_close
  (JNIEnv* env, jclass clazz, jlong handle, jobject component)
{
  hw_renderer_close(env, clazz, handle, component);
}

JNIEXPORT jlong JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_HWRenderer_open
  (JNIEnv* env, jclass clazz, jobject component)
{
  return hw_renderer_open(env, clazz, component);
}

JNIEXPORT jboolean JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_HWRenderer_paint
  (JNIEnv* env, jclass clazz, jlong handle, jobject component, jobject graphic, jint zOrder)
{
  JAWT awt;
  jboolean wantsPaint = JNI_FALSE;

  (void)zOrder;

  awt.version = JAWT_VERSION_1_3;

  if(JAWT_GetAWT(env, &awt) != JNI_FALSE)
  {
    JAWT_DrawingSurface* ds = NULL;

    ds = awt.GetDrawingSurface(env, component);

    if(ds)
    {
      jint dsLock;

      dsLock = ds->Lock(ds);

      if((dsLock & JAWT_LOCK_ERROR) == 0)
      {
        JAWT_DrawingSurfaceInfo* dsi = ds->GetDrawingSurfaceInfo(ds);

        if(dsi && dsi->platformInfo)
        {
          wantsPaint = hw_renderer_paint(dsi, clazz, handle, graphic);
          ds->FreeDrawingSurfaceInfo(dsi);
        }
        ds->Unlock(ds);
      }
      awt.FreeDrawingSurface(ds);
    }
  }
  return wantsPaint;
}

JNIEXPORT jboolean JNICALL Java_org_jitsi_impl_neomedia_jmfext_media_renderer_video_HWRenderer_process
  (JNIEnv* env, jclass clazz, jlong handle, jobject component, jlong data, jint offset, jint length, jint width, jint height)
{
  return hw_renderer_process(env, clazz, handle, component, data, offset, length, width, height);
}

