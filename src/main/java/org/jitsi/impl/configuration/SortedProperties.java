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
package org.jitsi.impl.configuration;

import java.util.*;

/**
 * This class is a sorted version of classical <tt>java.util.Properties</tt>. It
 * is strongly inspired by http://forums.sun.com/thread.jspa?threadID=141144.
 *
 * @author Sebastien Vincent
 * @author Damian Minkov
 */
public class SortedProperties
    extends Properties
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Gets an <tt>Enumeration</tt> of the keys in this <tt>Properties</tt>
     * object. Contrary to the original <tt>Properties</tt> implementation, it
     * forces the keys to be alphabetically sorted.
     *
     * @return an <tt>Enumeration</tt> of the keys in this <tt>Properties</tt>
     * object
     */
    @Override
    public synchronized Enumeration<Object> keys()
    {
        final Object[] keys = keySet().toArray();

        Arrays.sort(keys);
        return
            new Enumeration<Object>()
                    {
                        private int i = 0;

                        public boolean hasMoreElements()
                        {
                            return i < keys.length;
                        }

                        public Object nextElement()
                        {
                            return keys[i++];
                        }
                    };
    }

    /**
     * Does not allow putting empty <tt>String</tt> keys in this
     * <tt>Properties</tt> object.
     *
     * @param key the key
     * @param value the value
     * @return the previous value of the specified <tt>key</tt> in this
     * <tt>Hashtable</tt>, or <tt>null</tt> if it did not have one
     */
    @Override
    public synchronized Object put(Object key, Object value)
    {
        /*
         * We discovered a special case related to the Properties
         * ConfigurationService implementation during testing in which the key
         * was a String composed of null characters only (which would be
         * trimmed) consumed megabytes of heap. Do now allow such keys.
         */
        if (key.toString().trim().length() == 0)
            return null;

        return super.put(key, value);
    }
}
