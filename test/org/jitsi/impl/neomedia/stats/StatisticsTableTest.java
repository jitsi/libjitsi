package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.java8.Optional;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StatisticsTableTest {

    private StatisticsTable table;
    private long MAGIC_VALUE = 0xbaL;
    private String COUNTER_NAME = "bababa";

    @Before
    public void setup()
    {
        table = new StatisticsTable();
    }

    @Test
    public void testGetCreatesNewCounter()
    {
        Counter counter = table.get("");
        assertEquals(0, counter.get());
    }

    @Test
    public void testIdMultiGetCreatesNewCounter()
    {
        Counter counter = table.get(0, "");
        assertEquals(0, counter.get());
    }

    @Test
    public void testGetReturnsSameCounter()
    {
        Counter counter = table.get(0, "");
        Counter counter2 = table.get(0, "");
        assertTrue(counter == counter2);
    }

    @Test
    public void testGetCounterWithId()
    {
        StatisticsTable.GetCounterWithStreamId getter = table.get(0);
        Counter counter = getter.get(COUNTER_NAME);
        assertEquals(0, counter.get());
        assertTrue(counter == table.get(0, COUNTER_NAME));
    }

    @Test
    public void testGetWithCounterNames()
    {
        Counter counter;
        counter = table.get(CounterName.AVAILABLE_SEND_BANDWIDTH_BPS);
        assertEquals(0, counter.get());

        counter = table.get(0, CounterName.AVAILABLE_SEND_BANDWIDTH_BPS);
        assertEquals(0, counter.get());

        counter = table.get(0).get(CounterName.AVAILABLE_SEND_BANDWIDTH_BPS);
        assertEquals(0, counter.get());
    }

    @Test
    public void testWithKeyPrefixReturnsNamespacedTable()
    {
        Counter counter = table.get(0, "hello.jo");
        Counter counter2 = table.withKeyPrefix("hello").get(0, "jo");
        assertTrue(counter == counter2);
    }

    @Test
    public void testToMapContainsMapContents()
    {
        table.get(0, COUNTER_NAME).set(MAGIC_VALUE);
        Map<StatisticsTable.Key, Long> map = table.toMap();
        Long count = map.get(new StatisticsTable.Key(Optional.of(0), COUNTER_NAME));
        assertEquals(MAGIC_VALUE, count.longValue());
    }
}
