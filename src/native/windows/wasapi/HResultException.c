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

#include "HResultException.h"

#include <stdio.h> /* fflush */
#include <tchar.h> /* _ftprintf */

jclass WASAPI_hResultExceptionClass = 0;
jmethodID WASAPI_hResultExceptionMethodID = 0;

void
WASAPI_throwNewHResultException
    (JNIEnv *env, HRESULT hresult, const char *func, unsigned int line)
{
    /*
     * Print the system message (if any) which represents a human-readable
     * format of the specified HRESULT value on the standard error to facilitate
     * debugging.
     */
    {
        LPTSTR message = NULL;
        DWORD length
            = FormatMessage(
                    FORMAT_MESSAGE_ALLOCATE_BUFFER
                        | FORMAT_MESSAGE_FROM_SYSTEM
                        | FORMAT_MESSAGE_IGNORE_INSERTS,
                    /* lpSource */ NULL,
                    hresult,
                    /* dwLanguageId */ 0,
                    (LPTSTR) &message,
                    /* nSize */ 0,
                    /* Arguments */ NULL);
        BOOL printed = FALSE;

        if (message)
        {
            if (length)
            {
                _ftprintf(stderr, TEXT("%s:%u: %s\r\n"), func, line, message);
                printed = TRUE;
            }
            LocalFree(message);
        }
        if (!printed)
        {
            _ftprintf(
                    stderr,
                    TEXT("%s:%u: HRESULT 0x%x\r\n"),
                    func, line,
                    (unsigned int) hresult);
        }
        fflush(stderr);
    }

    {
        jclass clazz = WASAPI_hResultExceptionClass;

        if (clazz)
        {
            jmethodID methodID = WASAPI_hResultExceptionMethodID;

            if (methodID)
            {
                jobject t
                    = (*env)->NewObject(env, clazz, methodID, (jint) hresult);

                if (t)
                    (*env)->Throw(env, (jthrowable) t);
            }
        }
    }
}
