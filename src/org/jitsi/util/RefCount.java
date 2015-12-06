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
package org.jitsi.util;

/**
 * A helper class that can be used to track references to an object. This
 * class is not thread-safe.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
public class RefCount<T>
{
    private final T referent;

    private int refCount;

    public RefCount(T referent)
    {
        this.referent = referent;
        this.refCount = 0;
    }

    public void increase()
    {
        refCount++;
    }

    public void decrease()
    {
        refCount--;
    }

    public int get()
    {
        return this.refCount;
    }

    public T getReferent()
    {
        return this.referent;
    }
}
