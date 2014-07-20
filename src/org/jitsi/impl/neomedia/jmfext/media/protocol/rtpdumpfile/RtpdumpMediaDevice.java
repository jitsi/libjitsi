/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;


import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.MediaFormat;

/**
 * This class contains the method <tt>createRtpdumpMediaDevice</tt> that
 * can create <tt>MediaDevice</tt>s that will read the rtpdump file given.
 * This static method is here for convenience. 
 * 
 * @author Thomas Kuntz
 */
public class RtpdumpMediaDevice
{
    /**
     * Create a new <tt>MediaDevice</tt> instance which will read
     * the rtpdump file located at <tt>filePath</tt>, and which will have the
     * encoding format <tt>encodingConstant</tt>.
     * 
     * @param filename filename 
     * @param formatConstant the format this <tt>MediaDevice</tt> will have.
     * You can find the list of possible format in the class <tt>Constants</tt>
     * of libjitsi (ex : Constants.VP8_RTP).
     * 
     * @param filePath the location of the rtpdump file
     * @param encodingConstant the format this <tt>MediaDevice</tt> will have.
     * You can find the list of possible format in the class <tt>Constants</tt>
     * of libjitsi (ex : Constants.VP8_RTP).
     * @param sampleRate The sampleRate of the format behind the rtp packet
     * recorded in the rtpdump file (only if the <tt>MediaDevice</tt> wanted
     * is an audio device).
     * @param type the <tt>MediaType</tt> of the <tt>MediaDevice</tt> you want
     * to create.
     * @return a <tt>MediaDevice</tt> that will read the rtpdump file given.
     */
    public static MediaDevice createRtpdumpMediaDevice(
            String filePath,
            String rtpEncodingConstant,
            MediaFormat format)
    {
        MediaDevice dev = null;

        switch(format.getMediaType())
        {
            case AUDIO:
                dev = new AudioMediaDeviceImpl(new CaptureDeviceInfo(
                            "Audio rtpdump file",
                            new MediaLocator("rtpdumpfile:" + filePath),
                            new Format[]{ new AudioFormat(
                                    rtpEncodingConstant, /* Encoding */
                                    format.getClockRate(), /* sampleRate */
                                    Format.NOT_SPECIFIED, /* sampleSizeInBits */
                                    Format.NOT_SPECIFIED) /* channels */
                            }));
                break;
            case VIDEO:
                dev = new MediaDeviceImpl(new CaptureDeviceInfo(
                            "Video rtpdump file",
                            new MediaLocator("rtpdumpfile:" + filePath),
                            new Format[] { new VideoFormat(
                                    rtpEncodingConstant, /* Encoding */
                                    null, /* Dimension */
                                    Format.NOT_SPECIFIED, /* maxDataLength */
                                    Format.byteArray, /* dataType */
                                    (float) format.getClockRate()) /* frameRate */
                            }),
                        MediaType.VIDEO);
                break;
            default:
                break;
        }

        return dev;
    }
}
