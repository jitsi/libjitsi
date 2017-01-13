package org.jitsi.impl.neomedia.java8;

import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class MapExtensionTest
{
    private static final Integer MAGIC = 0xd0b;
    private static final Integer MAGIC_TWO = 0xd0bd0b;

    private final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();


    @Test
    public void testComputeIfAbsent() {
        Integer value = MapExtension.computeIfAbsent(map, 0, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer var) {
                return MAGIC;
            }
        });
        assertEquals(MAGIC, value);
        computeIfAbsentShouldNoopComputeForSecondCall();
    }

    private void computeIfAbsentShouldNoopComputeForSecondCall() {
        Integer value = MapExtension.computeIfAbsent(map, 0, new Function<Integer, Integer>() {
            @Override
            public Integer apply(Integer var) {
                return MAGIC_TWO;
            }
        });
        assertEquals(MAGIC, value);
    }
}
