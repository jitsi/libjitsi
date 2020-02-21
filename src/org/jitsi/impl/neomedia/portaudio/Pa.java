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
package org.jitsi.impl.neomedia.portaudio;

import java.lang.reflect.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.OSUtils;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * Provides the interface to the native PortAudio library.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Sebastien Vincent
 */
public final class Pa
{
    /**
     * Enumerates the unchanging unique identifiers of each of the supported
     * host APIs. The type is used in the <tt>PaHostApiInfo</tt> structure. The
     * values are guaranteed to be unique and to never change, thus allowing
     * code to be written that conditionally uses host API specific extensions.
     */
    public static enum HostApiTypeId
    {
        paAL(9),
        paALSA(8),
        paASIO(3),
        paAudioScienceHPI(14),
        paBeOS(10),
        paCoreAudio(5),
        paDirectSound(1),
        paInDevelopment(0) /* use while developing support for a new host API */,
        paJACK(12),
        paMME(2),
        paOSS(7),
        paSoundManager(4),
        paWASAPI(13),
        paWDMKS(11);

        /**
         * Returns the <tt>PaHostApiTypeId</tt> which has a specific value or
         * <tt>null</tt> if there is no such representation.
         *
         * @param value
         * @return the <tt>PaHostApiTypeId</tt> which has the specified
         * <tt>value</tt> or <tt>null</tt> if there is no such representation
         */
        public static HostApiTypeId valueOf(int value)
        {
            for (HostApiTypeId hati : values())
            {
                if (hati.value == value)
                    return hati;
            }
            return null;
        }

        private final int value;

        private HostApiTypeId(int value)
        {
            this.value = value;
        }
    }

    /**
     * The number of milliseconds to be read from or written to a native
     * PortAudio stream in a single transfer of data.
     */
    public static final int DEFAULT_MILLIS_PER_BUFFER = 20;

    /**
     * The default value for the sample rate of the input and the output
     * PortAudio streams with which they are to be opened if no other specific
     * sample rate is specified to the PortAudio <tt>DataSource</tt> or
     * <tt>PortAudioRenderer</tt> that they represent.
     */
    public static final double DEFAULT_SAMPLE_RATE = 44100;

    private static Runnable devicesChangedCallback;

    /**
     * Can be passed as the framesPerBuffer parameter to
     * <tt>Pa_OpenStream()</tt> or <tt>Pa_OpenDefaultStream()</tt> to indicate
     * that the stream callback will accept buffers of any size.
     */
    public static final long FRAMES_PER_BUFFER_UNSPECIFIED = 0;

    /**
     * Used when creating new stream parameters for suggested latency to use
     * high input/output value.
     */
    public static final double LATENCY_HIGH = -1d;

    /**
     * Used when creating new stream parameters for suggested latency to use low
     * input/default value.
     */
    public static final double LATENCY_LOW = -2d;

    /**
     * Used when creating new stream parameters for suggested latency to use
     * default value.
     */
    public static final double LATENCY_UNSPECIFIED = 0d;

    /**
     * The <tt>Logger</tt> used by the <tt>Pa</tt> class for logging output.
     */
    private static final Logger logger = Logger.getLogger(Pa.class);

    /**
     * The constant defined by Windows Multimedia and utilized by PortAudio's
     * wmme host API to signal that no device driver is present.
     */
    public static final int MMSYSERR_NODRIVER = 6;

    /**
     * The constant defined by the native PortAudio library to signal that no
     * device is specified.
     */
    public static final int paNoDevice = -1;

    /**
     * The <tt>PaErrorCode</tt> value defined by the native PortAudio library to
     * signal that no error is detected/reported.
     */
    public static final int paNoError = 0;

    /**
     * The <tt>PaErrorCode</tt> value defined by the native PortAudio library to
     * signal that a timeout has occurred.
     */
    public static final int paTimedOut = -9987;

    /**
     * The <tt>PaErrorCode</tt> value defined by the native PortAudio library to
     * signal that an unanticipated error has been detected by a host API.
     */
    public static final int paUnanticipatedHostError = -9999;

    /**
     * The name of the <tt>double</tt> property which determines the suggested
     * latency to be used when opening PortAudio streams.
     */
    private static final String PROP_SUGGESTED_LATENCY
        = "net.java.sip.communicator.impl.neomedia.portaudio.suggestedLatency";

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paFloat32</tt>.
     */
    public static final long SAMPLE_FORMAT_FLOAT32 = 0x00000001;

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paInt16</tt>.
     */
    public static final long SAMPLE_FORMAT_INT16 = 0x00000008;

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paInt24</tt>.
     */
    public static final long SAMPLE_FORMAT_INT24 = 0x00000004;

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paInt32</tt>.
     */
    public static final long SAMPLE_FORMAT_INT32 = 0x00000002;

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paInt8</tt>.
     */
    public static final long SAMPLE_FORMAT_INT8 = 0x00000010;

    /**
     * A type used to specify one or more sample formats. The standard format
     * <tt>paUInt8</tt>.
     */
    public static final long SAMPLE_FORMAT_UINT8 = 0x00000020;

    /** Disables default clipping of out of range samples. */
    public static final long STREAM_FLAGS_CLIP_OFF = 0x00000001;

    /** Disables default dithering. */
    public static final long STREAM_FLAGS_DITHER_OFF = 0x00000002;

    /**
     * Flag requests that where possible a full duplex stream will not discard
     * overflowed input samples without calling the stream callback. This flag
     * is only valid for full duplex callback streams and only when used in
     * combination with the <tt>paFramesPerBufferUnspecified</tt> (<tt>0</tt>)
     * framesPerBuffer parameter. Using this flag incorrectly results in a
     * <tt>paInvalidFlag</tt> error being returned from <tt>Pa_OpenStream</tt>
     * and <tt>Pa_OpenDefaultStream</tt>.
     */
    public static final long STREAM_FLAGS_NEVER_DROP_INPUT = 0x00000004;

    /**
     * Flags used to control the behavior of a stream. They are passed as
     * parameters to <tt>Pa_OpenStream</tt> or <tt>Pa_OpenDefaultStream</tt>.
     */
    public static final long STREAM_FLAGS_NO_FLAG = 0;

    /** A mask specifying the platform specific bits. */
    public static final long STREAM_FLAGS_PLATFORM_SPECIFIC_FLAGS = 0xFFFF0000;

    /**
     * Call the stream callback to fill initial output buffers, rather than the
     * default behavior of priming the buffers with zeros (silence). This flag
     * has no effect for input-only and blocking read/write streams.
     */
    public static final long
        STREAM_FLAGS_PRIME_OUTPUT_BUFFERS_USING_STREAM_CALLBACK
            = 0x00000008;

    static
    {
        JNIUtils.loadLibrary("jnportaudio", Pa.class);

        try
        {
            Initialize();
        }
        catch (PortAudioException paex)
        {
            logger.error("Failed to initialize the PortAudio library.", paex);
            throw new UndeclaredThrowableException(paex);
        }
    }

    /**
     * Terminates audio processing immediately without waiting for pending
     * buffers to complete.
     *
     * @param stream the steam pointer.
     * @throws PortAudioException
     */
    public static native void AbortStream(long stream)
        throws PortAudioException;

    /**
     * Closes an audio stream. If the audio stream is active it discards any
     * pending buffers as if <tt>Pa_AbortStream()</tt> had been called.
     *
     * @param stream the steam pointer.
     * @throws PortAudioException
     */
    public static native void CloseStream(long stream)
        throws PortAudioException;

    /**
     * Returns defaultHighInputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultHighInputLatency for the device.
     */
    public static native double DeviceInfo_getDefaultHighInputLatency(
            long deviceInfo);

    /**
     * Returns defaultHighOutputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultHighOutputLatency for the device.
     */
    public static native double DeviceInfo_getDefaultHighOutputLatency(
            long deviceInfo);

    /**
     * Returns defaultLowInputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultLowInputLatency for the device.
     */
    public static native double DeviceInfo_getDefaultLowInputLatency(
            long deviceInfo);

    /**
     * Returns defaultLowOutputLatency for the device.
     *
     * @param deviceInfo device info pointer.
     * @return defaultLowOutputLatency for the device.
     */
    public static native double DeviceInfo_getDefaultLowOutputLatency(
            long deviceInfo);

    /**
     * The default sample rate for the device.
     *
     * @param deviceInfo device info pointer.
     * @return the default sample rate for the device.
     */
    public static native double DeviceInfo_getDefaultSampleRate(
            long deviceInfo);

    /**
     * Device UID for the device (persistent across boots).
     *
     * @param deviceInfo device info pointer.
     * @return The device UID.
     */
    public static String DeviceInfo_getDeviceUID(long deviceInfo)
    {
        return StringUtils.newString(DeviceInfo_getDeviceUIDBytes(deviceInfo));
    }

    /**
     * Device UID for the device (persistent across boots).
     *
     * @param deviceInfo device info pointer.
     * @return The device UID.
     */
    public static native byte[] DeviceInfo_getDeviceUIDBytes(long deviceInfo);

    /**
     * The host api of the device.
     *
     * @param deviceInfo device info pointer.
     * @return The host api of the device.
     */
    public static native int DeviceInfo_getHostApi(long deviceInfo);

    /**
     * Maximum input channels for the device.
     *
     * @param deviceInfo device info pointer.
     * @return Maximum input channels for the device.
     */
    public static native int DeviceInfo_getMaxInputChannels(long deviceInfo);

    /**
     * Maximum output channels for the device.
     *
     * @param deviceInfo device info pointer.
     * @return Maximum output channels for the device.
     */
    public static native int DeviceInfo_getMaxOutputChannels(long deviceInfo);

    /**
     * Gets the human-readable name of the <tt>PaDeviceInfo</tt> specified by a
     * pointer to it.
     *
     * @param deviceInfo the pointer to the <tt>PaDeviceInfo</tt> to get the
     * human-readable name of
     * @return the human-readable name of the <tt>PaDeviceInfo</tt> pointed to
     * by <tt>deviceInfo</tt>
     */
    public static String DeviceInfo_getName(long deviceInfo)
    {
        return StringUtils.newString(DeviceInfo_getNameBytes(deviceInfo));
    }

    /**
     * Gets the name as a <tt>byte</tt> array of the PortAudio device specified
     * by the pointer to its <tt>PaDeviceInfo</tt> instance.
     *
     * @param deviceInfo the pointer to the <tt>PaDeviceInfo</tt> instance to
     * get the name of
     * @return the name as a <tt>byte</tt> array of the PortAudio device
     * specified by the <tt>PaDeviceInfo</tt> instance pointed to by
     * <tt>deviceInfo</tt>
     */
    private static native byte[] DeviceInfo_getNameBytes(long deviceInfo);

    /**
     * Transport type for the device: BuiltIn, USB, BLuetooth, etc.
     *
     * @param deviceInfo device info pointer.
     * @return The transport type identifier.
     */
    public static String DeviceInfo_getTransportType(long deviceInfo)
    {
        return StringUtils.newString(
                DeviceInfo_getTransportTypeBytes(deviceInfo));
    }

    /**
     * Transport type for the device: BuiltIn, USB, BLuetooth, etc.
     *
     * @param deviceInfo device info pointer.
     * @return The transport type identifier.
     */
    public static native byte[] DeviceInfo_getTransportTypeBytes(
            long deviceInfo);

    /**
     * Implements a callback which gets called by the native PortAudio
     * counterpart to notify the Java counterpart that the list of PortAudio
     * devices has changed.
     */
    public static void devicesChangedCallback()
    {
        Runnable devicesChangedCallback = Pa.devicesChangedCallback;

        if (devicesChangedCallback != null)
            devicesChangedCallback.run();
    }

    private static native void free(long ptr);

    /**
     * Retrieve the index of the default input device.
     *
     * @return The default input device index for the default host API, or
     * <tt>paNoDevice</tt> if no default input device is available or an error
     * was encountered.
     */
    public static native int GetDefaultInputDevice();

    /**
     * Retrieve the index of the default output device.
     *
     * @return The default input device index for the default host API, or
     * <tt>paNoDevice</tt> if no default input device is available or an error
     * was encountered.
     */
    public static native int GetDefaultOutputDevice();

    /**
     * Retrieve the number of available devices. The number of available devices
     * may be zero.
     *
     * @return the number of devices.
     * @throws PortAudioException
     */
    public static native int GetDeviceCount()
        throws PortAudioException;

    /**
     * Returns the PortAudio index of the device identified by a specific
     * <tt>deviceID</tt> or {@link Pa#paNoDevice} if no such device exists. The
     * <tt>deviceID</tt> is either a <tt>deviceUID</tt> or a (PortAudio device)
     * name depending, for example, on operating system/API availability. Since
     * at least names may not be unique, the PortAudio device to return the
     * index of may be identified more specifically by the minimal numbers of
     * channels to be required from the device for input and output.
     *
     * @param deviceID a <tt>String</tt> identifying the PortAudio device to
     * retrieve the index of. It is either a <tt>deviceUID</tt> or a (PortAudio
     * device) name.
     * @param minInputChannels
     * @param minOutputChannels
     * @return the PortAudio index of the device identified by the specified
     * <tt>deviceID</tt> or <tt>Pa.paNoDevice</tt> if no such device exists
     */
    public static int getDeviceIndex(
            String deviceID,
            int minInputChannels, int minOutputChannels)
    {
        if(deviceID != null)
        {
            int deviceCount = 0;

            try
            {
                deviceCount = Pa.GetDeviceCount();
            }
            catch(PortAudioException paex)
            {
                /*
                 * A deviceCount equal to 0 will eventually result in a return
                 * value equal to paNoDevice.
                 */
            }
            for(int deviceIndex = 0; deviceIndex < deviceCount; ++deviceIndex)
            {
                long deviceInfo = Pa.GetDeviceInfo(deviceIndex);
                /* The deviceID is either the deviceUID or the name. */
                String deviceUID = Pa.DeviceInfo_getDeviceUID(deviceInfo);

                if(deviceID.equals(
                        ((deviceUID == null) || (deviceUID.length() == 0))
                            ? Pa.DeviceInfo_getName(deviceInfo)
                            : deviceUID))
                {
                    /*
                     * Resolve deviceID clashes by further identifying the
                     * device through the numbers of channels that it supports
                     * for input and output.
                     */
                    if ((minInputChannels > 0)
                            && (Pa.DeviceInfo_getMaxInputChannels(deviceInfo)
                                    < minInputChannels))
                        continue;
                    if ((minOutputChannels > 0)
                            && (Pa.DeviceInfo_getMaxOutputChannels(deviceInfo)
                                    < minOutputChannels))
                        continue;

                    return deviceIndex;
                }
            }
        }

        // No corresponding device was found.
        return Pa.paNoDevice;
    }

    /**
     * Retrieve a pointer to a PaDeviceInfo structure containing information
     * about the specified device.
     *
     * @param deviceIndex the device index
     * @return pointer to device info structure.
     */
    public static native long GetDeviceInfo(int deviceIndex);

    /**
     * Retrieve a pointer to a structure containing information about a specific
     * host Api.
     *
     * @param hostApiIndex host api index.
     * @return A pointer to an immutable PaHostApiInfo structure describing a
     * specific host API.
     */
    public static native long GetHostApiInfo(int hostApiIndex);

    /**
     * Gets the native <tt>PaSampleFormat</tt> with a specific size in bits.
     *
     * @param sampleSizeInBits the size in bits of the native
     * <tt>PaSampleFormat</tt> to get
     * @return the native <tt>PaSampleFormat</tt> with the specified size in
     * bits
     */
    public static long getPaSampleFormat(int sampleSizeInBits)
    {
        switch (sampleSizeInBits)
        {
        case 8:
            return SAMPLE_FORMAT_INT8;
        case 24:
            return SAMPLE_FORMAT_INT24;
        case 32:
            return SAMPLE_FORMAT_INT32;
        default:
            return SAMPLE_FORMAT_INT16;
        }
    }

    /**
     * Retrieve the size of a given sample format in bytes.
     *
     * @param format the format.
     * @return The size in bytes of a single sample in the specified format, or
     * <tt>paSampleFormatNotSupported</tt> if the format is not supported.
     */
    public static native int GetSampleSize(long format);

    /**
     * Retrieve the number of frames that can be read from the stream without
     * waiting.
     *
     * @param stream pointer to the stream.
     * @return returns a non-negative value representing the maximum number of
     * frames that can be read from the stream without blocking or busy waiting
     * or, a <tt>PaErrorCode</tt> (which are always negative) if PortAudio is
     * not initialized or an error is encountered.
     */
    public static native long GetStreamReadAvailable(long stream);

    /**
     * Retrieve the number of frames that can be written to the stream
     * without waiting.
     *
     * @param stream pointer to the stream.
     * @return returns a non-negative value representing the maximum number of
     * frames that can be written to the stream without blocking or busy waiting
     * or, a PaErrorCode (which are always negative) if PortAudio is not
     * initialized or an error is encountered.
     */
    public static native long GetStreamWriteAvailable(long stream);

    /**
     * Gets the suggested latency to be used when opening PortAudio streams.
     *
     * @return the suggested latency to be used when opening PortAudio streams
     */
    public static double getSuggestedLatency()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null)
        {
            String suggestedLatencyString
                = cfg.getString(PROP_SUGGESTED_LATENCY);

            if (suggestedLatencyString != null)
            {
                try
                {
                    double suggestedLatency
                        = Double.parseDouble(suggestedLatencyString);

                    if (suggestedLatency != LATENCY_UNSPECIFIED)
                        return suggestedLatency;
                }
                catch (NumberFormatException nfe)
                {
                    logger.error(
                            "Failed to parse configuration property "
                                + PROP_SUGGESTED_LATENCY
                                + " value as a double",
                            nfe);
                }
            }
        }

        if (OSUtils.IS_MAC || OSUtils.IS_LINUX)
            return LATENCY_HIGH;
        else if (OSUtils.IS_WINDOWS)
            return 0.1d;
        else
            return LATENCY_UNSPECIFIED;
    }

    /**
     * The default input device for this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The default input device for this host API.
     */
    public static native int HostApiInfo_getDefaultInputDevice(
            long hostApiInfo);

    /**
     * The default output device for this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The default output device for this host API.
     */
    public static native int HostApiInfo_getDefaultOutputDevice(
            long hostApiInfo);

    /**
     * The number of devices belonging to this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The number of devices belonging to this host API.
     */
    public static native int HostApiInfo_getDeviceCount(long hostApiInfo);

    /**
     * The well known unique identifier of this host API.
     *
     * @param hostApiInfo pointer to host API info structure.
     * @return The well known unique identifier of this host API.
     *         Enumerator:
     *              paInDevelopment
     *              paDirectSound
     *              paMME
     *              paASIO
     *              paSoundManager
     *              paCoreAudio
     *              paOSS
     *              paALSA
     *              paAL
     *              paBeOS
     *              paWDMKS
     *              paJACK
     *              paWASAPI
     *              paAudioScienceHPI
     */
    public static native int HostApiInfo_getType(long hostApiInfo);

    /**
     * Initializes the native PortAudio library.
     *
     * @throws PortAudioException
     */
    private static native void Initialize()
        throws PortAudioException;

    /**
     * Determine whether it would be possible to open a stream
     * with the specified parameters.
     *
     * @param inputParameters A structure that describes the input parameters
     * used to open a stream.
     * @param outputParameters A structure that describes the output parameters
     * used to open a stream.
     * @param sampleRate The required sampleRate.
     * @return returns 0 if the format is supported, and an error code
     * indicating why the format is not supported otherwise. The constant
     * paFormatIsSupported is provided to compare with the return value for
     * success.
     */
    public static native boolean IsFormatSupported(
        long inputParameters,
        long outputParameters,
        double sampleRate);

    /**
     * Opens a stream for either input, output or both.
     *
     * @param inputParameters the input parameters or 0 if absent.
     * @param outputParameters the output parameters or 0 if absent.
     * @param sampleRate The desired sampleRate.
     * @param framesPerBuffer The number of frames passed to the stream callback
     * function, or the preferred block granularity for a blocking read/write
     * stream
     * @param streamFlags Flags which modify the behavior of the streaming
     * process.
     * @param streamCallback A pointer to a client supplied function that is
     * responsible for processing and filling input and output buffers. If
     * <tt>null</tt>, the stream will be opened in 'blocking read/write' mode.
     * @return pointer to the opened stream.
     * @throws PortAudioException
     */
    public static native long OpenStream(
            long inputParameters,
            long outputParameters,
            double sampleRate,
            long framesPerBuffer,
            long streamFlags,
            PortAudioStreamCallback streamCallback)
        throws PortAudioException;

    /**
     * Read samples from an input stream. The function doesn't return until
     * the entire buffer has been filled - this may involve waiting for
     * the operating system to supply the data.
     *
     * @param stream pointer to the stream.
     * @param buffer a buffer of sample frames.
     * @param frames The number of frames to be read into buffer.
     * @throws PortAudioException
     */
    public static native void ReadStream(
            long stream, byte[] buffer, long frames)
        throws PortAudioException;

    /**
     * Sets the indicator which determines whether a specific (input) PortAudio
     * stream is to have denoise performed on the audio data it provides.
     *
     * @param stream the (input) PortAudio stream for which denoise is to be
     * enabled or disabled
     * @param denoise <tt>true</tt> if denoise is to be performed on the audio
     * data provided by <tt>stream</tt>; otherwise, <tt>false</tt>
     */
    public static native void setDenoise(long stream, boolean denoise);

    public static void setDevicesChangedCallback(
            Runnable devicesChangedCallback)
    {
        Pa.devicesChangedCallback = devicesChangedCallback;
    }

    /**
     * Sets the number of milliseconds of echo to be canceled in the audio data
     * provided by a specific (input) PortAudio stream.
     *
     * @param stream the (input) PortAudio stream for which the number of
     * milliseconds of echo to be canceled is to be set
     * @param echoFilterLengthInMillis the number of milliseconds of echo to be
     * canceled in the audio data provided by <tt>stream</tt>
     */
    public static native void setEchoFilterLengthInMillis(
            long stream,
            long echoFilterLengthInMillis);

    /**
     * Commences audio processing.
     *
     * @param stream pointer to the stream
     * @throws PortAudioException
     */
    public static native void StartStream(long stream)
        throws PortAudioException;

    /**
     * Terminates audio processing. It waits until all pending audio buffers
     * have been played before it returns.
     *
     * @param stream pointer to the stream
     * @throws PortAudioException
     */
    public static native void StopStream(long stream)
        throws PortAudioException;

    /**
     * Free StreamParameters resources specified by a pointer to it.
     *
     * @param streamParameters the pointer to the <tt>PaStreamParameters</tt>
     * to free
     */
    public static void StreamParameters_free(long streamParameters)
    {
        free(streamParameters);
    }

    /**
     * Creates parameters used for opening streams.
     *
     * @param deviceIndex the device.
     * @param channelCount the channels to be used.
     * @param sampleFormat the sample format.
     * @param suggestedLatency the suggested latency in milliseconds:
     *          LATENCY_UNSPECIFIED -
     *              use default(default high input/output latency)
     *          LATENCY_HIGH - use default high input/output latency
     *          LATENCY_LOW - use default low input/output latency
     *          ... - any other value in milliseconds (e.g. 0.1 is acceptable)
     * @return pointer to the params used for Pa_OpenStream.
     */
    public static native long StreamParameters_new(
            int deviceIndex,
            int channelCount,
            long sampleFormat,
            double suggestedLatency);

    public static native void UpdateAvailableDeviceList();

    /**
     * Writes samples to an output stream. Does not return until the specified
     * samples have been consumed - this may involve waiting for the operating
     * system to consume the data.
     * <p>
     * Provides better efficiency than achieved through multiple consecutive
     * calls to {@link #WriteStream(long, byte[], long)} with one and the
     * same buffer because the JNI access to the bytes of the buffer which is
     * likely to copy the whole buffer is only performed once.
     * </p>
     *
     * @param stream the pointer to the PortAudio stream to write the samples to
     * @param buffer the buffer containing the samples to be written
     * @param offset the byte offset in <tt>buffer</tt> at which the samples to
     * be written start
     * @param frames the number of frames from <tt>buffer</tt> starting at
     * <tt>offset</tt> are to be written with a single write
     * @param numberOfWrites the number of writes each writing <tt>frames</tt>
     * number of frames to be performed
     * @throws PortAudioException if anything goes wrong while writing
     */
    public static native void WriteStream(
            long stream,
            byte[] buffer,
            int offset,
            long frames,
            int numberOfWrites)
        throws PortAudioException;

    /**
     * Write samples to an output stream. This function doesn't return until
     * the entire buffer has been consumed - this may involve waiting for the
     * operating system to consume the data.
     *
     * @param stream pointer to the stream
     * @param buffer A buffer of sample frames.
     * @param frames The number of frames to be written from buffer.
     * @throws PortAudioException
     */
    public static void WriteStream(long stream, byte[] buffer, long frames)
        throws PortAudioException
    {
        WriteStream(stream, buffer, 0, frames, 1);
    }

    /**
     * Prevents the initialization of <tt>Pa</tt> instances.
     */
    private Pa()
    {
    }
}
