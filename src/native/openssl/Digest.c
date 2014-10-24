/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "Digest.h"

#include <openssl/evp.h>
#include <stdint.h>

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestFinal_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray md, jint off)
{
    jbyte *md_ = (*env)->GetPrimitiveArrayCritical(env, md, NULL);
    int i;

    if (md_)
    {
        unsigned int s = 0;

        i = EVP_DigestFinal_ex((EVP_MD_CTX *) (intptr_t) ctx, md_ + off, &s);
        (*env)->ReleasePrimitiveArrayCritical(env, md, md_, 0);
        i = i ? ((int) s) : -1;
    }
    else
    {
        i = -1;
    }
    return i;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestInit_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jlong type, jlong impl)
{
    int i
        = EVP_DigestInit_ex(
                (EVP_MD_CTX *) (intptr_t) ctx,
                (const EVP_MD *) (intptr_t) type,
                (ENGINE *) (intptr_t) impl);

    return i ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestUpdate
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray d, jint off, jint cnt)
{
    jbyte *d_ = (*env)->GetPrimitiveArrayCritical(env, d, NULL);
    jboolean b;

    if (d_)
    {
        int i = EVP_DigestUpdate((EVP_MD_CTX *) (intptr_t) ctx, d_ + off, cnt);

        (*env)->ReleasePrimitiveArrayCritical(env, d, d_, JNI_ABORT);
        b = i ? JNI_TRUE : JNI_FALSE;
    }
    else
    {
        b = JNI_FALSE;
    }
    return b;
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1block_1size
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return EVP_MD_CTX_block_size((const EVP_MD_CTX *) (intptr_t) ctx);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1create
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_MD_CTX_create();
}

JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1destroy
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    EVP_MD_CTX_destroy((EVP_MD_CTX *) (intptr_t) ctx);
}

JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1size
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return EVP_MD_CTX_size((const EVP_MD_CTX *) (intptr_t) ctx);
}

JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1sha1
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_sha1();
}
