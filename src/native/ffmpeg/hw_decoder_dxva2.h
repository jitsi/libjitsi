/**
 * \file hw_decoder_dxva2.h
 * \brief Hardware decoder for DXVA2.
 * \author Sebastien Vincent
 * \date 2013
 */

#ifndef HW_DECODER_DXVA2_H
#define HW_DECODER_DXVA2_H

#if defined(_WIN32) || defined(_WIN64)

#include <d3d9.h>
#include <dxva2api.h>

/**
 * \def DXVA2_MAX_SURFACES
 * \brief Maximum number of surfaces.
 */
#define DXVA2_MAX_SURFACES 32

/**
 * \struct hw_dxva2_surface
 * \brief DXVA2 surface reference.
 */
struct hw_dxva2_surface
{
  LPDIRECT3DSURFACE9 surface; /**< DXVA2 surface object. */
  int is_used; /**< If the surface is currently in use by FFmpeg. */
};

/**
 * \struct hw_dxva2_context.
 * \brief DXVA2 context.
 */
struct hw_dxva2_context
{
  HINSTANCE d3d_dll; /**< Direct3D dll. */
  HINSTANCE dxva2_dll; /**< Direct3D dll. */
  LPDIRECT3D9 d3d; /**< Direct3D pointer. */
  D3DPRESENT_PARAMETERS present_params; /**< Direct3D present parameters. */
  LPDIRECT3DDEVICE9 device; /**< Direct3D device. */
#if 0
  IDirect3DDeviceManager9* manager; /**< Direct3D manager. */
  unsigned int manager_token; /**< Direct3D manager token. */
  HANDLE device_handle; /**< Direct3D device handle. */
#endif
  IDirectXVideoDecoderService* decoder_service; /**< DXVA2 decoder service. */
  GUID decoder_input; /**< decoder input GUID. */
  D3DFORMAT render_format; /**< render format. */
  DXVA2_ConfigPictureDecode config; /**< DXVA2 configuration. */
  IDirectXVideoDecoder* decoder; /**< DXVA2 decoder. */
  struct hw_dxva2_surface surfaces[DXVA2_MAX_SURFACES]; /**< DXVA2 surfaces. */
  LPDIRECT3DSURFACE9 d3d_surfaces[DXVA2_MAX_SURFACES]; /**< Raw surfaces. */
  size_t nb_surfaces; /**< Number of surfaces. */
  DXVA2_VideoDesc video_desc; /**< Video description. */
};

/**
 * \struct hw_decoder
 * \brief Hardware decoder using DXVA2.
 */
struct hw_decoder
{
  enum CodecID codec_id; /**< FFmpeg codec ID. */
  int width; /**< Width of the image. */
  int height; /**< Height of the image. */
  struct hw_dxva2_context context; /**< Hardware decoder context. */
};

#endif

#endif /* HW_DECODER_DXVA2_H */

