package org.jitsi.impl.neomedia.java8;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility functions to give Maps functionality similar to Java 1.8.
 */
public class MapExtension
{
    /**
     * Implements similar functionality to the Java 1.8 computeIfAbsent
     */
    public static <K,V> V computeIfAbsent(ConcurrentHashMap<K,V> map, K key, Function<K,V> defaultValue)
    {
        V value = map.get(key);
        if (null == value)
        {
            V newValue = defaultValue.apply(key);
            value = map.putIfAbsent(key, newValue);
            if (value == null)
            {
                value = newValue;
            }
        }
        return value;
    }
}
