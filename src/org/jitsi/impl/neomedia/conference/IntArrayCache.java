/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.conference;

import java.lang.ref.*;
import java.util.*;

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
    private SoftReference<List<int[]>> intArrays;

    public synchronized int[] allocateIntArray(int minSize)
    {
        List<int[]> intArrays
            = (this.intArrays == null) ? null : this.intArrays.get();

        if (intArrays != null)
        {
            Iterator<int[]> i = intArrays.iterator();

            while (i.hasNext())
            {
                int[] intArray = i.next();

                if (intArray.length >= minSize)
                {
                    i.remove();
                    return intArray;
                }
            }
        }

        return new int[minSize];
    }

    public synchronized void deallocateIntArray(int[] intArray)
    {
        if (intArray == null)
            return;

        List<int[]> intArrays;

        if ((this.intArrays == null)
                || ((intArrays = this.intArrays.get()) == null))
        {
            intArrays = new LinkedList<int[]>();
            this.intArrays = new SoftReference<List<int[]>>(intArrays);
        }

        if (intArrays.size() != 0)
            for (int[] element : intArrays)
                if (element == intArray)
                    return;

        intArrays.add(intArray);
    }

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
