/**
 * \file hw_decoder.h
 * \brief Hardware decoder for media (H.264, ...).
 * \author Sebastien Vincent
 * \date 2013
 */

#ifndef HW_DECODER_H
#define HW_DECODER_H

#include <libavcodec/avcodec.h>

/* forward declaration. */
struct hw_decoder;

/**
 * \brief Returns whether or not specified codec is supported.
 * \param codec_id ID of the codec to test.
 * \return 1 if codec is supported, 0 otherwise.
 */
int hw_decoder_is_codec_supported(int codec_id);

/**
 * \brief Initialize hardware decoder.
 * \param codec_id ID of the codec.
 * \return valid decoder pointer if success, NULL otherwise.
 */
struct hw_decoder* hw_decoder_new(enum CodecID codec_id);

/**
 * \brief Free hardware decoder.
 * \param pointer on hw_decoder pointer.
 */
void hw_decoder_free(struct hw_decoder** obj);

/**
 * \brief Initialize the decoder with the format parameters.
 * \param obj hardware decoder pointer.
 * \param profile profise used.
 * \param width width of the image.
 * \param height height of the image.
 * \return 0 if success, -1 otherwise.
 */
int hw_decoder_init(struct hw_decoder* obj, void* profile, int width,int height);

/**
 * \brief Get a free surface.
 * \param obj hardware decoder pointer.
 * \return surface or NULL if no surface can be returned.
 */
void* hw_decoder_get_surface(struct hw_decoder* obj);

/**
 * \brief Release a surface.
 * \param obj hardware decoder pointer.
 * \param surface to release.
 */
void hw_decoder_release_surface(struct hw_decoder* obj, void* surface);

/**
 * \brief Initialize the FFmepg hwaccel_context.
 * \param obj hardware decoder pointer.
 * \param hwaccel_context FFmpeg hwaccel_context.
 */
void hw_decoder_init_hwaccel_context(struct hw_decoder* obj, void* hwaccel_context);

/**
 * \brief Render the image on the display.
 * \param obj hardware decoder pointer.
 * \param display display pointer (OS specific: Display on X11, ...).
 * \param drawable drawable pointer (OS specific: Drawable on X11, ...).
 * \param surface surface (OS specific: VASurfaceID for VAAPI, ...).
 */
void hw_decoder_render(struct hw_decoder* obj, void* display, void* drawable, void* surface);

/**
 * \brief FFmpeg get_format callback.
 * \param avctx FFmpeg codec context.
 * \param fmt array of pixel formats.
 * \return pixel first format that match decoder or PIX_FMT_NONE if none match.
 */
enum PixelFormat hw_ffmpeg_get_format(struct AVCodecContext *avctx,
    const enum PixelFormat *fmt);

/**
 * \brief FFmpeg get_buffer callback.
 * \param avctx FFmpeg codec context.
 * \param avframe FFmpeg frame.
 * \return 0.
 */
int hw_ffmpeg_get_buffer(struct AVCodecContext* avctx, AVFrame* avframe);

/**
 * \brief FFmpeg release_buffer callback.
 * \param avctx FFmpeg codec context.
 * \param avframe FFmpeg frame.
 */
void hw_ffmpeg_release_buffer(struct AVCodecContext* avctx, AVFrame* avframe);

#endif /* HW_DECODER_H */

