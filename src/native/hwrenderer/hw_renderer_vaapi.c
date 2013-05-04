/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

/**
 * \file hw_renderer_vaapi.c
 * \brief Hardware renderer for VAAPI.
 * \author Sebastien Vincent
 * \date 2013
 */

#ifdef __linux__

#include <va/va.h>
#include <va/va_x11.h>

#include <jawt_md.h>
#include <libavcodec/avcodec.h>

#include "hw_renderer.h"
#include "../ffmpeg/hw_decoder.h"
#include "../ffmpeg/hw_decoder_vaapi.h"

/**
 * \struct hw_renderer_vaapi
 * \brief Hardware renderer for VAAPI.
 */
struct hw_renderer_vaapi
{
  struct hw_decoder* decoder; /**< Hardware decoder used. */
  AVFrame* avframe; /**< FFmpeg frame. */
};

void hw_renderer_close(JNIEnv* env, jclass clazz, jlong handle,
    jobject component)
{
  struct hw_renderer_vaapi* renderer = (struct hw_renderer_vaapi*)handle;

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
  struct hw_renderer_vaapi* renderer = malloc(sizeof(struct hw_renderer_vaapi));

  (void)env;
  (void)clazz;
  (void)component;

  if(!renderer)
  {
    return 0;
  }

  renderer->decoder = NULL;
  renderer->avframe = NULL;
  return (jlong)renderer;
}

jboolean hw_renderer_paint(JAWT_DrawingSurfaceInfo* dsi, jclass clazz,
    jlong handle, jobject graphic)
{
  struct hw_renderer_vaapi* renderer = (struct hw_renderer_vaapi*)handle;
  JAWT_X11DrawingSurfaceInfo* x11_dsi =
    (JAWT_X11DrawingSurfaceInfo*)dsi->platformInfo;
  Drawable drawable = x11_dsi->drawable;

  (void)clazz;
  (void)graphic;
    
  if(renderer->avframe && renderer->decoder)
  {
    hw_decoder_render(renderer->decoder, (void*)x11_dsi->display,
        (void*)drawable, (void*)renderer->avframe->data[3]);
    return JNI_TRUE;
  }

  return JNI_TRUE;
}

jboolean hw_renderer_process(JNIEnv* env, jclass clazz, jlong handle,
    jobject component, jlong data, jint offset, jint length, jint width, jint height)
{
  struct hw_renderer_vaapi* renderer = (struct hw_renderer_vaapi*)handle;
  AVFrame* avframe = (AVFrame*)data;

  (void)env;
  (void)clazz;
  (void)component;
  (void)offset;
  (void)length;
  (void)width;
  (void)height;

  if(avframe)
  {
    renderer->decoder = avframe->opaque;
    renderer->avframe = avframe;
  }
  return JNI_TRUE;
}

void hw_decoder_render(struct hw_decoder* obj, void* display, void* drawable, void* surface)
{
  VASurfaceID surface_id = (VASurfaceID)(intptr_t)surface;
  Drawable x11_drawable = (Drawable)(intptr_t)drawable;
  Display* x11_display = (Display*)(intptr_t)display;
  struct hw_vaapi_context* context = NULL;
  Window root;
  unsigned int width = 0;
  unsigned int height = 0;
  int x = 0;
  int y = 0;
  unsigned int border = 0;
  unsigned int depth = 0;

  /* basic checks */
  if(!obj || surface_id == VA_INVALID_ID)
  {
    return;
  }

  context = &obj->context;

  if(XGetGeometry(x11_display, x11_drawable, &root, &x, &y, &width, &height,
        &border, &depth))
  {
    if(vaPutSurface(context->display, surface_id, x11_drawable,
          0, 0, obj->width, obj->height,
          0, 0, width, height,
          NULL,0, VA_FRAME_PICTURE) != VA_STATUS_SUCCESS)
    {
      return;
    }
    else
    {
      /*
      fprintf(stdout, "Rendering with VAAPI!\n");
      fflush(stdout);
      */
    }
  }
}

#endif

