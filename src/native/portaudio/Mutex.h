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

#ifndef _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_MUTEX_H_
#define _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_MUTEX_H_

#include <stdlib.h>

#ifdef _WIN32
#include <windows.h>

typedef CRITICAL_SECTION Mutex;

static inline void Mutex_free(Mutex* mutex)
{
    DeleteCriticalSection(mutex);
    free(mutex);
}

static inline int Mutex_lock(Mutex* mutex)
{
    EnterCriticalSection(mutex);
    return 0;
}

static inline Mutex *Mutex_new(void* attr)
{
    Mutex *mutex = malloc(sizeof(Mutex));

    (void) attr;

    if (mutex)
        InitializeCriticalSection(mutex);
    return mutex;
}

static inline int Mutex_unlock(Mutex* mutex)
{
    LeaveCriticalSection(mutex);
    return 0;
}

#else /* #ifdef _WIN32 */
#include <pthread.h>

typedef pthread_mutex_t Mutex;

static inline void Mutex_free(Mutex* mutex)
{
    if (!pthread_mutex_destroy(mutex))
        free(mutex);
}

static inline int Mutex_lock(Mutex* mutex)
{
    return pthread_mutex_lock(mutex);
}

static inline Mutex *Mutex_new(void* attr)
{
    Mutex *mutex = malloc(sizeof(Mutex));

    if (mutex && pthread_mutex_init(mutex, attr))
    {
        free(mutex);
        mutex = NULL;
    }
    return mutex;
}

static inline int Mutex_unlock(Mutex* mutex)
{
    return pthread_mutex_unlock(mutex);
}
#endif /* #ifdef _WIN32 */

#endif /* #ifndef _ORG_JITSI_IMPL_NEOMEDIA_PORTAUDIO_MUTEX_H_ */
