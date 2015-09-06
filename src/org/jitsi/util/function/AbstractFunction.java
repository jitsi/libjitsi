/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.util.function;

/**
 * Represents a function that accepts one argument and produces a result. This
 * is a poor man's backport of the <tt>Function</tt> interface found in Java
 * 1.8.
 *
 * @author George Politis
 */
public abstract class AbstractFunction<T, R>
{
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     */
    public abstract R apply(T t);

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of input to the {@code before} function, and to the
     *           composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws NullPointerException if before is null
     *
     * @see #andThen(AbstractFunction)
     */
    public <V> AbstractFunction<V, R> compose(
            final AbstractFunction<? super V, ? extends T> before) {

        if (before == null)
        {
            throw new NullPointerException();
        }

        return new AbstractFunction<V, R>()
        {
            @Override
            public R apply(V v)
            {
                return AbstractFunction.this.apply(before.apply(v));
            }
        };
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     *
     * @see #compose(AbstractFunction)
     */
    public <V> AbstractFunction<T, V> andThen(
            final AbstractFunction<? super R, ? extends V> after) {

        if (after == null)
        {
            throw new NullPointerException();
        }

        return new AbstractFunction<T, V>()
        {
            @Override
            public V apply(T t)
            {
                return after.apply(AbstractFunction.this.apply(t));
            }
        };
    }

}
