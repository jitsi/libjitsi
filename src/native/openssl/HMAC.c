/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "Digest.h"

#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <stdint.h>
#include <stdlib.h>

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    EVP_MD_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_EVP_1MD_1size
    (JNIEnv *env, jclass clazz, jlong md)
{
    return EVP_MD_size((const EVP_MD *) (intptr_t) md);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    EVP_sha1
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_EVP_1sha1
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_sha1();
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_CTX_cleanup
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1CTX_1cleanup
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    HMAC_CTX_cleanup((HMAC_CTX *) (intptr_t) ctx);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1CTX_1create
    (JNIEnv *env, jclass clazz)
{
    HMAC_CTX *ctx = malloc(sizeof(HMAC_CTX));

    if (ctx)
        HMAC_CTX_init(ctx);
    return (jlong) (intptr_t) ctx;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1CTX_1destroy
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    HMAC_CTX *ctx_ = (HMAC_CTX *) (intptr_t) ctx;

    HMAC_CTX_cleanup(ctx_);
    free(ctx_);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_Final
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1Final
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray md, jint mdOff,
        jint mdLen)
{
    jbyte *md_ = (*env)->GetPrimitiveArrayCritical(env, md, NULL);
    int i;

    if (md_)
    {
        unsigned int len = mdLen;

        i
            = HMAC_Final(
                    (HMAC_CTX *) (intptr_t) ctx,
                    (unsigned char *) (md_ + mdOff),
                    &len);
        (*env)->ReleasePrimitiveArrayCritical(env, md, md_, 0);
        i = i ? len : -1;
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_Init_ex
 * Signature: (J[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1Init_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray key, jint keyLen,
        jlong md, jlong impl)
{
    jbyte *key_;
    jboolean ok;

    if (key)
    {
        key_ = (*env)->GetPrimitiveArrayCritical(env, key, NULL);
        ok = key_ ? JNI_TRUE : JNI_FALSE;
    }
    else
    {
        key_ = NULL;
        ok = JNI_TRUE;
    }
    if (JNI_TRUE == ok)
    {
        ok
            = HMAC_Init_ex(
                    (HMAC_CTX *) (intptr_t) ctx,
                    (const void *) (intptr_t) key_,
                    keyLen,
                    (const EVP_MD *) (intptr_t) md,
                    (ENGINE *) (intptr_t) impl);
        if (key_)
            (*env)->ReleasePrimitiveArrayCritical(env, key, key_, JNI_ABORT);
    }
    return ok;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC
 * Method:    HMAC_Update
 * Signature: (J[BII)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLHMAC_HMAC_1Update
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray data, jint off, jint len)
{
    jbyte *data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    jboolean ok;

    if (data_)
    {
        ok
            = HMAC_Update(
                    (HMAC_CTX *) (intptr_t) ctx,
                    (const unsigned char *) (data_ + off),
                    len);
        (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
    }
    else
    {
        ok = JNI_FALSE;
    }
    return ok;
}
