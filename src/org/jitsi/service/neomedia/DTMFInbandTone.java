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
package org.jitsi.service.neomedia;

/**
 * Manages the generation of the inband DMTF signal. A signal is identified by a
 * value (1, 2, 3, 4, 5, 6, 7, 8, 9, *, #, A, B, C and D) and each signal is
 * produced by the composition of 2 frequencies (as defined below).
 * (cf. ITU recommendation Q.23)
 *
 * +------------------------------------------------+
 * |        | 1209 Hz | 1336 Hz | 1477 Hz | 1633 Hz |
 * +------------------------------------------------+
 * | 697 Hz |    1    |    2    |    3    |    A    |
 * | 770 Hz |    4    |    5    |    6    |    B    |
 * | 852 Hz |    7    |    8    |    9    |    C    |
 * | 941 Hz |    *    |    0    |    #    |    D    |
 * +------------------------------------------------+
 *
 * @author Vincent Lucas
 */
public class DTMFInbandTone
{
    /**
     * The first set of frequencies in Hz which composes an inband DTMF.
     */
    private static final double[] FREQUENCY_LIST_1
        = new double[] { 697.0, 770.0, 852.0, 941.0 };

    /**
     * The second set of frequencies in Hz which composes an inband DTMF.
     */
    private static final double[] FREQUENCY_LIST_2
        = new double[] { 1209.0, 1336.0, 1477.0, 1633.0 };

    /**
     * The "0" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_0 = new DTMFInbandTone("0",
            FREQUENCY_LIST_1[3],
            FREQUENCY_LIST_2[1]);

    /**
     * The "1" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_1 = new DTMFInbandTone("1",
            FREQUENCY_LIST_1[0],
            FREQUENCY_LIST_2[0]);

    /**
     * The "2" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_2 = new DTMFInbandTone("2",
            FREQUENCY_LIST_1[0],
            FREQUENCY_LIST_2[1]);

    /**
     * The "3" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_3 = new DTMFInbandTone("3",
            FREQUENCY_LIST_1[0],
            FREQUENCY_LIST_2[2]);

    /**
     * The "4" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_4 = new DTMFInbandTone("4",
            FREQUENCY_LIST_1[1],
            FREQUENCY_LIST_2[0]);

    /**
     * The "5" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_5 = new DTMFInbandTone("5",
            FREQUENCY_LIST_1[1],
            FREQUENCY_LIST_2[1]);

    /**
     * The "6" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_6 = new DTMFInbandTone("6",
            FREQUENCY_LIST_1[1],
            FREQUENCY_LIST_2[2]);

    /**
     * The "7" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_7 = new DTMFInbandTone("7",
            FREQUENCY_LIST_1[2],
            FREQUENCY_LIST_2[0]);

    /**
     * The "8" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_8 = new DTMFInbandTone("8",
            FREQUENCY_LIST_1[2],
            FREQUENCY_LIST_2[1]);

    /**
     * The "9" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_9 = new DTMFInbandTone("9",
            FREQUENCY_LIST_1[2],
            FREQUENCY_LIST_2[2]);

    /**
     * The "*" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_STAR =
        new DTMFInbandTone("*",
            FREQUENCY_LIST_1[3],
            FREQUENCY_LIST_2[0]);

    /**
     * The "#" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_SHARP =
        new DTMFInbandTone("#",
            FREQUENCY_LIST_1[3],
            FREQUENCY_LIST_2[2]);

    /**
     * The "A" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_A = new DTMFInbandTone("A",
            FREQUENCY_LIST_1[0],
            FREQUENCY_LIST_2[3]);

    /**
     * The "B" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_B = new DTMFInbandTone("B",
            FREQUENCY_LIST_1[1],
            FREQUENCY_LIST_2[3]);

    /**
     * The "C" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_C = new DTMFInbandTone("C",
            FREQUENCY_LIST_1[2],
            FREQUENCY_LIST_2[3]);

    /**
     * The "D" DTMF Inband Tone.
     */
    public static final DTMFInbandTone DTMF_INBAND_D = new DTMFInbandTone("D",
            FREQUENCY_LIST_1[3],
            FREQUENCY_LIST_2[3]);

    /**
     * The default duration of an inband DTMF tone in ms.
     * 50 ms c.f.
     * http://nemesis.lonestar.org/reference/telecom/signaling/dtmf.html
     * which cites the norm ANSI T1.401-1988.
     * But when testing it at 50 ms, the Asterisk servers miss some DTMF tone
     * impulses. Thus, set up to 150 ms.
     */
    private static final int TONE_DURATION = 150;

    /**
     * The default duration of an inband DTMF tone in ms.
     * 45 ms c.f.
     * http://nemesis.lonestar.org/reference/telecom/signaling/dtmf.html
     * which cites the norm ANSI T1.401-1988.
     * Moreover, the minimum duty cycle (signal tone + silence) for
     * ANSI-compliance shall be greater or equal to 100 ms.
     */
    private static final int INTER_DIGIT_INTERVAL = 45;

    /**
     * The value which identifies the current inband tone. Available values are
     * (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, *, #, A, B, C and D).
     */
    private String value;

    /**
     * The first frequency which composes the current inband tone.
     */
    private double frequency1;

    /**
     * The second frequency which composes the current inband tone.
     */
    private double frequency2;

    /**
     * Creates a new instance of an inband tone. The value given is the main
     * identifier which determines which are the two frequencies to used to
     * generate this tone.
     *
     * @param value The identifier of the tone. Available values are (0, 1, 2,
     * 3, 4, 5, 6, 7, 8, 9, *, #, A, B, C and D).
     * @param frequency1 The first frequency which composes the tone. Available
     * values corresponds to DTMFInbandTone.frequencyList1.
     * @param frequency2 The second frequency which composes the tone. Available
     * values corresponds to DTMFInbandTone.frequencyList2.
     */
    public DTMFInbandTone(String value, double frequency1, double frequency2)
    {
        this.value = value;
        this.frequency1 = frequency1;
        this.frequency2 = frequency2;
    }

    /**
     * Returns this tone value as a string representation.
     *
     * @return this tone value.
     */
    public String getValue()
    {
        return this.value;
    }

    /**
     * Returns the first frequency coded by this tone.
     *
     * @return the first frequency coded by this tone.
     */
    public double getFrequency1()
    {
        return this.frequency1;
    }

    /**
     * Returns the second frequency coded by this tone.
     *
     * @return the second frequency coded by this tone.
     */
    public double getFrequency2()
    {
        return this.frequency2;
    }

    /**
     * Generates a sample for the current tone signal.
     *
     * @param samplingFrequency The sampling frequency (codec clock rate) in Hz
     * of the stream which will encapsulate this signal.
     * @param sampleNumber The sample number of this signal to be produced. The
     * sample number corresponds to the abscissa of the signal function.
     *
     * @return the sample generated. This sample corresponds to the ordinate of
     * the signal function
     */
    public double getAudioSampleContinuous(
            double samplingFrequency,
            int sampleNumber)
    {
        double u1 = 2.0 * Math.PI * this.frequency1 / samplingFrequency;
        double u2 = 2.0 * Math.PI * this.frequency2 / samplingFrequency;
        // The signal generated is composed of 2 sinusoidal signals, which
        // ampltudes is between -1 and 1 (included).
        double audioSample
            = Math.sin(u1 * sampleNumber) * 0.5
                + Math.sin(u2 * sampleNumber) * 0.5;

        return audioSample;
    }

    /**
     * Generates a sample for the current tone signal converted into a discrete
     * signal.
     *
     * @param samplingFrequency The sampling frequency (codec clock rate) in Hz
     * of the stream which will encapsulate this signal.
     * @param sampleNumber The sample number of this signal to be produced. The
     * sample number corresponds to the abscissa of the signal function.
     * @param sampleSizeInBits The size of each sample (8 for a byte, 16 for a
     * short and 32 for an int)
     *
     * @return the sample generated. This sample corresponds to the ordinate of
     * the signal function
     */
    public int getAudioSampleDiscrete(
            double samplingFrequency,
            int sampleNumber,
            int sampleSizeInBits)
    {
        // If the param sampleSizeInBits is equal to 8, then its corresponds to
        // a Java byte which is 8 bits signed type which contains a number
        // between -128 and 127. Thus, as the result of the continuous function
        // is between -1 and 1, we multiply each audioSample by 127.  This
        // generates a signal between -127 and 127.
        // Same operation if sampleSizeInBits is equal to 16, this function
        // generates a signal between -32767 and 32767.
        // As well as if sampleSizeInBits is equal to 32, this function
        // generates a signal between -2147483647 and 2147483647.
        double audioSampleContinuous
            = getAudioSampleContinuous(samplingFrequency, sampleNumber);
        double amplitudeCoefficient = (1L << (sampleSizeInBits - 1)) - 1L;
        int audioSampleDiscrete
            = (int) (audioSampleContinuous * amplitudeCoefficient);

        return audioSampleDiscrete;
    }

    /**
     * Generates a signal sample for the current tone signal and stores it into
     * the byte data array.
     *
     * @param sampleRate The sampling frequency (codec clock rate) in Hz of the
     * stream which will encapsulate this signal.
     * @param sampleSizeInBits The size of each sample (8 for a byte, 16 for a
     * short and 32 for an int)
     * @return The data array containing the DTMF signal.
     */
    public short[] getAudioSamples(double sampleRate, int sampleSizeInBits)
    {
        /*
         * TODO Rounding the sampleRate to an integer so early will lead to a
         * very inaccurate or at least not quite expected number of samples.
         */
        int kHz = (int) (sampleRate / 1000.0);
        int nbToneSamples = kHz * DTMFInbandTone.TONE_DURATION;
        int nbInterDigitSamples = kHz * DTMFInbandTone.INTER_DIGIT_INTERVAL;

        short[] samples
            = new short[
                    nbInterDigitSamples + nbToneSamples + nbInterDigitSamples];

        /*
         * The leading nbInterDigitSamples should be zeroes. They are because we
         * have just allocated the array.
         */
        for (int sampleNumber = nbInterDigitSamples,
                    endSampleNumber = nbInterDigitSamples + nbToneSamples;
                sampleNumber < endSampleNumber;
                sampleNumber++)
        {
            samples[sampleNumber]
                = (short)
                    getAudioSampleDiscrete(
                            sampleRate,
                            sampleNumber,
                            sampleSizeInBits);
        }
        /*
         * The trailing nbInterDigitSamples should be zeroes. They are because
         * we have just allocated the array.
         */

        return samples;
    }

    /**
     * Maps between protocol and media inband DTMF objects.
     * @param tone The DTMF tone as defined in the service protocol, which is
     * only composed by a value as its identifier.
     *
     * @return the corresponding DTMF tone which contains a value as an
     * identifier and two frequencies composing the inband tone.
     */
    public static DTMFInbandTone mapTone(DTMFTone tone)
    {
        if(tone.equals(DTMFTone.DTMF_0))
            return DTMFInbandTone.DTMF_INBAND_0;
        else if(tone.equals(DTMFTone.DTMF_1))
            return DTMFInbandTone.DTMF_INBAND_1;
        else if(tone.equals(DTMFTone.DTMF_2))
            return DTMFInbandTone.DTMF_INBAND_2;
        else if(tone.equals(DTMFTone.DTMF_3))
            return DTMFInbandTone.DTMF_INBAND_3;
        else if(tone.equals(DTMFTone.DTMF_4))
            return DTMFInbandTone.DTMF_INBAND_4;
        else if(tone.equals(DTMFTone.DTMF_5))
            return DTMFInbandTone.DTMF_INBAND_5;
        else if(tone.equals(DTMFTone.DTMF_6))
            return DTMFInbandTone.DTMF_INBAND_6;
        else if(tone.equals(DTMFTone.DTMF_7))
            return DTMFInbandTone.DTMF_INBAND_7;
        else if(tone.equals(DTMFTone.DTMF_8))
            return DTMFInbandTone.DTMF_INBAND_8;
        else if(tone.equals(DTMFTone.DTMF_9))
            return DTMFInbandTone.DTMF_INBAND_9;
        else if(tone.equals(DTMFTone.DTMF_A))
            return DTMFInbandTone.DTMF_INBAND_A;
        else if(tone.equals(DTMFTone.DTMF_B))
            return DTMFInbandTone.DTMF_INBAND_B;
        else if(tone.equals(DTMFTone.DTMF_C))
            return DTMFInbandTone.DTMF_INBAND_C;
        else if(tone.equals(DTMFTone.DTMF_D))
            return DTMFInbandTone.DTMF_INBAND_D;
        else if(tone.equals(DTMFTone.DTMF_SHARP))
            return DTMFInbandTone.DTMF_INBAND_SHARP;
        else if(tone.equals(DTMFTone.DTMF_STAR))
            return DTMFInbandTone.DTMF_INBAND_STAR;
        else
            return null;
    }
}
