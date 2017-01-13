package org.jitsi.impl.neomedia.java8;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Jitsi version of Java 1.8 Optional to allow code to have the same expressive elegance.
 */
public final class Optional<T>
{
    private static final Optional<?> EMPTY = new Optional<>();
    private final T value;

    private Optional()
    {
        value = null;
    }

    private Optional(T value)
    {
        this.value = Objects.requireNonNull(value);
    }


    public static <T> Optional<T> of(T value)
    {
        return new Optional<>(value);
    }


    public static <T> Optional<T> ofNullable(T value)
    {
        return (null == value) ? Optional.<T>empty() : of(value);
    }

    public static<T> Optional<T> empty()
    {
        @SuppressWarnings("unchecked")
        Optional<T> optional = (Optional<T>)EMPTY;
        return optional;
    }

    public T get()
    {
        return Objects.requireNonNull(value);
    }

    public boolean isPresent()
    {
        return null != value;
    }

    public T orElse(T other)
    {
        return (null == value) ? other : value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        if (!(o instanceof Optional))
        {
            return false;
        }
        @SuppressWarnings("unchecked")
        Optional<T> other = (Optional<T>)o;
        return value != null && value.equals(other.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(value);
    }

    @Override
    public String toString()
    {
        return (null == value) ? "empty" : "Optional[" + value + "]";
    }
}
