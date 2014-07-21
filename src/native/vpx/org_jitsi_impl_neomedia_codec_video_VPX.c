/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include <stdlib.h>
#include <string.h>

#include "vpx/vpx_decoder.h"
#include "vpx/vpx_encoder.h"
#include "vpx/vp8dx.h"
#include "vpx/vp8cx.h"

/*
 * Both openjdk-1.7's jni_md.h and vpx/vpx_codec.h define the 'UNUSED' macro.
 * Include this here, after the vpx includes, because it brings in jni_md.h, and
 * using this order at least allows for successful compilation.
 */
#include "org_jitsi_impl_neomedia_codec_video_VPX.h"

#define VPX_CODEC_DISABLE_COMPAT 1

/* Convert the INTERFACE_* constants defined in java to
   the (vpx_codec_iface_t *)'s used in libvpx */
#define GET_INTERFACE(x) \
    (((x) == org_jitsi_impl_neomedia_codec_video_VPX_INTEFACE_VP8_DEC) \
    ? vpx_codec_vp8_dx() \
    : (((x) == org_jitsi_impl_neomedia_codec_video_VPX_INTERFACE_VP8_ENC)) \
        ? vpx_codec_vp8_cx() \
        : NULL)

#define DEFINE_ENC_CFG_INT_PROPERTY_SETTER(name, property) \
    JNIEXPORT void JNICALL \
    Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1cfg_1set_1##name \
            (JNIEnv *env, jclass clazz, jlong cfg, jint value) \
        { \
            ((vpx_codec_enc_cfg_t *) (intptr_t) cfg)->property = (int) value; \
        }

#define DEFINE_IMG_INT_PROPERTY_SETTER(name, property) \
    JNIEXPORT void JNICALL \
    Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1set_1##name \
            (JNIEnv *env, jclass clazz, jlong img, jint value) \
        { \
            ((vpx_image_t *) (intptr_t) img)->property = (int) value; \
        }


/*
 * Method:    codec_ctx_malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1ctx_1malloc
    (JNIEnv *env,
     jclass clazz)
{
     return (jlong) (intptr_t) malloc(sizeof(vpx_codec_ctx_t));
}

/*
 * Method:    codec_dec_init
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1dec_1init
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jint iface,
     jlong cfg,
     jlong flags)
{
    return (jint) vpx_codec_dec_init(
                        (vpx_codec_ctx_t *) (intptr_t) context,
                        GET_INTERFACE(iface),
                        (vpx_codec_dec_cfg_t *) (intptr_t) cfg,
                        (vpx_codec_flags_t) flags);
}

/*
 * Method:    codec_decode
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1decode
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jbyteArray buf,
     jint buf_offset,
     jint buf_size,
     jlong user_priv,
     jlong deadline)
{
    jbyte *buf_ptr = (*env)->GetByteArrayElements(env, buf, NULL);

    vpx_codec_err_t ret;
    ret = vpx_codec_decode((vpx_codec_ctx_t *) (intptr_t) context,
                           (uint8_t *) (buf_ptr + buf_offset),
                           (unsigned int) buf_size,
                           (void *) (intptr_t) user_priv,
                           (long) deadline);
    (*env)->ReleaseByteArrayElements(env, buf, buf_ptr, JNI_ABORT);
    return (jint) ret;
}

/*
 * Method:    codec_get_frame
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1get_1frame
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlongArray iterArray)
{
    jlong *iter_ptr = (*env)->GetLongArrayElements(env, iterArray, NULL);

    vpx_image_t *ret;
    ret = vpx_codec_get_frame((vpx_codec_ctx_t *) (intptr_t) context,
                              (vpx_codec_iter_t *) iter_ptr);

    (*env)->ReleaseLongArrayElements(env, iterArray, iter_ptr, 0);
    return (jlong) (intptr_t) ret;
}

/*
 * Method:    codec_destroy
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1destroy
    (JNIEnv *env,
     jclass clazz,
     jlong context)
{
    return (jint) vpx_codec_destroy((vpx_codec_ctx_t *) (intptr_t) context);
}

/*
 * Method:    codec_get_mem_map
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1get_1mem_1map
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlongArray mmapArray,
     jlongArray iterArray)
{
    jlong *iter_ptr = (*env)->GetLongArrayElements(env, iterArray, NULL);
    jlong *mmap_ptr = (*env)->GetLongArrayElements(env, mmapArray, NULL);

    vpx_codec_err_t ret;
    ret = vpx_codec_get_mem_map((vpx_codec_ctx_t *) (intptr_t) context,
                                (vpx_codec_mmap_t *) mmap_ptr,
                                (vpx_codec_iter_t *) iter_ptr);

    (*env)->ReleaseLongArrayElements(env, iterArray, iter_ptr, 0);
    (*env)->ReleaseLongArrayElements(env, mmapArray, mmap_ptr, 0);

    return (jint) ret;
}

/*
 * Method:    codec_set_mem_map
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1set_1mem_1map
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlong mmap,
     jint count)
{
    vpx_codec_err_t ret;
    ret = vpx_codec_set_mem_map((vpx_codec_ctx_t *) (intptr_t) context,
                                (vpx_codec_mmap_t *) (intptr_t) mmap,
                                (unsigned int) count);
    return (jint) ret;
}

/*
 * Method:    codec_enc_init
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1init
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jint iface,
     jlong cfg,
     jlong flags)
{
    return (jint) vpx_codec_enc_init(
                        (vpx_codec_ctx_t *) (intptr_t) context,
                        GET_INTERFACE(iface),
                        (vpx_codec_enc_cfg_t *) (intptr_t) cfg,
                        (vpx_codec_flags_t) flags);
}

/*
 * Method:    codec_enc_config_set
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1config_1set
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlong cfg)
{
    return (jint) vpx_codec_enc_config_set(
                (vpx_codec_ctx_t *) (intptr_t) context,
                (vpx_codec_enc_cfg_t *) (intptr_t) context);
}

/*
 * Method:    codec_encode
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1encode
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlong jimg,
     jbyteArray bufArray,
     jint offset0,
     jint offset1,
     jint offset2,
     jlong pts,
     jlong duration,
     jlong flags,
     jlong deadline)
{
    unsigned char *buf
        = (unsigned char *) (*env)->GetByteArrayElements(env, bufArray, NULL);
    vpx_image_t *img = (vpx_image_t *) (intptr_t) jimg;
    img->planes[0] = (buf + offset0);
    img->planes[1] = (buf + offset1);
    img->planes[2] = (buf + offset2);
    img->planes[3] = 0;

    jint ret = (jint) vpx_codec_encode(
                    (vpx_codec_ctx_t *) (intptr_t) context,
                    img,
                    (vpx_codec_pts_t) pts,
                    (unsigned long) duration,
                    (vpx_enc_frame_flags_t) flags,
                    (unsigned long) deadline);

    (*env)->ReleaseByteArrayElements(env, bufArray, (jbyte *)buf, JNI_ABORT);
    return ret;
}

/*
 * Method:    codec_get_cx_data
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1get_1cx_1data
    (JNIEnv *env,
     jclass clazz,
     jlong context,
     jlongArray iterArray)
{
    jlong *iter_ptr = (*env)->GetLongArrayElements(env, iterArray, NULL);

    const vpx_codec_cx_pkt_t *ret;
    ret = vpx_codec_get_cx_data((vpx_codec_ctx_t *) (intptr_t) context,
                                (vpx_codec_iter_t *) iter_ptr);

    (*env)->ReleaseLongArrayElements(env, iterArray, iter_ptr, 0);
    return (jlong) (intptr_t) ret;

}

/*
 * Method:    codec_cx_pkt_get_kind
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1cx_1pkt_1get_1kind
    (JNIEnv *env,
     jclass clazz,
     jlong pkt)
{
    jint ret = (jint) ((vpx_codec_cx_pkt_t *) (intptr_t) pkt)->kind;
    return ret;
}

/*
 * Method:    codec_cx_pkt_get_size
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1cx_1pkt_1get_1size
    (JNIEnv *env,
     jclass clazz,
     jlong pkt)
{
    return (jint) ((vpx_codec_cx_pkt_t *) (intptr_t) pkt)->data.frame.sz;
}

/*
 * Method:    codec_cx_pkt_get_data
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1cx_1pkt_1get_1data
    (JNIEnv *env,
     jclass clazz,
     jlong pkt)
{
      return (jlong) (intptr_t)
                    ((vpx_codec_cx_pkt_t *) (intptr_t) pkt)->data.frame.buf;
}

/*
 * Method:    img_malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1malloc
    (JNIEnv *env,
     jclass clazz)
{
    return (jlong) (intptr_t) malloc(sizeof(vpx_image_t));
}

/*
 * Method:    img_get_w
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1w
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->w;
}

/*
 * Method:    img_get_h
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1h
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->h;
}

/*
 * Method:    img_get_d_w
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1d_1w
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->d_w;
}

/*
 * Method:    img_get_d_h
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1d_1h
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->d_h;
}

/*
 * Method:    img_get_plane0
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1plane0
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jlong) (intptr_t) ((vpx_image_t *) (intptr_t) img)->planes[0];
}

/*
 * Method:    img_get_plane1
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1plane1
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jlong) (intptr_t) ((vpx_image_t *) (intptr_t) img)->planes[1];
}

/*
 * Method:    img_get_plane2
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1plane2
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jlong) (intptr_t) ((vpx_image_t *) (intptr_t) img)->planes[2];
}

/*
 * Method:    img_get_stride0
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1stride0
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->stride[0];
}

/*
 * Method:    img_get_stride1
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1stride1
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->stride[1];
}

/*
 * Method:    img_get_stride2
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1stride2
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->stride[2];
}

/*
 * Method:    img_get_fmt
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1get_1fmt
    (JNIEnv *env,
     jclass clazz,
     jlong img)
{
    return (jint) ((vpx_image_t *) (intptr_t) img)->fmt;
}

DEFINE_IMG_INT_PROPERTY_SETTER(w, w)
DEFINE_IMG_INT_PROPERTY_SETTER(h, h)
DEFINE_IMG_INT_PROPERTY_SETTER(d_1w, d_w)
DEFINE_IMG_INT_PROPERTY_SETTER(d_1h, d_h)
DEFINE_IMG_INT_PROPERTY_SETTER(stride0, stride[0])
DEFINE_IMG_INT_PROPERTY_SETTER(stride1, stride[1])
DEFINE_IMG_INT_PROPERTY_SETTER(stride2, stride[2])
DEFINE_IMG_INT_PROPERTY_SETTER(stride3, stride[3])
DEFINE_IMG_INT_PROPERTY_SETTER(fmt, fmt)
DEFINE_IMG_INT_PROPERTY_SETTER(bps, bps)

/*
 * Method:    img_wrap
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_img_1wrap
    (JNIEnv *env,
     jclass clazz,
     jlong img,
     jint fmt,
     jint d_w,
     jint d_h,
     jint align,
     jlong data)
{
    vpx_img_wrap((vpx_image_t *) (intptr_t) img,
                 (vpx_img_fmt_t) fmt,
                 (unsigned int) d_w,
                 (unsigned int) d_h,
                 (unsigned int) align,
                 (unsigned char *) (intptr_t) data);
}

/*
 * Method:    codec_dec_cfg_malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1dec_1cfg_1malloc
    (JNIEnv *env,
     jclass clazz)
{
    return (jlong) (intptr_t) malloc(sizeof(vpx_codec_dec_cfg_t));
}

/*
 * Method:    codec_dec_cfg_set_w
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1dec_1cfg_1set_1w
    (JNIEnv *env,
     jclass clazz,
     jlong cfg,
     jint width)
{
    ((vpx_codec_dec_cfg_t *) (intptr_t) cfg)->w = width;
}

/*
 * Method:    codec_dec_cfg_set_w
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1dec_1cfg_1set_1h
    (JNIEnv *env,
     jclass clazz,
     jlong cfg,
     jint height)
{
    ((vpx_codec_dec_cfg_t *) (intptr_t) cfg)->h = height;
}

/*
 * Method:    codec_enc_cfg_malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1cfg_1malloc
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) malloc(sizeof(vpx_codec_enc_cfg_t));
}


/*
 * Method:    codec_enc_config_default
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1config_1default
    (JNIEnv *env,
     jclass clazz,
     jint iface,
     jlong cfg,
     jint usage)
{
    return vpx_codec_enc_config_default(
                     GET_INTERFACE(iface),
                     (vpx_codec_enc_cfg_t *) (intptr_t) cfg,
                     (int) usage);
}

DEFINE_ENC_CFG_INT_PROPERTY_SETTER(profile, g_profile)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(threads, g_threads)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(w, g_w)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(h, g_h)

/*
 * Method:    codec_enc_cfg_set_error_resilient
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1enc_1cfg_1set_1error_1resilient
    (JNIEnv *env,
     jclass clazz,
     jlong cfg,
     jint flags)
{
    ((vpx_codec_enc_cfg_t *) (intptr_t) cfg)->g_error_resilient
        = (vpx_codec_er_flags_t) flags;
}

DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1target_1bitrate, rc_target_bitrate)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1dropframe_1thresh, rc_dropframe_thresh)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1resize_1allowed, rc_resize_allowed)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1resize_1up_1thresh, rc_resize_up_thresh)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1resize_1down_1thresh, rc_resize_down_thresh)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1end_1usage, rc_end_usage)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1min_1quantizer, rc_min_quantizer)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1max_1quantizer, rc_max_quantizer)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1undershoot_1pct, rc_undershoot_pct)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1overshoot_1pct, rc_overshoot_pct)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1buf_1sz, rc_buf_sz)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1buf_1initial_1sz, rc_buf_initial_sz)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(rc_1buf_1optimal_1sz, rc_buf_optimal_sz)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(kf_1mode, kf_mode)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(kf_1min_1dist, kf_min_dist)
DEFINE_ENC_CFG_INT_PROPERTY_SETTER(kf_1max_1dist, kf_max_dist)

/*
 * Method:    stream_info_malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_stream_1info_1malloc
    (JNIEnv *env,
     jclass clazz)
{
    return (jlong) (intptr_t) malloc(sizeof(vpx_codec_stream_info_t));
}

/*
 * Method:    stream_info_get_w
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_stream_1info_1get_1w
    (JNIEnv *env,
     jclass clazz,
     jlong si)
{
    return (jint) ((vpx_codec_stream_info_t *) (intptr_t) si)->w;
}

/*
 * Method:    stream_info_get_h
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_stream_1info_1get_1h
    (JNIEnv *env,
     jclass clazz,
     jlong si)
{
    return (jint) ((vpx_codec_stream_info_t *) (intptr_t) si)->h;
}

/*
 * Method:    stream_info_get_is_kf
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_stream_1info_1get_1is_1kf
    (JNIEnv *env,
     jclass clazz,
     jlong si)
{
    return (jint) ((vpx_codec_stream_info_t *) (intptr_t) si)->h;
}

/*
 * Method:    codec_peek_stream_info
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1peek_1stream_1info
    (JNIEnv *env,
     jclass clazz,
     jint iface,
     jbyteArray buf,
     jint buf_offset,
     jint buf_size,
     jlong si)
{
    vpx_codec_err_t ret;
    jbyte *buf_ptr = (*env)->GetByteArrayElements(env, buf, NULL);

    ret = vpx_codec_peek_stream_info(GET_INTERFACE(iface),
                                     (uint8_t *) (buf_ptr + buf_offset),
                                     buf_size,
                                     (vpx_codec_stream_info_t *) (intptr_t)si);
    (*env)->ReleaseByteArrayElements(env, buf, buf_ptr, JNI_ABORT);
    return ret;
}

/*
 * Method:    codec_mmap_get_sz
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1mmap_1get_1sz
    (JNIEnv *env,
     jclass clazz,
     jlong map)
{
    return (jlong) ((vpx_codec_mmap_t *) (intptr_t) map)->sz;
}

/*
 * Method:    codec_mmap_set_base
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1mmap_1set_1base
    (JNIEnv *env,
     jclass clazz,
     jlong map,
     jlong base)
{
    ((vpx_codec_mmap_t *) (intptr_t) map)->base = (void *) (intptr_t) base;
}

/*
 * Method:    malloc
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_malloc
    (JNIEnv *env,
     jclass clazz,
     jlong size)
{
    return (jlong) (intptr_t) malloc((size_t) size);
}

/*
 * Method:    free
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_free
    (JNIEnv *env,
     jclass clazz,
     jlong ptr)
{
    free((void *) (intptr_t) ptr);
}

/*
 * Method:    memcpy
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_memcpy
    (JNIEnv *env,
     jclass clazz,
     jbyteArray dstArray,
     jlong src,
     jint n)
{
    jbyte *dst = (*env)->GetByteArrayElements(env, dstArray, NULL);
    memcpy(dst, (char *) (intptr_t) src, n);
    (*env)->ReleaseByteArrayElements(env, dstArray, dst, 0);
}

/*
 * Method:    codec_err_to_string
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_video_VPX_codec_1err_1to_1string
    (JNIEnv *env,
     jclass clazz,
     jint err,
     jbyteArray buf,
     jint buf_size)
{
    const char *err_str = vpx_codec_err_to_string((vpx_codec_err_t) err);
    jbyte *buf_ptr = (*env)->GetByteArrayElements(env, buf, NULL);

    int i;
    for(i = 0; i < buf_size-1 && err_str[i] != '\0'; i++)
        buf_ptr[i] = (jbyte) err_str[i];
    buf_ptr[i] = (jbyte) '\0';

    (*env)->ReleaseByteArrayElements(env, buf, buf_ptr, 0);

    return i;
}

