/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

/**
 * Encapsulate the concept of packet transformation of some type T. Given (an
 * array possibly) of T packets, a <tt>Transformer</tt> can either "transform"
 * each one of them, or "reverse transform" (e.g. restore) each one of them.
 *
 * @author George Politis
 */
public interface Transformer<T>
{
    /**
     * Transforms each packet in an array of packets. Null values must be
     * ignored.
     *
     * @param t the packets to be transformed
     * @return the transformed packets
     */
    T transform(T t);

    /**
     * Reverse-transforms each packet in an array of packets. Null values
     * must be ignored.
     *
     * @param t the transformed packets to be restored.
     * @return the restored packets.
     */
    T reverseTransform(T t);

    /**
     * Closes this <tt>Transformer</tt> i.e. releases the resources
     * allocated by it and prepares it for garbage collection.
     */
    void close();
}
