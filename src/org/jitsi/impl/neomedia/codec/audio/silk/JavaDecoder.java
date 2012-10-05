/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec.audio.silk;

import javax.media.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.util.*;

/**
 * Implements the SILK decoder as an FMJ/JMF <tt>Codec</tt>.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 */
public class JavaDecoder
    extends AbstractCodecExt
{
    /**
     * The <tt>Logger</tt> used by this <tt>JavaDecoder</tt> instance
     * for logging output.
     */
    private final Logger logger
            = Logger.getLogger(JavaDecoder.class);

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
     * The length of an output frame as reported by
     * {@link Silk_dec_API#SKP_Silk_SDK_Decode(Object, SKP_SILK_SDK_DecControlStruct, int, byte[], int, int, short[], int, short[])}.
     */
    private final short[] outputLength = new short[1];

    /**
     * Previous packet RTP sequence number
     */
    private long lastPacketSeq;

    /**
     * Whether at least one packet has already been processed. Use this to
     * prevent FEC data from trying to be decoded from the first packet in a
     * session.
     */
    private boolean firstPacketProcessed = false;

    /**
     * Temporary buffer used to hold the lbrr data when decoding FEC. Defined
     * here to avoid using <tt>new</tt> in <tt>doProcess</tt>.
     */
    private byte[] lbrrData = new byte[JavaEncoder.MAX_BYTES_PER_FRAME];

    /**
     * Temporary buffer used when decoding FEC. Defined here to
     * avoid using <tt>new</tt> in <tt>doProcess</tt>.
     */
    private short[] lbrrBytes = new short[1];

    /**
     * Initializes a new <tt>JavaDecoder</tt> instance.
     */
    public JavaDecoder()
    {
        super("SILK Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);
        
        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    protected void doClose()
    {
        decState = null;
        decControl = null;
    }

    protected void doOpen()
        throws ResourceUnavailableException
    {
        decState = new SKP_Silk_decoder_state();
        if (Silk_dec_API.SKP_Silk_SDK_InitDecoder(decState) != 0)
            throw
                new ResourceUnavailableException(
                        "Silk_dec_API.SKP_Silk_SDK_InitDecoder");

        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        double sampleRate = inputFormat.getSampleRate();
        int channels = inputFormat.getChannels();

        decControl = new SKP_SILK_SDK_DecControlStruct();
        decControl.API_sampleRate = (int) sampleRate;

        frameLength = (short) ((FRAME_DURATION * sampleRate * channels) / 1000);
    }

    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        byte[] inputData = (byte[]) inputBuffer.getData();
        int inputOffset = inputBuffer.getOffset();
        int inputLength = inputBuffer.getLength();

        short[] outputData = validateShortArraySize(outputBuffer, frameLength);
        int outputOffset = 0;

        boolean decodeFEC = false;

        /* Check whether a packet has been lost.
         * If a packet has more than one frame, we go through each frame in a
         * new call to <tt>process</tt>, so having the same sequence number as
         * on the previous pass is fine. */
        long sequenceNumber = inputBuffer.getSequenceNumber();
        if(firstPacketProcessed &&
                sequenceNumber != lastPacketSeq &&
                sequenceNumber != lastPacketSeq+1 &&
                /* RTP sequence number is a 16bit field */
                !(lastPacketSeq == 65535 && sequenceNumber == 0))
            decodeFEC = true;

        int processed;

        /* Decode packet normally */
        if(!decodeFEC)
        {
            outputLength[0] = frameLength;
            if (Silk_dec_API.SKP_Silk_SDK_Decode(
                        decState, decControl,
                        0,
                        inputData, inputOffset, inputLength,
                        outputData, outputOffset, outputLength)
                    == 0)
            {
                outputBuffer.setDuration(FRAME_DURATION * 1000000);
                outputBuffer.setLength(outputLength[0]);
                outputBuffer.setOffset(outputOffset);

                if (decControl.moreInternalDecoderFrames == 0)
                    processed = BUFFER_PROCESSED_OK;
                else
                {
                    framesPerPayload++;
                    processed
                        = (framesPerPayload >= MAX_FRAMES_PER_PAYLOAD)
                            ? BUFFER_PROCESSED_OK
                            : INPUT_BUFFER_NOT_CONSUMED;
                }
            }
            else
                processed = BUFFER_PROCESSED_FAILED;

            if ((processed & INPUT_BUFFER_NOT_CONSUMED)
                    != INPUT_BUFFER_NOT_CONSUMED)
                framesPerPayload = 0;
        }
        else /* Decode the packet's FEC data */
        {
            outputLength[0] = frameLength;

            lbrrBytes[0] = 0;
            Silk_dec_API.SKP_Silk_SDK_search_for_LBRR(
                    inputData, inputOffset, (short)inputLength,
                    1 /* previous packet */,
                    lbrrData, 0, lbrrBytes);

            if(logger.isTraceEnabled())
            {
                logger.trace("Packet loss detected. Last seen " + lastPacketSeq
                        + ", current " + sequenceNumber);
                logger.trace("Looking for LBRR info, got " + lbrrBytes[0] + "bytes");
            }

            if(lbrrBytes[0] == 0)
                //No FEC data found, process the normal data in the packet next
                processed = INPUT_BUFFER_NOT_CONSUMED;
            else if(Silk_dec_API.SKP_Silk_SDK_Decode(
                            decState, decControl, 0,
                            lbrrData, 0, lbrrBytes[0],
                            outputData, outputOffset, outputLength)
                    == 0)
            {
                //Found FEC data, decode it
                outputBuffer.setDuration(FRAME_DURATION * 1000000);
                outputBuffer.setLength(outputLength[0]);
                outputBuffer.setOffset(outputOffset);

                //Go on and process the normal data in the packet next
                processed = INPUT_BUFFER_NOT_CONSUMED;
            }
            else
                processed = BUFFER_PROCESSED_FAILED;
        }

        lastPacketSeq = sequenceNumber;
        firstPacketProcessed = true;
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
