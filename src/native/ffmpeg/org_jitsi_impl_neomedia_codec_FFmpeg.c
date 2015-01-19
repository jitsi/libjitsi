/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_codec_FFmpeg.h"

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <libavutil/avutil.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/avfiltergraph.h>
#include <libavfilter/buffersrc.h>

#ifndef _JITSI_LIBAV_
#include <libavfilter/formats.h> /* ff_default_query_formats, ff_make_format_list, ff_set_common_formats */
#include <libavfilter/internal.h> /* ff_request_frame */
#endif

#include <libswscale/swscale.h>

#define DEFINE_AVCODECCONTEXT_F_PROPERTY_SETTER(name, property) \
    JNIEXPORT void JNICALL \
    Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1##name \
        (JNIEnv *env, jclass clazz, jlong ctx, jfloat property) \
    { \
        ((AVCodecContext *) (intptr_t) ctx)->property = (float) property; \
    }
#define DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(name, property) \
    JNIEXPORT void JNICALL \
    Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1##name \
        (JNIEnv *env, jclass clazz, jlong ctx, jint property) \
    { \
        ((AVCodecContext *) (intptr_t) ctx)->property = (int) property; \
    }

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_av_1free
    (JNIEnv *env, jclass clazz, jlong ptr)
{
    av_free((void *) (intptr_t) ptr);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_av_1malloc
    (JNIEnv *env, jclass clazz, jint size)
{
    return (jlong) (intptr_t) av_malloc((unsigned int) size);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_av_1register_1all
    (JNIEnv *env, jclass clazz)
{
    av_register_all();
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1alloc_1context3
    (JNIEnv *env, jclass clazz, jlong codec)
{
    return
        (jlong) (intptr_t)
            avcodec_alloc_context3((const AVCodec *) (intptr_t) codec);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1alloc_1frame
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) avcodec_alloc_frame();
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avcodec_alloc_packet
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1alloc_1packet
    (JNIEnv *env, jclass clazz, jint size)
{
    AVPacket *pkt = av_malloc(sizeof(AVPacket));

    if (pkt)
    {
        if (av_new_packet(pkt, size))
        {
            av_free(pkt);
            pkt = 0;
        }
    }
    return (jlong) (intptr_t) pkt;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1close
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return (jint) avcodec_close((AVCodecContext *) (intptr_t) ctx);
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avcodec_decode_audio4
 * Signature: (JJ[ZJ)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1decode_1audio4
    (JNIEnv *env, jclass clazz, jlong avctx, jlong frame,
        jbooleanArray got_frame, jlong avpkt)
{
    jint ret;
    int i;

    ret
        = avcodec_decode_audio4(
                (AVCodecContext *) (intptr_t) avctx,
                (AVFrame *) (intptr_t) frame,
                &i,
                (const AVPacket *) (intptr_t) avpkt);

    if (got_frame)
    {
        jboolean b = i ? JNI_TRUE : JNI_FALSE;

        (*env)->SetBooleanArrayRegion(env, got_frame, 0, 1, &b);
    }

    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1decode_1video__JJ_3Z_3BI
    (JNIEnv *env, jclass clazz, jlong ctx, jlong frame,
        jbooleanArray got_picture, jbyteArray buf, jint buf_size)
{
    jint ret;

    if (buf)
    {
        jbyte *buf_ = (*env)->GetByteArrayElements(env, buf, NULL);

        if (buf_)
        {
            int i;
            AVPacket avpkt;

            av_init_packet(&avpkt);
            avpkt.data = (uint8_t *) buf_;
            avpkt.size = (int) buf_size;

            ret
                = avcodec_decode_video2(
                        (AVCodecContext *) (intptr_t) ctx,
                        (AVFrame *) (intptr_t) frame,
                        &i,
                        &avpkt);

            (*env)->ReleaseByteArrayElements(env, buf, buf_, 0);

            if (got_picture)
            {
                jboolean b = i ? JNI_TRUE : JNI_FALSE;

                (*env)->SetBooleanArrayRegion(env, got_picture, 0, 1, &b);
            }
        }
        else
        {
            ret = -1;
        }
    }
    else
    {
        ret = -1;
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1decode_1video__JJJI
    (JNIEnv *env, jclass clazz, jlong ctx, jlong avframe, jlong src,
        jint src_length)
{
    int got_picture = 0;
    AVPacket avpkt;
    int ret;

    av_init_packet(&avpkt);
    avpkt.data = (uint8_t*) (intptr_t) src;
    avpkt.size = (int) src_length;

    ret
        = avcodec_decode_video2(
                (AVCodecContext *) (intptr_t) ctx,
                (AVFrame *) (intptr_t) avframe,
                &got_picture,
                &avpkt);

    return got_picture ? ret : -1;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1encode_1audio
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray buf, jint buf_offset,
        jint buf_size, jbyteArray samples, jint samples_offset)
{
    jint ret;

    if (buf)
    {
        jbyte *buf_ = (*env)->GetByteArrayElements(env, buf, NULL);

        if (buf_)
        {
            jbyte *samples_ = (*env)->GetByteArrayElements(env, samples, NULL);

            if (samples_)
            {
                ret
                    = (jint)
                        avcodec_encode_audio(
                                (AVCodecContext *) (intptr_t) ctx,
                                (uint8_t *) (buf_ + buf_offset),
                                (int) buf_size,
                                (const short *) (samples_ + samples_offset));
                (*env)->ReleaseByteArrayElements(
                        env,
                        samples, samples_,
                        JNI_ABORT);
            }
            else
            {
                ret = -1;
            }
            (*env)->ReleaseByteArrayElements(env, buf, buf_, 0);
        }
        else
        {
            ret = -1;
        }
    }
    else
    {
        ret = -1;
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1encode_1video
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray buf, jint buf_size,
        jlong frame)
{
    jint ret;

    if (buf)
    {
        jbyte *buf_ = (*env)->GetByteArrayElements(env, buf, NULL);

        if (buf_)
        {
            ret
                = (jint)
                    avcodec_encode_video(
                            (AVCodecContext *) (intptr_t) ctx,
                            (uint8_t *) buf_,
                            (int) buf_size,
                            (const AVFrame *) (intptr_t) frame);
            (*env)->ReleaseByteArrayElements(env, buf, buf_, 0);
        }
        else
        {
            ret = -1;
        }
    }
    else
    {
        ret = -1;
    }
    return ret;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1find_1decoder
    (JNIEnv *env, jclass clazz, jint id)
{
    return (jlong) (intptr_t) avcodec_find_decoder((enum CodecID) id);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1find_1encoder
    (JNIEnv *env, jclass clazz, jint id)
{
    return (jlong) (intptr_t) avcodec_find_encoder((enum CodecID) id);
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avcodec_free_packet
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1free_1packet
    (JNIEnv *env, jclass clazz, jlong pkt)
{
    if (pkt)
    {
        AVPacket *pkt_ = (AVPacket *) (intptr_t) pkt;

        av_free_packet(pkt_);
        av_free(pkt_);
    }
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1open2
    (JNIEnv *env, jclass clazz, jlong ctx, jlong codec, jobjectArray options)
{
    AVDictionary *options_ = NULL;
    int ret = 0;

    if (options)
    {
        jsize length = (*env)->GetArrayLength(env, options);

        if (length)
        {
            if (length % 2)
            {
                ret = AVERROR(EINVAL);
            }
            else
            {
                jsize i = 0;

                while ((0 <= ret) && (i < length))
                {
                    jstring key
                        = (jstring)
                            (*env)->GetObjectArrayElement(env, options, i++);
                    const char *key_;

                    if (key)
                    {
                        key_ = (*env)->GetStringUTFChars(env, key, NULL);
                        if (!key_)
                            ret = AVERROR(ENOMEM);
                    }
                    else
                    {
                        key_ = NULL;
                    }
                    if (0 <= ret)
                    {
                        jstring value
                            = (jstring)
                                (*env)->GetObjectArrayElement(
                                        env,
                                        options,
                                        i++);
                        const char *value_;

                        if (value)
                        {
                            value_
                                = (*env)->GetStringUTFChars(env, value, NULL);
                            if (!value_)
                                ret = AVERROR(ENOMEM);
                        }
                        else
                        {
                            value_ = NULL;
                        }
                        if (0 <= ret)
                        {
                            ret = av_dict_set(&options_, key_, value_, 0);
                            (*env)->ReleaseStringUTFChars(env, value, value_);
                        }
                        (*env)->ReleaseStringUTFChars(env, key, key_);
                    }
                }
            }
        }
    }
    if (0 <= ret)
    {
        ret
            = avcodec_open2(
                    (AVCodecContext *) (intptr_t) ctx,
                    (AVCodec *) (intptr_t) codec,
                    &options_);
    }
    if (options_)
        av_dict_free(&options_);
    return ret;
}

/**
 * Implements a log callback which does not log anything and thus prevents logs
 * from appearing on stdout and/or stderr.
 */
static void
null_log_callback(void* ptr, int level, const char* fmt, va_list vl)
{
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodec_1register_1all
    (JNIEnv *env, jclass clazz)
{
    avcodec_register_all();
    av_log_set_callback(null_log_callback);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1add_1flags
    (JNIEnv *env, jclass clazz, jlong ctx, jint flags)
{
    ((AVCodecContext *) (intptr_t) ctx)->flags |= (int) flags;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1add_1flags2
    (JNIEnv *env, jclass clazz, jlong ctx, jint flags2)
{
    ((AVCodecContext *) (intptr_t) ctx)->flags2 |= (int) flags2;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1get_1frame_1size
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return (jint) (((AVCodecContext *) (intptr_t) ctx)->frame_size);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1get_1height
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return (jint) (((AVCodecContext *) (intptr_t) ctx)->height);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1get_1pix_1fmt
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return (jint) (((AVCodecContext *) (intptr_t) ctx)->pix_fmt);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1get_1width
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return (jint) (((AVCodecContext *) (intptr_t) ctx)->width);
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(b_1frame_1strategy, b_frame_strategy)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(bit_1rate, bit_rate)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(bit_1rate_1tolerance, bit_rate_tolerance)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(channels, channels)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(chromaoffset, chromaoffset)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(gop_1size, gop_size)

DEFINE_AVCODECCONTEXT_F_PROPERTY_SETTER(i_1quant_1factor, i_quant_factor)

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(keyint_1min, keyint_min)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(max_1b_1frames, max_b_frames)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(mb_1decision, mb_decision)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(me_1cmp, me_cmp)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(me_1method, me_method)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(me_1range, me_range)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(me_1subpel_1quality, me_subpel_quality)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(pix_1fmt, pix_fmt)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(profile, profile)

DEFINE_AVCODECCONTEXT_F_PROPERTY_SETTER(qcompress, qcompress)

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1quantizer
    (JNIEnv *env, jclass clazz, jlong ctx, jint qmin, jint qmax,
        jint max_qdiff)
{
    AVCodecContext *ctx_ = (AVCodecContext *) (intptr_t) ctx;

    ctx_->qmin = (int) qmin;
    ctx_->qmax = (int) qmax;
    ctx_->max_qdiff = (int) max_qdiff;
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(rc_1buffer_1size, rc_buffer_size)

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1rc_1eq
    (JNIEnv *env, jclass clazz, jlong ctx, jstring rc_eq)
{
    char *s;

    if (rc_eq)
    {
        const char *js = (*env)->GetStringUTFChars(env, rc_eq, NULL);

        if (js)
        {
            s = av_strdup(js);
            (*env)->ReleaseStringUTFChars(env, rc_eq, js);
        }
        else
        {
            s = NULL;
        }
    }
    else
    {
        s = NULL;
    }
    ((AVCodecContext *) (intptr_t) ctx)->rc_eq = s;
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(rc_1max_1rate, rc_max_rate)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(refs, refs)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(rtp_1payload_1size, rtp_payload_size)

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1sample_1aspect_1ratio
    (JNIEnv *env, jclass clazz, jlong ctx, jint num, jint den)
{
    AVRational *sample_aspect_ratio
        = &(((AVCodecContext *) (intptr_t) ctx)->sample_aspect_ratio);

    sample_aspect_ratio->num = (int) num;
    sample_aspect_ratio->den = (int) den;
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(sample_1fmt, sample_fmt)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(sample_1rate, sample_rate)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(scenechange_1threshold, scenechange_threshold)

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1size
    (JNIEnv *env, jclass clazz, jlong ctx, jint width, jint height)
{
    AVCodecContext *ctx_ = (AVCodecContext *) (intptr_t) ctx;

    ctx_->width = (int) width;
    ctx_->height = (int) height;
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(thread_1count, thread_count)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(ticks_1per_1frame, ticks_per_frame)

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avcodeccontext_1set_1time_1base
    (JNIEnv *env, jclass clazz, jlong ctx, jint num, jint den)
{
    AVRational *time_base = &(((AVCodecContext *) (intptr_t) ctx)->time_base);

    time_base->num = (int) num;
    time_base->den = (int) den;
}

DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(trellis, trellis)
DEFINE_AVCODECCONTEXT_I_PROPERTY_SETTER(workaround_1bugs, workaround_bugs)

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1graph_1alloc
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) avfilter_graph_alloc();
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1graph_1config
    (JNIEnv *env, jclass clazz, jlong graph, jlong log_ctx)
{
    return
        (jint)
            avfilter_graph_config(
                    (AVFilterGraph *) (intptr_t) graph,
                    (AVClass *) (intptr_t) log_ctx);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1graph_1free
    (JNIEnv *env, jclass clazz, jlong graph)
{
    AVFilterGraph *graph_ = (AVFilterGraph *) (intptr_t) graph;

    avfilter_graph_free(&graph_);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1graph_1get_1filter
    (JNIEnv *env, jclass clazz, jlong graph, jstring name)
{
    const char *name_ = (*env)->GetStringUTFChars(env, name, NULL);
    AVFilterContext *filter;

    if (name_)
    {
        filter
            = avfilter_graph_get_filter(
                    (AVFilterGraph *) (intptr_t) graph,
                    (char *) name_);
        (*env)->ReleaseStringUTFChars(env, name, name_);
    }
    else
    {
        filter = NULL;
    }
    return (jlong) (intptr_t) filter;
}

static int
ffsink_end_frame(AVFilterLink *link)
{
    if (link->cur_buf)
        link->dst->priv = avfilter_ref_buffer(link->cur_buf, ~0);
    return 0;
}

static int
ffsink_query_formats(AVFilterContext *ctx)
{
    AVFilterContext *src = ctx;
    int err;

    /* Find buffer. */
#ifdef _JITSI_LIBAV_
    while (src && src->input_count && src->inputs)
#else
    while (src && src->nb_inputs && src->inputs)
#endif
    {
        AVFilterLink *link = src->inputs[0];

        if (link)
            src = link->src;
        else
            break;
    }

    /* Make ffsink output in the format in which buffer inputs. */
    if (src)
    {
        const int pix_fmts[] = { src->outputs[0]->in_formats->formats[0], -1 };

#ifdef _JITSI_LIBAV_
        avfilter_set_common_formats(ctx, ff_make_format_list(pix_fmts));
#else
        ff_set_common_formats(ctx, ff_make_format_list(pix_fmts));
#endif

        err = 0;
    }
    else
#ifdef _JITSI_LIBAV_
        err = query_formats(ctx);
#else
        err = ff_default_query_formats(ctx);
#endif

    return err;
}

static void
ffsink_uninit(AVFilterContext *ctx)
{
    ctx->priv = NULL;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1graph_1parse
    (JNIEnv *env, jclass clazz, jlong graph, jstring filters, jlong inputs,
        jlong outputs, jlong log_ctx)
{
    const char *filters_ = (*env)->GetStringUTFChars(env, filters, NULL);
    int ret;

    if (filters_)
    {
        AVFilterGraph *graph_ = (AVFilterGraph *) (intptr_t) graph;

        ret
            = avfilter_graph_parse(
                    graph_,
                    filters_,
                    (AVFilterInOut **) (intptr_t) inputs,
                    (AVFilterInOut **) (intptr_t) outputs,
                    (AVClass *) (intptr_t) log_ctx);

        /*
         * FIXME The implementation at the time of this writing presumes that
         * the first filter is buffer, the last filter is nullsink meant to be
         * ffsink and the ffsink is expected to output in the format in which
         * the buffer inputs.
         */
        if (0 == ret)
        {
            /* Turn nullsink into ffsink. */
            unsigned filterCount = graph_->filter_count;

            if (filterCount)
            {
                AVFilterContext *ffsink = graph_->filters[filterCount - 1];

                /*
                 * Make sure query_format of ffsink outputs in the format in
                 * which buffer inputs. Otherwise, the output format may end up
                 * different on the C and Java sides.
                 */
                ffsink->filter->uninit = ffsink_uninit;
                ffsink->priv = NULL;
                ffsink->filter->query_formats = ffsink_query_formats;

                ffsink->input_pads->end_frame = ffsink_end_frame;
                ffsink->input_pads->min_perms = AV_PERM_READ;
                ffsink->input_pads->start_frame = NULL;
            }
        }

        (*env)->ReleaseStringUTFChars(env, filters, filters_);
    }
    else
        ret = AVERROR(ENOMEM);
    return (jint) ret;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1register_1all
    (JNIEnv *env, jclass clazz)
{
    avfilter_register_all();
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avfilter_1unref_1buffer
    (JNIEnv *env, jclass clazz, jlong ref)
{
    avfilter_unref_buffer((AVFilterBufferRef *) (intptr_t) ref);
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avframe_get_data0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1get_1data0
    (JNIEnv *env, jclass clazz, jlong frame)
{
    return (jlong) (intptr_t) (((AVFrame *) (intptr_t) frame)->data[0]);
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avframe_get_linesize0
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1get_1linesize0
    (JNIEnv *env, jclass clazz, jlong frame)
{
    return ((AVFrame *) (intptr_t) frame)->linesize[0];
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1get_1pts
    (JNIEnv *env, jclass clazz, jlong frame)
{
    return (jlong) (((AVFrame *) (intptr_t) frame)->pts);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1set_1data
    (JNIEnv *env, jclass clazz,
    jlong frame, jlong data0, jlong offset1, jlong offset2)
{
    AVFrame *frame_ = (AVFrame *) (intptr_t) frame;

    frame_->data[0] = (uint8_t *) (intptr_t) data0;
    frame_->data[1] = frame_->data[0] + offset1;
    frame_->data[2] = frame_->data[1] + offset2;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1set_1key_1frame
    (JNIEnv *env, jclass clazz, jlong frame, jboolean key_frame)
{
    AVFrame *frame_ = (AVFrame *) (intptr_t) frame;

    if (JNI_TRUE == key_frame)
    {
        frame_->key_frame = 1;
        frame_->pict_type = AV_PICTURE_TYPE_I;
    }
    else
    {
        frame_->key_frame = 0;
        frame_->pict_type = 0;
    }
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avframe_1set_1linesize
    (JNIEnv *env, jclass clazz, jlong frame, jint linesize0,
        jint linesize1, jint linesize2)
{
    AVFrame *frame_ = (AVFrame *) (intptr_t) frame;

    frame_->linesize[0] = (int) linesize0;
    frame_->linesize[1] = (int) linesize1;
    frame_->linesize[2] = (int) linesize2;
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    avpacket_set_data
 * Signature: (J[BII)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avpacket_1set_1data
    (JNIEnv *env, jclass clazz, jlong pkt, jbyteArray data, jint offset,
        jint length)
{
    jbyte *data_;
    jboolean ok;

    if (data)
    {
        data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
        ok = data_ ? JNI_TRUE : JNI_FALSE;
    }
    else
    {
        data_ = NULL;
        ok = JNI_TRUE;
    }
    if (JNI_TRUE == ok)
    {
        int delta;
        AVPacket *pkt_ = (AVPacket *) (intptr_t) pkt;

        delta = length - pkt_->size;
        if (delta > 0)
        {
            if (av_grow_packet(pkt_, delta) != 0)
                ok = JNI_FALSE;
        }
        else if (delta < 0)
        {
            av_shrink_packet(pkt_, length);
        }
        if (JNI_TRUE == ok)
            memcpy(pkt_->data, data_ + offset, length);
        if (data_)
            (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
    }
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_avpicture_1fill
    (JNIEnv *env, jclass clazz, jlong picture, jlong ptr, jint pix_fmt,
        jint width, jint height)
{
    return
        (jint)
            avpicture_fill(
                    (AVPicture *) (intptr_t) picture,
                    (uint8_t *) (intptr_t) ptr,
                    (int) pix_fmt,
                    (int) width, (int) height);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_get_1filtered_1video_1frame
    (JNIEnv *env, jclass clazz, jlong input, jint width, jint height,
        jint pixFmt, jlong buffer, jlong ffsink, jlong output)
{
    AVFrame *input_ = (AVFrame *) (intptr_t) input;
    AVFilterContext *buffer_ = (AVFilterContext *) (intptr_t) buffer;
    AVFilterBufferRef *ref = NULL;

    input_->width = width;
    input_->height = height;
    input_->format = pixFmt;
    if (av_buffersrc_write_frame(buffer_, input_) == 0)
    {
        AVFilterContext *ffsink_ = (AVFilterContext *) (intptr_t) ffsink;

        if (ff_request_frame(ffsink_->inputs[0]) == 0)
        {
            ref = (AVFilterBufferRef *) (ffsink_->priv);
            if (ref)
            {
                AVFrame *output_ = (AVFrame *) (intptr_t) output;

                /*
                 * The data of cur_buf will be returned into output so it needs
                 * to exist at least while output needs it. So take ownership of
                 * cur_buf and the user of output will unref it when they are
                 * done with output.
                 */
                ffsink_->priv = NULL;

                memcpy(output_->data, ref->data, sizeof(output_->data));
                memcpy(
                    output_->linesize,
                    ref->linesize,
                    sizeof(output_->linesize));
                output_->interlaced_frame = ref->video->interlaced;
                output_->top_field_first = ref->video->top_field_first;
            }
        }
    }
    return (jlong) (intptr_t) ref;
}

/*
 * Class:     org_jitsi_impl_neomedia_codec_FFmpeg
 * Method:    memcpy
 * Signature: ([BIIJ)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_memcpy___3BIIJ
    (JNIEnv *env, jclass clazz, jbyteArray dst, jint dst_offset,
        jint dst_length, jlong src)
{
    (*env)->SetByteArrayRegion(
            env,
            dst, dst_offset, dst_length,
            (jbyte *) (intptr_t) src);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_memcpy___3IIIJ
    (JNIEnv *env, jclass clazz, jintArray dst, jint dst_offset, jint dst_length,
        jlong src)
{
    (*env)->SetIntArrayRegion(
            env,
            dst, dst_offset, dst_length,
            (jint *) (intptr_t) src);
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_memcpy__J_3BII
    (JNIEnv *env, jclass clazz, jlong dst, jbyteArray src, jint src_offset,
        jint src_length)
{
    (*env)->GetByteArrayRegion(
            env,
            src, src_offset, src_length,
            (jbyte *) (intptr_t) dst);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_PIX_1FMT_1BGR32
    (JNIEnv *env, jclass clazz)
{
    return PIX_FMT_BGR32;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_PIX_1FMT_1BGR32_11
    (JNIEnv *env, jclass clazz)
{
    return PIX_FMT_BGR32_1;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_PIX_1FMT_1RGB24
    (JNIEnv *env, jclass clazz)
{
    uint32_t test = 1;
    int little_endian = *((uint8_t*) &test);

    return little_endian ? PIX_FMT_BGR24 : PIX_FMT_RGB24;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_PIX_1FMT_1RGB32
    (JNIEnv *env, jclass clazz)
{
    return PIX_FMT_RGB32;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_PIX_1FMT_1RGB32_11
    (JNIEnv *env, jclass clazz)
{
    return PIX_FMT_RGB32_1;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_sws_1freeContext
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    sws_freeContext((struct SwsContext *) (intptr_t) ctx);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_sws_1getCachedContext
    (JNIEnv *env, jclass clazz, jlong ctx, jint srcW, jint srcH, jint srcFormat,
        jint dstW, jint dstH, jint dstFormat, jint flags)
{
    return
        (jlong) (intptr_t)
            sws_getCachedContext(
                (struct SwsContext *) (intptr_t) ctx,
                (int) srcW, (int) srcH, (enum PixelFormat) srcFormat,
                (int) dstW, (int) dstH, (enum PixelFormat) dstFormat,
                (int) flags,
                NULL, NULL, NULL);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_sws_1scale__JJIILjava_lang_Object_2III
    (JNIEnv *env, jclass clazz, jlong ctx, jlong src, jint srcSliceY,
        jint srcSliceH, jobject dst, jint dstFormat, jint dstW, jint dstH)
{
    AVPicture *srcPicture;
    uint8_t *dst_;
    int ret;

    srcPicture = (AVPicture *) (intptr_t) src;
    dst_ = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_)
    {
        AVPicture dstPicture;

        /* Turn the bytes into an AVPicture. */
        avpicture_fill(
                &dstPicture,
                dst_, (int) dstFormat, (int) dstW, (int) dstH);
        ret
            = sws_scale(
                    (struct SwsContext *) (intptr_t) ctx,
                    (const uint8_t * const *) srcPicture->data,
                    (int *) srcPicture->linesize,
                    (int) srcSliceY,
                    (int) srcSliceH,
                    (uint8_t **) dstPicture.data,
                    (int *) dstPicture.linesize);
        (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_, 0);
    }
    else
    {
        ret = -1;
    }
    return (jint) ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_FFmpeg_sws_1scale__JLjava_lang_Object_2IIIIILjava_lang_Object_2III
    (JNIEnv *env, jclass class, jlong ctx, jobject src, jint srcFormat,
        jint srcW, jint srcH, jint srcSliceY, jint srcSliceH, jobject dst,
        jint dstFormat, jint dstW, jint dstH)
{
    uint8_t *src_;
    jint ret;

    src_ = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_)
    {
        AVPicture srcPicture;

        avpicture_fill(
                &srcPicture,
                src_, (int) srcFormat, (int) srcW, (int) srcH);
        ret
            = Java_org_jitsi_impl_neomedia_codec_FFmpeg_sws_1scale__JJIILjava_lang_Object_2III(
                    env,
                    class,
                    ctx,
                    (jlong) (intptr_t) &srcPicture, srcSliceY, srcSliceH,
                    dst, dstFormat, dstW, dstH);
        (*env)->ReleasePrimitiveArrayCritical(env, src, src_, 0);
    }
    else
    {
        ret = -1;
    }
    return ret;
}
