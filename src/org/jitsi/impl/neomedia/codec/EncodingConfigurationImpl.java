/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

import java.io.*;
import java.util.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;

/**
 * Configuration of encoding priorities.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class EncodingConfigurationImpl extends EncodingConfiguration
{
    /**
     * The indicator which determines whether the G.729 codec is enabled.
     *
     * WARNING: The use of G.729 may require a license fee and/or royalty fee in
     * some countries and is licensed by
     * <a href="http://www.sipro.com">SIPRO Lab Telecom</a>.
     */
    public static final boolean G729 = false;

    /**
     * The additional custom JMF codecs.
     */
    private static final String[] CUSTOM_CODECS =
        {
            "org.jitsi.impl.neomedia.codec.audio.alaw.DePacketizer",
            "org.jitsi.impl.neomedia.codec.audio.alaw.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.audio.alaw.Packetizer",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.audio.ulaw.Packetizer",
            "org.jitsi.impl.neomedia.codec.audio.opus.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.audio.opus.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.speex.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.audio.speex.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.speex.SpeexResampler",
            "org.jitsi.impl.neomedia.codec.audio.speex.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.speex.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.audio.mp3.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.ilbc.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.ilbc.JavaEncoder",
            G729
                ? "org.jitsi.impl.neomedia.codec.audio.g729.JavaDecoder"
                : null,
            G729
                ? "org.jitsi.impl.neomedia.codec.audio.g729.JavaEncoder"
                : null,
            "net.java.sip.communicator.impl.neomedia.codec.audio.g722.JNIDecoder",
            "net.java.sip.communicator.impl.neomedia.codec.audio.g722.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Decoder",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Encoder",
            "org.jitsi.impl.neomedia.codec.audio.gsm.DePacketizer",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Packetizer",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaEncoder",
            "org.jitsi.impl.neomedia.codec.video.h263p.DePacketizer",
            "org.jitsi.impl.neomedia.codec.video.h263p.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.video.h263p.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.video.h263p.Packetizer",
            "org.jitsi.impl.neomedia.codec.video.h264.DePacketizer",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.video.h264.Packetizer",
            "org.jitsi.impl.neomedia.codec.video.SwScaler"
        };

    /**
     * Whether custom codecs have been registered with JFM
     */
    private static boolean codecsRegistered = false;

    /**
     * Whether custom packages have been registered with JFM
     */
    private static boolean packagesRegistered = false;

    /**
     * The package prefixes of the additional JMF <tt>DataSource</tt>s (e.g. low
     * latency PortAudio and ALSA <tt>CaptureDevice</tt>s).
     */
    private static final String[] CUSTOM_PACKAGES
        = new String[]
                {
                    "org.jitsi.impl.neomedia.jmfext",
                    "net.java.sip.communicator.impl.neomedia.jmfext",
                    "net.sf.fmj"
                };

    /**
     * Constructor. Loads the hard-coded default preferences and registers
     * packages and codecs with JMF.
     */
    public EncodingConfigurationImpl()
    {
        initializeFormatPreferences();

        registerCustomPackages();
        registerCustomCodecs();
    }

    /**
     * Sets default format preferences.
     */
    private void initializeFormatPreferences()
    {
        // first init default preferences
        // video
        setEncodingPreference(
            "H264",
            VideoMediaFormatImpl.DEFAULT_CLOCK_RATE,
            1100);

        setEncodingPreference(
            "H263-1998",
            VideoMediaFormatImpl.DEFAULT_CLOCK_RATE,
            0);
        /*
        setEncodingPreference(
            "H263",
            VideoMediaFormatImpl.DEFAULT_CLOCK_RATE,
            1000);
        */
        setEncodingPreference(
            "JPEG",
            VideoMediaFormatImpl.DEFAULT_CLOCK_RATE,
            950);
        setEncodingPreference(
            "H261",
            VideoMediaFormatImpl.DEFAULT_CLOCK_RATE,
            800);

        // audio
        setEncodingPreference("G722", 8000 /* actually, 16 kHz */, 705);
        setEncodingPreference("SILK", 24000, 704);
        setEncodingPreference("SILK", 16000, 703);
        setEncodingPreference("speex", 32000, 701);
        setEncodingPreference("speex", 16000, 700);
        setEncodingPreference("PCMU", 8000, 650);
        setEncodingPreference("PCMA", 8000, 600);
        setEncodingPreference("iLBC", 8000, 500);
        setEncodingPreference("GSM", 8000, 450);
        setEncodingPreference("speex", 8000, 352);
        setEncodingPreference("DVI4", 8000, 300);
        setEncodingPreference("DVI4", 16000, 250);
        setEncodingPreference("G723", 8000, 150);

        setEncodingPreference("SILK", 12000, 0);
        setEncodingPreference("SILK", 8000, 0);
        setEncodingPreference("G729", 8000, 0 /* proprietary */);
        setEncodingPreference("opus", 48000, 2);

        // enables by default telephone event(DTMF rfc4733), with lowest
        // priority as it is not needed to order it with audio codecs
        setEncodingPreference(Constants.TELEPHONE_EVENT, 8000, 1);
    }

    /**
     * Sets the priority of the given encoding and updates the configuration
     * via the configuration service.
     *
     * @param encoding the <tt>MediaFormat</tt> specifying the encoding to set
     * the priority of
     * @param priority a positive <tt>int</tt> indicating the priority of
     * <tt>encoding</tt> to set
     * @param updateConfig Whether configuration should be updated.
     */
    @Override
    public void setPriority(MediaFormat encoding, int priority,
        boolean updateConfig)
    {
        super.setPriority(encoding, priority);
        
        if(updateConfig)
        {
            // save the settings
            //TODO: remove the whole method
            LibJitsi.getConfigurationService().setProperty(
                    "net.java.sip.communicator.impl.neomedia.codec.EncodingConfiguration"
                        + "."
                        + getEncodingPreferenceKey(encoding),
                    priority);
        }
    }

    /**
     * Register in JMF the custom codecs we provide
     */
    private void registerCustomCodecs()
    {
        if(codecsRegistered)
        {
            return;
        }
        
        // Register the custom codec which haven't already been registered.
        @SuppressWarnings("unchecked")
        Collection<String> registeredPlugins
            = new HashSet<String>(
                    PlugInManager.getPlugInList(
                            null,
                            null,
                            PlugInManager.CODEC));
        boolean commit = false;

        // Remove JavaRGBToYUV.
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.JavaRGBToYUV",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.JavaRGBConverter",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.colorspace.RGBScaler",
                PlugInManager.CODEC);

        // Remove JMF's H263 codec.
        PlugInManager.removePlugIn(
                "com.sun.media.codec.video.vh263.NativeDecoder",
                PlugInManager.CODEC);
        PlugInManager.removePlugIn(
                "com.ibm.media.codec.video.h263.NativeEncoder",
                PlugInManager.CODEC);

        // Remove JMF's GSM codec. As working only on some OS.
        String gsmCodecPackage = "com.ibm.media.codec.audio.gsm.";
        String[] gsmCodecClasses
            = new String[]
                    {
                        "JavaDecoder",
                        "JavaDecoder_ms",
                        "JavaEncoder",
                        "JavaEncoder_ms",
                        "NativeDecoder",
                        "NativeDecoder_ms",
                        "NativeEncoder",
                        "NativeEncoder_ms",
                        "Packetizer"
                    };
        for(String gsmCodecClass : gsmCodecClasses)
        {
            PlugInManager.removePlugIn(
                gsmCodecPackage + gsmCodecClass,
                PlugInManager.CODEC);
        }

        /*
         * Remove FMJ's JavaSoundCodec because it seems to slow down the
         * building of the filter graph and we do not currently seem to need it.
         */
        PlugInManager.removePlugIn(
                "net.sf.fmj.media.codec.JavaSoundCodec",
                PlugInManager.CODEC);

        for (String className : CUSTOM_CODECS)
        {

            /*
             * A codec with a className of null is configured at compile time to
             * not be registered.
             */
            if (className == null)
                continue;

            if (registeredPlugins.contains(className))
            {
                if (logger.isDebugEnabled())
                    logger.debug(
                        "Codec " + className + " is already registered");
            }
            else
            {
                commit = true;

                boolean registered;
                Throwable exception = null;

                try
                {
                    Codec codec = (Codec)
                        Class.forName(className).newInstance();

                    registered =
                        PlugInManager.addPlugIn(
                            className,
                            codec.getSupportedInputFormats(),
                            codec.getSupportedOutputFormats(null),
                            PlugInManager.CODEC);
                }
                catch (Throwable ex)
                {
                    registered = false;
                    exception = ex;
                }
                if (registered)
                {
                    if (logger.isDebugEnabled())
                        logger.debug(
                            "Codec "
                                + className
                                + " is successfully registered");
                }
                else
                {
                    if (logger.isDebugEnabled())
                        logger.debug(
                            "Codec "
                                + className
                                + " is NOT succsefully registered", exception);
                }
            }
        }

        /*
         * If Jitsi provides a codec which is also provided by FMJ and/or JMF,
         * use Jitsi's version.
         */
        @SuppressWarnings("unchecked")
        Vector<String> codecs
            = PlugInManager.getPlugInList(null, null, PlugInManager.CODEC);

        if (codecs != null)
        {
            boolean setPlugInList = false;

            for (int i = CUSTOM_CODECS.length - 1; i >= 0; i--)
            {
                String className = CUSTOM_CODECS[i];

                if (className != null)
                {
                    int classNameIndex = codecs.indexOf(className);

                    if (classNameIndex != -1)
                    {
                        codecs.remove(classNameIndex);
                        codecs.add(0, className);
                        setPlugInList = true;
                    }
                }
            }

            if (setPlugInList)
                PlugInManager.setPlugInList(codecs, PlugInManager.CODEC);
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad())
        {
            try
            {
                PlugInManager.commit();
            }
            catch (IOException ex)
            {
                logger.error("Cannot commit to PlugInManager", ex);
            }
        }
        
        codecsRegistered = true;
    }

    /**
     * Register in JMF the custom packages we provide
     */
    private void registerCustomPackages()
    {
        if(packagesRegistered)
            return;
        
        @SuppressWarnings("unchecked")
        Vector<String> packages = PackageManager.getProtocolPrefixList();
        boolean loggerIsDebugEnabled = logger.isDebugEnabled();

        for (String customPackage : CUSTOM_PACKAGES)
        {
            /*
             * Linear search in a loop but it doesn't have to scale since the
             * list is always short.
             */
            if (!packages.contains(customPackage))
            {
                packages.add(customPackage);
                if (loggerIsDebugEnabled)
                    if (logger.isDebugEnabled())
                        logger.debug("Adding package  : " + customPackage);
            }
        }

        PackageManager.setProtocolPrefixList(packages);
        PackageManager.commitProtocolPrefixList();
        if (loggerIsDebugEnabled)
        {
            if (logger.isDebugEnabled())
                logger.debug(
                    "Registering new protocol prefix list: " + packages);
        }
        
        packagesRegistered = true;
    }
}
