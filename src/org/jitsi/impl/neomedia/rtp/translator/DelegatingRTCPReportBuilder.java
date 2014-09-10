/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.*;

/**
* Created by gp on 7/2/14.
*/
class DelegatingRTCPReportBuilder
    implements RTCPReportBuilder
{
    /**
     *
     */
    private RTCPReportBuilder delegate;

    /**
     *
     */
    private final RTCPReportBuilder fallback =
            new DefaultRTCPReportBuilderImpl(); // TODO configurable?

    /**
     *
     */
    private RTCPTransmitter rtcpTransmitter;

    public void setDelegate(RTCPReportBuilder delegate)
    {
        if (this.delegate != delegate)
        {
            this.delegate = delegate;
            onDelegateChanged();
        }
    }

    /**
     *
     * @return
     */
    @Override
    public RTCPPacket[] makeReports()
    {
        RTCPReportBuilder d = this.delegate;
        if (d != null)
        {
            return d.makeReports();
        }
        else
        {
            return fallback.makeReports();
        }
    }

    /**
     *
     */
    @Override
    public void reset()
    {
        RTCPReportBuilder d = this.delegate;
        if (d != null)
        {
            d.reset();
        }

        fallback.reset();
    }

    /**
     *
     * @param rtcpTransmitter
     */
    @Override
    public void setRTCPTransmitter(RTCPTransmitter rtcpTransmitter)
    {
        if (this.rtcpTransmitter != rtcpTransmitter)
        {
            this.rtcpTransmitter = rtcpTransmitter;
            onRTCPTransmitterChanged();
        }
    }

    @Override
    public RTCPTransmitter getRTCPTransmitter()
    {
        return this.rtcpTransmitter;
    }

    /**
     *
     */
    private void onRTCPTransmitterChanged()
    {
        RTCPReportBuilder d = this.delegate;
        if (d != null)
        {
            d.setRTCPTransmitter(this.rtcpTransmitter);
        }

        fallback.setRTCPTransmitter(this.rtcpTransmitter);
    }

    /**
     *
     */
    private void onDelegateChanged()
    {
        RTCPReportBuilder d = this.delegate;
        if (d != null)
        {
            d.setRTCPTransmitter(rtcpTransmitter);
        }
    }
}
