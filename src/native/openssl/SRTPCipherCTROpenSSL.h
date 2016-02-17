/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL */

#ifndef _Included_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
#define _Included_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    CIPHER_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_CIPHER_1CTX_1create
  (JNIEnv *, jclass);

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    CIPHER_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_CIPHER_1CTX_1destroy
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_init
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1init
  (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_process
 * Signature: (J[B[BII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1process
  (JNIEnv *, jclass, jlong, jbyteArray, jbyteArray, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
