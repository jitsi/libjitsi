/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.audiolevel;

import org.jitsi.impl.neomedia.*;

/**
 * Implements the calculation of audio level as defined by RFC 6465 &quot;A
 * Real-time Transport Protocol (RTP) Header Extension for Mixer-to-Client Audio
 * Level Indication&quot;.
 *
 * @author Lyubomir Marinov
 */
public class AudioLevelCalculator
{
    /**
     * The maximum audio level.
     */
    public static final byte MAX_AUDIO_LEVEL = 0;

    /**
     * The minimum audio level.
     */
    public static final byte MIN_AUDIO_LEVEL = 127;

    /**
     * Calculates the audio level of a signal with specific <tt>samples</tt>.
     *
     * @param samples the samples of the signal to calculate the audio level of
     * @param offset the offset in <tt>samples</tt> in which the samples start
     * @param length the length in bytes of the signal in <tt>samples<tt>
     * starting at <tt>offset</tt>
     * @return the audio level of the specified signal
     */
    public static byte calculateAudioLevel(
            byte[] samples,
            int offset,
            int length)
    { 
        double rms = 0; // root mean square (RMS) amplitude

        for (; offset < length; offset += 2)
        {
            double sample = ArrayIOUtils.readShort(samples, offset);

            sample /= Short.MAX_VALUE;
            rms += sample * sample;
        }

        int sampleCount = length / 2;

        rms = (sampleCount == 0) ? 0 : Math.sqrt(rms / sampleCount);

        double db;

        if (rms > 0)
        {
            db = 20 * Math.log10(rms);
            // XXX The audio level is expressed in -dBov.
            db = -db;
            // Ensure that the calculated audio level is within the range
            // between MIN_AUDIO_LEVEL and MAX_AUDIO_LEVEL.
            if (db > MIN_AUDIO_LEVEL)
                db = MIN_AUDIO_LEVEL;
            else if (db < MAX_AUDIO_LEVEL)
                db = MAX_AUDIO_LEVEL;
        }
        else
        {
            db = MIN_AUDIO_LEVEL;
        }

        return (byte) db;
    }
}
