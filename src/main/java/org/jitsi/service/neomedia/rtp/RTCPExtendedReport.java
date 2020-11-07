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
package org.jitsi.service.neomedia.rtp;

import java.io.*;
import java.util.*;

import net.sf.fmj.media.rtp.*;

/**
 * Represents an RTP Control Protocol Extended Report (RTCP XR) packet in the
 * terms of FMJ i.e. as an <tt>RTCPPacket</tt> sub-class.
 *
 * @author Lyubomir Marinov
 */
public class RTCPExtendedReport
    extends RTCPPacket
{
    /**
     * Represents an abstract, base extended report block.
     *
     * @author Lyubomir Marinov
     */
    public static abstract class ReportBlock
    {
        /**
         * The block type/format of this report block.
         */
        public final short blockType;

        /**
         * Initializes a new <tt>ReportBlock</tt> instance of a specific block
         * type.
         *
         * @param blockType the block type/format of the new instance
         */
        protected ReportBlock(short blockType)
        {
            this.blockType = blockType;
        }

        /**
         * Serializes/writes the binary representation of this
         * <tt>ReportBlock</tt> into a specific <tt>DataOutputStream</tt>.
         *
         * @param dataoutputstream the <tt>DataOutputStream</tt> into which the
         * binary representation of this <tt>ReportBlock</tt> is to be
         * serialized/written.
         * @throws IOException if an input/output error occurs during the
         * serialization/writing of the binary representation of this
         * <tt>ReportBlock</tt>
         */
        protected abstract void assemble(DataOutputStream dataoutputstream)
            throws IOException;

        /**
         * Computes the length in <tt>byte</tt>s of this <tt>ReportBlock</tt>,
         * including the header and any padding.
         * <p>
         * The implementation of <tt>ReportBlock</tt> returns the length in
         * <tt>byte</tt>s of the header of an extended report block i.e.
         * <tt>4</tt>. The implementation is provided as a convenience because
         * RFC 3611 defines that the type-specific block contents of an extended
         * report block may be zero bits long if the block type definition
         * permits.
         * </p>
         *
         * @return the length in <tt>byte</tt>s of this <tt>ReportBlock</tt>,
         * including the header and any padding.
         */
        public int calcLength()
        {
            return
                1 /* block type (BT) */
                    + 1 /* type-specific */
                    + 2 /* block length */;
        }
    }

    /**
     * Implements &quot;VoIP Metrics Report Block&quot; i.e. an extended report
     * block which provides metrics for monitoring voice over IP (VoIP) calls.
     *
     * @author Lyubomir Marinov
     */
    public static class VoIPMetricsReportBlock
        extends ReportBlock
    {
        /**
         * The jitter buffer size is being dynamically adjusted to deal with
         * varying levels of jitter.
         */
        public static final byte ADAPTIVE_JITTER_BUFFER_ADAPTIVE = 3;

        /**
         * Silence is being inserted in place of lost packets.
         */
        public static final byte DISABLED_PACKET_LOSS_CONCEALMENT = 1;

        /**
         * An enhanced interpolation algorithm is being used; algorithms of this
         * type are able to conceal high packet loss rates effectively.
         */
        public static final byte ENHANCED_PACKET_LOSS_CONCEALMENT = 2;

        /**
         * The jitter buffer size is maintained at a fixed level.
         */
        public static final byte NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE = 2;

        public static final byte RESERVED_JITTER_BUFFER_ADAPTIVE = 1;

        public static final String SDP_PARAMETER = "voip-metrics";

        /**
         * A simple replay or interpolation algorithm is being used to fill-in
         * the missing packet; this approach is typically able to conceal
         * isolated lost packets at low packet loss rates.
         */
        public static final byte STANDARD_PACKET_LOSS_CONCEALMENT = 3;

        public static final byte UNKNOWN_JITTER_BUFFER_ADAPTIVE = 0;

        /**
         * No information is available concerning the use of packet loss
         * concealment (PLC); however, for some codecs this may be inferred.
         */
        public static final byte UNSPECIFIED_PACKET_LOSS_CONCEALMENT = 0;

        public static final short VOIP_METRICS_REPORT_BLOCK_TYPE = 7;

        /**
         * The fraction of RTP data packets within burst periods since the
         * beginning of reception that were either lost or discarded. The value
         * is expressed as a fixed point number with the binary point at the
         * left edge of the field. It is calculated by dividing the total number
         * of packets lost or discarded (excluding duplicate packet discards)
         * within burst periods by the total number of packets expected within
         * the burst periods, multiplying the result of the division by 256,
         * limiting the maximum value to 255 (to avoid overflow), and taking the
         * integer part.  The field MUST be populated and MUST be set to zero if
         * no packets have been received.
         */
        private short burstDensity = 0;

        private int burstDuration = 0;

        /**
         * The fraction of RTP data packets from the source that have been
         * discarded since the beginning of reception, due to late or early
         * arrival, under-run or overflow at the receiving jitter buffer. The
         * value is expressed as a fixed point number with the binary point at
         * the left edge of the field. It is calculated by dividing the total
         * number of packets discarded (excluding duplicate packet discards) by
         * the total number of packets expected, multiplying the result of the
         * division by 256, limiting the maximum value to 255 (to avoid
         * overflow), and taking the integer part.
         */
        private short discardRate = 0;

        private int endSystemDelay = 0;

        private byte extRFactor = 127;

        /**
         * The fraction of RTP data packets within inter-burst gaps since the
         * beginning of reception that were either lost or discarded. The value
         * is expressed as a fixed point number with the binary point at the
         * left edge of the field. It is calculated by dividing the total number
         * of packets lost or discarded (excluding duplicate packet discards)
         * within gap periods by the total number of packets expected within the
         * gap periods, multiplying the result of the division by 256, limiting
         * the maximum value to 255 (to avoid overflow), and taking the integer
         * part. The field MUST be populated and MUST be set to zero if no
         * packets have been received.
         */
        private short gapDensity = 0;

        private int gapDuration = 0;

        private short gMin = 16;

        private int jitterBufferAbsoluteMaximumDelay;

        /**
         * Whether the jitter buffer is adaptive. The value is one of the
         * constants {@link #ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #RESERVED_JITTER_BUFFER_ADAPTIVE}, and
         * {@link #UNKNOWN_JITTER_BUFFER_ADAPTIVE}.
         */
        private byte jitterBufferAdaptive = UNKNOWN_JITTER_BUFFER_ADAPTIVE;

        private int jitterBufferMaximumDelay;

        private int jitterBufferNominalDelay;

        /**
         * The implementation specific adjustment rate of a jitter buffer in
         * adaptive mode. Defined in terms of the approximate time taken to
         * fully adjust to a step change in peak to peak jitter from 30 ms to
         * 100 ms such that: <tt>adjustment time = 2 * J * frame size (ms)<tt>
         * where <tt>J = adjustment rate (0-15)<tt>. The parameter is intended
         * only to provide a guide to the degree of &quot;aggressiveness&quot;
         * of an adaptive jitter buffer and may be estimated. A value of
         * <tt>0</tt> indicates that the adjustment time is unknown for this
         * implementation.
         */
        private byte jitterBufferRate = 0;

        /**
         * The fraction of RTP data packets from the source lost since the
         * beginning of reception, expressed as a fixed point number with the
         * binary point at the left edge of the field.  This value is calculated
         * by dividing the total number of packets lost (after the effects of
         * applying any error protection such as FEC) by the total number of
         * packets expected, multiplying the result of the division by 256,
         * limiting the maximum value to 255 (to avoid overflow), and taking the
         * integer part. The numbers of duplicated packets and discarded packets
         * do not enter into this calculation. Since receivers cannot be
         * required to maintain unlimited buffers, a receiver MAY categorize
         * late-arriving packets as lost. The degree of lateness that triggers a
         * loss SHOULD be significantly greater than that which triggers a
         * discard.
         */
        private short lossRate = 0;

        private byte mosCq = 127;

        private byte mosLq = 127;

        private byte noiseLevel = 127;

        /**
         * The type of packet loss concealment (PLC). The value is one of the
         * constants {@link #STANDARD_PACKET_LOSS_CONCEALMENT},
         * {@link #ENHANCED_PACKET_LOSS_CONCEALMENT},
         * {@link #DISABLED_PACKET_LOSS_CONCEALMENT}, and
         * {@link #UNSPECIFIED_PACKET_LOSS_CONCEALMENT}.
         */
        private byte packetLossConcealment
            = UNSPECIFIED_PACKET_LOSS_CONCEALMENT;

        private byte residualEchoReturnLoss = 127;

        private byte rFactor = 127;

        private int roundTripDelay = 0;

        private byte signalLevel = 127;

        /**
         * The synchronization source identifier (SSRC) of the RTP data packet
         * source being reported upon by this report block.
         */
        private int sourceSSRC;

        /**
         * Initializes a new <tt>VoIPMetricsReportBlock</tt> instance.
         */
        public VoIPMetricsReportBlock()
        {
            super(VOIP_METRICS_REPORT_BLOCK_TYPE);
        }

        /**
         * Initializes a new <tt>VoIPMetricsReportBlock</tt> instance by
         * deserializing/reading a binary representation from a
         * <tt>DataInputStream</tt>.
         *
         * @param blockLength the length of the extended report block to read,
         * not including the header, in bytes.
         * @param datainputstream the binary representation from which the new
         * instance is to be initialized. The <tt>datainputstream</tt> is asumed
         * to contain type-specific block contents without extended report block
         * header i.e. no block type (BT), type-specific, and block length
         * fields will be read from <tt>datainputstream</tt>.
         * @throws IOException if an input/output error occurs while
         * deserializing/reading the new instance from <tt>datainputstream</tt>
         * or the binary representation does not parse into an
         * <tt>VoIPMetricsReportBlock</tt> instance
         */
        public VoIPMetricsReportBlock(
                int blockLength,
                DataInputStream datainputstream)
            throws IOException
        {
            this();

            // block length (RFC 3611, Section 4.7)
            if (blockLength != 8 * 4)
            {
                throw new IOException(
                    "Invalid RTCP XR VoIP Metrics block length.");
            }

            // SSRC of source
            setSourceSSRC(datainputstream.readInt());
            // lost rate
            setLossRate((short) datainputstream.readUnsignedByte());
            // discard rate
            setDiscardRate((short) datainputstream.readUnsignedByte());
            // burst density
            setBurstDensity((short) datainputstream.readUnsignedByte());
            // gap density
            setGapDensity((short) datainputstream.readUnsignedByte());
            // burst duration
            setBurstDuration(datainputstream.readUnsignedShort());
            // gap duration
            setGapDuration(datainputstream.readUnsignedShort());
            // round trip delay
            setRoundTripDelay(datainputstream.readUnsignedShort());
            // end system delay
            setEndSystemDelay(datainputstream.readUnsignedShort());
            // signal level
            setSignalLevel(datainputstream.readByte());
            // noise level
            setNoiseLevel(datainputstream.readByte());
            // residual echo return loss (RERL)
            setResidualEchoReturnLoss(datainputstream.readByte());
            // Gmin
            setGMin((short) datainputstream.readUnsignedByte());
            // R factor
            setRFactor(datainputstream.readByte());
            // ext. R factor
            setExtRFactor(datainputstream.readByte());
            // MOS-LQ
            setMosLq(datainputstream.readByte());
            // MOS-CQ
            setMosCq(datainputstream.readByte());

            // receiver configuration byte (RX config)
            int rxConfig = datainputstream.readUnsignedByte();

            setPacketLossConcealment((byte) ((rxConfig & 0xC0) >>> 6));
            setJitterBufferAdaptive((byte) ((rxConfig & 0x30) >>> 4));
            setJitterBufferRate((byte) (rxConfig & 0x0F));
            // reserved
            datainputstream.readByte();
            // jitter buffer nominal delay (JB nominal)
            setJitterBufferNominalDelay(datainputstream.readUnsignedShort());
            // jitter buffer maximum delay (JB maximum)
            setJitterBufferMaximumDelay(datainputstream.readUnsignedShort());
            // jitter buffer absolute maximum delay (JB abs max)
            setJitterBufferAbsoluteMaximumDelay(
                    datainputstream.readUnsignedShort());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void assemble(DataOutputStream dataoutputstream)
            throws IOException
        {
            // BT=7
            dataoutputstream.writeByte(VOIP_METRICS_REPORT_BLOCK_TYPE);
            // reserved
            dataoutputstream.writeByte(0);
            // block length = 8
            dataoutputstream.writeShort(8);
            // SSRC of source
            dataoutputstream.writeInt(getSourceSSRC());
            // loss rate
            dataoutputstream.writeByte(getLossRate());
            // discard rate
            dataoutputstream.writeByte(getDiscardRate());
            // burst density
            dataoutputstream.writeByte(getBurstDensity());
            // gap density
            dataoutputstream.writeByte(getGapDensity());
            // burst duration
            dataoutputstream.writeShort(getBurstDuration());
            // gap duration
            dataoutputstream.writeShort(getGapDuration());
            // round trip delay
            dataoutputstream.writeShort(getRoundTripDelay());
            // end system delay
            dataoutputstream.writeShort(getEndSystemDelay());
            // signal level
            dataoutputstream.writeByte(getSignalLevel());
            // noise level
            dataoutputstream.writeByte(getNoiseLevel());
            // residual echo return loss (RERL)
            dataoutputstream.writeByte(getResidualEchoReturnLoss());
            // Gmin
            dataoutputstream.writeByte(getGMin());
            // R factor
            dataoutputstream.writeByte(getRFactor());
            // ext. R factor
            dataoutputstream.writeByte(getExtRFactor());
            // MOS-LQ
            dataoutputstream.writeByte(getMosLq());
            // MOS-CQ
            dataoutputstream.writeByte(getMosCq());
            // receiver configuration byte (RX config)
            dataoutputstream.writeByte(
                    ((getPacketLossConcealment() & 0x03) << 6)
                        | ((getJitterBufferAdaptive() & 0x03) << 4)
                        | (getJitterBufferRate() & 0x0F));
            // reserved
            dataoutputstream.writeByte(0);
            // jitter buffer nominal delay (JB nominal)
            dataoutputstream.writeShort(getJitterBufferNominalDelay());
            // jitter buffer maximum delay (JB maximum)
            dataoutputstream.writeShort(getJitterBufferMaximumDelay());
            // jitter buffer absolute maximum delay (JB abs max)
            dataoutputstream.writeShort(getJitterBufferAbsoluteMaximumDelay());
        }

        /**
         * {@inheritDoc}
         *
         * As defined by RFC 3611, a VoIP Metrics Report Block has a length in
         * <tt>byte</tt>s equal to <tt>36</tt>, including the extended report
         * block header.
         */
        @Override
        public int calcLength()
        {
            return (8 /* block length */ + 1) * 4;
        }

        /**
         * Gets the fraction of RTP data packets within burst periods since the
         * beginning of reception that were either lost or discarded.
         *
         * @return the fraction of RTP data packets within burst periods since
         * the beginning of reception that were either lost or discarded
         */
        public short getBurstDensity()
        {
            return burstDensity;
        }

        public int getBurstDuration()
        {
            return burstDuration;
        }

        /**
         * Gets the fraction of RTP data packets from the source that have been
         * discarded since the beginning of reception, due to late or early
         * arrival, under-run or overflow at the receiving jitter buffer.
         *
         * @return the fraction of RTP data packets from the source that have
         * been discarded since the beginning of reception, due to late or early
         * arrival, under-run or overflow at the receiving jitter buffer
         * @see #discardRate
         */
        public short getDiscardRate()
        {
            return discardRate;
        }

        public int getEndSystemDelay()
        {
            return endSystemDelay;
        }

        public byte getExtRFactor()
        {
            return extRFactor;
        }

        /**
         * Get the fraction of RTP data packets within inter-burst gaps since
         * the beginning of reception that were either lost or discarded.
         *
         * @return the fraction of RTP data packets within inter-burst gaps
         * since the beginning of reception that were either lost or discarded
         */
        public short getGapDensity()
        {
            return gapDensity;
        }

        public int getGapDuration()
        {
            return gapDuration;
        }

        public short getGMin()
        {
            return gMin;
        }

        public int getJitterBufferAbsoluteMaximumDelay()
        {
            return jitterBufferAbsoluteMaximumDelay;
        }

        /**
         * Gets whether the jitter buffer is adaptive.
         *
         * @return {@link #ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #RESERVED_JITTER_BUFFER_ADAPTIVE}, or
         * {@link #UNKNOWN_JITTER_BUFFER_ADAPTIVE}
         */
        public byte getJitterBufferAdaptive()
        {
            return jitterBufferAdaptive;
        }

        public int getJitterBufferMaximumDelay()
        {
            return jitterBufferMaximumDelay;
        }

        public int getJitterBufferNominalDelay()
        {
            return jitterBufferNominalDelay;
        }

        /**
         * Gets the implementation specific adjustment rate of a jitter buffer
         * in adaptive mode.
         *
         * @return the implementation specific adjustment rate of a jitter
         * buffer in adaptive mode
         */
        public byte getJitterBufferRate()
        {
            return jitterBufferRate;
        }

        /**
         * Gets the fraction of RTP data packets from the source lost since the
         * beginning of reception.
         *
         * @return the fraction of RTP data packets from the source lost since
         * the beginning of reception
         * @see #lossRate
         */
        public short getLossRate()
        {
            return lossRate;
        }

        public byte getMosCq()
        {
            return mosCq;
        }

        public byte getMosLq()
        {
            return mosLq;
        }

        public byte getNoiseLevel()
        {
            return noiseLevel;
        }

        /**
         * Gets the type of packet loss concealment (PLC).
         *
         * @return {@link #STANDARD_PACKET_LOSS_CONCEALMENT},
         * {@link #ENHANCED_PACKET_LOSS_CONCEALMENT},
         * {@link #DISABLED_PACKET_LOSS_CONCEALMENT}, or
         * {@link #UNSPECIFIED_PACKET_LOSS_CONCEALMENT}
         */
        public byte getPacketLossConcealment()
        {
            return packetLossConcealment;
        }

        public byte getResidualEchoReturnLoss()
        {
            return residualEchoReturnLoss;
        }

        public byte getRFactor()
        {
            return rFactor;
        }

        public int getRoundTripDelay()
        {
            return roundTripDelay;
        }

        public byte getSignalLevel()
        {
            return signalLevel;
        }

        /**
         * Gets the synchronization source identifier (SSRC) of the RTP data
         * packet source being reported upon by this report block.
         *
         * @return the synchronization source identifier (SSRC) of the RTP data
         * packet source being reported upon by this report block
         */
        public int getSourceSSRC()
        {
            return sourceSSRC;
        }

        /**
         * Sets the fraction of RTP data packets within burst periods since the
         * beginning of reception that were either lost or discarded.
         *
         * @param burstDensity the fraction of RTP data packets within burst
         * periods since the beginning of reception that were either lost or
         * discarded
         */
        public void setBurstDensity(short burstDensity)
        {
            this.burstDensity = burstDensity;
        }

        public void setBurstDuration(int burstDuration)
        {
            this.burstDuration = burstDuration;
        }

        /**
         * Sets the fraction of RTP data packets from the source that have been
         * discarded since the beginning of reception, due to late or early
         * arrival, under-run or overflow at the receiving jitter buffer.
         *
         * @param discardRate the fraction of RTP data packets from the source
         * that have been discarded since the beginning of reception, due to
         * late or early arrival, under-run or overflow at the receiving jitter
         * buffer
         * @see #discardRate
         */
        public void setDiscardRate(short discardRate)
        {
            this.discardRate = discardRate;
        }

        public void setEndSystemDelay(int endSystemDelay)
        {
            this.endSystemDelay = endSystemDelay;
        }

        public void setExtRFactor(byte extRFactor)
        {
            this.extRFactor = extRFactor;
        }

        /**
         * Sets the fraction of RTP data packets within inter-burst gaps since
         * the beginning of reception that were either lost or discarded.
         *
         * @param gapDensity the fraction of RTP data packets within inter-burst
         * gaps since the beginning of reception that were either lost or
         * discarded
         */
        public void setGapDensity(short gapDensity)
        {
            this.gapDensity = gapDensity;
        }

        public void setGapDuration(int gapDuration)
        {
            this.gapDuration = gapDuration;
        }

        public void setGMin(short gMin)
        {
            this.gMin = gMin;
        }

        public void setJitterBufferAbsoluteMaximumDelay(
                int jitterBufferAbsoluteMaximumDelay)
        {
            this.jitterBufferAbsoluteMaximumDelay
                = jitterBufferAbsoluteMaximumDelay;
        }

        /**
         * Sets whether the jitter buffer is adaptive.
         *
         * @param jitterBufferAdaptive {@link #ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE},
         * {@link #RESERVED_JITTER_BUFFER_ADAPTIVE}, or
         * {@link #UNKNOWN_JITTER_BUFFER_ADAPTIVE}
         * @throws IllegalArgumentException if the specified
         * <tt>jitterBufferAdapter</tt> is not one of the constants
         * <tt>ADAPTIVE_JITTER_BUFFER_ADAPTIVE</tt>,
         * <tt>NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE</tt>,
         * <tt>RESERVED_JITTER_BUFFER_ADAPTIVE</tt>, and
         * <tt>UNKNOWN_JITTER_BUFFER_ADAPTIVE</tt>
         */
        public void setJitterBufferAdaptive(byte jitterBufferAdaptive)
        {
            switch (jitterBufferAdaptive)
            {
            case ADAPTIVE_JITTER_BUFFER_ADAPTIVE:
            case NON_ADAPTIVE_JITTER_BUFFER_ADAPTIVE:
            case RESERVED_JITTER_BUFFER_ADAPTIVE:
            case UNKNOWN_JITTER_BUFFER_ADAPTIVE:
                this.jitterBufferAdaptive = jitterBufferAdaptive;
                break;
            default:
                throw new IllegalArgumentException("jitterBufferAdaptive");
            }
        }

        public void setJitterBufferMaximumDelay(int jitterBufferMaximumDelay)
        {
            this.jitterBufferMaximumDelay = jitterBufferMaximumDelay;
        }

        public void setJitterBufferNominalDelay(int jitterBufferNominalDelay)
        {
            this.jitterBufferNominalDelay = jitterBufferNominalDelay;
        }

        /**
         * Sets the implementation specific adjustment rate of a jitter buffer
         * in adaptive mode.
         *
         * @param jitterBufferRate the implementation specific adjustment rate
         * of a jitter buffer in adaptive mode
         */
        public void setJitterBufferRate(byte jitterBufferRate)
        {
            this.jitterBufferRate = jitterBufferRate;
        }

        /**
         * Sets the fraction of RTP data packets from the source lost since the
         * beginning of reception.
         *
         * @param lossRate the fraction of RTP data packets from the source lost
         * since the beginning of reception
         * @see #lossRate
         */
        public void setLossRate(short lossRate)
        {
            this.lossRate = lossRate;
        }

        public void setMosCq(byte mosCq)
        {
            this.mosCq = mosCq;
        }

        public void setMosLq(byte mosLq)
        {
            this.mosLq = mosLq;
        }

        public void setNoiseLevel(byte noiseLevel)
        {
            this.noiseLevel = noiseLevel;
        }

        /**
         * Sets the type of packet loss concealment (PLC).
         *
         * @param packetLossConcealment
         * {@link #STANDARD_PACKET_LOSS_CONCEALMENT},
         * {@link #ENHANCED_PACKET_LOSS_CONCEALMENT},
         * {@link #DISABLED_PACKET_LOSS_CONCEALMENT}, or
         * {@link #UNSPECIFIED_PACKET_LOSS_CONCEALMENT}
         * @throws IllegalArgumentException if the specified
         * <tt>packetLossConcealment</tt> is not one of the constants
         * <tt>STANDARD_PACKET_LOSS_CONCEALMENT</tt>,
         * <tt>ENHANCED_PACKET_LOSS_CONCEALMENT</tt>,
         * <tt>DISABLED_PACKET_LOSS_CONCEALMENT</tt>, and
         * <tt>UNSPECIFIED_PACKET_LOSS_CONCEALMENT</tt>
         */
        public void setPacketLossConcealment(byte packetLossConcealment)
        {
            switch (packetLossConcealment)
            {
            case STANDARD_PACKET_LOSS_CONCEALMENT:
            case ENHANCED_PACKET_LOSS_CONCEALMENT:
            case DISABLED_PACKET_LOSS_CONCEALMENT:
            case UNSPECIFIED_PACKET_LOSS_CONCEALMENT:
                this.packetLossConcealment = packetLossConcealment;
                break;
            default:
                throw new IllegalArgumentException("packetLossConcealment");
            }
        }

        public void setResidualEchoReturnLoss(byte residualEchoReturnLoss)
        {
            this.residualEchoReturnLoss = residualEchoReturnLoss;
        }

        public void setRFactor(byte rFactor)
        {
            this.rFactor = rFactor;
        }

        public void setRoundTripDelay(int roundTripDelay)
        {
            this.roundTripDelay = roundTripDelay;
        }

        public void setSignalLevel(byte signalLevel)
        {
            this.signalLevel = signalLevel;
        }

        /**
         * Sets the synchronization source identifier (SSRC) of the RTP data
         * packet source being reported upon by this report block.
         *
         * @param sourceSSRC the synchronization source identifier (SSRC) of the
         * RTP data packet source being reported upon by this report block
         */
        public void setSourceSSRC(int sourceSSRC)
        {
            this.sourceSSRC = sourceSSRC;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            StringBuilder s = new StringBuilder("VoIP Metrics");

            s.append(", SSRC of source ")
                .append(getSourceSSRC() & 0xFFFFFFFFL);
            s.append(", loss rate ").append(getLossRate());
            s.append(", discard rate ").append(getDiscardRate());
            s.append(", burst density ").append(getBurstDensity());
            s.append(", gap density ").append(getGapDensity());
            s.append(", burst duration ").append(getBurstDuration());
            s.append(", gap duration ").append(getGapDuration());
            s.append(", round trip delay ").append(getRoundTripDelay());
            // TODO Auto-generated method stub
            return s.toString();
        }
    }

    public static final String SDP_ATTRIBUTE = "rtcp-xr";

    /**
     * The packet type (PT) constant <tt>207</tt> which identifies RTCP XR
     * packets.
     */
    public static final int XR = 207;

    /**
     * The list of zero or more extended report blocks carried by this
     * <tt>RTCPExtendedReport</tt>.
     */
    private final List<ReportBlock> reportBlocks
        = new LinkedList<ReportBlock>();

    /**
     * The synchronization source identifier (SSRC) of the originator of this XR
     * packet.
     */
    private int ssrc;

    /**
     * The <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPExtendedReport</tt> has been received or sent by the local
     * endpoint.
     */
    private long systemTimeStamp;

    /**
     * Initializes a new <tt>RTCPExtendedReport</tt> instance.
     */
    public RTCPExtendedReport()
    {
        type = XR;
    }

    /**
     * Initializes a new <tt>RTCPExtendedReport</tT> instance by
     * deserializing/reading a binary representation from a <tt>byte</tt> array.
     *
     * @param buf the binary representation from which the new instance is to be
     * initialized
     * @param off the offset in <tt>buf</tt> at which the binary representation
     * starts
     * @param len the number of <tt>byte</tt>s in <tt>buf</tt> starting at
     * <tt>off</tt> which comprise the binary representation
     * @throws IOException if an input/output error occurs while
     * deserializing/reading the new instance from <tt>buf</tt> or the binary
     * representation does not parse into an <tt>RTCPExtendedReport</tt>
     * instance
     */
    public RTCPExtendedReport(byte[] buf, int off, int len)
        throws IOException
    {
        this(new DataInputStream(new ByteArrayInputStream(buf, off, len)));
    }

    /**
     * Initializes a new <tt>RTCPExtendedReport</tt> instance by
     * deserializing/reading a binary representation from a
     * <tt>DataInputStream</tt>.
     *
     * @param datainputstream the binary representation from which the new
     * instance is to be initialized.
     * @throws IOException if an input/output error occurs while
     * deserializing/reading the new instance from <tt>datainputstream</tt> or
     * the binary representation does not parse into an
     * <tt>RTCPExtendedReport</tt> instance.
     */
    public RTCPExtendedReport(DataInputStream datainputstream)
        throws IOException
    {
        this();

        // V=2, P, reserved
        int b0 = datainputstream.readUnsignedByte();

        // PT=XR=207
        int pt = datainputstream.readUnsignedByte();

        // length
        int length = datainputstream.readUnsignedShort();

        if (length < 1)
            throw new IOException("Invalid RTCP length.");

        parse(b0, pt, length, datainputstream);
    }

    /**
     * Initializes a new <tt>RTCPExtendedReport</tt> instance by
     * deserializing/reading a binary representation of part of the packet from
     * a <tt>DataInputStream</tt>, and taking the values found in the
     * first 4 bytes of the binary representation as arguments.
     *
     * @param b0 the first byte of the binary representation.
     * @param pt the value of the {@code packet type} field.
     * @param length the value of the {@code length} field.
     * @param datainputstream the binary representation from which the new
     * instance is to be initialized, excluding the first 4 bytes.
     * @throws IOException if an input/output error occurs while
     * deserializing/reading the new instance from <tt>datainputstream</tt> or
     * the binary representation does not parse into an
     * <tt>RTCPExtendedReport</tt> instance.
     */
    public RTCPExtendedReport(int b0, int pt, int length,
                              DataInputStream datainputstream)
            throws IOException
    {
        this();

        parse(b0, pt, length, datainputstream);
    }

    /**
     * Initializes a new <tt>RTCPExtendedReport</tt> instance by
     * deserializing/reading a binary representation of part of the packet from
     * a <tt>DataInputStream</tt>, and taking the values normally found in the
     * first 4 bytes of the binary representation as arguments.
     *
     * @param b0 the first byte of the binary representation.
     * @param pt the value of the {@code packet type} field.
     * @param length the length in bytes of the RTCP packet, including all of
     * it's headers and excluding any padding.
     * @param datainputstream the binary representation from which the new
     * instance is to be initialized, excluding the first 4 bytes.
     * @throws IOException if an input/output error occurs while
     * deserializing/reading the new instance from <tt>datainputstream</tt> or
     * the binary representation does not parse into an
     * <tt>RTCPExtendedReport</tt> instance.
     */
    private void parse(int b0, int pt, int length, DataInputStream datainputstream)
            throws IOException
    {
        // The first 4 bytes have already been read.
        length -= 4;

        // V=2
        if ((b0 & 0xc0) != 128)
            throw new IOException("Invalid RTCP version (V).");

        if (pt != XR)
            throw new IOException("Invalid RTCP packet type (PT).");

        // SSRC
        setSSRC(datainputstream.readInt());
        length = length - 4;

        // report blocks. A block is at least 4 bytes long.
        while (length >= 4)
        {
            // block type (BT)
            int bt = datainputstream.readUnsignedByte();

            // type-specific
            datainputstream.readByte();

            // block length in bytes, including the block header
            int blockLength = datainputstream.readUnsignedShort() + 1 << 2;

            if (length < blockLength)
            {
                throw new IOException("Invalid extended block");
            }

            if (bt == VoIPMetricsReportBlock.VOIP_METRICS_REPORT_BLOCK_TYPE)
            {
                addReportBlock(
                        new VoIPMetricsReportBlock(
                                blockLength - 4,
                                datainputstream));
            }
            else
            {
                 // The implementation reads and ignores any extended report
                 // blocks other than VoIP Metrics Report Block.

                // Already read 4 bytes
                datainputstream.skip(blockLength - 4);
            }
            length = length - blockLength;
        }

        // If we didn't read all bytes of the packet, the stream is probably in
        // an inconsistent state.
        if (length != 0)
        {
            throw new IOException("Invalid XR packet, unread bytes");
        }
    }

    /**
     * Adds an extended report block to this extended report.
     *
     * @param reportBlock the extended report block to add to this extended
     * report
     * @return <tt>true</tt> if the list of extended report blocks carried by
     * this extended report changed because of the method invocation; otherwise,
     * <tt>false</tt>
     * @throws NullPointerException if <tt>reportBlock</tt> is <tt>null</tt>
     */
    public boolean addReportBlock(ReportBlock reportBlock)
    {
        if (reportBlock == null)
            throw new NullPointerException("reportBlock");
        else
            return reportBlocks.add(reportBlock);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assemble(DataOutputStream dataoutputstream)
        throws IOException
    {
        // V=2, P, reserved
        dataoutputstream.writeByte(128);
        // PT=XR=207
        dataoutputstream.writeByte(XR);
        // length
        dataoutputstream.writeShort(calcLength() / 4 - 1);
        // SSRC
        dataoutputstream.writeInt(getSSRC());
        // report blocks
        for (ReportBlock reportBlock : getReportBlocks())
            reportBlock.assemble(dataoutputstream);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int calcLength()
    {
        int length
            = 1 /* V=2, P, reserved */
                + 1 /* PT */
                + 2 /* length */
                + 4 /* SSRC */;

        // report blocks
        for (ReportBlock reportBlock : getReportBlocks())
            length += reportBlock.calcLength();

        return length;
    }

    /**
     * Gets the number of the extended report blocks carried by this
     * <tt>RTCPExtendedReport</tt>.
     *
     * @return the number of the extended report blocks carried by this
     * <tt>RTCPExtendedReport</tt>
     */
    public int getReportBlockCount()
    {
        return reportBlocks.size();
    }

    /**
     * Gets a list of the extended report blocks carried by this
     * <tt>RTCPExtendedReport</tt>.
     *
     * @return a list of the extended repot blocks carried by this
     * <tt>RTCPExtendedReport</tt>
     */
    public List<ReportBlock> getReportBlocks()
    {
        return Collections.unmodifiableList(reportBlocks);
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the originator of
     * this XR packet.
     *
     * @return the synchronization source identifier (SSRC) of the originator of
     * this XR packet
     */
    public int getSSRC()
    {
        return ssrc;
    }

    /**
     * Gets the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPExtendedReport</tt> has been received or sent by the local
     * endpoint.
     *
     * @return the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPExtendedReport</tt> has been received or sent by the local
     * endpoint
     */
    public long getSystemTimeStamp()
    {
        return systemTimeStamp;
    }

    /**
     * Removes an extended report block from this extended report.
     *
     * @param reportBlock the extended report block to remove from this extended
     * report
     * @return <tt>true</tt> if the list of extended report blocks carried by
     * this extended report changed because of the method invocation; otherwise,
     * <tt>false</tt>
     */
    public boolean removeReportBlock(ReportBlock reportBlock)
    {
        if (reportBlock == null)
            return false;
        else
            return reportBlocks.remove(reportBlock);
    }

    /**
     * Sets the synchronization source identifier (SSRC) of the originator of
     * this XR packet.
     *
     * @param ssrc the synchronization source identifier (SSRC) of the
     * originator of this XR packet
     */
    public void setSSRC(int ssrc)
    {
        this.ssrc = ssrc;
    }

    /**
     * Sets the <tt>System</tt> time in milliseconds at which this
     * <tt>RTCPExtendedReport</tt> has been received or sent by the local
     * endpoint.
     *
     * @param systemTimeStamp the <tt>System</tt> time in milliseconds at which
     * this <tt>RTCPExtendedReport</tt> has been received or sent by the local
     * endpoint
     */
    public void setSystemTimeStamp(long systemTimeStamp)
    {
        this.systemTimeStamp = systemTimeStamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder("RTCP XR");

        // SSRC
        s.append(", SSRC ").append(getSSRC() & 0xFFFFFFFFL);

        List<ReportBlock> reportBlocks = getReportBlocks();
        boolean b = false;

        // report blocks
        s.append(", report blocks [");
        for (ReportBlock reportBlock : reportBlocks)
        {
            if (b)
                s.append("; ");
            else
                b = true;
            s.append(reportBlock);
        }
        s.append("]");

        return s.toString();
    }
}
