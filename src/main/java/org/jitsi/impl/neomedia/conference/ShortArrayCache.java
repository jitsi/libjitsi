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
package org.jitsi.impl.neomedia.conference;

import java.lang.ref.*;

import javax.media.*;

/**
 * Caches <tt>short</tt> arrays for the purposes of reducing garbage collection.
 *
 * @author Lyubomir Marinov
 */
class ShortArrayCache
{
    /**
     * The cache of <tt>short</tt> arrays managed by this instance for the
     * purposes of reducing garbage collection.
     */
    private SoftReference<short[][]> elements;

    /**
     * The number of elements at the head of {@link #elements} which are
     * currently utilized. Introduced to limit the scope of iteration.
     */
    private int length;

    /**
     * Allocates a <tt>short</tt> array with length/size greater than or equal
     * to a specific number. The returned array may be a newly-initialized
     * instance or one of the elements cached/pooled by this instance.
     *
     * @param minSize the minimum length/size of the array to be returned
     * @return a <tt>short</tt> array with length/size greater than or equal to
     * <tt>minSize</tt>
     */
    public synchronized short[] allocateShortArray(int minSize)
    {
        short[][] elements
            = (this.elements == null) ? null : this.elements.get();

        if (elements != null)
        {
            for (int i = 0; i < length; i++)
            {
                short[] element = elements[i];

                if ((element != null) && element.length >= minSize)
                {
                    elements[i] = null;
                    return element;
                }
            }
        }

        return new short[minSize];
    }

    /**
     * Returns a specific non-<tt>null</tt> <tt>short</tt> array into the
     * cache/pool implemented by this instance.
     *
     * @param shortArray the <tt>short</tt> array to be returned into the
     * cache/pool  implemented by this instance. If <tt>null</tt>, the method
     * does nothing.
     */
    public synchronized void deallocateShortArray(short[] shortArray)
    {
        if (shortArray == null)
            return;

        short[][] elements;

        if ((this.elements == null)
                || ((elements = this.elements.get()) == null))
        {
            elements = new short[8][];
            this.elements = new SoftReference<short[][]>(elements);
            length = 0;
        }

        if (length != 0)
            for (int i = 0; i < length; i++)
                if (elements[i] == shortArray)
                    return;

        if (length == elements.length)
        {
            /*
             * Compact the non-null elements at the head of the storage in order
             * to possibly prevent reallocation.
             */
            int newLength = 0;

            for (int i = 0; i < length; i++)
            {
                short[] element = elements[i];

                if (element != null)
                {
                    if (i != newLength)
                    {
                        elements[newLength] = element;
                        elements[i] = null;
                    }
                    newLength++;
                }
            }

            if (newLength == length)
            {
                // Expand the storage.
                short[][] newElements = new short[elements.length + 4][];

                System.arraycopy(elements, 0, newElements, 0, elements.length);
                elements = newElements;
                this.elements = new SoftReference<short[][]>(elements);
            }
            else
            {
                length = newLength;
            }
        }

        elements[length++] = shortArray;
    }

    /**
     * Ensures that the <tt>data</tt> property of a specific <tt>Buffer</tt> is
     * set to an <tt>short</tt> array with length/size greater than or equal to
     * a specific number.
     * 
     * @param buffer the <tt>Buffer</tt> the <tt>data</tt> property of which is
     * to be validated
     * @param newSize the minimum length/size of the <tt>short</tt> array to be
     * set as the value of the <tt>data</tt> property of the specified
     * <tt>buffer</tt> and to be returned
     * @return the value of the <tt>data</tt> property of the specified
     * <tt>buffer</tt> which is guaranteed to have a length/size of at least
     * <tt>newSize</tt> elements
     */
    public short[] validateShortArraySize(Buffer buffer, int newSize)
    {
        Object data = buffer.getData();
        short[] shortArray;

        if (data instanceof short[])
        {
            shortArray = (short[]) data;
            if (shortArray.length < newSize)
            {
                deallocateShortArray(shortArray);
                shortArray = null;
            }
        }
        else
            shortArray = null;
        if (shortArray == null)
        {
            shortArray = allocateShortArray(newSize);
            buffer.setData(shortArray);
        }
        return shortArray;
    }
}
