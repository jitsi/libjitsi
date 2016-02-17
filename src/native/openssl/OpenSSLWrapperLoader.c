
#include "OpenSSLWrapperLoader.h"
#include <dlfcn.h>

#include <stdio.h>

/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_OpenSSLWrapperLoader
 * Method:    OpenSSL_Init
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_jitsi_impl_neomedia_transform_srtp_OpenSSLWrapperLoader_OpenSSL_1Init
  (JNIEnv *env, jclass clazz)
{
    if (dlopen("libcrypto.so", RTLD_NOW | RTLD_GLOBAL) == NULL)
        return JNI_FALSE;

    return JNI_TRUE;
}

