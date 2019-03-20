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

import java.util.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;

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
     * Constructor. Loads the default preferences.
     */
    public EncodingConfigurationImpl()
    {
        initializeFormatPreferences();
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

        setEncodingPreference(
            "VP8",
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
        setEncodingPreference("opus", 48000, 750);
        setEncodingPreference("SILK", 24000, 714);
        setEncodingPreference("SILK", 16000, 713);
        setEncodingPreference("G722", 8000 /* actually, 16 kHz */, 705);
        setEncodingPreference("speex", 32000, 701);
        setEncodingPreference("speex", 16000, 700);
        setEncodingPreference("PCMU", 8000, 650);
        setEncodingPreference("PCMA", 8000, 600);
        setEncodingPreference("iLBC", 8000, 500);
        setEncodingPreference("GSM", 8000, 450);
        setEncodingPreference("speex", 8000, 352);
        setEncodingPreference("G723", 8000, 150);

        setEncodingPreference("SILK", 12000, 0);
        setEncodingPreference("SILK", 8000, 0);
        setEncodingPreference("G729", 8000, 0 /* proprietary */);

        // enables by default telephone event(DTMF rfc4733), with lowest
        // priority as it is not needed to order it with audio codecs
        setEncodingPreference(Constants.TELEPHONE_EVENT, 8000, 1);
    }

    /**
     * Sets <tt>pref</tt> as the preference associated with <tt>encoding</tt>.
     * Use this method for both audio and video encodings and don't worry if
     * preferences are equal since we rarely need to compare prefs of video
     * encodings to those of audio encodings.
     *
     * @param encoding the SDP int of the encoding whose pref we're setting.
     * @param clockRate clock rate
     * @param pref a positive int indicating the preference for that encoding.
     */
    @Override
    protected void setEncodingPreference(
            String encoding, double clockRate,
            int pref)
    {
        MediaFormat mediaFormat = null;

        /*
         * The key in encodingPreferences associated with a MediaFormat is
         * currently composed of the encoding and the clockRate only so it makes
         * sense to ignore the format parameters.
         */
        for (MediaFormat mf : MediaUtils.getMediaFormats(encoding))
        {
            if (mf.getClockRate() == clockRate)
            {
                mediaFormat = mf;
                break;
            }
        }
        if (mediaFormat != null)
        {
            encodingPreferences.put(
                    getEncodingPreferenceKey(mediaFormat),
                    pref);
        }
    }

    /**
     * Returns all the available encodings for a specific <tt>MediaType</tt>.
     * This includes disabled ones (ones with priority 0).
     *
     * @param type the <tt>MediaType</tt> we would like to know the available
     * encodings of
     * @return array of <tt>MediaFormat</tt> supported for the
     * <tt>MediaType</tt>
     */
    @Override
    public MediaFormat[] getAllEncodings(MediaType type)
    {
        return MediaUtils.getMediaFormats(type);
    }

    /**
     * Compares the two formats for order. Returns a negative integer, zero, or
     * a positive integer as the first format has been assigned a preference
     * higher, equal to, or greater than the one of the second.
     *
     * @param enc1 the first format to compare for preference.
     * @param enc2 the second format to compare for preference
     * @return a negative integer, zero, or a positive integer as the first
     * format has been assigned a preference higher, equal to, or greater than
     * the one of the second
     */
    @Override
    protected int compareEncodingPreferences(MediaFormat enc1, MediaFormat enc2)
    {
        int res = getPriority(enc2) - getPriority(enc1);

        /*
         * If the encodings are with same priority, compare them by name. If we
         * return equals, TreeSet will not add equal encodings.
         */
        if (res == 0)
        {
            res = enc1.getEncoding().compareToIgnoreCase(enc2.getEncoding());
            /*
             * There are formats with one and the same encoding (name) but
             * different clock rates.
             */
            if (res == 0)
            {
                res = Double.compare(enc2.getClockRate(), enc1.getClockRate());
                /*
                 * And then again, there are formats (e.g. H.264) with one and
                 * the same encoding (name) and clock rate but different format
                 * parameters (e.g. packetization-mode).
                 */
                if (res == 0)
                {
                    // Try to preserve the order specified by MediaUtils.
                    int index1;
                    int index2;

                    if (((index1 = MediaUtils.getMediaFormatIndex(enc1)) != -1)
                            && ((index2 = MediaUtils.getMediaFormatIndex(enc2))
                            != -1))
                    {
                        res = (index1 - index2);
                    }

                    if (res == 0)
                    {
                        /*
                         * The format with more parameters will be considered
                         * here to be the format with higher priority.
                         */
                        Map<String, String> fmtps1 = enc1.getFormatParameters();
                        Map<String, String> fmtps2 = enc2.getFormatParameters();
                        int fmtpCount1 = (fmtps1 == null) ? 0 : fmtps1.size();
                        int fmtpCount2 = (fmtps2 == null) ? 0 : fmtps2.size();

                        /*
                         * TODO Even if the number of format parameters is
                         * equal, the two formats may still be different.
                         * Consider ordering by the values of the format
                         * parameters as well.
                         */
                        res = (fmtpCount2 - fmtpCount1);
                    }
                }
            }
        }
        return res;
    }
}
