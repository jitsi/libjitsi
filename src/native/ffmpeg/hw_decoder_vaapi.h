/**
 * \file hw_decoder_vaapi.h
 * \brief Hardware decoder for VAAPI.
 * \author Sebastien Vincent
 * \date 2013
 */

#ifndef HW_DECODER_VAAPI_H
#define HW_DECODER_VAAPI_H

/**
 * \def VAAPI_MAX_SURFACES
 * \brief Maximum number of surfaces.
 */
#define VAAPI_MAX_SURFACES 32

/**
 * \struct hw_vaapi_surface
 * \brief VAAPI surface reference.
 */
struct hw_vaapi_surface
{
  VASurfaceID surface; /**< VAAPI surface object. */
  int is_used; /**< If the surface is currently in use by FFmpeg. */
};

/**
 * \struct hw_vaapi_context.
 * \brief VAAPI context.
 */
struct hw_vaapi_context
{
  VADisplay display; /**< The VAAPI display. */
  VAContextID context; /**< VAAPI context. */
  VAConfigID config; /**< The VAAPI configuration. */
  VAProfile profile; /**< VAAPI profile. */
  struct hw_vaapi_surface surfaces[VAAPI_MAX_SURFACES]; /**< VAAPI surfaces. */
  size_t nb_surfaces; /**< Number of surfaces. */
};

/**
 * \struct hw_decoder
 * \brief Hardware decoder using VAAPI.
 */
struct hw_decoder
{
  enum CodecID codec_id; /**< FFmpeg codec ID. */
  Display* x11_display; /**< X11 display. */
  int width; /**< Width of the image. */
  int height; /**< Height of the image. */
  struct hw_vaapi_context context; /**< Hardware decoder context. */
};

#endif /* HW_DECODER_VAAPI_H */

