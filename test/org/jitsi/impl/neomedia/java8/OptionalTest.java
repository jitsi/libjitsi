package org.jitsi.impl.neomedia.java8;

import org.junit.Test;

import static org.junit.Assert.*;

public class OptionalTest {

    private static final Integer MAGIC = 0xd0d0;
    private static final Integer OTHER_MAGIC = 0x9d9d;

    @Test(expected=NullPointerException.class)
    public void testOfNullExpectsNullPointerException()
    {
        Optional.of(null);
    }

    @Test
    public void testOfNullableNullObject()
    {
        Optional<?> nullOption = Optional.ofNullable(null);
        assertEquals(Optional.empty(), nullOption);
    }

    @Test
    public void testOfNullable()
    {
        Optional<?> a = Optional.ofNullable(MAGIC);
        assertEquals(MAGIC, a.get());
    }

    @Test
    public void testIsPresent()
    {
        assertFalse(Optional.empty().isPresent());
        assertTrue(Optional.of(MAGIC).isPresent());
    }

    @Test
    public void testGet()
    {
        assertEquals(MAGIC, Optional.of(MAGIC).get());
    }

    @Test(expected=NullPointerException.class)
    public void testGetExpectNullPointerException()
    {
        Optional.empty().get();
    }

    @Test
    public void testOrElse()
    {
        assertEquals(MAGIC, Optional.of(MAGIC).orElse(OTHER_MAGIC));
        assertEquals(OTHER_MAGIC, Optional.empty().orElse(OTHER_MAGIC));
    }

    @Test
    public void testEqualsSameObject()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        assertTrue(a.equals(a));
    }

    @Test
    public void testEqualsDifferentObjectSameValue()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        Optional<Integer> b = Optional.of(MAGIC);
        assertTrue(a.equals(b));
    }

    @Test
    public void testEqualsDifferentValues()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        Optional<Integer> b = Optional.of(MAGIC+1);
        assertFalse(a.equals(b));
    }

    @Test
    public void testEqualsNull()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        assertFalse(a.equals(null));
    }

    @Test
    public void testEqualsDifferentTypes()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        Optional<Long> b = Optional.of((long)MAGIC);
        assertFalse(a.equals(b));
    }

    @Test
    public void testEqualsEmpty()
    {
        assertTrue(Optional.empty().equals(Optional.empty()));
        assertFalse(Optional.empty().equals(Optional.of(MAGIC)));
    }

    @Test
    public void testEqualsNonOption()
    {
        Optional<Integer> a = Optional.of(MAGIC);
        assertFalse(a.equals(MAGIC));
    }
}
