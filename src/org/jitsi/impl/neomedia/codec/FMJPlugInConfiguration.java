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
package org.jitsi.impl.neomedia.codec;

import java.io.*;
import java.util.*;

import javax.media.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.util.*;

/**
 * Utility class that handles registration of FMJ packages and plugins.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class FMJPlugInConfiguration
{
    /**
     * Whether the custom codecs have been registered with FMJ.
     */
    private static boolean codecsRegistered = false;

    /**
     * Whether the custom multiplexers have been registered with FMJ.
     */
    private static boolean multiplexersRegistered = false;

    /**
     * The additional custom JMF codecs.
     */
    private static final String[] CUSTOM_CODECS
        = {
//            "org.jitsi.impl.neomedia.codec.AndroidMediaCodec",
            OSUtils.IS_ANDROID
                ? "org.jitsi.impl.neomedia.codec.video.AndroidEncoder"
                : null,
            OSUtils.IS_ANDROID
                ? "org.jitsi.impl.neomedia.codec.video.AndroidDecoder"
                : null,
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
            "org.jitsi.impl.neomedia.codec.audio.ilbc.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.ilbc.JavaEncoder",
            EncodingConfigurationImpl.G729
                ? "org.jitsi.impl.neomedia.codec.audio.g729.JavaDecoder"
                : null,
            EncodingConfigurationImpl.G729
                ? "org.jitsi.impl.neomedia.codec.audio.g729.JavaEncoder"
                : null,
            "org.jitsi.impl.neomedia.codec.audio.g722.JNIDecoderImpl",
            "org.jitsi.impl.neomedia.codec.audio.g722.JNIEncoderImpl",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Decoder",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Encoder",
            "org.jitsi.impl.neomedia.codec.audio.gsm.DePacketizer",
            "org.jitsi.impl.neomedia.codec.audio.gsm.Packetizer",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaDecoder",
            "org.jitsi.impl.neomedia.codec.audio.silk.JavaEncoder",
            // VP8
            "org.jitsi.impl.neomedia.codec.video.vp8.DePacketizer",
            "org.jitsi.impl.neomedia.codec.video.vp8.Packetizer",
            "org.jitsi.impl.neomedia.codec.video.vp8.VPXDecoder",
            "org.jitsi.impl.neomedia.codec.video.vp8.VPXEncoder",
        };

    /**
     * The additional custom JMF codecs, which depend on ffmpeg and should
     * therefore only be used when ffmpeg is enabled.
     */
    private static final String[] CUSTOM_CODECS_FFMPEG
        = {
            // MP3
            "org.jitsi.impl.neomedia.codec.audio.mp3.JNIEncoder",
            // h264
            "org.jitsi.impl.neomedia.codec.video.h264.DePacketizer",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.video.h264.JNIEncoder",
            "org.jitsi.impl.neomedia.codec.video.h264.Packetizer",
            "org.jitsi.impl.neomedia.codec.video.SwScale",
            //"org.jitsi.impl.neomedia.codec.video.h263p.DePacketizer",
            //"org.jitsi.impl.neomedia.codec.video.h263p.JNIDecoder",
            //"org.jitsi.impl.neomedia.codec.video.h263p.JNIEncoder",
            //"org.jitsi.impl.neomedia.codec.video.h263p.Packetizer",
            // Adaptive Multi-Rate Wideband (AMR-WB)
            // "org.jitsi.impl.neomedia.codec.audio.amrwb.DePacketizer",
            "org.jitsi.impl.neomedia.codec.audio.amrwb.JNIDecoder",
            "org.jitsi.impl.neomedia.codec.audio.amrwb.JNIEncoder",
            // "org.jitsi.impl.neomedia.codec.audio.amrwb.Packetizer",
        };

    /**
     * The package prefixes of the additional JMF <tt>DataSource</tt>s (e.g. low
     * latency PortAudio and ALSA <tt>CaptureDevice</tt>s).
     */
    private static final String[] CUSTOM_PACKAGES
        = {
            "org.jitsi.impl.neomedia.jmfext",
            "net.java.sip.communicator.impl.neomedia.jmfext",
            "net.sf.fmj"
        };

    /**
     * The list of class names to register as FMJ plugins with type
     * <tt>PlugInManager.MULTIPLEXER</tt>.
     */
    private static final String[] CUSTOM_MULTIPLEXERS
        = {
            "org.jitsi.impl.neomedia.recording.BasicWavMux"
        };

    /**
     * The <tt>Logger</tt> used by the <tt>FMJPlugInConfiguration</tt> class
     * for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FMJPlugInConfiguration.class);

    /**
     * Whether custom packages have been registered with JFM
     */
    private static boolean packagesRegistered = false;

    /**
     * Register in JMF the custom codecs we provide
     * @param enableFfmpeg whether codecs which depend of ffmpeg should be
     * registered.
     */
    public static void registerCustomCodecs(boolean enableFfmpeg)
    {
        if(codecsRegistered)
            return;

        // Register the custom codecs which haven't already been registered.
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

        List<String> customCodecs = new LinkedList<>();
        customCodecs.addAll(Arrays.asList(CUSTOM_CODECS));
        if (enableFfmpeg)
        {
            customCodecs.addAll(Arrays.asList(CUSTOM_CODECS_FFMPEG));
        }

        for (String className : customCodecs)
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
                {
                    logger.debug(
                            "Codec " + className + " is already registered");
                }
            }
            else
            {
                commit = true;

                boolean registered;
                Throwable exception = null;

                try
                {
                    Codec codec
                        = (Codec) Class.forName(className).newInstance();

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
                    if (logger.isTraceEnabled())
                    {
                        logger.trace(
                                "Codec " + className
                                    + " is successfully registered");
                    }
                }
                else
                {
                    logger.warn(
                            "Codec " + className
                                + " is NOT successfully registered",
                            exception);
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

            for (String className : customCodecs)
            {
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
    public static void registerCustomPackages()
    {
        if(packagesRegistered)
            return;

        @SuppressWarnings("unchecked")
        Vector<String> packages = PackageManager.getProtocolPrefixList();
        boolean loggerIsDebugEnabled = logger.isDebugEnabled();

        // We prefer our custom packages/protocol prefixes over FMJ's.
        for (int i = CUSTOM_PACKAGES.length - 1; i >= 0; i--)
        {
            String customPackage = CUSTOM_PACKAGES[i];

            /*
             * Linear search in a loop but it doesn't have to scale since the
             * list is always short.
             */
            if (!packages.contains(customPackage))
            {
                packages.add(0, customPackage);
                if (loggerIsDebugEnabled)
                    logger.debug("Adding package  : " + customPackage);
            }
        }

        PackageManager.setProtocolPrefixList(packages);
        PackageManager.commitProtocolPrefixList();
        if (loggerIsDebugEnabled)
            logger.debug("Registering new protocol prefix list: " + packages);

        packagesRegistered = true;
    }

    /**
     * Registers custom libjitsi <tt>Multiplexer</tt> implementations.
     */
    @SuppressWarnings("unchecked")
    public static void registerCustomMultiplexers()
    {
        if (multiplexersRegistered)
            return;

        // Remove the FMJ WAV multiplexers, as they don't work.
        PlugInManager.removePlugIn(
                "com.sun.media.multiplexer.audio.WAVMux",
                PlugInManager.MULTIPLEXER);
        PlugInManager.removePlugIn(
                "net.sf.fmj.media.multiplexer.audio.WAVMux",
                PlugInManager.MULTIPLEXER);

        Collection<String> registeredMuxers
            = new HashSet<String>(
                PlugInManager.getPlugInList(
                    null,
                    null,
                    PlugInManager.MULTIPLEXER));

        boolean commit = false;
        for (String className : CUSTOM_MULTIPLEXERS)
        {
            if (className == null)
                continue;
            if (registeredMuxers.contains(className))
            {
                if (logger.isDebugEnabled())
                    logger.debug("Multiplexer " + className + " is already "
                                 + "registered");
                continue;
            }

            boolean registered;
            Throwable exception = null;
            try
            {
                Multiplexer multiplexer
                        = (Multiplexer) Class.forName(className).newInstance();

                registered =
                    PlugInManager.addPlugIn(
                        className,
                        multiplexer.getSupportedInputFormats(),
                        multiplexer.getSupportedOutputContentDescriptors(null),
                        PlugInManager.MULTIPLEXER);
            }
            catch (Throwable ex)
            {
                registered = false;
                exception = ex;
            }

            if (registered)
            {
                if (logger.isTraceEnabled())
                {
                    logger.trace(
                            "Codec " + className
                                    + " is successfully registered");
                }
            }
            else
            {
                logger.warn(
                        "Codec " + className
                                + " is NOT successfully registered",
                        exception);
            }

            commit |= registered;
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

        multiplexersRegistered = true;
    }
}
