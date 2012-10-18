/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
     * Creates the audio clip and initialize the listener used from the
     * loop timer.
     *
     * @param url the url pointing to the audio file
     * @param audioNotifier the audio notify service
     * @throws IOException cannot audio clip with supplied url.
     */
    public JavaSoundClipImpl(URL url, AudioNotifierService audioNotifier)
            throws IOException
    {
        super(url, audioNotifier);

        audioClip = createAppletAudioClip(url.openStream());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void internalStop()
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
     */
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
