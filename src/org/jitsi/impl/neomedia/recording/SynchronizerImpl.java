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
package org.jitsi.impl.neomedia.recording;

import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author Boris Grozev
 */
public class SynchronizerImpl
    implements Synchronizer
{
    /**
     * Whether the CNAME identifier from RTCP SDES packets should be use as
     * an endpoint identifier.
     *
     * If set to true, RTCP SDES packets fed through
     * {@link #addRTCPPacket(RawPacket)} will be searched for CNAME items, and
     * the values will be used as endpoint identifiers.
     */
    private static final boolean USE_CNAME_AS_ENDPOINT_ID = false;

    /**
     * The RTCP Sender Report packet type.
     */
    private static final int PT_SENDER_REPORT = 200;

    /**
     * The RTCP SDES packet type.
     */
    private static final int PT_SDES = 202;

    /**
     * Maps an SSRC to the <tt>SSRCDesc</tt> structure containing information
     * about it.
     */
    private final Map<Long, SSRCDesc> ssrcs = new ConcurrentHashMap<>();

    /**
     * Maps an endpoint identifier to an <tt>Endpoint</tt> structure containing
     * information about the endpoint.
     */
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtpClockRate(long ssrc, long clockRate)
    {
        SSRCDesc ssrcDesc = getSSRCDesc(ssrc);
        if (ssrcDesc.clockRate == -1)
        {
            synchronized (ssrcDesc)
            {
                if (ssrcDesc.clockRate == -1)
                    ssrcDesc.clockRate = clockRate;
                else if (ssrcDesc.clockRate != clockRate)
                {
                    // this shouldn't happen...but if the clock rate really
                    // changed for some reason, out timings are now irrelevant.
                    ssrcDesc.clockRate = clockRate;
                    ssrcDesc.ntpTime = -1.0;
                    ssrcDesc.rtpTime = -1;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setEndpoint(long ssrc, String endpointId)
    {
        SSRCDesc ssrcDesc = getSSRCDesc(ssrc);
        synchronized (ssrcDesc)
        {
            ssrcDesc.endpointId = endpointId;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void mapRtpToNtp(long ssrc, long rtpTime, double ntpTime)
    {
        SSRCDesc ssrcDesc = getSSRCDesc(ssrc);

        if (rtpTime != -1 && ntpTime != -1.0) // have valid values to update
        {
            if (ssrcDesc.rtpTime == -1 || ssrcDesc.ntpTime == -1.0)
            {
                synchronized (ssrcDesc)
                {
                    if (ssrcDesc.rtpTime == -1 || ssrcDesc.ntpTime == -1.0)
                    {
                        ssrcDesc.rtpTime = rtpTime;
                        ssrcDesc.ntpTime = ntpTime;
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void mapLocalToNtp(long ssrc, long localTime, double ntpTime)
    {
        SSRCDesc ssrcDesc = getSSRCDesc(ssrc);

        if (localTime != -1 && ntpTime != -1.0 && ssrcDesc.endpointId != null)
        {
            Endpoint endpoint = getEndpoint(ssrcDesc.endpointId);
            if (endpoint.localTime == -1 || endpoint.ntpTime == -1.0)
            {
                synchronized (endpoint)
                {
                    if (endpoint.localTime == -1 || endpoint.ntpTime == -1.0)
                    {
                        endpoint.localTime = localTime;
                        endpoint.ntpTime = ntpTime;
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public long getLocalTime(long ssrc, long rtp0)
    {
        //don't use getSSRCDesc, because we don't want to create an instance
        SSRCDesc ssrcDesc = ssrcs.get(ssrc);
        if (ssrcDesc == null)
        {
            return -1;
        }


        // get all required times
        long clockRate; //the clock rate for the RTP clock for the given SSRC
        long rtp1; //some time X in the RTP clock for the given SSRC
        double ntp1; //the same time X in the source's wallclock
        String endpointId;
        synchronized (ssrcDesc)
        {
            clockRate = ssrcDesc.clockRate;
            rtp1 = ssrcDesc.rtpTime;
            ntp1 = ssrcDesc.ntpTime;
            endpointId = ssrcDesc.endpointId;
        }

        // if something is missing, we can't calculate the time
        if (clockRate == -1
                || rtp1 == -1
                || ntp1 == -1.0
                || endpointId == null)
        {
            return -1;
        }

        Endpoint endpoint = endpoints.get(ssrcDesc.endpointId);
        if (endpoint == null)
        {
            return -1;
        }

        double ntp2; //some time Y in the source's wallclock (same clock as for ntp1)
        long local2; //the same time Y in the local clock
        synchronized (endpoint)
        {
            ntp2 = endpoint.ntpTime;
            local2 = endpoint.localTime;
        }

        if (ntp2 == -1.0 || local2 == -1)
        {
            return -1;
        }


        // crunch the numbers. we're looking for 'local0',
        // the local time corresponding to 'rtp0'
        long local0;

        double diff1S = ntp1 - ntp2;
        double diff2S = ((double)RTPUtils.rtpTimestampDiff(rtp0, rtp1)) / clockRate;

        long diffMs = Math.round((diff1S + diff2S) * 1000);

        local0 = local2 + diffMs;

        return local0;
    }

    /**
     * Adds an RTCP packet to this instance. Time mappings are extracted and
     * used by this instance.
     * @param pkt the packet to add.
     */
    void addRTCPPacket(RawPacket pkt)
    {
        addRTCPPacket(pkt, System.currentTimeMillis());
    }

    /**
     * Adds an RTCP packet to this instance. Time mappings are extracted and
     * used by this instance.
     * @param pkt the packet to add.
     * @param localTime the local time of reception of the packet.
     */
    void addRTCPPacket(RawPacket pkt, long localTime)
    {
        if (!isValidRTCP(pkt))
        {
            return;
        }

        switch (getPacketType(pkt))
        {
        case PT_SENDER_REPORT:
            addSR(pkt, localTime);
            break;
        case PT_SDES:
            addSDES(pkt);
            break;
        }
    }

    /**
     * Handles and RTCP SDES packet.
     * @param pkt the packet to handle.
     */
    private void addSDES(RawPacket pkt)
    {
        if (USE_CNAME_AS_ENDPOINT_ID)
        {
            for (CNAMEItem item : getCnameItems(pkt))
            {
                SSRCDesc ssrc = getSSRCDesc(item.ssrc);
                if (ssrc.endpointId == null)
                {
                    synchronized (ssrc)
                    {
                        if (ssrc.endpointId == null)
                            ssrc.endpointId = item.cname;
                    }
                }
            }
        }
    }

    /**
     * Handles an RTCP Sender Report packet.
     * @param pkt the packet to handle.
     * @param localTime the local time of reception of the packet.
     */
    private void addSR(RawPacket pkt, long localTime)
    {
        long ssrc = pkt.getRTCPSSRC();
        long rtpTime = pkt.readUint32AsLong(16);

        long sec = pkt.readUint32AsLong(8);
        long fract = pkt.readUint32AsLong(12);
        double ntpTime = sec + (((double)fract) / (1L<<32));

        if (localTime != -1 && ntpTime != -1.0)
            mapLocalToNtp(ssrc, localTime, ntpTime);

        if (rtpTime != -1 && ntpTime != -1.0)
            mapRtpToNtp(ssrc, rtpTime, ntpTime);
    }

    /**
     * Returns the <tt>SSRCDesc</tt> instance mapped to the SSRC <tt>ssrc</tt>.
     * If no instance is mapped to <tt>ssrc</tt>, create one and inserts it in
     * the map. Always returns non-null.
     * @param ssrc the ssrc to get the <tt>SSRCDesc</tt> for.
     * @return the <tt>SSRCDesc</tt> instance mapped to the SSRC <tt>ssrc</tt>.
     */
    private SSRCDesc getSSRCDesc(long ssrc)
    {
        synchronized (ssrcs)
        {
            SSRCDesc ssrcDesc = ssrcs.get(ssrc);
            if (ssrcDesc == null)
            {
                ssrcDesc = new SSRCDesc();
                ssrcs.put(ssrc, ssrcDesc);
            }
            return ssrcDesc;
        }
    }

    /**
     * Returns the <tt>Endpoint</tt> with id <tt>endpointId</tt>. Creates an
     * <tt>Endpoint</tt> if necessary. Always returns non-null.
     * @param endpointId the string identifying the endpoint.
     * @return the <tt>Endpoint</tt> with id <tt>endpointId</tt>. Creates an
     * <tt>Endpoint</tt> if necessary.
     */
    private Endpoint getEndpoint(String endpointId)
    {
        synchronized (endpoints)
        {
            Endpoint endpoint = endpoints.get(endpointId);
            if (endpoint == null)
            {
                endpoint = new Endpoint();
                endpoints.put(endpointId, endpoint);
            }
            return endpoint;
        }
    }

    /**
     * Return a set of all items with type CNAME from the RTCP SDES packet
     * <tt>pkt</tt>.
     * @param pkt the packet to parse for CNAME items.
     * @return a set of all items with type CNAME from the RTCP SDES packet
     * <tt>pkt</tt>.
     */
    private Set<CNAMEItem> getCnameItems(RawPacket pkt)
    {
        Set<CNAMEItem> ret = new HashSet<>();

        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        //first item
        int ptr = 4;

        while (ptr + 6 < len) //an item is at least 6B: 4B ssrc, 1B type, 1B len
        {
            int type = buf[off + ptr + 4];
            int len2 = buf[off + ptr + 5];
            if (ptr + 6 + len2 >= len) //not enough buffer for the whole item
                break;

            if (type == 1) //CNAME
            {
                CNAMEItem item = new CNAMEItem();
                item.ssrc = readUnsignedIntAsLong(buf, off + ptr);
                item.cname = readString(buf, off + ptr + 6, len2);
                ret.add(item);
            }

            ptr += 6 + len2;
        }

        return ret;
    }

    /**
     * Reads a portion of a byte array as a string.
     * @return the string with length <tt>len</tt>read from <tt>buf</tt> at
     * offset <tt>off</tt>.
     */
    private String readString(byte[] buf, int off, int len)
    {
        String ret = "";
        for (int i = off; i < off + len; i++)
        {
            ret += (char) buf[i];
        }
        return ret;
    }

    /**
     * Read an unsigned integer as long at specified offset
     *
     * @param off start offset of this unsigned integer
     * @return unsigned integer as long at offset
     */
    public long readUnsignedIntAsLong(byte[] buf, int off)
    {
        int b0 = (0x000000FF & (buf[off + 0]));
        int b1 = (0x000000FF & (buf[off + 1]));
        int b2 = (0x000000FF & (buf[off + 2]));
        int b3 = (0x000000FF & (buf[off + 3]));

        return  ((b0 << 24 | b1 << 16 | b2 << 8 | b3)) & 0xFFFFFFFFL;
    }

    /**
     * Checks whether <tt>pkt</tt> looks like a valid RTCP packet.
     * @param pkt the packet to check.
     * @return <tt>true</tt> if <tt>pkt</tt> seems to be a valid RTCP packet.
     */
    private boolean isValidRTCP(RawPacket pkt)
    {
        byte[] buf = pkt.getBuffer();
        int off = pkt.getOffset();
        int len = pkt.getLength();

        if (len < 4)
            return false;

        int v = (buf[off] & 0xc0) >>> 6;
        if (v != 2)
            return false;

        int lengthInWords = (buf[off + 2] & 0xFF) << 8 | (buf[off + 3] & 0xFF);
        int lengthInBytes = (lengthInWords + 1) * 4;
        if (len < lengthInBytes)
            return false;

        return true;
    }

    /**
     * Returns the value of the packet type field from the RTCP header of
     * <tt>pkt</tt>. Assumes that <tt>pkt</tt> is a valid RTCP packet (at least
     * as reported by {@link #isValidRTCP(RawPacket)}).
     * @param pkt the packet to get the packet type of.
     * @return the value of the packet type field from the RTCP header of
     * <tt>pkt</tt>.
     */
    private int getPacketType(RawPacket pkt)
    {
        return pkt.getBuffer()[pkt.getOffset() + 1] & 0xff;
    }

    /**
     * Removes the RTP-NTP mapping for a given SSRC.
     *
     * @param ssrc the SSRC for which to remove the RTP-NTP mapping
     */
    void removeMapping(long ssrc)
    {
        SSRCDesc ssrcDesc = ssrcs.get(ssrc);
        if (ssrcDesc != null)
        {
            synchronized (ssrcDesc)
            {
                ssrcDesc.ntpTime = -1.0;
                ssrcDesc.rtpTime = -1;
            }
        }
    }

    /**
     * Represents an SSRC for the purpose of this <tt>Synchronizer</tt>.
     */
    private static class SSRCDesc
    {
        /**
         * The string identifying the endpoint associated with this SSRC.
         */
        String endpointId = null;

        /**
         * The RTP clock rate for this SSRC.
         */
        long clockRate = -1;


        double ntpTime = -1.0;
        long rtpTime = -1;
    }

    /**
     * A class used to identify an "endpoint" or "source". Contains a mapping
     * between a wallclock at the endpoint and a time we chose on the local
     * system clock to correspond to it.
     */
    private static class Endpoint
    {
        /**
         * The time in seconds on the "endpoint"'s clock.
         */
        double ntpTime = -1.0;

        /**
         * The local time.
         */
        long localTime = -1;
    }

    /**
     * Represents an item of type CNAME from an RTCP SDES packet.
     */
    private static class CNAMEItem
    {
        /**
         * The SSRC of the item.
         */
        long ssrc;

        /**
         * The CNAME value.
         */
        String cname;
    }
}
