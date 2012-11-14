/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

/**
 * Provides the interface to the native FFmpeg library. Extends
 * <tt>net.java.sip.communicator.impl.neomedia.codec.FFmpeg</tt> to allow the
 * Java code to refer to the <tt>FFmpeg</tt> class by the new
 * <tt>org.jitsi.impl.neomedia.codec</tt> package while leaving the
 * corresponding JNI binaries in the old
 * <tt>net.java.sip.communicator.impl.neomedia.codec</tt> package.
 *
 * @author Lyubomir Marinov
 */
public class FFmpeg
    extends net.java.sip.communicator.impl.neomedia.codec.FFmpeg
{
}
