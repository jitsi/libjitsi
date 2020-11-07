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
package org.jitsi.impl.neomedia.codec.video;

import org.jitsi.utils.*;

/**
 * A wrapper for the libvpx native library.
 * See {@link "http://www.webmproject.org/docs/"}
 *
 * @author Boris Grozev
 */
public class VPX {
    /**
     * Operation completed without error.
     * Corresponds to <tt>VPX_CODEC_OK</tt> from <tt>vpx/vpx_codec.h</tt>
     */
    public static final int CODEC_OK = 0;

    /**
     * An iterator reached the end of list.
     * Corresponds to <tt>VPX_CODEC_LIST_END</tt> from <tt>vpx/vpx_codec.h</tt>
     */
    public static final int CODEC_LIST_END = 9;

    /**
     * Use eXternal Memory Allocation mode flag
     * Corresponds to <tt>VPX_CODEC_USE_XMA</tt> from <tt>vpx/vpx_codec.h</tt>
     */
    public static final int CODEC_USE_XMA = 0x00000001;

    /**
     * Output one partition at a time. Each partition is returned in its own
     * <tt>VPX_CODEC_CX_FRAME_PKT</tt>.
     */
    public static final int CODEC_USE_OUTPUT_PARTITION = 0x20000;

    /**
     * Improve resiliency against losses of whole frames.
     *
     * To set this option for an encoder, enable this bit in the value passed
     * to <tt>vpx_enc_cft_set_error_resilient</tt> for the encoder's
     * configuration.
     *
     * Corresponds to <tt>VPX_ERROR_RESILIENT_DEFAULT</tt> from
     * <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int ERROR_RESILIENT_DEFAULT = 0x1;

    /**
     * The frame partitions are independently decodable by the bool decoder,
     * meaning that partitions can be decoded even though earlier partitions
     * have been lost. Note that intra predicition is still done over the
     * partition boundary.
     *
     * To set this option for Coan encoder, enable this bit in the value passed
     * to <tt>vpx_enc_cft_set_error_resilient</tt> for the encoder's
     * configuration.
     *
     * Corresponds to <tt>VPX_ERROR_RESILIENT_PARTITIONS</tt> from
     * <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int ERROR_RESILIENT_PARTITIONS = 0x2;

    /**
     * I420 format constant
     * Corresponds to <tt>VPX_IMG_FMT_I420</tt> from <tt>vpx/vpx_image.h</tt>

     */
    public static final int IMG_FMT_I420 = 258;

    /**
     * Variable Bitrate mode.
     * Corresponds to <tt>VPX_VBR</tt> from <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int RC_MODE_VBR = 0;

    /**
     * Constant Bitrate mode.
     * Corresponds to <tt>VPX_CBR</tt> from <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int RC_MODE_CBR = 1;

    /**
     * Constant Quality mode.
     * Corresponds to <tt>VPX_CQ</tt> from <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int RC_MODE_CQ = 2;

    /**
     * Encoder determines optimal placement automatically.
     * Corresponds to <tt>VPX_KF_AUTO</tt> from in <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int KF_MODE_AUTO = 1;

    /**
     * Encoder does not place keyframes.
     * Corresponds to <tt>VPX_KF_DISABLED</tt> from <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int KF_MODE_DISABLED = 1;

    /**
     * Process and return as soon as possible ('realtime' deadline)
     * Corresponds to <tt>VPX_DL_REALTIME</tt> from <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int DL_REALTIME = 1;

    /**
     * Compressed video frame packet type.
     * Corresponds to <tt>VPX_CODEC_CX_FRAME_PKT</tt> from
     * <tt>vpx/vpx_encoder.h</tt>
     */
    public static final int CODEC_CX_FRAME_PKT = 0;


    /**
     * Constant for VP8 decoder interface
     */
    public static final int INTEFACE_VP8_DEC = 0;

    /**
     * Constant for VP8 encoder interface
     */
    public static final int INTERFACE_VP8_ENC = 1;

    /**
     * Allocates memory for a <tt>vpx_codec_ctx_t</tt> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_ctx_malloc();

    /**
     * Initializes a vpx decoder context.
     * @param context Pointer to a pre-allocated <tt>vpx_codec_ctx_t</tt>.
     * @param iface Interface to be used. Has to be one of the
     * <tt>VPX.INTERFACE_*</tt> constants.
     * @param cfg Pointer to a pre-allocated <tt>vpx_codec_dec_cfg_t</tt>, may
     * be 0.
     * @param flags Flags.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_dec_init(long context,
                                            int iface,
                                            long cfg,
                                            long flags);

    /**
     * Decodes the frame in <tt>buf</tt>, at offset <tt>buf_offset</tt>.
     *
     * @param context The context to use.
     * @param buf Encoded frame buffer.
     * @param buf_offset Offset into <tt>buf</tt> where the encoded frame begins.
     * @param buf_size Size of the encoded frame.
     * @param user_priv Application specific data to associate with this frame.
     * @param deadline Soft deadline the decoder should attempt to meet,
     * in microseconds. Set to zero for unlimited.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_decode(long context,
                                          byte[] buf,
                                          int buf_offset,
                                          int buf_size,
                                          long user_priv,
                                          long deadline);

    /**
     * Gets the next frame available to display from the decoder context
     * <tt>context</tt>.
     * The list of available frames becomes valid upon completion of the
     * <tt>codec_decode</tt> call, and remains valid until the next call to
     * <tt>codec_decode</tt>.
     *
     * @param context The decoder context to use.
     * @param iter Iterator storage, initialized by setting its first element
     * to 0.
     *
     * @return Pointer to a <tt>vpx_image_t</tt> describing the decoded frame,
     * or 0 if no more frames are available
     */
    public static native long codec_get_frame(long context,
                                              long[] iter);

    /**
     * Destroys a codec context, freeing any associated memory buffers.
     *
     * @param context Pointer to the <tt>vpx_codec_ctx_t</tt> context to destroy.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_destroy(long context);

    /**
     * Initializes a vpx encoder context.
     *
     * @param context Pointer to a pre-allocated <tt>vpx_codec_ctx_t</tt>.
     * @param iface Interface to be used. Has to be one of the
     * <tt>VPX.INTERFACE_*</tt> constants.
     * @param cfg Pointer to a pre-allocated <tt>vpx_codec_enc_cfg_t</tt>,
     * may be 0.
     * @param flags Flags.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_init(long context,
                                            int iface,
                                            long cfg,
                                            long flags);

    /**
     *
     * @param context Pointer to the codec context on which to set the
     * confirutation
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt> to set.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_config_set(long context,
                                                  long cfg);

    /**
     * Encodes the frame described by <tt>img</tt>, <tt>buf</tt>,
     * <tt>offset0</tt>, <tt>offset1</tt> and <tt>offset2</tt>.
     *
     * Note that <tt>buf</tt> and the offsets describe where the frames is
     * stored, but <tt>img</tt> has to have all of its other parameters (format,
     * dimensions, strides) already set.
     *
     * The reason <tt>buf</tt> and the offsets are treated differently is to
     * allow for the encoder to operate on java memory and avoid copying the raw
     * frame to native memory.
     *
     * @param context Pointer to the codec context to use.
     * @param img Pointer to a <tt>vpx_image_t</tt> describing the raw frame
     * @param buf Contains the raw frame
     * @param offset0 Offset of the first plane
     * @param offset1 Offset of the second plane
     * @param offset2 Offset of the third plane
     * @param pts Presentation time stamp, in timebase units.
     * @param duration Duration to show frame, in timebase units.
     * @param flags Flags to use for encoding this frame.
     * @param deadline Time to spend encoding, in microseconds. (0=infinite)
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_encode(long context,
                                          long img,
                                          byte[] buf,
                                          int offset0,
                                          int offset1,
                                          int offset2,
                                          long pts,
                                          long duration,
                                          long flags,
                                          long deadline);

    /**
     * Encoded data iterator.
     * Iterates over a list of data packets to be passed from the encoder to
     * the application. The kind of a packet can be determined using
     * {@link VPX#codec_cx_pkt_get_kind}
     * Packets of kind <tt>CODEC_CX_FRAME_PKT</tt> should be passed to the
     * application's muxer.
     *
     * @param context The codec context to use.
     * @param iter Iterator storage, initialized by setting its first element
     * to 0.
     *
     * @return Pointer to a vpx_codec_cx_pkt_t containing the output data
     * packet, or 0 to indicate the end of available packets
     */
    public static native long codec_get_cx_data(long context,
                                                long[] iter);

    /**
     * Returns the <tt>kind</tt> of the <tt>vpx_codec_cx_pkt_t</tt> pointed to
     * by <tt>pkt</tt>.
     *
     * @param pkt Pointer to the <tt>vpx_codec_cx_pkt_t</tt> to return the
     * <tt>kind</tt> of.
     * @return The kind of <tt>pkt</tt>.
     */
    public static native int codec_cx_pkt_get_kind(long pkt);

    /**
     * Returns the size of the data in the <tt>vpx_codec_cx_pkt_t</tt> pointed
     * to by <tt>pkt</tt>. Can only be used for packets of <tt>kind</tt>
     * <tt>CODEC_CX_FRAME_PKT</tt>.
     *
     * @param pkt Pointer to a <tt>vpx_codec_cx_pkt_t</tt>.
     *
     * @return The size of the data of <tt>pkt</tt>.
     */
    public static native int codec_cx_pkt_get_size(long pkt);

    /**
     * Returns a pointer to the data in the <tt>vpx_codec_cx_pkt_t</tt> pointed
     * to by<tt>pkt</tt>. Can only be used for packets of <tt>kind</tt>
     * <tt>CODEC_CX_FRAME_PKT</tt>.
     *
     * @param pkt Pointer to the <tt>vpx_codec_cx_pkt_t</tt>.
     * @return Pointer to the data of <tt>pkt</tt>.
     */
    public static native long codec_cx_pkt_get_data(long pkt);

    //img
    /**
     * Allocates memory for a <tt>vpx_image_t</tt> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long img_malloc();

    /**
     * Returns the value of the <tt>w</tt> (width) field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>w</tt> (width) field of <tt>img</tt>.
     */
    public static native int img_get_w(long img);

    /**
     * Returns the value of the <tt>h</tt> (height) field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>h</tt> (height) field of <tt>img</tt>.
     */
    public static native int img_get_h(long img);

    /**
     * Returns the value of the <tt>d_w</tt> (displayed width) field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>d_w</tt> (displayed width) field of <tt>img</tt>.
     */
    public static native int img_get_d_w(long img);

    /**
     * Returns the value of the <tt>d_h</tt> (displayed height) field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>d_h</tt> (displayed height) field of <tt>img</tt>.
     */
    public static native int img_get_d_h(long img);

    /**
     * Returns the value of the <tt>planes[0]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>planes[0]</tt> field of <tt>img</tt>.
     */
    public static native long img_get_plane0(long img);

    /**
     * Returns the value of the <tt>planes[1]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>planes[1]</tt> field of <tt>img</tt>.
     */
    public static native long img_get_plane1(long img);

    /**
     * Returns the value of the <tt>planes[2]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>planes[2]</tt> field of <tt>img</tt>.
     */
    public static native long img_get_plane2(long img);

    /**
     * Returns the value of the <tt>stride[0]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>stride[0]</tt> field of <tt>img</tt>.
     */
    public static native int img_get_stride0(long img);

    /**
     * Returns the value of the <tt>stride[1]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>stride[1]</tt> field of <tt>img</tt>.
     */
    public static native int img_get_stride1(long img);

    /**
     * Returns the value of the <tt>stride[2]</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>stride[2]</tt> field of <tt>img</tt>.
     */
    public static native int img_get_stride2(long img);

    /**
     * Returns the value of the <tt>fmt</tt> field of a
     * <tt>vpx_image_t</tt>.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     *
     * @return The <tt>fmt</tt> field of <tt>img</tt>.
     */
    public static native int img_get_fmt(long img);

    /**
     * Sets the <tt>w</tt> (width) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_w(long img, int value);

    /**
     * Sets the <tt>h</tt> (height) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_h(long img, int value);

    /**
     * Sets the <tt>d_w</tt> (displayed width) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_d_w(long img, int value);

    /**
     * Sets the <tt>d_h</tt> (displayed height) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_d_h(long img, int value);

    /**
     * Sets the <tt>stride[0]</tt> field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_stride0(long img, int value);

    /**
     * Sets the <tt>stride[1]</tt> field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_stride1(long img, int value);

    /**
     * Sets the <tt>stride[2]</tt> field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_stride2(long img, int value);

    /**
     * Sets the <tt>stride[3]</tt> field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_stride3(long img, int value);

    /**
     * Sets the <tt>fmt</tt> (format) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_fmt(long img, int value);

    /**
     * Sets the <tt>bps</tt> (bits per sample) field of a <tt>vpx_image_t</tt>.
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param value The value to set.
     */
    public static native void img_set_bps(long img, int value);

    /**
     * Open a descriptor, using existing storage for the underlying image.
     *
     * Returns a descriptor for storing an image of the given format. The
     * storage for descriptor has been allocated elsewhere, and a descriptor is
     * desired to "wrap" that storage.
     *
     * @param img Pointer to a <tt>vpx_image_t</tt>.
     * @param fmt Format of the image.
     * @param d_w Width of the image.
     * @param d_h Height of the image.
     * @param align Alignment, in bytes, of each row in the image.
     * @param data Storage to use for the image
     */
    public static native void img_wrap(long img,
                                       int fmt,
                                       int d_w,
                                       int d_h,
                                       int align,
                                       long data);

    /**
     * Allocates memory for a <tt>vpx_codec_dec_cfg_t</tt> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_dec_cfg_malloc();

    /**
     * Sets the <tt>w</tt> field of a <tt>vpx_codec_dec_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_dec_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_dec_cfg_set_w(long cfg, int value);

    /**
     * Sets the <tt>h</tt> field of a <tt>vpx_codec_dec_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_dec_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_dec_cfg_set_h(long cfg, int value);

    /**
     * Allocates memory for a <tt>vpx_codec_enc_cfg_t</tt> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_enc_cfg_malloc();

    /**
     * Initializes a encoder configuration structure with default values.
     *
     * @param iface Interface. Should be one of the <tt>INTERFACE_*</tt>
     * constants
     * @param cfg Pointer to the vpx_codec_enc_cfg_t to initialize
     * @param usage End usage. Set to 0 or use codec specific values.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_config_default(int iface,
                                                      long cfg,
                                                      int usage);

    /**
     * Sets the <tt>g_profile</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_profile(long cfg,
                                                        int value);

    /**
     * Sets the <tt>g_threads</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_threads(long cfg,
                                                        int value);

    /**
     * Sets the <tt>g_w</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_w(long cfg,
                                                  int value);

    /**
     * Sets the <tt>g_h</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_h(long cfg,
                                                  int value);

    /**
     * Sets the <tt>g_error_resilient</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_error_resilient(long cfg,
                                                                int value);

    /**
     * Sets the <tt>rc_target_bitrate</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_target_bitrate(long cfg,
                                                                  int value);

    /**
     * Sets the <tt>rc_dropframe_thresh</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_dropframe_thresh(long cfg,
                                                                    int value);

    /**
     * Sets the <tt>rc_resize_allowed</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_allowed(long cfg,
                                                                  int value);

    /**
     * Sets the <tt>rc_resize_up_thresh</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_up_thresh(long cfg,
                                                                    int value);

    /**
     * Sets the <tt>rc_resize_down_thresh</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_down_thresh(long cfg,
                                                                      int value);

    /**
     * Sets the <tt>rc_end_usage</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_end_usage(long cfg,
                                                             int value);

    /**
     * Sets the <tt>rc_min_quantizer</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_min_quantizer(long cfg,
                                                                 int value);

    /**
     * Sets the <tt>rc_max_quantizer</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_max_quantizer(long cfg,
                                                                 int value);

    /**
     * Sets the <tt>rc_undershoot_pct</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_undershoot_pct(long cfg,
                                                                  int value);

    /**
     * Sets the <tt>rc_overshoot_pct</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_overshoot_pct(long cfg,
                                                                 int value);

    /**
     * Sets the <tt>rc_buf_sz</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_sz(long cfg,
                                                          int value);

    /**
     * Sets the <tt>rc_buf_initial_sz</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_initial_sz(long cfg,
                                                                  int value);

    /**
     * Sets the <tt>rc_buf_optimal_sz</tt> field of a
     * <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_optimal_sz(long cfg,
                                                                  int value);

    /**
     * Sets the <tt>kf_mode</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_mode(long cfg,
                                                        int value);

    /**
     * Sets the <tt>kf_min_dist</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_min_dist(long cfg,
                                                            int value);

    /**
     * Sets the <tt>kf_max_dist</tt> field of a <tt>vpx_codec_enc_cfg_t</tt>.
     *
     * @param cfg Pointer to a <tt>vpx_codec_enc_cfg_t</tt>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_max_dist(long cfg,
                                                            int value);

    /**
     * Allocates memory for a <tt>vpx_codec_stream_info_t</tt> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long stream_info_malloc();


    /**
     * Returns the <tt>w</tt> field of a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @param stream_info Pointer to a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @return The <tt>w</tt> field of a <tt>stream_info</tt>.
     */
    public static native int stream_info_get_w(long stream_info);

    /**
     * Returns the <tt>h</tt> field of a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @param stream_info Pointer to a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @return The <tt>h</tt> field of a <tt>stream_info</tt>.
     */
    public static native int stream_info_get_h(long stream_info);


    /**
     * Returns the <tt>is_kf</tt> field of a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @param stream_info Pointer to a <tt>vpx_codec_stream_info_t</tt>.
     *
     * @return The <tt>w</tt> field of a <tt>stream_info</tt>.
     */
    public static native int stream_info_get_is_kf(long stream_info);


    /**
     * Performs high level parsing of the bitstream. Construction of a decoder
     * context is not necessary. Can be used to determine if the bitstream is
     * of the proper format, and to extract information from the stream.
     *
     * @param iface Interface, should be one of the <tt>INTERFACE_*</tt>
     * constants.
     * @param buf Buffer containing a compressed frame.
     * @param buf_offset Offset into <tt>buf</tt> where the compressed frame
     * begins.
     * @param buf_size Size of the compressed frame.
     * @param si_ptr Pointer to a <tt>vpx_codec_stream_info_t</tt> which will
     * be filled with information about the compressed frame.
     *
     * @return <tt>CODEC_OK</tt> on success, or an error code otherwise. The
     * error code can be converted to a <tt>String</tt> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_peek_stream_info(int iface,
                                                    byte[] buf,
                                                    int buf_offset,
                                                    int buf_size,
                                                    long si_ptr);

    /**
     * Allocates memorry on the heap (a simple wrapped around the native
     * <tt>malloc()</tt>)
     * @param s Number of bytes to allocate
     *
     * @return Pointer to the memory allocated.
     */
    public static native long malloc(long s);

    /**
     * Frees memory, which has been allocated with {@link VPX#malloc(long)} or
     * one of the <tt>*_malloc()</tt> functions.
     *
     * @param ptr Pointer to the memory to free.
     */
    public static native void free(long ptr);

    /**
     * Copies <tt>n</tt> bytes from <tt>src</tt> to <tt>dst</tt>. Simple wrapper
     * around the native <tt>memcpy()</tt> funciton.
     *
     * @param dst Destination.
     * @param src Source.
     * @param n Number of bytes to copy.
     */
    public static native void memcpy(byte[] dst, long src, int n);

    /**
     * Fills in <tt>buf</tt> with a string description of the error code
     * <tt>err</tt>. Fills at most <tt>buf_size</tt> bytes of <tt>buf</tt>
     *
     * @param err Error code
     * @param buf Buffer to copy the string into
     * @param buf_size Buffer size
     *
     * @return The number of bytes written to <tt>buf</tt>
     */
    public static native int codec_err_to_string(int err,
                                                 byte[] buf,
                                                 int buf_size);

    /**
     * Returns a <tt>String</tt> describing the error code <tt>err</tt>.
     * @param err Error code
     *
     * @return A <tt>String</tt> describing the error code <tt>err</tt>.
     */
    public static String codec_err_to_string(int err)
    {
        byte[] buf = new byte[100];
        codec_err_to_string(err, buf, buf.length);
        return new String(buf);
    }



    static
    {
        JNIUtils.loadLibrary("jnvpx", VPX.class);
    }

    /**
     * Java wrapper around vpx_codec_stream_info_t. Contains basic information,
     * obtainable from an encoded frame without a decoder context.
     */
    static class StreamInfo
    {
        /**
         * Width
         */
        int w;

        /**
         * Height
         */
        int h;

        /**
         * Is keyframe
         */
        boolean is_kf;

        /**
         * Initializes this instance by parsing <tt>buf</tt>
         *
         * @param iface Interface, should be one of the <tt>INTERFACE_*</tt>
         * constants.
         * @param buf Buffer containing a compressed frame to parse.
         * @param buf_offset Offset into buffer where the compressed frame
         * begins.
         * @param buf_size Size of the compressed frame.
         */
        StreamInfo(int iface, byte[] buf, int buf_offset, int buf_size)
        {
            long si = stream_info_malloc();

            if(codec_peek_stream_info(iface, buf, buf_offset, buf_size, si)
                    != CODEC_OK)
                return;

            w = stream_info_get_w(si);
            h = stream_info_get_h(si);
            is_kf = stream_info_get_is_kf(si) != 0;

            if(si != 0)
                free(si);
        }

        /**
         * Gets the <tt>w</tt> (width) field of this instance.
         *
         * @return the <tt>w</tt> (width) field of this instance.
         */
        public int getW()
        {
            return w;
        }

        /**
         * Gets the <tt>h</tt> (height) field of this instance.
         *
         * @return the <tt>h</tt> (height) field of this instance.
         */
        public int getH()
        {
            return h;
        }

        /**
         * Gets the <tt>is_kf</tt> (is keyframe) field of this instance.
         *
         * @return the <tt>is_kf</tt> (is keyframe) field of this instance.
         */
        public boolean isKf()
        {
            return is_kf;
        }
    }
}
