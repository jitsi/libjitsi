/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "Digest.h"

#include <openssl/evp.h>
#include <stdint.h>

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_DigestFinal_ex
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestFinal_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray md, jint off)
{
    jbyte *md_ = (*env)->GetPrimitiveArrayCritical(env, md, NULL);
    int i;

    if (md_)
    {
        unsigned int s = 0;

        i
            = EVP_DigestFinal_ex(
                    (EVP_MD_CTX *) (intptr_t) ctx,
                    (unsigned char *) (md_ + off),
                    &s);
        (*env)->ReleasePrimitiveArrayCritical(env, md, md_, 0);
        i = i ? ((int) s) : -1;
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_DigestInit_ex
 * Signature: (JJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestInit_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jlong type, jlong impl)
{
    return
        EVP_DigestInit_ex(
                (EVP_MD_CTX *) (intptr_t) ctx,
                (const EVP_MD *) (intptr_t) type,
                (ENGINE *) (intptr_t) impl);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_DigestUpdate
 * Signature: (J[BII)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1DigestUpdate
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray d, jint off, jint cnt)
{
    jbyte *d_ = (*env)->GetPrimitiveArrayCritical(env, d, NULL);
    jboolean ok;

    if (d_)
    {
        ok = EVP_DigestUpdate((EVP_MD_CTX *) (intptr_t) ctx, d_ + off, cnt);
        (*env)->ReleasePrimitiveArrayCritical(env, d, d_, JNI_ABORT);
    }
    else
    {
        ok = JNI_FALSE;
    }
    return ok;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_MD_CTX_block_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1block_1size
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return EVP_MD_CTX_block_size((const EVP_MD_CTX *) (intptr_t) ctx);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_MD_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1create
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_MD_CTX_create();
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_MD_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1destroy
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    EVP_MD_CTX_destroy((EVP_MD_CTX *) (intptr_t) ctx);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_MD_CTX_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1MD_1CTX_1size
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    return EVP_MD_CTX_size((const EVP_MD_CTX *) (intptr_t) ctx);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest
 * Method:    EVP_sha1
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLDigest_EVP_1sha1
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_sha1();
}
