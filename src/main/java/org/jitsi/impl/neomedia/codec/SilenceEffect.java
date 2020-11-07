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

import org.jitsi.utils.logging.*;

import javax.media.*;
import javax.media.format.*;
import java.util.*;

/**
 * An <tt>Effect</tt> which detects discontinuities in an audio stream by
 * monitoring the input <tt>Buffer</tt>s' timestamps and lengths, and
 * inserts silence to account for missing data.
 *
 * @author Boris Grozev
 */
public class SilenceEffect
    extends AbstractCodec2
    implements Effect
{
    /**
     * The <tt>Logger</tt> used by the <tt>SilenceEffect</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(SilenceEffect.class);

    /**
     * The indicator which determines whether <tt>SilenceEffect</tt>
     * instances are to perform the copying of the data from input
     * <tt>Buffer</tt>s to output <tt>Buffer</tt>s themselves (e.g. using
     * {@link System#arraycopy(Object, int, Object, int, int)}).
     */
    private static final boolean COPY_DATA_FROM_INPUT_TO_OUTPUT = true;

    /**
     * The name of this <tt>PlugIn</tt>.
     */
    private static final String NAME = "Silence Effect";

    /**
     * The maximum number of samples of silence to insert in a single
     * <tt>Buffer</tt>.
     */
    private static final int MAX_SAMPLES_PER_PACKET = 48000;

    /**
     * The sample rate of the input/output format.
     */
    private static final int sampleRate = 48000;

    /**
     * The size of a single sample of input in bits.
     */
    private static final int sampleSizeInBits = 16;

    /**
     * Max samples of silence to insert between two <tt>Buffer</tt>s.
     */
    private static final int MAX_SAMPLES_SILENCE = sampleRate * 3; //3sec

    /**
     * The <tt>Format</tt>s supported as input/output by this <tt>Effect</tt>.
     */
    public static final Format[] SUPPORTED_FORMATS = new Format[] {
            new AudioFormat(
                    AudioFormat.LINEAR,
                    sampleRate,
                    sampleSizeInBits,
                    1, //channels
                    Format.NOT_SPECIFIED, //endian
                    Format.NOT_SPECIFIED) //signed/unsigned
    };

    /**
     * Whether to use the input <tt>Buffer</tt>s' RTP timestamps (with
     * <tt>Buffer.getRtpTimestamp()</tt>), or their "regular" timestamps (with
     * <tt>Buffer.getTimestamp()</tt>).
     */
    private final boolean useRtpTimestamp;

    /**
     * The clock rate for the timestamps of input <tt>Buffer</tt>s
     * (i.e. the number of units which constitute one second).
     */
    private final int clockRate;

    /**
     * The total number of samples of silence inserted by this instance.
     */
    private int totalSamplesInserted = 0;

    /**
     * The timestamp (either the RTP timestamp, or the <tt>Buffer</tt>'s
     * timestamp, according to the value of {@link #useRtpTimestamp}) of the
     * last sample that was output by this <tt>Codec</tt>.
     */
    private long lastOutputTimestamp = Buffer.TIME_UNKNOWN;

    private Listener listener = null;

    /**
     * Initializes a new <tt>SilenceEffect</tt>, which is to use the input
     * <tt>Buffer</tt>s' timestamps (as opposed to using their RTP timestamps).
     */
    public SilenceEffect()
    {
        super(NAME, AudioFormat.class, SUPPORTED_FORMATS);

        this.useRtpTimestamp = false;
        // Buffer.getTimestamp() will be used, which is in nanoseconds.
        this.clockRate = 1000 * 1000 * 1000;
    }

    /**
     * Initializes a new <tt>SilenceEffect</tt>, which is to use the input
     * <tt>Buffer</tt>s' RTP timestamps.
     * @param rtpClockRate the clock rate that the RTP timestamps use.
     */
    public SilenceEffect(int rtpClockRate)
    {
        super(NAME, AudioFormat.class, SUPPORTED_FORMATS);

        this.useRtpTimestamp = true;
        this.clockRate = rtpClockRate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        if (logger.isInfoEnabled())
            logger.info("Closing SilenceEffect, inserted a total of "
                        + totalSamplesInserted + " samples of silence.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException
    {
    }

    /**
     * Processes <tt>inBuf</tt>, and either copies its data to <tt>outBuf</tt>
     * or copies silence
     * @param inBuf the input <tt>Buffer</tt>.
     * @param outBuf the output <tt>Buffer</tt>.
     * @return  <tt>BUFFER_PROCESSED_OK</tt> if <tt>inBuf</tt>'s date was copied
     * to <tt>outBuf</tt>, and <tt>INPUT_BUFFER_NOT_CONSUMED</tt> if silence
     * was inserted instead.
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        boolean useInput = true;
        long timestamp
                = useRtpTimestamp
                ? inBuf.getRtpTimeStamp()
                : inBuf.getTimeStamp();


        if (timestamp == Buffer.TIME_UNKNOWN)
        {
            // if the current Buffer's timestamp is unknown, we don't know how
            // much silence to insert, so we let the Buffer pass and reset our
            // state.
            lastOutputTimestamp = Buffer.TIME_UNKNOWN;
        }
        else if (lastOutputTimestamp == Buffer.TIME_UNKNOWN)
        {
            // Initialize lastOutputTimestamp. The samples from the current
            // buffer will be added below.

            lastOutputTimestamp = timestamp;

            if (listener != null)
                listener.onSilenceNotInserted(timestamp);
        }
        else // timestamp != -1 && lastOutputTimestamp != -1
        {
            long diff = timestamp - lastOutputTimestamp;
            if (useRtpTimestamp && diff < -(1L<<31))
            {
                // RTP timestamps have wrapped
                diff += 1L<<32;
            }
            else if (useRtpTimestamp && diff < 0)
            {
                // an older packet received (possibly a retransmission)
                outBuf.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            long diffSamples = Math.round( ((double)(diff * sampleRate))
                                           / clockRate);
            if (diffSamples > MAX_SAMPLES_SILENCE)
            {
                logger.info("More than the maximum of " + MAX_SAMPLES_SILENCE
                             + " samples of silence need to be inserted.");

                if (listener != null)
                    listener.onSilenceNotInserted(timestamp);
                lastOutputTimestamp = timestamp;
                diffSamples = 0;
            }

            if (diffSamples > 0)
            {
                useInput = false;
                int samplesInserted = setSilence(outBuf, (int) diffSamples);
                totalSamplesInserted += samplesInserted;

                if (useRtpTimestamp)
                    outBuf.setRtpTimeStamp(lastOutputTimestamp);
                else
                    outBuf.setTimeStamp(lastOutputTimestamp);

                outBuf.setDuration( (diffSamples * 1000L * 1000L * 1000L)
                                    / sampleRate);


                lastOutputTimestamp
                        = calculateTimestamp(lastOutputTimestamp, samplesInserted);
            }
        }

        if (useInput)
        {
            int inLen = inBuf.getLength();

            if (COPY_DATA_FROM_INPUT_TO_OUTPUT)
            {
                // Copy the actual data from the input to the output.
                byte[] outData = validateByteArraySize(outBuf, inLen, false);

                outBuf.setLength(inLen);
                outBuf.setOffset(0);

                System.arraycopy(
                        inBuf.getData(), inBuf.getOffset(),
                        outData, 0,
                        inLen);

                // Now copy the remaining attributes.
                outBuf.setFormat(inBuf.getFormat());
                outBuf.setHeader(inBuf.getHeader());
                outBuf.setSequenceNumber(inBuf.getSequenceNumber());
                outBuf.setTimeStamp(inBuf.getTimeStamp());
                outBuf.setRtpTimeStamp(inBuf.getRtpTimeStamp());
                outBuf.setFlags(inBuf.getFlags());
                outBuf.setDiscard(inBuf.isDiscard());
                outBuf.setEOM(inBuf.isEOM());
                outBuf.setDuration(inBuf.getDuration());
            }
            else
            {
                outBuf.copy(inBuf);
            }

            lastOutputTimestamp = calculateTimestamp(lastOutputTimestamp,
                                                     (inLen * 8) / sampleSizeInBits);
        }

        return useInput ? BUFFER_PROCESSED_OK : INPUT_BUFFER_NOT_CONSUMED;
    }

    /**
     * Returns the timestamp obtained by adding <tt>samplesToAdd</tt> samples
     * (using a sample rate of <tt>this.sampleRate</tt> per second) to timestamp
     * (with a clock rate of <tt>this.clockRate</tt> per second).
     * @param oldTimestamp the timestamp to which to add.
     * @param samplesToAdd the number of samples to add.
     * @return the timestamp obtained by adding <tt>samplesToAdd</tt> samples
     * (using a sample rate of <tt>this.sampleRate</tt> per second) to timestamp
     * (with a clock rate of <tt>this.clockRate</tt> per second).
     */
    private long calculateTimestamp(long oldTimestamp, long samplesToAdd)
    {
        // duration of samplesToAdd (in seconds per clockRate)
        long duration = Math.round(
                ((double)(clockRate * samplesToAdd)) / sampleRate);

        long timestamp = oldTimestamp + duration;

        //RTP timestamps come from a 32bit field and wrap.
        if (useRtpTimestamp && timestamp > 1L<<32)
            timestamp -= 1L<<32;

        return timestamp;
    }

    /**
     * Fills the data of <tt>buf</tt> to at most <tt>samples</tt> samples of
     * silence. Returns the actual number of samples used.
     * @param buf the <tt>Buffer</tt> to fill with silence
     * @param samples the number of samples of silence to fill.
     * @return the number of samples of silence added in <tt>buf</tt>.
     */
    private int setSilence(Buffer buf, int samples)
    {
        int samplesToFill = Math.min(samples, MAX_SAMPLES_PER_PACKET);
        int len = samplesToFill * sampleSizeInBits / 8;
        byte[] data = validateByteArraySize(buf, len, false);
        Arrays.fill(data, (byte)0);

        buf.setOffset(0);
        buf.setLength(len);

        return samplesToFill;
    }

    /**
     * Resets the state of this <tt>SilenceEffect</tt>.
     *
     * TODO: is it appropriate to override the <tt>reset()</tt> method?
     */
    public void resetSilence()
    {
        lastOutputTimestamp = Buffer.TIME_UNKNOWN;
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public interface Listener
    {
        void onSilenceNotInserted(long timestamp);
    }
}
