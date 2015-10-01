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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_CONDITIONVARIABLE_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_CONDITIONVARIABLE_H_

#include "Mutex.h"

#ifdef _WIN32
#include <windows.h>

typedef HANDLE ConditionVariable;

static inline void ConditionVariable_free(ConditionVariable *condVar)
{
    if (CloseHandle(*condVar))
        free(condVar);
}

static inline ConditionVariable *ConditionVariable_new(void *attr)
{
    ConditionVariable *condVar = malloc(sizeof(ConditionVariable));

    if (condVar)
    {
        HANDLE event = CreateEvent(NULL, FALSE, FALSE, NULL);

        if (event)
            *condVar = event;
        else
        {
            free(condVar);
            condVar = NULL;
        }
    }
    return condVar;
}

static inline int ConditionVariable_notify(ConditionVariable *condVar)
{
    return SetEvent(*condVar) ? 0 : GetLastError();
}

static inline int ConditionVariable_wait
    (ConditionVariable *condVar, Mutex *mutex)
{
    DWORD waitForSingleObject;

    LeaveCriticalSection(mutex);
    waitForSingleObject = WaitForSingleObject(*condVar, INFINITE);
    EnterCriticalSection(mutex);
    return waitForSingleObject;
}

#else /* #ifdef _WIN32 */
#include <pthread.h>

typedef pthread_cond_t ConditionVariable;

static inline void ConditionVariable_free(ConditionVariable *condVar)
{
    if (!pthread_cond_destroy(condVar))
        free(condVar);
}

static inline ConditionVariable *ConditionVariable_new(void *attr)
{
    ConditionVariable *condVar = malloc(sizeof(ConditionVariable));

    if (condVar && pthread_cond_init(condVar, attr))
    {
        free(condVar);
        condVar = NULL;
    }
    return condVar;
}

static inline int ConditionVariable_notify(ConditionVariable *condVar)
{
    return pthread_cond_signal(condVar);
}

static inline int ConditionVariable_wait
    (ConditionVariable *condVar, Mutex *mutex)
{
    return pthread_cond_wait(condVar, mutex);
}
#endif /* #ifdef _WIN32 */

#endif /* _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_CONDITIONVARIABLE_H_ */
