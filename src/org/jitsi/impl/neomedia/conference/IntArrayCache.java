/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.conference;

import java.lang.ref.*;

import javax.media.*;

/**
 * Caches <tt>int</tt> arrays for the purposes of reducing garbage collection.
 *
 * @author Lyubomir Marinov
 */
class IntArrayCache
{
    /**
     * The cache of <tt>int</tt> arrays managed by this instance for the
     * purposes of reducing garbage collection.
     */
    private SoftReference<int[][]> elements;

    /**
     * The number of elements at the head of {@link #elements} which are
     * currently utilized. Introduced to limit the scope of iteration.
     */
    private int length;

    /**
     * Allocates an <tt>int</tt> array with length/size greater than or equal to
     * a specific number. The returned array may be a newly-initialized instance
     * or one of the elements cached/pooled by this instance.
     *
     * @param minSize the minimum length/size of the array to be returned
     * @return an <tt>int</tt> array with length/size greater than or equal to
     * <tt>minSize</tt>
     */
    public synchronized int[] allocateIntArray(int minSize)
    {
        int[][] elements = (this.elements == null) ? null : this.elements.get();

        if (elements != null)
        {
            for (int i = 0; i < length; i++)
            {
                int[] element = elements[i];

                if ((element != null) && element.length >= minSize)
                {
                    elements[i] = null;
                    return element;
                }
            }
        }

        return new int[minSize];
    }

    /**
     * Returns a specific non-<tt>null</tt> <tt>int</tt> array into the
     * cache/pool implemented by this instance.
     *
     * @param intArray the <tt>int</tt> array to be returned into the cache/pool
     * implemented by this instance. If <tt>null</tt>, the method does nothing.
     */
    public synchronized void deallocateIntArray(int[] intArray)
    {
        if (intArray == null)
            return;

        int[][] elements;

        if ((this.elements == null)
                || ((elements = this.elements.get()) == null))
        {
            elements = new int[8][];
            this.elements = new SoftReference<int[][]>(elements);
            length = 0;
        }

        if (length != 0)
            for (int i = 0; i < length; i++)
                if (elements[i] == intArray)
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
                int[] element = elements[i];

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
                int[][] newElements = new int[elements.length + 4][];

                System.arraycopy(elements, 0, newElements, 0, elements.length);
                elements = newElements;
                this.elements = new SoftReference<int[][]>(elements);
            }
            else
            {
                length = newLength;
            }
        }

        elements[length++] = intArray;
    }

    /**
     * Ensures that the <tt>data</tt> property of a specific <tt>Buffer</tt> is
     * set to an <tt>int</tt> array with length/size greater than or equal to a
     * specific number.
     * 
     * @param buffer the <tt>Buffer</tt> the <tt>data</tt> property of which is
     * to be validated
     * @param newSize the minimum length/size of the <tt>int</tt> array to be
     * set as the value of the <tt>data</tt> property of the specified
     * <tt>buffer</tt> and to be returned
     * @return the value of the <tt>data</tt> property of the specified
     * <tt>buffer</tt> which is guaranteed to have a length/size of at least
     * <tt>newSize</tt> elements
     */
    public int[] validateIntArraySize(Buffer buffer, int newSize)
    {
        Object data = buffer.getData();
        int[] intArray;

        if (data instanceof int[])
        {
            intArray = (int[]) data;
            if (intArray.length < newSize)
            {
                deallocateIntArray(intArray);
                intArray = null;
            }
        }
        else
            intArray = null;
        if (intArray == null)
        {
            intArray = allocateIntArray(newSize);
            buffer.setData(intArray);
        }
        return intArray;
    }
}
