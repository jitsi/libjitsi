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
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.rtpdumpfile;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;

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
     * Create a new video <tt>MediaDevice</tt> instance which will read
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
    public static MediaDevice createRtpdumpVideoMediaDevice(
            String filePath,
            String rtpEncodingConstant,
            MediaFormat format)
    {
        /*
         * NOTE: The RtpdumpStream instance needs to know the RTP clock rate,
         * to correctly interpret the RTP timestamps. We use the frameRate field
         * of VideoFormat, to piggyback the RTP clock rate. See
         * RtpdumpStream#RtpdumpStream().
         * TODO: Avoid this hack...
         */
        return new MediaDeviceImpl(
            new CaptureDeviceInfo(
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
        }

    /**
     * Create a new audio <tt>MediaDevice</tt> instance which will read
     * the rtpdump file located at <tt>filePath</tt>, and which will have the
     * encoding format <tt>format</tt>.
     *
     * Note: for proper function, <tt>format</tt> has to implement correctly
     * the <tt>computeDuration(long)</tt> method, because FMJ insists on using
     * this to compute its own RTP timestamps.
     *
     * Note: The RtpdumpStream instance needs to know the RTP clock rate to
     * correctly interpret the RTP timestamps. We use the sampleRate field of
     * AudioFormat, or the frameRate field of VideoFormat, to piggyback the RTP
     * clock rate. See
     * {@link RtpdumpStream#RtpdumpStream(DataSource, javax.media.control.FormatControl)}
     * TODO: Avoid this hack...
     *
     * @param filePath the location of the rtpdump file
     * @param format the <tt>AudioFormat</tt> of the data contained in the
     * payload of the recorded rtp packet in the rtpdump file.
     * @return a <tt>MediaDevice</tt> that will read the rtpdump file given.
     */
    public static MediaDevice createRtpdumpAudioMediaDevice(
            String filePath,
            AudioFormat format)
    {
        return new MyAudioMediaDeviceImpl(new CaptureDeviceInfo(
                "Audio rtpdump file",
                new MediaLocator("rtpdumpfile:" + filePath),
                new Format[]{format}));
    }

    /**
     * An implementation of <tt>AudioMediaDevice</tt>.
     */
    private static class MyAudioMediaDeviceImpl
        extends AudioMediaDeviceImpl
    {
        /**
         * Initializes a new <tt>MyAudioMediaDeviceImpl</tt>.
         * @param captureDeviceInfo
         */
        private MyAudioMediaDeviceImpl(CaptureDeviceInfo captureDeviceInfo)
        {
            super(captureDeviceInfo);
        }

        /**
         * {@inheritDoc}
         *
         * Makes sure that the <tt>MediaDeviceSession</tt> created by this
         * <tt>AudioMediaDevice</tt> does not try to register an
         * <tt>AudioLevelEffect</tt>, because this causes media to be re-encoded
         * (as <tt>AudioLevelEffect</tt> only works with raw audio formats).
         */
        @Override
        public MediaDeviceSession createSession()
        {
            return new AudioMediaDeviceSession(MyAudioMediaDeviceImpl.this)
            {
                @Override
                protected void registerLocalUserAudioLevelEffect(
                        Processor processor)
                {
                }
            };
        }
    }
}
