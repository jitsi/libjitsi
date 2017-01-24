package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.java8.BiFunction;
import org.jitsi.impl.neomedia.java8.MapExtension;
import org.jitsi.impl.neomedia.java8.Function;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A specialized map implementation that uses multiple keys to map the value.
 *
 * This implementation optimizes for get and put operations that do not involve the
 * creation of objects---an attempt to make a high volume of operations be GC wait free.
 *
 * This implementation may be inefficient if the set size of K1 is large.
 */
public class ConcurrentMultiKeyMap<K1,K2,V>
{
    private final ConcurrentHashMap<K1, ConcurrentHashMap<K2, V>> map;

    public ConcurrentMultiKeyMap() {
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Obtain the map of secondary key to value of all mappings
     * where the primary key is the specified key.
     */
    public ConcurrentHashMap<K2,V> get(K1 key1)
    {
        return populateSubMapIfAbsent(key1);
    }

    /**
     * Return the value for the specified key or <tt>null</tt> if this map
     * contains no mapping for this key.
     */
    public V get(K1 key1, K2 key2)
    {
        Map<K2, V> subMap = populateSubMapIfAbsent(key1);
        return subMap.get(key2);
    }

    private ConcurrentHashMap<K2,V> populateSubMapIfAbsent(K1 key1)
    {
        return MapExtension.computeIfAbsent(map, key1, new Function<K1, ConcurrentHashMap<K2, V>>()
        {
            @Override
            public ConcurrentHashMap<K2, V> apply(K1 var)
            {
                return new ConcurrentHashMap<>();
            }
        });
    }

    /**
     * If the specified key is not already associated with a value,
     * associate it with the given value.
     * Same as {@link ConcurrentMap.putIfAbsent} except with a multiple value key.
     *
     * @return previous value of associated with key or <tt>null</tt> if there was
     *   no mapping for this key.
     */
    public V putIfAbsent(K1 key1, K2 key2, V value)
    {
        ConcurrentHashMap<K2,V> subMap = populateSubMapIfAbsent(key1);
        return subMap.putIfAbsent(key2, value);
    }

    /**
     * If the specified key is not already associated with a value, run
     * the given mapping function and enter the value into the map.
     * Same as Java 1.8 {@link ConcurrentMap.computeIfAbsent} except with a multiple value key.
     *
     * @return the current (existing or computed) value associated with the key,
     *    or <tt>null</tt> if the computed value is <tt>null</tt>.
     */
    public V computeIfAbsent(K1 key1, K2 key2, BiFunction<K1,K2,V> defaultValue)
    {
        V value = get(key1, key2);
        if (null == value)
        {
            V newValue = defaultValue.apply(key1, key2);
            value = putIfAbsent(key1, key2, newValue);
            if (value == null)
            {
                value = newValue;
            }
        }
        return value;
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.
     */
    public Set<Map.Entry<K1,ConcurrentHashMap<K2, V>>> entrySet()
    {
        return map.entrySet();
    }
}
