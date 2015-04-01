/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import net.sf.fmj.media.rtp.*;

/**
 * Implements an <tt>RTCPReportBuilder</tt> which delegates its calls to a
 * specific <tt>RTCPReportBuilder</tt>. If a delegate is not specified, then
 * this class delegates its calls to an <tt>DefaultRTCPReportBuilderImpl</tt>
 * instance.
 *
 * This class can be used to change the RTCP termination strategy during the
 * run-time.
 *
 * @author George Politis
 */
class DelegatingRTCPReportBuilder
    implements RTCPReportBuilder
{
    /**
     * Delegate <tt><RTCPReportBuilder/tt>.
     */
    private RTCPReportBuilder delegate;

    /**
     * Fallback <tt><RTCPReportBuilder/tt>.
     */
    private final RTCPReportBuilder fallback =
            new DefaultRTCPReportBuilderImpl();

    /**
     * The <tt>RTCPTransmitter</tt> of this <tt>RTCPReportBuilder</tt>.
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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
     * {@inheritDoc}
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

    /**
     * {@inheritDoc}
     */
    @Override
    public RTCPTransmitter getRTCPTransmitter()
    {
        return this.rtcpTransmitter;
    }

    /**
     * Notifies this instance that {@link #rtcpTransmitter} has changed.
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
     * Notifies this instance that {@link #delegate} has changed.
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
