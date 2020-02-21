/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.codec.audio.speex;

import org.jitsi.utils.*;

/**
 * Provides the interface to the native Speex library.
 *
 * @author Lubomir Marinov
 */
public final class Speex
{
    public static final int SPEEX_GET_FRAME_SIZE = 3;

    public static final int SPEEX_MODEID_NB = 0;

    public static final int SPEEX_MODEID_UWB = 2;

    public static final int SPEEX_MODEID_WB = 1;

    public static final int SPEEX_RESAMPLER_QUALITY_VOIP = 3;

    public static final int SPEEX_SET_ENH = 0;

    public static final int SPEEX_SET_QUALITY = 4;

    public static final int SPEEX_SET_SAMPLING_RATE = 24;

    static
    {
        JNIUtils.loadLibrary("jnspeex", Speex.class);
    }

    public static void assertSpeexIsFunctional()
    {
        speex_lib_get_mode(SPEEX_MODEID_NB);
    }

    public static native void speex_bits_destroy(long bits);

    public static native long speex_bits_init();

    public static native int speex_bits_nbytes(long bits);

    public static native void speex_bits_read_from(
            long bits,
            byte[] bytes, int bytesOffset,
            int len);

    public static native int speex_bits_remaining(long bits);

    public static native void speex_bits_reset(long bits);

    public static native int speex_bits_write(
            long bits,
            byte[] bytes, int bytesOffset,
            int max_len);

    public static native int speex_decode_int(
            long state,
            long bits,
            byte[] out, int byteOffset);

    public static native int speex_decoder_ctl(long state, int request);

    public static native int speex_decoder_ctl(
            long state,
            int request,
            int value);

    public static native void speex_decoder_destroy(long state);

    public static native long speex_decoder_init(long mode);

    public static native int speex_encode_int(
            long state,
            byte[] in, int inOffset,
            long bits);

    public static native int speex_encoder_ctl(long state, int request);

    public static native int speex_encoder_ctl(
            long state,
            int request,
            int value);

    public static native void speex_encoder_destroy(long state);

    public static native long speex_encoder_init(long mode);

    public static native long speex_lib_get_mode(int mode);

    public static native void speex_resampler_destroy(long state);

    public static native long speex_resampler_init(
            int nb_channels,
            int in_rate,
            int out_rate,
            int quality,
            long err);

    public static native int speex_resampler_process_interleaved_int(
            long state,
            byte[] in, int inOffset, int in_len,
            byte[] out, int outOffset, int out_len);

    public static native int speex_resampler_set_rate(
            long state,
            int in_rate,
            int out_rate);

    /**
     * Prevents the creation of <tt>Speex</tt> instances.
     */
    private Speex()
    {
    }
}
