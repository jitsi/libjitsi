/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

#include "Typecasting.h"

#include <objbase.h>

#include "HResultException.h"

HRESULT
WASAPI_iidFromString(JNIEnv *env, jstring str, LPIID iid)
{
    HRESULT hr;

    if (str)
    {
        const jchar *sz = (*env)->GetStringChars(env, str, NULL);

        if (sz)
        {
            hr = IIDFromString((LPOLESTR) sz, iid);
            (*env)->ReleaseStringChars(env, str, sz);
        }
        else
            hr = E_OUTOFMEMORY;
        if (FAILED(hr))
            WASAPI_throwNewHResultException(env, hr, __func__, __LINE__);
    }
    else
        hr = S_OK;
    return hr;
}
