/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file hw_renderer_dxva2.c
 * \brief Hardware renderer for dxva2.
 * \author Sebastien Vincent
 * \date 2013
 */

#if defined(_WIN32) || defined(_WIN64)

#include "dxva2api_mingw.h"

#include <jawt_md.h>
#include <libavcodec/avcodec.h>

#include "hw_renderer.h"

/**
 * \struct hw_renderer_dxva2
 * \brief Hardware renderer for dxva2.
 */
struct hw_renderer_dxva2
{
  AVFrame* avframe; /**< FFmpeg frame. */
  /* rendering stuff */
  IDirectXVideoProcessorService* render_service; /**< DXVA2 render service. */
  IDirectXVideoProcessor* renderer; /**< DXVA2 renderer. */
};

void hw_renderer_display(struct hw_renderer_dxva2* obj, void* hwnd,
        void* hdc, void* surface)
{
  /* basic checks */
  if(!obj || !surface || !hwnd)
  {
    return;
  }

  (void)hdc;

  /* TODO */
}

void hw_renderer_close(JNIEnv* env, jclass clazz, jlong handle,
    jobject component)
{
  struct hw_renderer_dxva2* renderer =
      (struct hw_renderer_dxva2*)(intptr_t)handle;

  (void)env;
  (void)clazz;
  (void)component;

  if(renderer)
  {
    free(renderer);
  }
}

jlong hw_renderer_open(JNIEnv* env, jclass clazz, jobject component)
{
  struct hw_renderer_dxva2* renderer = malloc(sizeof(struct hw_renderer_dxva2));

  (void)env;
  (void)clazz;
  (void)component;

  if(!renderer)
  {
    return 0;
  }

  renderer->render_service = NULL;
  renderer->renderer = NULL;
  renderer->avframe = NULL;
  return (jlong)(intptr_t)renderer;
}

jboolean hw_renderer_paint(JAWT_DrawingSurfaceInfo* dsi, jclass clazz,
    jlong handle, jobject graphic)
{
  struct hw_renderer_dxva2* renderer =
      (struct hw_renderer_dxva2*)(intptr_t)handle;
  JAWT_Win32DrawingSurfaceInfo* win32_dsi =
    (JAWT_Win32DrawingSurfaceInfo*)dsi->platformInfo;
  HWND hwnd = win32_dsi->hwnd ? win32_dsi->hwnd : WindowFromDC(win32_dsi->hdc);

  (void)clazz;
  (void)graphic;
    
  if(renderer->avframe)
  {
    hw_renderer_display(renderer, (void*)hwnd,
        win32_dsi->hdc, (void*)renderer->avframe->data[3]);
    return JNI_TRUE;
  }

  return JNI_TRUE;
}

jboolean hw_renderer_process(JNIEnv* env, jclass clazz, jlong handle,
    jobject component, jlong data, jint offset, jint length, jint width, jint height)
{
  struct hw_renderer_dxva2* renderer =
      (struct hw_renderer_dxva2*)(intptr_t)handle;
  AVFrame* avframe = (AVFrame*)(intptr_t)data;

  (void)env;
  (void)clazz;
  (void)component;
  (void)offset;
  (void)length;
  (void)width;
  (void)height;

  if(avframe)
  {
    /* renderer->decoder = avframe->opaque; */
    renderer->avframe = avframe;
  }
  return JNI_TRUE;
}

#endif

