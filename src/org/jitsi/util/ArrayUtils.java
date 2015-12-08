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

import java.lang.reflect.*;

/**
 *
 * @author Lyubomir Marinov
 */
public final class ArrayUtils
{
    /**
     * Adds a specific element to a specific array with a specific component
     * type if the array does not contain the element yet.
     * 
     * @param array the array to add <tt>element</tt> to
     * @param componentType the component type of <tt>array</tt>
     * @param element the element to add to <tt>array</tt>
     * @return an array with the specified <tt>componentType</tt> and
     * containing <tt>element</tt>. If <tt>array</tt> contained <tt>element</tt>
     * already, returns <tt>array</tt>.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] add(T[] array, Class<T> componentType, T element)
    {
        if (element == null)
            throw new NullPointerException("element");

        if (array == null)
        {
            array = (T[]) Array.newInstance(componentType, 1);
        }
        else
        {
            for (T a : array)
            {
                if (element.equals(a))
                    return array;
            }

            T[] newArray
                = (T[]) Array.newInstance(componentType, array.length + 1);

            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }
        array[array.length - 1] = element;
        return array;
    }

    /** Prevents the initialization of new {@code ArrayUtils} instances. */
    private ArrayUtils()
    {
    }
}
