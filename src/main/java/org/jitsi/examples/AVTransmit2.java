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
package org.jitsi.examples;

import java.io.*;
import java.net.*;
import java.util.*;

import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;

/**
 * Implements an example application in the fashion of JMF's AVTransmit2 example
 * which demonstrates the use of the <tt>libjitsi</tt> library for the purposes
 * of transmitting audio and video via RTP means.
 *
 * @author Lyubomir Marinov
 */
public class AVTransmit2
{
    /**
     * The port which is the source of the transmission i.e. from which the
     * media is to be transmitted.
     *
     * @see #LOCAL_PORT_BASE_ARG_NAME
     */
    private int localPortBase;

    /**
     * The <tt>MediaStream</tt> instances initialized by this instance indexed
     * by their respective <tt>MediaType</tt> ordinal.
     */
    private MediaStream[] mediaStreams;

    /**
     * The <tt>InetAddress</tt> of the host which is the target of the
     * transmission i.e. to which the media is to be transmitted.
     *
     * @see #REMOTE_HOST_ARG_NAME
     */
    private InetAddress remoteAddr;

    /**
     * The port which is the target of the transmission i.e. to which the media
     * is to be transmitted.
     *
     * @see #REMOTE_PORT_BASE_ARG_NAME
     */
    private int remotePortBase;

    /**
     * Initializes a new <tt>AVTransmit2</tt> instance which is to transmit
     * audio and video to a specific host and a specific port.
     *
     * @param localPortBase the port which is the source of the transmission
     * i.e. from which the media is to be transmitted
     * @param remoteHost the name of the host which is the target of the
     * transmission i.e. to which the media is to be transmitted
     * @param remotePortBase the port which is the target of the transmission
     * i.e. to which the media is to be transmitted
     * @throws Exception if any error arises during the parsing of the specified
     * <tt>localPortBase</tt>, <tt>remoteHost</tt> and <tt>remotePortBase</tt>
     */
    private AVTransmit2(
            String localPortBase,
            String remoteHost, String remotePortBase)
        throws Exception
    {
        this.localPortBase
            = (localPortBase == null)
                ? -1
                : Integer.parseInt(localPortBase);
        this.remoteAddr = InetAddress.getByName(remoteHost);
        this.remotePortBase = Integer.parseInt(remotePortBase);
    }

    /**
     * Starts the transmission. Returns null if transmission started ok.
     * Otherwise it returns a string with the reason why the setup failed.
     */
    private String start()
        throws Exception
    {
        /*
         * Prepare for the start of the transmission i.e. initialize the
         * MediaStream instances.
         */
        MediaType[] mediaTypes = MediaType.values();
        MediaService mediaService = LibJitsi.getMediaService();
        int localPort = localPortBase;
        int remotePort = remotePortBase;

        mediaStreams = new MediaStream[mediaTypes.length];
        for (MediaType mediaType : mediaTypes)
        {
            /*
             * The default MediaDevice (for a specific MediaType) is configured
             * (by the user of the application via some sort of UI) into the
             * ConfigurationService. If there is no ConfigurationService
             * instance known to LibJitsi, the first available MediaDevice of
             * the specified MediaType will be chosen by MediaService.
             */
            MediaDevice device
                = mediaService.getDefaultDevice(mediaType, MediaUseCase.CALL);
            MediaStream mediaStream = mediaService.createMediaStream(device);

            // direction
            /*
             * The AVTransmit2 example sends only and the AVReceive2 receives
             * only. In a call, the MediaStream's direction will most commonly
             * be set to SENDRECV.
             */
            mediaStream.setDirection(MediaDirection.SENDONLY);

            // format
            String encoding;
            double clockRate;
            /*
             * The AVTransmit2 and AVReceive2 examples use the H.264 video
             * codec. Its RTP transmission has no static RTP payload type number
             * assigned.
             */
            byte dynamicRTPPayloadType;

            switch (device.getMediaType())
            {
            case AUDIO:
                encoding = "PCMU";
                clockRate = 8000;
                /* PCMU has a static RTP payload type number assigned. */
                dynamicRTPPayloadType = -1;
                break;
            case VIDEO:
                encoding = "H264";
                clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
                /*
                 * The dymanic RTP payload type numbers are usually negotiated
                 * in the signaling functionality.
                 */
                dynamicRTPPayloadType = 99;
                break;
            default:
                encoding = null;
                clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
                dynamicRTPPayloadType = -1;
            }

            if (encoding != null)
            {
                MediaFormat format
                    = mediaService.getFormatFactory().createMediaFormat(
                            encoding,
                            clockRate);

                /*
                 * The MediaFormat instances which do not have a static RTP
                 * payload type number association must be explicitly assigned
                 * a dynamic RTP payload type number.
                 */
                if (dynamicRTPPayloadType != -1)
                {
                    mediaStream.addDynamicRTPPayloadType(
                            dynamicRTPPayloadType,
                            format);
                }

                mediaStream.setFormat(format);
            }

            // connector
            StreamConnector connector;

            if (localPortBase == -1)
            {
                connector = new DefaultStreamConnector();
            }
            else
            {
                int localRTPPort = localPort++;
                int localRTCPPort = localPort++;

                connector
                    = new DefaultStreamConnector(
                            new DatagramSocket(localRTPPort),
                            new DatagramSocket(localRTCPPort));
            }
            mediaStream.setConnector(connector);

            // target
            /*
             * The AVTransmit2 and AVReceive2 examples follow the common
             * practice that the RTCP port is right after the RTP port.
             */
            int remoteRTPPort = remotePort++;
            int remoteRTCPPort = remotePort++;

            mediaStream.setTarget(
                    new MediaStreamTarget(
                            new InetSocketAddress(remoteAddr, remoteRTPPort),
                            new InetSocketAddress(remoteAddr, remoteRTCPPort)));

            // name
            /*
             * The name is completely optional and it is not being used by the
             * MediaStream implementation at this time, it is just remembered so
             * that it can be retrieved via MediaStream#getName(). It may be
             * integrated with the signaling functionality if necessary.
             */
            mediaStream.setName(mediaType.toString());

            mediaStreams[mediaType.ordinal()] = mediaStream;
        }

        /*
         * Do start the transmission i.e. start the initialized MediaStream
         * instances.
         */
        for (MediaStream mediaStream : mediaStreams)
            if (mediaStream != null)
                mediaStream.start();

        return null;
    }

    /**
     * Stops the transmission if already started
     */
    private void stop()
    {
        if (mediaStreams != null)
        {
            for (int i = 0; i < mediaStreams.length; i++)
            {
                MediaStream mediaStream = mediaStreams[i];

                if (mediaStream != null)
                {
                    try
                    {
                        mediaStream.stop();
                    }
                    finally
                    {
                        mediaStream.close();
                        mediaStreams[i] = null;
                    }
                }
            }

            mediaStreams = null;
        }
    }

    /**
     * The name of the command-line argument which specifies the port from which
     * the media is to be transmitted. The command-line argument value will be
     * used as the port to transmit the audio RTP from, the next port after it
     * will be to transmit the audio RTCP from. Respectively, the subsequent
     * ports will be used to transmit the video RTP and RTCP from."
     */
    private static final String LOCAL_PORT_BASE_ARG_NAME
        = "--local-port-base=";

    /**
     * The name of the command-line argument which specifies the name of the
     * host to which the media is to be transmitted.
     */
    private static final String REMOTE_HOST_ARG_NAME = "--remote-host=";

    /**
     * The name of the command-line argument which specifies the port to which
     * the media is to be transmitted. The command-line argument value will be
     * used as the port to transmit the audio RTP to, the next port after it
     * will be to transmit the audio RTCP to. Respectively, the subsequent ports
     * will be used to transmit the video RTP and RTCP to."
     */
    private static final String REMOTE_PORT_BASE_ARG_NAME
        = "--remote-port-base=";

    /**
     * The list of command-line arguments accepted as valid by the
     * <tt>AVTransmit2</tt> application along with their human-readable usage
     * descriptions.
     */
    private static final String[][] ARGS
        = {
            {
                LOCAL_PORT_BASE_ARG_NAME,
                "The port which is the source of the transmission i.e. from"
                    + " which the media is to be transmitted. The specified"
                    + " value will be used as the port to transmit the audio"
                    + " RTP from, the next port after it will be used to"
                    + " transmit the audio RTCP from. Respectively, the"
                    + " subsequent ports will be used to transmit the video RTP"
                    + " and RTCP from."
            },
            {
                REMOTE_HOST_ARG_NAME,
                "The name of the host which is the target of the transmission"
                    + " i.e. to which the media is to be transmitted"
            },
            {
                REMOTE_PORT_BASE_ARG_NAME,
                "The port which is the target of the transmission i.e. to which"
                    + " the media is to be transmitted. The specified value"
                    + " will be used as the port to transmit the audio RTP to"
                    + " the next port after it will be used to transmit the"
                    + " audio RTCP to. Respectively, the subsequent ports will"
                    + " be used to transmit the video RTP and RTCP to."
            }
        };

    public static void main(String[] args)
        throws Exception
    {
        // We need two parameters to do the transmission. For example,
        // ant run-example -Drun.example.name=AVTransmit2 -Drun.example.arg.line="--remote-host=127.0.0.1 --remote-port-base=10000"
        if (args.length < 2)
        {
            prUsage();
        }
        else
        {
            Map<String, String> argMap = parseCommandLineArgs(args);

            LibJitsi.start();
            try
            {
                // Create a audio transmit object with the specified params.
                AVTransmit2 at
                    = new AVTransmit2(
                            argMap.get(LOCAL_PORT_BASE_ARG_NAME),
                            argMap.get(REMOTE_HOST_ARG_NAME),
                            argMap.get(REMOTE_PORT_BASE_ARG_NAME));
                // Start the transmission
                String result = at.start();

                // result will be non-null if there was an error. The return
                // value is a String describing the possible error. Print it.
                if (result == null)
                {
                    System.err.println("Start transmission for 60 seconds...");

                    // Transmit for 60 seconds and then close the processor
                    // This is a safeguard when using a capture data source
                    // so that the capture device will be properly released
                    // before quitting.
                    // The right thing to do would be to have a GUI with a
                    // "Stop" button that would call stop on AVTransmit2
                    try
                    {
                        Thread.sleep(60000);
                    } catch (InterruptedException ie)
                    {
                    }

                    // Stop the transmission
                    at.stop();

                    System.err.println("...transmission ended.");
                }
                else
                {
                    System.err.println("Error : " + result);
                }
            }
            finally
            {
                LibJitsi.stop();
            }
        }
    }

    /**
     * Parses the arguments specified to the <tt>AVTransmit2</tt> application on
     * the command line.
     *
     * @param args the arguments specified to the <tt>AVTransmit2</tt>
     * application on the command line
     * @return a <tt>Map</tt> containing the arguments specified to the
     * <tt>AVTransmit2</tt> application on the command line in the form of
     * name-value associations
     */
    static Map<String, String> parseCommandLineArgs(String[] args)
    {
        Map<String, String> argMap = new HashMap<String, String>();

        for (String arg : args)
        {
            int keyEndIndex = arg.indexOf('=');
            String key;
            String value;

            if (keyEndIndex == -1)
            {
                key = arg;
                value = null;
            }
            else
            {
                key = arg.substring(0, keyEndIndex + 1);
                value = arg.substring(keyEndIndex + 1);
            }
            argMap.put(key, value);
        }
        return argMap;
    }

    /**
     * Outputs human-readable description about the usage of the
     * <tt>AVTransmit2</tt> application and the command-line arguments it
     * accepts as valid.
     */
    private static void prUsage()
    {
        PrintStream err = System.err;

        err.println("Usage: " + AVTransmit2.class.getName() + " <args>");
        err.println("Valid args:");
        for (String[] arg : ARGS)
            err.println("  " + arg[0] + " " + arg[1]);
    }
}
