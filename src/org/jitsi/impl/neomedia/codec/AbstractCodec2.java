/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

import java.awt.*;

import javax.media.*;
import javax.media.format.*;

import net.sf.fmj.media.*;

/**
 * Extends FMJ's <tt>AbstractCodec</tt> to make it even easier to implement a
 * <tt>Codec</tt>.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractCodec2
    extends AbstractCodec
{
    /**
     * The <tt>Buffer</tt> flag which indicates that the respective
     * <tt>Buffer</tt> contains audio data which has been decoded as a result of
     * the operation of FEC.
     */
    public static final int BUFFER_FLAG_FEC = (1 << 24);
    /**
     * The <tt>Buffer</tt> flag which indicates that the respective
     * <tt>Buffer</tt> contains audio data which has been decoded as a result of
     * the operation of PLC.
     */
    public static final int BUFFER_FLAG_PLC = (1 << 25);

    /**
     * An empty array of <tt>Format</tt> element type. Explicitly defined to
     * reduce unnecessary allocations.
     */
    public static final Format[] EMPTY_FORMATS = new Format[0];

    /**
     * The maximum (RTP) sequence number value.
     */
    public static final int SEQUENCE_MAX = 65535;

    /**
     * The minimum (RTP) sequence number value.
     */
    public static final int SEQUENCE_MIN = 0;

    /**
     * Calculates the number of sequences which have been lost i.e. which have
     * not been received.
     *
     * @param lastSeqNo the last received sequence number (prior to the current
     * sequence number represented by <tt>seqNo</tt>.) May be
     * {@link Buffer#SEQUENCE_UNKNOWN}. May be equal to <tt>seqNo</tt> for the
     * purposes of Codec implementations which repeatedly process one and the
     * same input Buffer multiple times.
     * @param seqNo the current sequence number. May be equal to
     * <tt>lastSeqNo</tt> for the purposes of Codec implementations which
     * repeatedly process one and the same input Buffer multiple times.
     * @return the number of sequences (between <tt>lastSeqNo</tt> and
     * <tt>seqNo</tt>) which have been lost i.e. which have not been received
     */
    public static int calculateLostSeqNoCount(long lastSeqNo, long seqNo)
    {
    	if (lastSeqNo == Buffer.SEQUENCE_UNKNOWN)
    		return 0;

    	int delta = (int) (seqNo - lastSeqNo);

    	/*
    	 * We explicitly allow the same sequence number to be received multiple
    	 * times for the purposes of Codec implementations which repeatedly
    	 * process one and the same input Buffer multiple times.
    	 */
    	if (delta == 0)
    		return 0;
    	else if (delta > 0)
    	{
    		// The sequence number has not wrapped yet.
    		return delta - 1;
    	}
    	else
    	{
    		// The sequence number has wrapped.
    		return delta + SEQUENCE_MAX;
    	}
    }

    /**
     * Incements a specific sequence number and makes sure that the result stays
     * within the range of valid RTP sequence number values.
     *
     * @param seqNo the sequence number to increment
     * @return a sequence number which represents an increment over the
     * specified <tt>seqNo</tt> within the range of valid RTP sequence number
     * values
     */
    public static long incrementSeqNo(long seqNo)
    {
    	seqNo++;
    	if (seqNo > SEQUENCE_MAX)
    		seqNo = SEQUENCE_MIN;
    	return seqNo;
    }

    /**
     * Utility to perform format matching.
     *
     * @param in input format
     * @param outs array of output formats
     * @return the first output format that is supported
     */
    public static Format matches(Format in, Format outs[])
    {
        for (Format out : outs)
            if (in.matches(out))
                return out;
        return null;
    }

    public static YUVFormat specialize(YUVFormat yuvFormat, Class<?> dataType)
    {
        Dimension size = yuvFormat.getSize();
        int strideY = yuvFormat.getStrideY();

        if ((strideY == Format.NOT_SPECIFIED) && (size != null))
            strideY = size.width;

        int strideUV = yuvFormat.getStrideUV();

        if ((strideUV == Format.NOT_SPECIFIED)
                && (strideY != Format.NOT_SPECIFIED))
            strideUV = (strideY + 1) / 2;

        int offsetY = yuvFormat.getOffsetY();

        if (offsetY == Format.NOT_SPECIFIED)
            offsetY = 0;

        int offsetU = yuvFormat.getOffsetU();

        if ((offsetU == Format.NOT_SPECIFIED)
                && (strideY != Format.NOT_SPECIFIED)
                && (size != null))
            offsetU = offsetY + strideY * size.height;

        int offsetV = yuvFormat.getOffsetV();

        if ((offsetV == Format.NOT_SPECIFIED)
                && (offsetU != Format.NOT_SPECIFIED)
                && (strideUV != Format.NOT_SPECIFIED)
                && (size != null))
            offsetV = offsetU + strideUV * ((size.height + 1) / 2);

        int maxDataLength
            = ((strideY != Format.NOT_SPECIFIED)
                    && (strideUV != Format.NOT_SPECIFIED))
                    && (size != null)
                ? (strideY * size.height
                        + 2 * strideUV * ((size.height + 1) / 2)
                        + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE)
                : Format.NOT_SPECIFIED;

        return
            new YUVFormat(
                    size,
                    maxDataLength,
                    (dataType == null) ? yuvFormat.getDataType() : dataType,
                    yuvFormat.getFrameRate(),
                    YUVFormat.YUV_420,
                    strideY, strideUV,
                    offsetY, offsetU, offsetV);
    }

    private final Class<? extends Format> formatClass;

    /**
     * The name of this <tt>PlugIn</tt>.
     */
    private final String name;

    private final Format[] supportedOutputFormats;

    /**
     * Initializes a new <tt>AbstractCodec2</tt> instance with a specific
     * <tt>PlugIn</tt> name, a specific <tt>Class</tt> of input and output
     * <tt>Format</tt>s and a specific list of <tt>Format</tt>s supported as
     * output.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param formatClass the <tt>Class</tt> of input and output
     * <tt>Format</tt>s supported by the new instance
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by
     * the new instance as output
     */
    protected AbstractCodec2(
            String name,
            Class<? extends Format> formatClass,
            Format[] supportedOutputFormats)
    {
        this.formatClass = formatClass;
        this.name = name;
        this.supportedOutputFormats = supportedOutputFormats;

        /*
         * An Effect is a Codec that does not modify the Format of the data, it
         * modifies the contents.
         */
        if (this instanceof Effect)
            inputFormats = this.supportedOutputFormats;
    }

    @Override
    public void close()
    {
        if (!opened)
            return;

        doClose();

        opened = false;
        super.close();
    }

    protected void discardOutputBuffer(Buffer outputBuffer)
    {
        outputBuffer.setDiscard(true);
    }

    protected abstract void doClose();

    /**
     * Opens this <tt>Codec</tt> and acquires the resources that it needs to
     * operate. A call to {@link PlugIn#open()} on this instance will result in
     * a call to <tt>doOpen</tt> only if {@link AbstractCodec#opened} is
     * <tt>false</tt>. All required input and/or output formats are assumed to
     * have been set on this <tt>Codec</tt> before <tt>doOpen</tt> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <tt>Codec</tt> needs to operate cannot be acquired
     */
    protected abstract void doOpen()
        throws ResourceUnavailableException;

    protected abstract int doProcess(Buffer inputBuffer, Buffer outputBuffer);

    /**
     * Gets the <tt>Format</tt>s which are supported by this <tt>Codec</tt> as
     * output when the input is in a specific <tt>Format</tt>.
     *
     * @param inputFormat the <tt>Format</tt> of the input for which the
     * supported output <tt>Format</tt>s are to be returned
     * @return an array of <tt>Format</tt>s supported by this <tt>Codec</tt> as
     * output when the input is in the specified <tt>inputFormat</tt>
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        /*
         * An Effect is a Codec that does not modify the Format of the data, it
         * modifies the contents.
         */
        if (this instanceof Effect)
            return new Format[] { inputFormat };

        return
            (supportedOutputFormats == null)
                ? EMPTY_FORMATS
                : supportedOutputFormats.clone();
    }

    @Override
    public String getName()
    {
        return (name == null) ? super.getName() : name;
    }

    /**
     * Implements {@link AbstractCodec#getSupportedOutputFormats(Format)}.
     *
     * @param inputFormat input format
     * @return array of supported output format
     * @see AbstractCodec#getSupportedOutputFormats(Format)
     */
    public Format[] getSupportedOutputFormats(Format inputFormat)
    {
        if (inputFormat == null)
            return supportedOutputFormats;

        if (!formatClass.isInstance(inputFormat)
                || (matches(inputFormat, inputFormats) == null))
            return EMPTY_FORMATS;

        return getMatchingOutputFormats(inputFormat);
    }

    /**
     * Opens this <tt>PlugIn</tt> software or hardware component and acquires
     * the resources that it needs to operate. All required input and/or output
     * formats have to be set on this <tt>PlugIn</tt> before <tt>open</tt> is
     * called. Buffers should not be passed into this <tt>PlugIn</tt> without
     * first calling <tt>open</tt>.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <tt>PlugIn</tt> needs to operate cannot be acquired
     * @see AbstractPlugIn#open()
     */
    @Override
    public void open()
        throws ResourceUnavailableException
    {
        if (opened)
            return;

        doOpen();

        opened = true;
        super.open();
    }

    /**
     * Implements AbstractCodec#process(Buffer, Buffer).
     *
     * @param inputBuffer
     * @param outputBuffer
     * @return BUFFER_PROCESSED_OK if all go OK or BUFFER_PROCESSED_FAILED if
     * problems occurred
     * @see AbstractCodec#process(Buffer, Buffer)
     */
    public int process(Buffer inputBuffer, Buffer outputBuffer)
    {
        if (!checkInputBuffer(inputBuffer))
            return BUFFER_PROCESSED_FAILED;
        if (isEOM(inputBuffer))
        {
            propagateEOM(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }
        if (inputBuffer.isDiscard())
        {
            discardOutputBuffer(outputBuffer);
            return BUFFER_PROCESSED_OK;
        }

        return doProcess(inputBuffer, outputBuffer);
    }

    @Override
    public Format setInputFormat(Format format)
    {
        if (!formatClass.isInstance(format)
                || (matches(format, inputFormats) == null))
            return null;

        return super.setInputFormat(format);
    }

    @Override
    public Format setOutputFormat(Format format)
    {
        if (!formatClass.isInstance(format)
                || (matches(format, getMatchingOutputFormats(inputFormat))
                        == null))
            return null;

        return super.setOutputFormat(format);
    }

    /**
     * Updates the <tt>format</tt>, <tt>length</tt> and <tt>offset</tt> of a
     * specific output <tt>Buffer</tt> to specific values.
     *
     * @param outputBuffer the output <tt>Buffer</tt> to update the properties
     * of
     * @param format the <tt>Format</tt> to set on <tt>outputBuffer</tt>
     * @param length the length to set on <tt>outputBuffer</tt>
     * @param offset the offset to set on <tt>outputBuffer</tt>
     */
    protected void updateOutput(
            Buffer outputBuffer,
            Format format, int length, int offset)
    {
        outputBuffer.setFormat(format);
        outputBuffer.setLength(length);
        outputBuffer.setOffset(offset);
    }

    /**
     * Ensures that the value of the <tt>data</tt> property of a specific
     * <tt>Buffer</tt> is an array of <tt>byte</tt>s whose length is at least a
     * specific number of bytes.
     *
     * @param buffer the <tt>Buffer</tt> whose <tt>data</tt> property value is
     * to be validated
     * @param newSize the minimum length of the array of <tt>byte</tt> which is
     * to be the value of the <tt>data</tt> property of <tt>buffer</tt>
     * @param arraycopy <tt>true</tt> to copy the bytes which are in the
     * value of the <tt>data</tt> property of <tt>buffer</tt> at the time of the
     * invocation of the method if the value of the <tt>data</tt> property of
     * <tt>buffer</tt> is an array of <tt>byte</tt> whose length is less than
     * <tt>newSize</tt>; otherwise, <tt>false</tt>
     * @return an array of <tt>byte</tt>s which is the value of the
     * <tt>data</tt> property of <tt>buffer</tt> and whose length is at least
     * <tt>newSize</tt> number of bytes
     */
    public static byte[] validateByteArraySize(
            Buffer buffer,
            int newSize,
            boolean arraycopy)
    {
        Object data = buffer.getData();
        byte[] newBytes;

        if (data instanceof byte[])
        {
            byte[] bytes = (byte[]) data;

            if (bytes.length < newSize)
            {
                newBytes = new byte[newSize];
                buffer.setData(newBytes);
                if (arraycopy)
                    System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                else
                {
                    buffer.setLength(0);
                    buffer.setOffset(0);
                }
            }
            else
                newBytes = bytes;
        }
        else
        {
            newBytes = new byte[newSize];
            buffer.setData(newBytes);
            buffer.setLength(0);
            buffer.setOffset(0);
        }
        return newBytes;
    }

    protected short[] validateShortArraySize(Buffer buffer, int newSize)
    {
        Object data = buffer.getData();
        short[] newShorts;

        if (data instanceof short[])
        {
            short[] shorts = (short[]) data;

            if (shorts.length >= newSize)
                return shorts;

            newShorts = new short[newSize];
            System.arraycopy(shorts, 0, newShorts, 0, shorts.length);
        }
        else
        {
            newShorts = new short[newSize];
            buffer.setLength(0);
            buffer.setOffset(0);
        }

        buffer.setData(newShorts);
        return newShorts;
    }
}
