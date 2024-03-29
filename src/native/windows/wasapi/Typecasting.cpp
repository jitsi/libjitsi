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

#include "Typecasting.h"

#include <objbase.h>

#include "HResultException.h"

HRESULT
WASAPI_iidFromString(JNIEnv *env, jstring str, LPIID iid)
{
    HRESULT hr;

    if (str)
    {
        const jchar *sz = env->GetStringChars(str, nullptr);

        if (sz)
        {
            hr = IIDFromString((LPOLESTR) sz, iid);
            env->ReleaseStringChars(str, sz);
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
