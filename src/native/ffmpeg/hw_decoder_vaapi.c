/**
 * \file hw_decoder_vaapi.c
 * \brief Hardware decoder using VAAPI.
 * \author Sebastien Vincent
 * \date 2013
 */

/* only Linux */
#ifdef __linux__

#include <va/va.h>
#include <va/va_x11.h>

#include <libavcodec/vaapi.h>

#include "hw_decoder.h"
#include "hw_decoder_vaapi.h"

/**
 * \brief Returns the profile corresponding to codec.
 * \param codec_id ID of the codec.
 * \param nb_surface if not NULL, it is filled with the  surface number.
 * \return profile or -1 if no profile correspond to codec.
 */
static VAProfile hw_vaapi_get_profile(enum CodecID codec_id, int* nb_surfaces)
{
  int profile = -1;
  int surfaces = 0;

  switch (codec_id)
  {
    case CODEC_ID_MPEG2VIDEO:
      profile = VAProfileMPEG2Main;
      surfaces = 3;
      break;
    case CODEC_ID_MPEG4:
    case CODEC_ID_H263:
      profile = VAProfileMPEG4AdvancedSimple;
      surfaces = 3;
      break;
    case CODEC_ID_H264:
      profile = VAProfileH264High;
      surfaces = 21;
      break;
    case CODEC_ID_WMV3:
      profile = VAProfileVC1Main;
      surfaces = 3;
      break;
    case CODEC_ID_VC1:
      profile = VAProfileVC1Advanced;
      surfaces = 3;
      break;
    default:
      profile = -1;
      break;
  }

  if(nb_surfaces && profile != -1)
  {
    *nb_surfaces = surfaces <= VAAPI_MAX_SURFACES ? surfaces : VAAPI_MAX_SURFACES;
  }
  return profile;
}

/**
 * \brief Open X11 and VAAPI display.
 * \param va_display pointer on VAAPI display.
 * \param x11_display pointer on X11 display.
 * \return 0 if success, -1 otherwise.
 */
static int hw_vaapi_open_display(Display** x11_display, VADisplay* va_display)
{
  int major = 0;
  int minor = 0;

  *x11_display = XOpenDisplay(NULL);

  if(!*x11_display)
  {
    return -1;
  }

  *va_display = vaGetDisplay(*x11_display);

  if(!*va_display || vaInitialize(*va_display, &major,
        &minor) != VA_STATUS_SUCCESS)
  {
    XCloseDisplay(*x11_display);
    return -1;
  }

  return 0;
}

/**
 * \brief Close VAAPI and X11 display.
 * \param x11_display X11 display.
 * \param va_display VAAPI display.
 */
static void hw_vaapi_close_display(Display* x11_display, VADisplay va_display)
{
  vaTerminate(va_display);
  XCloseDisplay(x11_display);
}

/**
 * \brief Returns whether or not a profile is supported for decoding.
 * \param va_display VAAPI display.
 * \param profile profile to test.
 * \return 1 if profile is supported for decoding, 0 otherwise.
 */
static int hw_vaapi_is_profile_supported(VADisplay va_display,
    VAProfile profile)
{
  int max_entrypoint = vaMaxNumEntrypoints(va_display);
  VAEntrypoint* entries = malloc(sizeof(VAEntrypoint) * max_entrypoint);
  int found = 0;
  int nb = 0;

  if(!entries)
  {
    return 0;
  }
  
  memset(entries, 0x00, sizeof(VAEntrypoint) * max_entrypoint);

  if(vaQueryConfigEntrypoints(va_display, profile, entries,
        &nb) != VA_STATUS_SUCCESS)
  {
    free(entries);
    return 0;
  }
    
  for(int i = 0 ; i < nb ; i++)
  {
    if(entries[i] == VAEntrypointVLD)
    {
      found = 1;
      break;
    }
  }

  free(entries);

  return found;
}

struct hw_decoder* hw_decoder_new(enum CodecID codec_id)
{
  struct hw_decoder* obj = NULL;
  Display* x11_display = NULL;
  VADisplay va_display = NULL;
  int profile = -1;
  int surfaces = 0;

  if(hw_vaapi_open_display(&x11_display, &va_display) != 0)
  {
    return NULL;
  }

  profile = hw_vaapi_get_profile(codec_id, &surfaces);

  if(profile == -1 || !hw_vaapi_is_profile_supported(va_display, profile))
  {
    hw_vaapi_close_display(x11_display, va_display);
    return NULL;
  }

  obj = malloc(sizeof(struct hw_decoder));


  if(!obj)
  {
    hw_vaapi_close_display(x11_display, va_display);
    return NULL;
  }

  memset(obj, 0x00, sizeof(struct hw_decoder));

  /* some initializations */
  obj->width = 0;
  obj->height = 0;
  obj->codec_id = codec_id;
  obj->x11_display = x11_display;
  obj->context.nb_surfaces = surfaces;
  obj->context.display = va_display;
  obj->context.context = VA_INVALID_ID;
  obj->context.config = VA_INVALID_ID;
  obj->context.profile = VA_INVALID_ID;

  for(size_t i = 0 ; i < obj->context.nb_surfaces ; i++)
  {
    obj->context.surfaces[i].surface = VA_INVALID_ID;
    obj->context.surfaces[i].is_used = 0;
  }

  return obj;
}

void hw_decoder_free(struct hw_decoder** obj)
{
  struct hw_decoder* o = *obj;
  struct hw_vaapi_context* context = &o->context;

  /* release VAAPI context */
  if(context->context != VA_INVALID_ID)
  {
    vaDestroyContext(context->display, context->context);
  }

  /* release VAAPI surfaces */
  for(size_t i = 0; i < context->nb_surfaces ; i++)
  {
    if(context->surfaces[i].surface != VA_INVALID_ID)
    {
      vaDestroySurfaces(context->display, &context->surfaces[i].surface, 1);
    }
  }

  /* release VAAPI configuration */
  if(context->config != VA_INVALID_ID)
  {
    vaDestroyConfig(context->display, context->config);
  }

  hw_vaapi_close_display(o->x11_display, o->context.display);

  free(o);
  *obj = NULL;
}

int hw_decoder_init(struct hw_decoder* obj, void* profile, int width,
    int height)
{
  VAConfigAttrib config_attrib;
  VAProfile va_profile = (VAProfile)(intptr_t)profile;
  VASurfaceID surfaces[VAAPI_MAX_SURFACES];
  struct hw_vaapi_context* context = &obj->context;

  if(va_profile != obj->context.profile)
  {
    /* destroy config if it exist */
    if(obj->context.config != VA_INVALID_ID)
    {
      /* release previous surfaces and configuration */
      for(size_t i = 0 ; i < context->nb_surfaces ; i++)
      {
        vaDestroySurfaces(context->display, &context->surfaces[i].surface, 1);
        context->surfaces[i].surface = VA_INVALID_ID;
        context->surfaces[i].is_used = 0;
      }
      vaDestroyConfig(context->display, context->config);
    }

    /* VAAPI configuration */
    memset(&config_attrib, 0x00, sizeof(VAConfigAttrib));
    config_attrib.type = VAConfigAttribRTFormat;

    if((vaGetConfigAttributes(context->display, va_profile,
            VAEntrypointVLD, &config_attrib, 1) != VA_STATUS_SUCCESS) || 
        (config_attrib.value & VA_RT_FORMAT_YUV420) == 0)
    {
      return -1;
    }

    if(vaCreateConfig(context->display, va_profile, VAEntrypointVLD,
          &config_attrib, 1, &context->config) != VA_STATUS_SUCCESS)
    {
      context->config = VA_INVALID_ID;
      return -1;
    }
  }

  context->profile = va_profile;

  /* creates the surfaces */
  if(vaCreateSurfaces(context->display, width, height,
        VA_RT_FORMAT_YUV420, context->nb_surfaces,
        surfaces) != VA_STATUS_SUCCESS)
  {
    vaDestroyConfig(context->display, context->config);
    context->config = VA_INVALID_ID;
    return -1;
  }

  /* copy the surfaces */
  for(size_t i = 0 ; i < context->nb_surfaces ; i++)
  {
    context->surfaces[i].is_used = 0;
    context->surfaces[i].surface = surfaces[i];
  }

  /* creates the VAAPI context */
  if(vaCreateContext(context->display, context->config, width, height,
        VA_PROGRESSIVE, surfaces, context->nb_surfaces, &context->context) != VA_STATUS_SUCCESS)
  {
    return -1;
  }

  obj->width = width;
  obj->height = height;


  return 0;
}

void* hw_decoder_get_surface(struct hw_decoder* obj)
{
  struct hw_vaapi_context* context = &obj->context;
  static size_t idx = 0;
  size_t i = 0;
  void* ret = NULL;

  for(i = idx ; i < context->nb_surfaces ; i++)
  {
    if(!context->surfaces[i].is_used)
    {
      context->surfaces[i].is_used = 1;
      ret = (void*)(intptr_t)context->surfaces[i].surface;
      break;
    }
  }

  if(i >= context->nb_surfaces)
  {
    i = 0;
    /* all is busy so force to take the first one! */
    context->surfaces[0].is_used = 1;
    ret = (void*)(intptr_t)context->surfaces[0].surface;
  }
  idx = i + 1;
  return ret;
}

void hw_decoder_release_surface(struct hw_decoder* obj, void* surface)
{
  VASurfaceID surface_id = (VASurfaceID)(intptr_t)surface;
  struct hw_vaapi_context* context = &obj->context;
  
  for(size_t i = 0 ; i < context->nb_surfaces ; i++)
  {
    if(context->surfaces[i].surface == surface_id)
    {
      context->surfaces[i].is_used = 0;
      break;
    }
  }
}

void hw_decoder_init_hwaccel_context(struct hw_decoder* obj, void* hwaccel_context)
{
  struct vaapi_context* vaapi = hwaccel_context;

  vaapi->display = obj->context.display;
  vaapi->config_id = obj->context.config;
  vaapi->context_id = obj->context.context;
}

#if 0
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

int hw_decoder_is_codec_supported(int codec_id)
{
  Display* x11_display = NULL;
  VADisplay va_display = NULL;
  int profile = hw_vaapi_get_profile(codec_id, NULL);
  int found = 0;

  if(profile == -1)
  {
    return 0;
  }

  if(hw_vaapi_open_display(&x11_display, &va_display) != 0)
  {
    return 0;
  }

  found = hw_vaapi_is_profile_supported(va_display, profile);
  hw_vaapi_close_display(x11_display, va_display);

  return found;
}

enum PixelFormat hw_ffmpeg_get_format(struct AVCodecContext *avctx,
    const enum PixelFormat *fmt)
{
  int profile = -1;

  for(int i = 0; fmt[i] != PIX_FMT_NONE; i++)
  {
    if(fmt[i] != PIX_FMT_VAAPI_VLD)
    {
      continue;
    }

    profile = hw_vaapi_get_profile(avctx->codec_id, NULL);

    if(profile >= 0)
    {
      struct hw_decoder* obj = hw_decoder_new(avctx->codec_id);

      if(!obj)
      {
        continue;
      }

      if(hw_decoder_init(obj, (void*)(intptr_t)profile, avctx->width,
            avctx->height) == 0)
      {
        struct vaapi_context* hwaccel = malloc(sizeof(struct vaapi_context));
      
        if(!hwaccel)
        {
          hw_decoder_free(&obj);
          continue;
        }

        memset(hwaccel, 0x00, sizeof(struct vaapi_context));
        hw_decoder_init_hwaccel_context(obj, hwaccel);
        avctx->hwaccel_context = hwaccel;
        avctx->opaque = obj;

        fprintf(stdout, "Use VAAPI decoding!\n");
        fflush(stdout);
        return fmt[i];
      }
      else
      {
        hw_decoder_free(&obj);
      }
    }
  }
  
  return avcodec_default_get_format(avctx, fmt);
}

int hw_ffmpeg_get_buffer(struct AVCodecContext* avctx, AVFrame* avframe)
{
  if(avctx->hwaccel_context)
  {
    struct hw_decoder* obj = avctx->opaque;
    void *surface = NULL;

    surface = hw_decoder_get_surface(obj);

    avframe->type = FF_BUFFER_TYPE_USER;
    avframe->data[0] = surface;
    avframe->data[1] = NULL;
    avframe->data[2] = NULL;
    avframe->data[3] = surface;
    avframe->linesize[0] = 0;
    avframe->linesize[1] = 0;
    avframe->linesize[2] = 0;
    avframe->linesize[3] = 0;
    return 0;
  }

  return avcodec_default_get_buffer(avctx, avframe);
}

void hw_ffmpeg_release_buffer(struct AVCodecContext* avctx, AVFrame* avframe)
{
  if(avctx->hwaccel_context)
  {
    struct hw_decoder* obj = avctx->opaque;
    
    /* if(avframe->data[3] != (void*)(intptr_t)VA_INVALID_ID) */
    {
      hw_decoder_release_surface(obj, avframe->data[3]);
      avframe->data[3] = (void*)(intptr_t)VA_INVALID_ID;
    }
    for(size_t i = 0 ; i < 4 ; i++)
    {
      avframe->data[i] = NULL;
      avframe->linesize[i] = 0;
    }
    return;
  }

  avcodec_default_release_buffer(avctx, avframe);
}

#endif

