package org.jitsi.impl.neomedia.stats;

import org.jitsi.impl.neomedia.java8.BiFunction;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConcurrentMultiKeyMapTest {

    private static final Integer MAGIC = 0xd0b;
    private static final Integer MAGIC_TWO = 0xd0bd0b;
    private ConcurrentMultiKeyMap<Integer, Integer, Integer> map;

    @Before
    public void setup() {
        map = new ConcurrentMultiKeyMap<>();
    }

    @Test
    public void testGetSubmap() {
        Map<Integer, Integer> subMap = map.get(0);
        subMap.put(0, MAGIC);
        assertEquals(MAGIC, map.get(0, 0));
    }

    @Test
    public void testEmptyGet() {
        assertEquals(null, map.get(0, 0));
    }

    @Test
    public void testPutIfAbsent() {
        Integer previous = map.putIfAbsent(0, 0, MAGIC);
        assertEquals(null, previous);
        putIfAbsentShouldNoopSecondCall();
    }

    private void putIfAbsentShouldNoopSecondCall() {
        Integer previous = map.putIfAbsent(0, 0, MAGIC_TWO);
        assertEquals(MAGIC, previous);
    }

    @Test
    public void testGet() {
        map.putIfAbsent(0, 0, MAGIC);
        assertEquals(MAGIC, map.get(0, 0));
    }

    @Test
    public void testGet2() {
        map.putIfAbsent(1, 3, MAGIC);
        map.putIfAbsent(7, 2, MAGIC_TWO);
        assertEquals(MAGIC_TWO, map.get(7, 2));
    }


    @Test
    public void testComputeIfAbsent() {
        Integer value = map.computeIfAbsent(0, 0, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer var, Integer var2) {
                return MAGIC;
            }
        });
        assertEquals(MAGIC, value);
        computeIfAbsentShouldNoopComputeForSecondCall();
    }

    private void computeIfAbsentShouldNoopComputeForSecondCall() {
        Integer value = map.computeIfAbsent(0, 0, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer var, Integer var2) {
                return MAGIC_TWO;
            }
        });
        assertEquals(MAGIC, value);
    }
}
