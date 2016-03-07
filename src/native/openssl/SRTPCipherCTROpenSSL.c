/*
 * Copyright @ 2016 Atlassian Pty Ltd
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

#include "SRTPCipherCTROpenSSL.h"

#include <openssl/evp.h>
#include <stdint.h>
#include <stdlib.h>

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1create
  (JNIEnv *env, jclass clazz)
{
    EVP_CIPHER_CTX *ctx = malloc(sizeof(EVP_CIPHER_CTX));
    if (ctx)
        EVP_CIPHER_CTX_init(ctx);

    return (jlong) (intptr_t) ctx;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1destroy
  (JNIEnv *env, jclass clazz, jlong ctx)
{
    if (ctx) {
        EVP_CIPHER_CTX *ctx_ = (EVP_CIPHER_CTX *) (intptr_t) ctx;
        EVP_CIPHER_CTX_cleanup(ctx_);
        free(ctx_);
    }
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_init
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1init
  (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray key)
{
    unsigned char key_[16];
    (*env)->GetByteArrayRegion(env, key, 0, 16, key_);
    return EVP_CipherInit_ex((EVP_CIPHER_CTX *) (intptr_t) ctx, EVP_aes_128_ctr(), NULL, key_, NULL, 1);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL
 * Method:    AES128CTR_CTX_process
 * Signature: (J[B[BII)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_SRTPCipherCTROpenSSL_AES128CTR_1CTX_1process
  (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray iv, jbyteArray inOut, jint offset, jint len)
{
    int ok = 0;
    unsigned char iv_[16];
    (*env)->GetByteArrayRegion(env, iv, 0, 16, iv_);
    jbyte *inOut_;
    inOut_ = (*env)->GetPrimitiveArrayCritical(env, inOut, NULL);
    if (!inOut)
        goto exit;

    ok = EVP_CipherInit_ex(
                (EVP_CIPHER_CTX *) (intptr_t) ctx,
                NULL,
                NULL,
                NULL,
                iv_,
                -1);
    if(ok == 0)
        goto exit;

    int len_ = len;
    ok = EVP_CipherUpdate(
                (EVP_CIPHER_CTX *) (intptr_t) ctx,
                (unsigned char *) (inOut_ + offset), &len_,
                (unsigned char *) (inOut_ + offset), len);

exit:
    if (inOut_)
        (*env)->ReleasePrimitiveArrayCritical(env, inOut, inOut_, 0);

    return ok;
}

