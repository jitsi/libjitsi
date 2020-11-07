/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.impl.neomedia.notify;

import java.applet.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;

import org.jitsi.service.audionotifier.*;

/**
 * Implementation of SCAudioClip.
 *
 * @author Yana Stamcheva
 */
public class JavaSoundClipImpl
    extends AbstractSCAudioClip

{
    private static Constructor<AudioClip> acConstructor = null;

    @SuppressWarnings("unchecked")
    private static Constructor<AudioClip> createAcConstructor()
        throws ClassNotFoundException,
               NoSuchMethodException,
               SecurityException
    {
        Class<?> class1;
        try
        {
            class1
                = Class.forName(
                    "com.sun.media.sound.JavaSoundAudioClip",
                    true,
                    ClassLoader.getSystemClassLoader());
        }
        catch (ClassNotFoundException cnfex)
        {
            class1
                = Class.forName("sun.audio.SunAudioClip", true, null);
        }
        return
            (Constructor<AudioClip>) class1.getConstructor(InputStream.class);
    }

    /**
     * Creates an AppletAudioClip.
     *
     * @param inputstream the audio input stream
     * @throws IOException
     */
    private static AudioClip createAppletAudioClip(InputStream inputstream)
        throws IOException
    {
        if (acConstructor == null)
        {
            try
            {
                acConstructor
                    = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<Constructor<AudioClip>>()
                            {
                                public Constructor<AudioClip> run()
                                    throws ClassNotFoundException,
                                           NoSuchMethodException,
                                           SecurityException
                                {
                                    return createAcConstructor();
                                }
                            });
            }
            catch (PrivilegedActionException paex)
            {
                throw
                    new IOException(
                            "Failed to get AudioClip constructor: "
                                + paex.getException());
            }
        }

        try
        {
            return acConstructor.newInstance(inputstream);
        }
        catch (Exception ex)
        {
            throw new IOException("Failed to construct the AudioClip: " + ex);
        }
    }

    private final AudioClip audioClip;

    /**
     * Initializes a new <tt>JavaSoundClipImpl</tt> instance which is to play
     * audio stored at a specific <tt>URL</tt> using
     * <tt>java.applet.AudioClip</tt>.
     *
     * @param uri the <tt>URL</tt> at which the audio is stored and which the
     * new instance is to load
     * @param audioNotifier the <tt>AudioNotifierService</tt> which is
     * initializing the new instance and whose <tt>mute</tt> property/state is
     * to be monitored by the new instance
     * @throws IOException if a <tt>java.applet.AudioClip</tt> could not be
     * initialized or the audio at the specified <tt>url</tt> could not be read
     */
    public JavaSoundClipImpl(String uri, AudioNotifierService audioNotifier)
            throws IOException
    {
        super(uri, audioNotifier);

        audioClip = createAppletAudioClip(new URL(uri).openStream());
    }

    /**
     * {@inheritDoc}
     *
     * Stops the <tt>java.applet.AudioClip</tt> wrapped by this instance.
     */
    @Override
    protected void internalStop()
    {
        try
        {
            if (audioClip != null)
                audioClip.stop();
        }
        finally
        {
            super.internalStop();
        }
    }

    /**
     * {@inheritDoc}
     *
     * Plays the <tt>java.applet.AudioClip</tt> wrapped by this instance.
     */
    @Override
    protected boolean runOnceInPlayThread()
    {
        if (audioClip == null)
            return false;
        else
        {
            audioClip.play();
            return true;
        }
    }
}
