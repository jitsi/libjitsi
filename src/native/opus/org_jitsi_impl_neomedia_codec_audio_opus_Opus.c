/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "org_jitsi_impl_neomedia_codec_audio_opus_Opus.h"

#include <stdint.h>
#include <opus.h>

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decode
    (JNIEnv *env, jclass clazz, jlong decoder, jbyteArray input,
        jint inputOffset, jint inputLength, jbyteArray output,
        jint outputOffset, jint outputFrameSize, jint decodeFEC)
{
    int ret;

    if (output)
    {
        jbyte *input_;

        if (input && inputLength)
        {
            input_ = (*env)->GetPrimitiveArrayCritical(env, input, 0);
            ret = input_ ? OPUS_OK : OPUS_ALLOC_FAIL;
        }
        else
        {
            input_ = 0;
            ret = OPUS_OK;
        }
        if (OPUS_OK == ret)
        {
            jbyte *output_ = (*env)->GetPrimitiveArrayCritical(env, output, 0);

            if (output_)
            {
                ret
                    = opus_decode(
                            (OpusDecoder *) (intptr_t) decoder,
                            (unsigned char *)
                                (input_ ? (input_ + inputOffset) : NULL),
                            inputLength,
                            (opus_int16 *) (output_ + outputOffset),
                            outputFrameSize,
                            decodeFEC);
                (*env)->ReleasePrimitiveArrayCritical(env, output, output_, 0);
            }
            else
                ret = OPUS_ALLOC_FAIL;
            if (input_)
            {
                (*env)->ReleasePrimitiveArrayCritical(
                        env,
                        input, input_, JNI_ABORT);
            }
        }
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1create
    (JNIEnv *env, jclass clazz, jint Fs, jint channels)
{
    int error;
    OpusDecoder *decoder = opus_decoder_create(Fs, channels, &error);

    if (OPUS_OK != error)
        decoder = 0;
    return (jlong) (intptr_t) decoder;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1destroy
    (JNIEnv *env, jclass clazz, jlong decoder)
{
    opus_decoder_destroy((OpusDecoder *) (intptr_t) decoder);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1get_1nb_1samples
    (JNIEnv *env, jclass clazz, jlong decoder, jbyteArray packet, jint offset,
        jint length)
{
    int ret;

    if (packet)
    {
        jbyte *packet_ = (*env)->GetPrimitiveArrayCritical(env, packet, NULL);

        if (packet_)
        {
            ret
                = opus_decoder_get_nb_samples(
                        (OpusDecoder *) (intptr_t) decoder,
                        (unsigned char *) (packet_ + offset),
                        length);
            (*env)->ReleasePrimitiveArrayCritical(
                    env,
                    packet, packet_, JNI_ABORT);
        }
        else
            ret = OPUS_ALLOC_FAIL;
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1get_1size
    (JNIEnv *env, jclass clazz, jint channels)
{
    return opus_decoder_get_size(channels);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encode
    (JNIEnv *env, jclass clazz, jlong encoder, jbyteArray input,
        jint inputOffset, jint inputFrameSize, jbyteArray output,
        jint outputOffset, jint outputLength)
{
    int ret;

    if (input && output)
    {
        jbyte *input_ = (*env)->GetPrimitiveArrayCritical(env, input, 0);

        if (input_)
        {
            jbyte *output_ = (*env)->GetPrimitiveArrayCritical(env, output, 0);

            if (output_)
            {
                ret
                    = opus_encode(
                            (OpusEncoder *) (intptr_t) encoder,
                            (opus_int16 *) (input_ + inputOffset),
                            inputFrameSize,
                            (unsigned char *) (output_ + outputOffset),
                            outputLength);
                (*env)->ReleasePrimitiveArrayCritical(env, output, output_, 0);
            }
            else
                ret = OPUS_ALLOC_FAIL;
            (*env)->ReleasePrimitiveArrayCritical(
                    env,
                    input, input_, JNI_ABORT);
        }
        else
            ret = OPUS_ALLOC_FAIL;
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1create
    (JNIEnv *env, jclass clazz, jint Fs, jint channels)
{
    int error;
    OpusEncoder *encoder
        = opus_encoder_create(Fs, channels, OPUS_APPLICATION_VOIP, &error);

    if (OPUS_OK != error)
        encoder = 0;
    return (jlong) (intptr_t) encoder;
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1destroy
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_encoder_destroy((OpusEncoder *) (intptr_t) encoder);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1bandwidth
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_BANDWIDTH(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1bitrate
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_BITRATE(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1dtx
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_DTX(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1size
    (JNIEnv *enc, jclass clazz, jint channels)
{
    return opus_encoder_get_size(channels);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1vbr
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_VBR(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1vbr_1constraint
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_VBR_CONSTRAINT(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1inband_1fec
    (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_int32 x;
    int ret
        = opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_GET_INBAND_FEC(&x));

    return (OPUS_OK == ret) ? x : ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1bandwidth
    (JNIEnv *env, jclass clazz, jlong encoder, jint bandwidth)
{
    opus_int32 x = bandwidth;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_BANDWIDTH(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1bitrate
    (JNIEnv *env, jclass clazz, jlong encoder, jint bitrate)
{
    opus_int32 x = bitrate;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_BITRATE(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1complexity
    (JNIEnv *env, jclass clazz, jlong encoder, jint complexity)
{
    opus_int32 x = complexity;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_COMPLEXITY(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1dtx
    (JNIEnv *env, jclass clazz, jlong encoder, jint dtx)
{
    opus_int32 x = dtx;

    return
        opus_encoder_ctl((OpusEncoder *) (intptr_t) encoder, OPUS_SET_DTX(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1force_1channels
    (JNIEnv *env, jclass clazz, jlong encoder, jint forcechannels)
{
    opus_int32 x = forcechannels;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_FORCE_CHANNELS(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1inband_1fec
    (JNIEnv *env, jclass clazz, jlong encoder, jint inbandFEC)
{
    opus_int32 x = inbandFEC;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_INBAND_FEC(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1max_1bandwidth
    (JNIEnv *env, jclass clazz, jlong encoder, jint maxBandwidth)
{
    opus_int32 x = maxBandwidth;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_MAX_BANDWIDTH(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1packet_1loss_1perc
    (JNIEnv *env, jclass clazz, jlong encoder, jint packetLossPerc)
{
    opus_int32 x = packetLossPerc;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_PACKET_LOSS_PERC(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1vbr
    (JNIEnv *env, jclass clazz, jlong encoder, jint vbr)
{
    opus_int32 x = vbr;

    return
        opus_encoder_ctl((OpusEncoder *) (intptr_t) encoder, OPUS_SET_VBR(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1vbr_1constraint
  (JNIEnv *env, jclass clazz, jlong encoder, jint cvbr)
{
    opus_int32 x = cvbr;

    return
        opus_encoder_ctl(
                (OpusEncoder *) (intptr_t) encoder,
                OPUS_SET_VBR_CONSTRAINT(x));
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1bandwidth
    (JNIEnv *env, jclass clazz, jbyteArray data, jint offset)
{
    int ret;

    if (data)
    {
        jbyte *data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);

        if (data_)
        {
            ret = opus_packet_get_bandwidth((unsigned char *) (data_ + offset));
            (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
        }
        else
            ret = OPUS_ALLOC_FAIL;
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1nb_1channels
    (JNIEnv *env, jclass clazz, jbyteArray data, jint offset)
{
    int ret;

    if (data)
    {
        jbyte *data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);

        if (data_)
        {
            ret
                = opus_packet_get_nb_channels(
                        (unsigned char *) (data_ + offset));
            (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
        }
        else
            ret = OPUS_ALLOC_FAIL;
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1nb_1frames
    (JNIEnv *env, jclass clazz, jbyteArray packet, jint offset, jint length)
{
    int ret;

    if (packet)
    {
        jbyte *packet_ = (*env)->GetPrimitiveArrayCritical(env, packet, NULL);

        if (packet_)
        {
            ret
                = opus_packet_get_nb_frames(
                        (unsigned char *) (packet_ + offset),
                        length);
            (*env)->ReleasePrimitiveArrayCritical(
                    env,
                    packet, packet_, JNI_ABORT);
        }
        else
            ret = OPUS_ALLOC_FAIL;
    }
    else
        ret = OPUS_BAD_ARG;
    return ret;
}
