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
import org.jitsi.service.neomedia.format.*;

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
     * @param filePath the location of the rtpdump file
     * @param rtpEncodingConstant the format this <tt>MediaDevice</tt> will have.
     * You can find the list of possible format in the class <tt>Constants</tt>
     * of libjitsi (ex : Constants.VP8_RTP).
     * @param format the <tt>MediaFormat</tt> of the data contained in the
     * payload of the recorded rtp packet in the rtpdump file.
     * @return a <tt>MediaDevice</tt> that will read the rtpdump file given.
     */
    public static MediaDevice createRtpdumpMediaDevice(
            String filePath,
            String rtpEncodingConstant,
            MediaFormat format)
    {
        MediaDevice dev = null;

        /*
         * NOTE: The RtpdumpStream instance needs to know the RTP clock rate,
         * to correctly interpret the RTP timestamps. We use the sampleRate
         * field of AudioFormat, or the frameRate field of VideoFormat, to
         * piggyback the RTP clock rate. See RtpdumpStream#RtpdumpStream().
         * TODO: Avoid this hack...
         */
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
