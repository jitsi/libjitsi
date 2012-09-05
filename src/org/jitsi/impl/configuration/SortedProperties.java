/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

import java.util.*;

/**
 * This class is a sorted version of classical <tt>java.util.Properties</tt>.
 * It is strongly inspired by http://forums.sun.com/thread.jspa?threadID=141144.
 *
 * @author Sebastien Vincent
 */
public class SortedProperties extends Properties
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Get an enumeration of keys hold by the <tt>Properties</tt> object.
     * Contrary to the original <tt>Properties</tt> implementation, it forces
     * the keys to be sorted alphabetically.
     *
     * @return enumeration of the keys hold by the <tt>Properties</tt>.
     */
    public synchronized Enumeration<Object> keys()
    {
        final Object[] keys = keySet().toArray();
        Arrays.sort(keys);

        return new Enumeration<Object>()
        {
            int i = 0;

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
     * Do not allow putting empty String keys in the properties table.
     * @param key the key
     * @param value the value
     * @return the previous value of the specified key in this hashtable,
     *         or <code>null</code> if it did not have one
     */
    public synchronized Object put(Object key, Object value)
    {
        if(key instanceof String
            && ((String)key).trim().length() == 0)
        {
            // just skip the putting
            return null;
        }

        return super.put(key, value);
    }
}
