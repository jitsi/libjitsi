package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.java8.BiFunction;
import org.jitsi.impl.neomedia.java8.Function;
import org.jitsi.impl.neomedia.java8.MapExtension;
import org.jitsi.impl.neomedia.java8.Optional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A table which stores client counter values with multiple key formats.
 *
 * Supported key formats:
 * 1) string - for statistics that pertain to no specific stream.
 * 2) (stream-id, string) - for statistics that pertain to a specific stream.
 *
 * The keys only need to be unique within a given client.
 */
public class StatisticsTable
{
    private final ConcurrentHashMap<String, Counter> map;
    private final ConcurrentMultiKeyMap<Long, String, Counter> streamIdMap;
    private final Optional<String> keyPrefix;

    /**
     * Constructs a root statistics table.
     */
    public StatisticsTable()
    {
        this(Optional.<String>empty());
    }

    private StatisticsTable(Optional<String> keyPrefix)
    {
        this(keyPrefix, new ConcurrentHashMap<String, Counter>(), new ConcurrentMultiKeyMap<Long,String,Counter>());
    }

    private StatisticsTable(Optional<String> keyPrefix,
                            ConcurrentHashMap<String, Counter> map,
                            ConcurrentMultiKeyMap<Long, String, Counter> streamIdMap)
    {
        this.keyPrefix = keyPrefix;
        this.map = map;
        this.streamIdMap = streamIdMap;
    }

    /**
     * Creates a view of this statistic table with a different key prefix.
     * Any modifications to the underlying maps are reflected in the primary
     * StatisticsTable.
     */
    public StatisticsTable withKeyPrefix(String prefix)
    {
        return new StatisticsTable(Optional.of(prefix), map, streamIdMap);
    }

    private static String append(Optional<String> prefix, String key)
    {
        if (prefix.isPresent())
        {
            return String.format("%s.%s", prefix.get(), key);
        }
        else
        {
            return key;
        }
    }

    /**
     * Obtain a counter by streamId and String name.
     * This method is provided for debugging flexibility, but it is recommended
     * that you use {@link get(long,CounterName)} for official counters.
     */
    public Counter get(long streamId, String name)
    {
        return streamIdMap.computeIfAbsent(
            streamId, append(keyPrefix, name),
            new BiFunction<Long, String, Counter>()
            {
                @Override
                public Counter apply(Long id, String specifier)
                {
                    return new Counter();
                }
            });
    }

    /**
     * Obtain a counter by streamId and CounterName.
     * Any official counters should use this method to enable
     * typechecked correctness for counter definition as well
     * as future proof additional counter options in the future.
     */
    public Counter get(long streamId, CounterName name)
    {
        return get(streamId, name.getName());
    }

    /**
     * Obtains a getter function to obtain the counters associated
     * with a specific streamId.
     * This allows for efficiency when referencing multiple counters
     * of the same streamId.
     *
     * Example:
     * <pre>{@code
     *  GetCounterWithStreamId counter = stastisticsTable.get(streamId);
     *  counter.get(CounterName.RATE_CONTROL_HOLD).incrementAndGet();
     *  counter.get(CounterName.RATE_CONTROL_INCREASE).incrementAndGet();
     * }</pre>
     */
    public GetCounterWithStreamId get(final long streamId)
    {
        final StatisticsTable table = this;
        return new GetCounterWithStreamId() {
            @Override
            public Counter get(String name) {
                return table.get(streamId, name);
            }
        };
    }

    public abstract class GetCounterWithStreamId
    {
        abstract public Counter get(String name);

        public Counter get(CounterName name) {
            return get(name.getName());
        }
    }

    /**
     * Obtain a Counter by string that is unassociated with any specific streamId.
     * This method is provided for debugging flexibility, but it is recommended
     * that you use {@link get(CounterName)} for official counters.
     */
    public Counter get(String specifier)
    {
        return MapExtension.computeIfAbsent(map,
            append(keyPrefix, specifier),
            new Function<String, Counter>()
            {
                @Override
                public Counter apply(String specifier)
                {
                    return new Counter();
                }
            });
    }

    /**
     * Obtain a Counter by CounterName which is unassociate with any specific
     * streamId.
     * Any official counters should use this method to enable
     * typechecked correctness for counter definition as well
     * as future proof additional counter options in the future.
     */
    public Counter get(CounterName name) {
        return get(name.getName());
    }

    /**
     * Returns a single map view of the statisics table.
     *
     * This operation allocates Key objects which contains the muti-key parameters.
     */
    public Map<Key,Long> toMap()
    {
        Map<Key, Long> result = new HashMap<>();
        for (Map.Entry<Long, ConcurrentHashMap<String, Counter>> entry : streamIdMap.entrySet())
        {
            for  (Map.Entry<String, Counter> counterEntry : entry.getValue().entrySet())
            {
                Key key = new Key(Optional.of(entry.getKey()), counterEntry.getKey());
                result.put(key, counterEntry.getValue().get());
            }
        }
        for (Map.Entry<String, Counter> entry: map.entrySet())
        {
            Key key = new Key(Optional.<Long>empty(), entry.getKey());
            result.put(key, entry.getValue().get());
        }
        return result;
    }

    /**
     * A multi-value key which is used to map counter values.
     */
    public static class Key
    {
        private Optional<Long> streamId;
        private String name;

        public Key(Optional<Long> streamId, String name)
        {
            this.streamId = streamId;
            this.name = name;
        }

        public Optional<Long> getStreamId()
        {
            return streamId;
        }

        public String getName()
        {
            return name;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof Key)
            {
                Key other = (Key)obj;
                return streamId.equals(other.getStreamId()) && name.equals(other.getName());
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return streamId.hashCode() * 31 + name.hashCode();
        }
    }
}
