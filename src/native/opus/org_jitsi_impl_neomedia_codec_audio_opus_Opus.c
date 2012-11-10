#include "org_jitsi_impl_neomedia_codec_audio_opus_Opus.h"
#include <stdint.h>
#include <opus.h>

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1size
  (JNIEnv *enc, jclass clazz, jint channels)
{
    return opus_encoder_get_size(channels);
}

JNIEXPORT jlong JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1create
  (JNIEnv *env, jclass clazz, jint Fs, jint channels)
{
    int e;
    OpusEncoder *enc = opus_encoder_create(Fs, channels, OPUS_APPLICATION_VOIP, &e);

    if(e != OPUS_OK)
    {
        return (jlong) 0;
    }

    return (jlong) (intptr_t) enc;
}

JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1destroy
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    opus_encoder_destroy((OpusEncoder *)(intptr_t) encoder);
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1bitrate
  (JNIEnv *env, jclass clazz, jlong encoder, jint bitrate)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_BITRATE(bitrate));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1bitrate
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    int x, ret;
    ret = opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_GET_BITRATE(&x));
    if(ret < 0)
        return ret;
    return x;
}


JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1bandwidth
  (JNIEnv *env, jclass clazz, jlong encoder, jint bandwidth)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_BANDWIDTH(bandwidth));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1bandwidth
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    int x, ret;
    ret = opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_GET_BANDWIDTH(&x));
    if(ret<0) return ret;
    return x;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1vbr
  (JNIEnv *env, jclass clazz, jlong encoder, jint use_vbr)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_VBR(use_vbr));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1vbr
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    int x, ret;
    ret = opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_GET_VBR(&x));
    if(ret<0) return ret;
    return x;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1vbr_1constraint
  (JNIEnv *env, jclass clazz, jlong encoder, jint use_cvbr)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_VBR_CONSTRAINT(use_cvbr));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1vbr_1constraint
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    int x, ret;
    ret = opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_GET_VBR_CONSTRAINT(&x));
    if(ret<0) return ret;
    return x;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1complexity
  (JNIEnv *env, jclass clazz, jlong encoder, jint complexity)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_COMPLEXITY(complexity));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1inband_1fec
  (JNIEnv *env, jclass clazz, jlong encoder, jint use_inband_fec)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_INBAND_FEC(use_inband_fec));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1force_1channels
  (JNIEnv *env, jclass clazz, jlong encoder, jint forcechannels)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_FORCE_CHANNELS(forcechannels));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1dtx
  (JNIEnv *env, jclass clazz, jlong encoder, jint use_dtx)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_DTX(use_dtx));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1get_1dtx
  (JNIEnv *env, jclass clazz, jlong encoder)
{
    int x, ret;
    ret = opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_GET_DTX(&x));
    if(ret<0) return ret;
    return x;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1packet_1loss_1perc
  (JNIEnv *env, jclass clazz, jlong encoder, jint percentage)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_PACKET_LOSS_PERC(percentage));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encoder_1set_1max_1bandwidth
  (JNIEnv *env, jclass clazz, jlong encoder, jint max_bandwidth)
{
    return (jint) opus_encoder_ctl((OpusEncoder *)(intptr_t)encoder, OPUS_SET_MAX_BANDWIDTH(max_bandwidth));
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_encode
  (JNIEnv *env, jclass clazz, jlong encoder, jbyteArray input, jint inputOffset, jint frameSize, jbyteArray output, jint outputSize)
{
    jbyte *inputPtr = (*env)->GetByteArrayElements(env, input, NULL);
    jbyte *outputPtr = (*env)->GetByteArrayElements(env, output, NULL);
    jint ret;

    if (inputPtr && outputPtr)
    {
        ret = opus_encode((OpusEncoder *)(intptr_t)encoder,
                (opus_int16 *) (inputPtr + inputOffset),
                (int) frameSize,
                (unsigned char *)outputPtr,
                (int) outputSize);
    }
    else
        ret = 0;

    if(inputPtr)
        (*env)->ReleaseByteArrayElements(env, input, inputPtr, JNI_ABORT);
    if(outputPtr)
        (*env)->ReleaseByteArrayElements(env, output, outputPtr, 0);

    return ret;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1get_1size
  (JNIEnv *env, jclass clazz, jint channels)
{
    return opus_decoder_get_size(channels);
}

JNIEXPORT jlong JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1create
  (JNIEnv *env, jclass clazz, jint Fs, jint channels)
{
    int e;
    OpusDecoder *decoder = opus_decoder_create(Fs, channels, &e);

    if(e != OPUS_OK)
    {
        return (jlong) 0;
    }
    return (jlong)(intptr_t) decoder;
}

JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1destroy
  (JNIEnv *env, jclass clazz, jlong decoder)
{
    opus_decoder_destroy((OpusDecoder *)(intptr_t)decoder);
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decode
  (JNIEnv *env, jclass clazz, jlong decoder, jbyteArray input, jint inputOffset, jint inputSize, jbyteArray output, jint outputSize, jint decodeFEC)
{
    jbyte *inputPtr = (*env)->GetByteArrayElements(env, input, NULL);
    jbyte *outputPtr = (*env)->GetByteArrayElements(env, output, NULL);
    jint ret;

    if (inputPtr && outputPtr)
    {
        ret = opus_decode((OpusDecoder *)(intptr_t)decoder,
                (unsigned char *) ((char *)inputPtr + inputOffset),
        (int) inputSize,
                (opus_int16 *)outputPtr,
        (int) outputSize,
        (int) decodeFEC);
    }
    else
        ret = 0;

    if(inputPtr)
        (*env)->ReleaseByteArrayElements(env, input, inputPtr, JNI_ABORT);
    if(outputPtr)
        (*env)->ReleaseByteArrayElements(env, output, outputPtr, 0);

    return ret;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1bandwidth
  (JNIEnv *env, jclass clazz, jbyteArray packet, jint offset)
{
    jbyte *packetPtr = (*env)->GetByteArrayElements(env, packet, NULL);
    jint bandwidth = 0;
    if(packetPtr){
        bandwidth = (jint) opus_packet_get_bandwidth((unsigned char *)packetPtr+offset);
    (*env)->ReleaseByteArrayElements(env, packet, packetPtr, JNI_ABORT);
    }
    return bandwidth;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1nb_1channels
  (JNIEnv *env, jclass clazz, jbyteArray packet, jint offset)
{
    jbyte *packetPtr = (*env)->GetByteArrayElements(env, packet, NULL);
    jint channels = 0;
    if(packetPtr){
        channels = (jint) opus_packet_get_nb_channels((unsigned char *)packetPtr+offset);
    (*env)->ReleaseByteArrayElements(env, packet, packetPtr, JNI_ABORT);
    }
    return channels;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_packet_1get_1nb_1frames
  (JNIEnv *env, jclass clazz, jbyteArray packet, jint offset, jint len)
{
    jbyte *packetPtr = (*env)->GetByteArrayElements(env, packet, NULL);
    jint frames = 0;
    if(packetPtr){
        frames = (jint) opus_packet_get_nb_frames((unsigned char *)packetPtr+offset, len);
    (*env)->ReleaseByteArrayElements(env, packet, packetPtr, JNI_ABORT);
    }
    return frames;
}

JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_codec_audio_opus_Opus_decoder_1get_1nb_1samples
  (JNIEnv *env, jclass clazz, jlong decoder, jbyteArray packet, jint offset, jint len)
{
    int samples= 0;
    jbyte *packetPtr = (*env)->GetByteArrayElements(env, packet, NULL);
    if(decoder && packetPtr)
    {
        samples = opus_decoder_get_nb_samples((OpusDecoder*)(intptr_t)decoder, (unsigned char *)packetPtr+offset, len);
    }
    if(packetPtr)
    (*env)->ReleaseByteArrayElements(env, packet, packetPtr, JNI_ABORT);
    return samples;
}
