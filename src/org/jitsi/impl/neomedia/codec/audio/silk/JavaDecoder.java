/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import java.awt.*;
import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.util.*;


/**
 * Implements the SILK decoder as an FMJ/JMF <tt>Codec</tt>.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 */
public class JavaDecoder
    extends AbstractCodec2
{
    /**
     * A private class, an instance of which is registered via
     * <tt>addControl</tt>. This instance will be used by outside classes to
     * access decoder statistics.
     */
    private class Stats implements FECDecoderControl
    {
        /**
         * Returns the number packets for which FEC data was decoded in
         * <tt>JavaDecoder.this</tt>
         *
         * @return Returns the number packets for which FEC data was decoded in
         * <tt>JavaDecoder.this</tt>
         */
        public int fecPacketsDecoded()
        {
            return nbFECDecoded;
        }

        /**
         * Stub. Always return <tt>null</tt>, as it's not used.
         *
         * @return <tt>null</tt>
         */
        public Component getControlComponent()
        {
            return null;
        }
    }

    /**
     * The duration of a frame in milliseconds as defined by the SILK standard.
     */
    static final int FRAME_DURATION = 20;

    /**
     * The maximum number of frames encoded into a single payload as defined by
     * the SILK standard.
     */
    private static final int MAX_FRAMES_PER_PAYLOAD = 5;
    /**
     * The list of <tt>Format</tt>s of audio data supported as input by
     * <tt>JavaDecoder</tt> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS
        = JavaEncoder.SUPPORTED_OUTPUT_FORMATS;

    /**
     * The list of <tt>Format</tt>s of audio data supported as output by
     * <tt>JavaDecoder</tt> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS
        = JavaEncoder.SUPPORTED_INPUT_FORMATS;

    /**
     * The SILK decoder control (structure).
     */
    private SKP_SILK_SDK_DecControlStruct decControl;

    /**
     * The SILK decoder state.
     */
    private SKP_Silk_decoder_state decState;

    /**
     * The length of an output frame as determined by {@link #FRAME_DURATION}
     * and the <tt>inputFormat</tt> of this <tt>JavaDecoder</tt>.
     */
    private short frameLength;

    /**
     * The number of frames decoded from the last input <tt>Buffer</tt> which
     * has not been consumed yet.
     */
    private int framesPerPayload;

    /**
     * The sequence number of the last processed <tt>Buffer</tt>.
     */
    private long lastSeqNo = Buffer.SEQUENCE_UNKNOWN;

    /**
     * Temporary buffer used when decoding FEC. Defined here to
     * avoid using <tt>new</tt> in <tt>doProcess</tt>.
     */
    private short[] lbrrBytes = new short[1];

    /**
     * Temporary buffer used to hold the lbrr data when decoding FEC. Defined
     * here to avoid using <tt>new</tt> in <tt>doProcess</tt>.
     */
    private byte[] lbrrData = new byte[JavaEncoder.MAX_BYTES_PER_FRAME];

    /**
     * The <tt>Logger</tt> used by this <tt>JavaDecoder</tt> instance
     * for logging output.
     */
    private final Logger logger
            = Logger.getLogger(JavaDecoder.class);

    /**
     * Number of packets which: were missing, the following packet was available
     * and it contained FEC data.
     */
    private int nbFECDecoded = 0;

    /**
     * Number of packets which: were missing, the next packet was available, but
     * it did not contain FEC data.
     */
    private int nbFECNotDecoded = 0;

    /**
     * Number of packets which: were successfully decoded
     */
    private int nbPacketsDecoded = 0;

    /**
     * Number of packets which: were missing, and the subsequent packet was also
     * missing.
     */
    private int nbPacketsLost = 0;

    /**
     * The length of an output frame as reported by
     * {@link Silk_dec_API#SKP_Silk_SDK_Decode(Object, SKP_SILK_SDK_DecControlStruct, int, byte[], int, int, short[], int, short[])}.
     */
    private final short[] outputLength = new short[1];

    /**
     * Initializes a new <tt>JavaDecoder</tt> instance.
     */
    public JavaDecoder()
    {
        super("SILK Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);
        
        inputFormats = SUPPORTED_INPUT_FORMATS;

        addControl(new Stats());
    }

    protected void doClose()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Packets decoded normally: " + nbPacketsDecoded);
            logger.debug("Packets decoded with FEC: " + nbFECDecoded);
            logger.debug("Packets lost (subsequent missing):"
                    + nbPacketsLost);
            logger.debug("Packets lost (no FEC in subsequent): "
                    + nbFECNotDecoded);
        }

        decState = null;
        decControl = null;
    }

    protected void doOpen()
        throws ResourceUnavailableException
    {
        decState = new SKP_Silk_decoder_state();
        if (DecAPI.SKP_Silk_SDK_InitDecoder(decState) != 0)
        {
            throw new ResourceUnavailableException(
                    "DecAPI.SKP_Silk_SDK_InitDecoder");
        }

        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        double sampleRate = inputFormat.getSampleRate();
        int channels = inputFormat.getChannels();

        decControl = new SKP_SILK_SDK_DecControlStruct();
        decControl.API_sampleRate = (int) sampleRate;

        frameLength = (short) ((FRAME_DURATION * sampleRate * channels) / 1000);
        lastSeqNo = Buffer.SEQUENCE_UNKNOWN;
    }

    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        byte[] in = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();
        int inLength = inBuffer.getLength();

        short[] out = validateShortArraySize(outBuffer, frameLength);
        int outOffset = 0;

        boolean decodeFEC = false;

        /*
         * Check whether a packet has been lost. If a packet has more than one
         * frame, we go through each frame in a new call to the process method
         * so having the same sequence number as on the previous pass is fine.
         */
        long seqNo = inBuffer.getSequenceNumber();
        int nbPacketsLost = 0;

        if ((lastSeqNo != Buffer.SEQUENCE_UNKNOWN)
                && (seqNo != lastSeqNo)
                && (seqNo != lastSeqNo + 1)
                && (seqNo > lastSeqNo))
        {
            decodeFEC = true;
            nbPacketsLost = (int) (seqNo - lastSeqNo - 1);
        }
        if ((inBuffer.getFlags() & Buffer.FLAG_SKIP_FEC) != 0)
        {
            decodeFEC = false;
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Not decoding FEC for " + seqNo
                            + " because of Buffer.FLAG_SKIP_FEC.");
            }
        }

        int lostFlag = 0;

        if (decodeFEC) /* Decode with FEC. */
        {
            lbrrBytes[0] = 0;
            DecAPI.SKP_Silk_SDK_search_for_LBRR(
                    in, inOffset, (short)inLength,
                    nbPacketsLost /* lost_offset */,
                    lbrrData, 0, lbrrBytes);
            if (logger.isTraceEnabled())
            {
                logger.trace(
                        "Packet loss detected. Last seen " + lastSeqNo
                            + ", current " + seqNo);
                logger.trace(
                        "Looking for FEC data, found " + lbrrBytes[0]
                            + "bytes");
            }

            outputLength[0] = frameLength;
            if (lbrrBytes[0] == 0)
            {
                // No FEC data found, process the packet as lost.
                lostFlag = 1;
            }
            else if(DecAPI.SKP_Silk_SDK_Decode(
                        decState, decControl, 0,
                        lbrrData, 0, lbrrBytes[0],
                        out, outOffset, outputLength)
                    == 0)
            {
                // Found FEC data, decode it.
                nbFECDecoded++;

                outBuffer.setDuration(FRAME_DURATION * 1000000);
                outBuffer.setLength(outputLength[0]);
                outBuffer.setOffset(outOffset);

                outBuffer.setFlags(outBuffer.getFlags() | BUFFER_FLAG_FEC);
                outBuffer.setFlags(outBuffer.getFlags() & ~BUFFER_FLAG_PLC);

                /*
                 * We have decoded the expected sequence number from the FEC
                 * data.
                 */
                lastSeqNo++;
                if (lastSeqNo > 65535)
                    lastSeqNo = 0;
                return INPUT_BUFFER_NOT_CONSUMED;
            }
            else
            {
                nbFECNotDecoded++;
                if (nbPacketsLost > 0)
                    this.nbPacketsLost += nbPacketsLost;
                lastSeqNo = seqNo;
                return BUFFER_PROCESSED_FAILED;
            }
        }
        else if (nbPacketsLost > 0)
            this.nbPacketsLost += nbPacketsLost;

        int processed;

        /* Decode without FEC. */
        {
            outputLength[0] = frameLength;
            if (DecAPI.SKP_Silk_SDK_Decode(
                        decState, decControl,
                        lostFlag,
                        in, inOffset, inLength,
                        out, outOffset, outputLength)
                    == 0)
            {
                outBuffer.setDuration(FRAME_DURATION * 1000000);
                outBuffer.setLength(outputLength[0]);
                outBuffer.setOffset(outOffset);

                if (lostFlag == 0)
                {
                    outBuffer.setFlags(
                            outBuffer.getFlags()
                                & ~(BUFFER_FLAG_FEC | BUFFER_FLAG_PLC));

                    if (decControl.moreInternalDecoderFrames == 0)
                    {
                        nbPacketsDecoded++;
                        processed = BUFFER_PROCESSED_OK;
                    }
                    else
                    {
                        framesPerPayload++;
                        if (framesPerPayload >= MAX_FRAMES_PER_PAYLOAD)
                        {
                            nbPacketsDecoded++;
                            processed = BUFFER_PROCESSED_OK;
                        }
                        else
                            processed = INPUT_BUFFER_NOT_CONSUMED;
                    }
                    lastSeqNo = seqNo;
                }
                else
                {
                    outBuffer.setFlags(outBuffer.getFlags() & ~BUFFER_FLAG_FEC);
                    outBuffer.setFlags(outBuffer.getFlags() | BUFFER_FLAG_PLC);

                    processed = INPUT_BUFFER_NOT_CONSUMED;
                    // We have decoded the expected sequence number with PLC.
                    lastSeqNo++;
                    if (lastSeqNo > 65535)
                        lastSeqNo = 0;
                }
            }
            else
            {
                processed = BUFFER_PROCESSED_FAILED;
                if (lostFlag == 1)
                {
                    nbFECNotDecoded++;
                    if (nbPacketsLost > 0)
                        this.nbPacketsLost += nbPacketsLost;
                }
                lastSeqNo = seqNo;
            }

            if ((processed & INPUT_BUFFER_NOT_CONSUMED)
                    != INPUT_BUFFER_NOT_CONSUMED)
                framesPerPayload = 0;
        }

        return processed;
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodecExt#getMatchingOutputFormats(Format)
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        return
            JavaEncoder.getMatchingOutputFormats(
                    inputFormat,
                    SUPPORTED_INPUT_FORMATS,
                    SUPPORTED_OUTPUT_FORMATS);
    }
}
