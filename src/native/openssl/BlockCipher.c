/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "BlockCipher.h"

#include <openssl/evp.h>
#include <stdint.h>
#include <stdlib.h>

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_aes_128_ecb
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1aes_1128_1ecb
    (JNIEnv *env, jclass clazz)
{
    return (jlong) (intptr_t) EVP_aes_128_ecb();
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CIPHER_block_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CIPHER_1block_1size
    (JNIEnv *env, jclass clazz, jlong e)
{
    return EVP_CIPHER_block_size((const EVP_CIPHER *) (intptr_t) e);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CIPHER_CTX_cleanup
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CIPHER_1CTX_1cleanup
    (JNIEnv *env, jclass clazz, jlong a)
{
    return EVP_CIPHER_CTX_cleanup((EVP_CIPHER_CTX *) (intptr_t) a);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CIPHER_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CIPHER_1CTX_1create
    (JNIEnv *env, jclass clazz)
{
    EVP_CIPHER_CTX *ctx = malloc(sizeof(EVP_CIPHER_CTX));

    if (ctx)
        EVP_CIPHER_CTX_init(ctx);
    return (jlong) (intptr_t) ctx;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CIPHER_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CIPHER_1CTX_1destroy
    (JNIEnv *env, jclass clazz, jlong ctx)
{
    EVP_CIPHER_CTX *ctx_ = (EVP_CIPHER_CTX *) (intptr_t) ctx;

    EVP_CIPHER_CTX_cleanup(ctx_);
    free(ctx_);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CIPHER_CTX_set_padding
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CIPHER_1CTX_1set_1padding
    (JNIEnv *env, jclass clazz, jlong x, jboolean padding)
{
    return EVP_CIPHER_CTX_set_padding((EVP_CIPHER_CTX *) (intptr_t) x, padding);
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CipherFinal_ex
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CipherFinal_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray out, jint outOff,
        jint outl)
{
    jbyte *out_ = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    int i;

    if (out_)
    {
        int outl_ = outl;

        i
            = EVP_CipherFinal_ex(
                    (EVP_CIPHER_CTX *) (intptr_t) ctx,
                    (unsigned char *) (out_ + outOff),
                    &outl_);
        (*env)->ReleasePrimitiveArrayCritical(env, out, out_, 0);
        i = i ? outl_ : -1;
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CipherInit_ex
 * Signature: (JJJ[B[BI)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CipherInit_1ex
    (JNIEnv *env, jclass clazz, jlong ctx, jlong type, jlong impl,
        jbyteArray key, jbyteArray iv, jint enc)
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
        jbyte *iv_;

        if (iv)
        {
            iv_ = (*env)->GetPrimitiveArrayCritical(env, iv, NULL);
            ok = iv_ ? JNI_TRUE : JNI_FALSE;
        }
        else
        {
            iv_ = NULL;
            ok = JNI_TRUE;
        }
        if (JNI_TRUE == ok)
        {
            ok
                = EVP_CipherInit_ex(
                        (EVP_CIPHER_CTX *) (intptr_t) ctx,
                        (const EVP_CIPHER *) (intptr_t) type,
                        (ENGINE *) (intptr_t) impl,
                        (unsigned char *) key_,
                        (unsigned char *) iv_,
                        enc);
            if (iv_)
                (*env)->ReleasePrimitiveArrayCritical(env, iv, iv_, JNI_ABORT);
        }
        if (key_)
            (*env)->ReleasePrimitiveArrayCritical(env, key, key_, JNI_ABORT);
    }
    return ok;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CipherUpdate
 * Signature: (J[BII[BII)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CipherUpdate__J_3BII_3BII
    (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray out, jint outOff,
        jint outl, jbyteArray in, jint inOff, jint inl)
{
    jbyte *out_ = (*env)->GetPrimitiveArrayCritical(env, out, NULL);
    int i;

    if (out_)
    {
        jbyte *in_ = (*env)->GetPrimitiveArrayCritical(env, in, NULL);

        if (in_)
        {
            int outl_ = outl;

            i
                = EVP_CipherUpdate(
                        (EVP_CIPHER_CTX *) (intptr_t) ctx,
                        (unsigned char *) (out_ + outOff), &outl_,
                        (unsigned char *) (in_ + inOff), inl);
            (*env)->ReleasePrimitiveArrayCritical(env, in, in_, JNI_ABORT);
            i = i ? outl_ : -1;
        }
        else
        {
            i = -1;
        }
        (*env)->ReleasePrimitiveArrayCritical(env, out, out_, 0);
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_CipherUpdate
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1CipherUpdate__JLjava_nio_ByteBuffer_2IILjava_nio_ByteBuffer_2II
    (JNIEnv *env, jclass clazz, jlong ctx, jobject out, jint outOff, jint outl,
        jobject in, jint inOff, jint inl)
{
    unsigned char *out_ = (*env)->GetDirectBufferAddress(env, out);
    int i;

    if (out_)
    {
        unsigned char *in_ = (*env)->GetDirectBufferAddress(env, in);

        if (in_)
        {
            int outl_ = outl;

            i
                = EVP_CipherUpdate(
                        (EVP_CIPHER_CTX *) (intptr_t) ctx,
                        out_ + outOff, &outl_,
                        in_ + inOff, inl);
            i = i ? outl_ : -1;
        }
        else
        {
            i = -1;
        }
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher
 * Method:    EVP_get_cipherbyname
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLBlockCipher_EVP_1get_1cipherbyname
    (JNIEnv *env, jclass clazz, jstring name)
{
    const char *name_ = (*env)->GetStringUTFChars(env, name, NULL);
    const EVP_CIPHER *cipher;

    if (name_)
    {
        cipher = EVP_get_cipherbyname(name_);
        if (!cipher)
        {
            /*
             * The cipher table must be initialized using, for example, the
             * function OpenSSL_add_all_ciphers, for the function
             * EVP_get_cipherbyname to work.
             */
            OpenSSL_add_all_ciphers();
            cipher = EVP_get_cipherbyname(name_);
        }
        (*env)->ReleaseStringUTFChars(env, name, name_);
    }
    else
    {
        cipher = NULL;
    }
    return (jlong) (intptr_t) cipher;
}
