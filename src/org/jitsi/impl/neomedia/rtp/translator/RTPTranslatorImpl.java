/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtp.translator;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.RTPHeader;

import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements <tt>RTPTranslator</tt> which represents an RTP translator which
 * forwards RTP and RTCP traffic between multiple <tt>MediaStream</tt>s.
 *
 * @author Lyubomir Marinov
 */
public class RTPTranslatorImpl
    extends AbstractRTPTranslator
    implements ReceiveStreamListener
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTPTranslatorImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(RTPTranslatorImpl.class);

    /**
     * The indicator which determines whether the method
     * {@link #createFakeSendStreamIfNecessary()} is to be executed by
     * <tt>RTPTranslatorImpl</tt>.
     */
    private static final boolean CREATE_FAKE_SEND_STREAM_IF_NECESSARY = false;

    /**
     * The <tt>RTPConnector</tt> which is used by {@link #manager} and which
     * delegates to the <tt>RTPConnector</tt>s of the <tt>StreamRTPManager</tt>s
     * attached to this instance.
     */
    private RTPConnectorImpl connector;

    /**
     * The <tt>SendStream</tt> created by the <tt>RTPManager</tt> in order to
     * ensure that this <tt>RTPTranslatorImpl</tt> is able to disperse RTP and
     * RTCP received from remote peers even when the local peer is not
     * generating media to be transmitted.
     */
    private SendStream fakeSendStream;

    /**
     * The <tt>RTPManager</tt> which implements the actual RTP management of
     * this instance.
     */
    private final RTPManager manager = RTPManager.newInstance();

    /**
     * The <tt>SendStream</tt>s created by the <tt>RTPManager</tt> and the
     * <tt>StreamRTPManager</tt>-specific views to them.
     */
    private final List<SendStreamDesc> sendStreams
        = new LinkedList<SendStreamDesc>();

    /**
     * The list of <tt>StreamRTPManager</tt>s i.e. <tt>MediaStream</tt>s which
     * this instance forwards RTP and RTCP traffic between.
     */
    private final List<StreamRTPManagerDesc> streamRTPManagers
        = new ArrayList<StreamRTPManagerDesc>();

    /**
     * An instance which can be used to send RTCP Feedback Messages, using
     * as 'packet sender SSRC' the SSRC of (the <tt>RTPManager</tt> of) this
     * <tt>RTPTranslator</tt>.
     */
    private final RTCPFeedbackMessageSender rtcpFeedbackMessageSender
        = new RTCPFeedbackMessageSender(this);

    /**
     * A local SSRC for this <tt>RTPTranslator</tt>.
     */
    private long localSSRC = -1;

    /**
     * The <tt>RTCPTerminationStrategy</tt> which is to inspect and modify RTCP
     * traffic between multiple <tt>MediaStream</tt>s.
     */
    private RTCPTerminationStrategy rtcpTerminationStrategy;

    /**
     * The <tt>DelegatingRTCPReportBuilder</tt> that delegates its calls to the
     * <tt>RTCPReportBuilder</tt> of the active
     * <tt>RTCPTerminationStrategy</tt>.
     */
    private final DelegatingRTCPReportBuilder delegatingRTCPReportBuilder
            = new DelegatingRTCPReportBuilder();

    /**
     * Initializes a new <tt>RTPTranslatorImpl</tt> instance.
     */
    public RTPTranslatorImpl()
    {
        manager.addReceiveStreamListener(this);
    }

    /**
     * Specifies the RTP payload type (number) to be used for a specific
     * <tt>Format</tt>. The association between the specified <tt>format</tt>
     * and the specified <tt>payloadType</tt> is being added by a specific
     * <tt>StreamRTPManager</tt> but effects the <tt>RTPTranslatorImpl</tt>
     * globally.
     * 
     * @param streamRTPManager the <tt>StreamRTPManager</tt> that is requesting
     * the association of <tt>format</tt> to <tt>payloadType</tt>
     * @param format the <tt>Format</tt> which is to be associated with the
     * specified RTP payload type (number)
     * @param payloadType the RTP payload type (number) to be associated with
     * the specified <tt>format</tt>
     */
    public synchronized void addFormat(
            StreamRTPManager streamRTPManager,
            Format format, int payloadType)
    {
        manager.addFormat(format, payloadType);

        getStreamRTPManagerDesc(streamRTPManager, true)
            .addFormat(format, payloadType);
    }

    /**
     * Adds a <tt>ReceiveStreamListener</tt> to be notified about
     * <tt>ReceiveStreamEvent</tt>s related to a specific neomedia
     * <tt>MediaStream</tt> (expressed as a <tt>StreamRTPManager</tt> for the
     * purposes of and in the terms of <tt>RTPTranslator</tt>). If the specified
     * <tt>listener</tt> has already been added, the method does nothing.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> which specifies
     * the neomedia <tt>MediaStream</tt> with which the
     * <tt>ReceiveStreamEvent</tt>s delivered to the specified <tt>listener</tt>
     * are to be related. In other words, a <tt>ReceiveStreamEvent</tt> received
     * by <tt>RTPTranslatorImpl</tt> is first examined to determine which
     * <tt>StreamRTPManager</tt> it is related to and then it is delivered to
     * the <tt>ReceiveStreamListener</tt>s which have been added to this
     * <tt>RTPTranslatorImpl</tt> by that <tt>StreamRTPManager</tt>.
     * @param listener the <tt>ReceiveStreamListener</tt> to be notified about
     * <tt>ReceiveStreamEvent</tt>s related to the specified
     * <tt>streamRTPManager</tt>
     */
    public synchronized void addReceiveStreamListener(
            StreamRTPManager streamRTPManager,
            ReceiveStreamListener listener)
    {
        getStreamRTPManagerDesc(streamRTPManager, true)
            .addReceiveStreamListener(listener);
    }

    /**
     * Adds a <tt>RemoteListener</tt> to be notified about <tt>RemoteEvent</tt>s
     * received by this <tt>RTPTranslatorImpl</tt>. Though the request is being
     * made by a specific <tt>StreamRTPManager</tt>, the addition of the
     * specified <tt>listener</tt> and the deliveries of the
     * <tt>RemoteEvent</tt>s are performed irrespective of any
     * <tt>StreamRTPManager</tt>.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> which is requesting
     * the addition of the specified <tt>RemoteListener</tt>
     * @param listener the <tt>RemoteListener</tt> to be notified about
     * <tt>RemoteEvent</tt>s received by this <tt>RTPTranslatorImpl</tt>
     */
    public void addRemoteListener(
            StreamRTPManager streamRTPManager,
            RemoteListener listener)
    {
        manager.addRemoteListener(listener);
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    public void addSendStreamListener(
            StreamRTPManager streamRTPManager,
            SendStreamListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    public void addSessionListener(
            StreamRTPManager streamRTPManager,
            SessionListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Closes {@link #fakeSendStream} if it exists and is considered no longer
     * necessary; otherwise, does nothing.
     */
    private synchronized void closeFakeSendStreamIfNotNecessary()
    {
        /*
         * If a SendStream has been created in response to a request from the
         * clients of this RTPTranslator implementation, the newly-created
         * SendStream in question will disperse the received RTP and RTCP from
         * remote peers so fakeSendStream will be obsolete.
         */
        try
        {
            if ((!sendStreams.isEmpty() || (streamRTPManagers.size() < 2))
                    && (fakeSendStream != null))
            {
                try
                {
                    fakeSendStream.close();
                }
                catch (NullPointerException npe)
                {
                    /*
                     * Refer to MediaStreamImpl#stopSendStreams(
                     * Iterable<SendStream>, boolean) for an explanation about
                     * the swallowing of the exception.
                     */
                    logger.error("Failed to close fake send stream", npe);
                }
                finally
                {
                    fakeSendStream = null;
                }
            }
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else if (logger.isDebugEnabled())
            {
                logger.debug(
                        "Failed to close the fake SendStream of this"
                            + " RTPTranslator.",
                        t);
            }
        }
    }

    /**
     * Closes a specific <tt>SendStream</tt>.
     *
     * @param sendStreamDesc a <tt>SendStreamDesc</tt> instance that specifies
     * the <tt>SendStream</tt> to be closed
     */
    synchronized void closeSendStream(SendStreamDesc sendStreamDesc)
    {
        if (sendStreams.contains(sendStreamDesc)
                && (sendStreamDesc.getSendStreamCount() < 1))
        {
            SendStream sendStream = sendStreamDesc.sendStream;

            try
            {
                sendStream.close();
            }
            catch (NullPointerException npe)
            {
                /*
                 * Refer to MediaStreamImpl#stopSendStreams(
                 * Iterable<SendStream>, boolean) for an explanation about the
                 * swallowing of the exception.
                 */
                logger.error("Failed to close send stream", npe);
            }
            sendStreams.remove(sendStreamDesc);
        }
    }

    /**
     * Creates {@link #fakeSendStream} if it does not exist yet and is
     * considered necessary; otherwise, does nothing.
     */
    private synchronized void createFakeSendStreamIfNecessary()
    {
        /*
         * If no SendStream has been created in response to a request from the
         * clients of this RTPTranslator implementation, it will need
         * fakeSendStream in order to be able to disperse the received RTP and
         * RTCP from remote peers. Additionally, the fakeSendStream is not
         * necessary in the case of a single client of this RTPTranslator
         * because there is no other remote peer to disperse the received RTP
         * and RTCP to.
         */
        if ((fakeSendStream == null)
                && sendStreams.isEmpty()
                && (streamRTPManagers.size() > 1))
        {
            Format supportedFormat = null;

            for (StreamRTPManagerDesc s : streamRTPManagers)
            {
                Format[] formats = s.getFormats();

                if ((formats != null) && (formats.length > 0))
                {
                    for (Format f : formats)
                    {
                        if (f != null)
                        {
                            supportedFormat = f;
                            break;
                        }
                    }
                    if (supportedFormat != null)
                        break;
                }
            }
            if (supportedFormat != null)
            {
                try
                {
                    fakeSendStream
                        = manager.createSendStream(
                                new FakePushBufferDataSource(supportedFormat),
                                0);
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else
                    {
                        logger.error(
                                "Failed to create a fake SendStream to ensure"
                                    + " that this RTPTranslator is able to"
                                    + " disperse RTP and RTCP received from"
                                    + " remote peers even when the local peer"
                                    + " is not generating media to be"
                                    + " transmitted.",
                                t);
                    }
                }
            }
        }
    }

    /**
     * Creates a <tt>SendStream</tt> from the stream of a specific
     * <tt>DataSource</tt> that is at a specific zero-based position within the
     * array/list of streams of that <tt>DataSource</tt>.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> which is requesting
     * the creation of a <tt>SendStream</tt>. Since multiple
     * <tt>StreamRTPManager</tt> may request the creation of a
     * <tt>SendStream</tt> from one and the same combination of
     * <tt>dataSource</tt> and <tt>streamIndex</tt>, the method may not create
     * a completely new <tt>SendStream</tt> but may return a
     * <tt>StreamRTPManager</tt>-specific view of an existing
     * <tt>SendStream</tt>.
     * @param dataSource the <tt>DataSource</tt> which provides the stream from
     * which a <tt>SendStream</tt> is to be created
     * @param streamIndex the zero-based position within the array/list of
     * streams of the specified <tt>dataSource</tt> of the stream from which a
     * <tt>SendStream</tt> is to be created
     * @return a <tt>SendStream</tt> created from the specified
     * <tt>dataSource</tt> and <tt>streamIndex</tt>. The returned
     * <tt>SendStream</tt> implementation is a
     * <tt>streamRTPManager</tt>-dedicated view to an actual <tt>SendStream</tt>
     * which may have been created during a previous execution of the method
     * @throws IOException if an error occurs during the execution of
     * {@link RTPManager#createSendStream(DataSource, int)}
     * @throws UnsupportedFormatException if an error occurs during the
     * execution of <tt>RTPManager.createSendStream(DataSource, int)</tt>
     */
    public synchronized SendStream createSendStream(
            StreamRTPManager streamRTPManager,
            DataSource dataSource, int streamIndex)
        throws IOException,
               UnsupportedFormatException
    {
        SendStreamDesc sendStreamDesc = null;

        for (SendStreamDesc s : sendStreams)
        {
            if ((s.dataSource == dataSource) && (s.streamIndex == streamIndex))
            {
                sendStreamDesc = s;
                break;
            }
        }
        if (sendStreamDesc == null)
        {
            SendStream sendStream
                = manager.createSendStream(dataSource, streamIndex);

            if (sendStream != null)
            {
                sendStreamDesc
                    = new SendStreamDesc(
                            this,
                            dataSource, streamIndex, sendStream);
                sendStreams.add(sendStreamDesc);

                closeFakeSendStreamIfNotNecessary();
            }
        }
        return
            (sendStreamDesc == null)
                ? null
                : sendStreamDesc.getSendStream(streamRTPManager, true);
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    @Override
    public synchronized void dispose()
    {
        manager.removeReceiveStreamListener(this);
        try
        {
            manager.dispose();
        }
        catch (Throwable t)
        {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
            {
                /*
                 * RTPManager.dispose() often throws at least a
                 * NullPointerException in relation to some RTP BYE.
                 */
                logger.error("Failed to dispose of RTPManager", t);
            }
        }
    }

    /**
     * Releases the resources allocated by this instance for the purposes of the
     * functioning of a specific <tt>StreamRTPManager</tt> in the course of its
     * execution and prepares that <tt>StreamRTPManager</tt> to be garbage
     * collected (as far as this <tt>RTPTranslatorImpl</tt> is concerned).
     */
    public synchronized void dispose(StreamRTPManager streamRTPManager)
    {
        Iterator<StreamRTPManagerDesc> streamRTPManagerIter
            = streamRTPManagers.iterator();

        while (streamRTPManagerIter.hasNext())
        {
            StreamRTPManagerDesc streamRTPManagerDesc
                = streamRTPManagerIter.next();

            if (streamRTPManagerDesc.streamRTPManager == streamRTPManager)
            {
                RTPConnectorDesc connectorDesc
                    = streamRTPManagerDesc.connectorDesc;

                if (connectorDesc != null)
                {
                    if (this.connector != null)
                        this.connector.removeConnector(connectorDesc);
                    connectorDesc.connector.close();
                    streamRTPManagerDesc.connectorDesc = null;
                }

                streamRTPManagerIter.remove();

                closeFakeSendStreamIfNotNecessary();

                break;
            }
        }
    }

    /**
     * Finds the first <tt>StreamRTPManager</tt> which is related to a specific
     * receive/remote SSRC.
     * 
     * @param receiveSSRC the receive/remote SSRC to which the returned
     * <tt>StreamRTPManager</tt> is to be related
     * @param exclusion the <tt>StreamRTPManager</tt>, if any, to be excluded
     * from the search
     * @return the first <tt>StreamRTPManager</tt> which is related to the
     * specified <tt>receiveSSRC</tt>
     */
    private synchronized StreamRTPManagerDesc
        findStreamRTPManagerDescByReceiveSSRC(
            int receiveSSRC,
            StreamRTPManagerDesc exclusion)
    {
        for (int i = 0, count = streamRTPManagers.size(); i < count; i++)
        {
            StreamRTPManagerDesc s = streamRTPManagers.get(i);

            if ((s != exclusion) && s.containsReceiveSSRC(receiveSSRC))
                return s;
        }
        return null;
    }

    /**
     * Exposes {@link RTPManager#getControl(String)} on the internal/underlying
     * <tt>RTPManager</tt>.
     *
     * @param streamRTPManager ignored
     * @param controlType
     * @return the return value of the invocation of
     * <tt>RTPManager.getControl(String)</tt> on the internal/underlying
     * <tt>RTPManager</tt>
     */
    public Object getControl(
            StreamRTPManager streamRTPManager,
            String controlType)
    {
        return manager.getControl(controlType);
    }

    /**
     * Exposes {@link RTPManager#getGlobalReceptionStats()} on the
     * internal/underlying <tt>RTPManager</tt>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of
     * <tt>RTPManager.getGlobalReceptionStats()</tt> on the internal/underlying
     * <tt>RTPManager</tt>
     */
    public GlobalReceptionStats getGlobalReceptionStats(
            StreamRTPManager streamRTPManager)
    {
        return manager.getGlobalReceptionStats();
    }

    /**
     * Exposes {@link RTPManager#getGlobalTransmissionStats()} on the
     * internal/underlying <tt>RTPManager</tt>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of
     * <tt>RTPManager.getGlobalTransmissionStats()</tt> on the
     * internal/underlying <tt>RTPManager</tt>
     */
    public GlobalTransmissionStats getGlobalTransmissionStats(
            StreamRTPManager streamRTPManager)
    {
        return manager.getGlobalTransmissionStats();
    }

    /**
     * Exposes {@link RTPSessionMgr#getLocalSSRC()} on the internal/underlying
     * <tt>RTPSessionMgr</tt>.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of
     * <tt>RTPSessionMgr.getLocalSSRC()</tt> on the internal/underlying
     * <tt>RTPSessionMgr</tt>
     */
    public long getLocalSSRC(StreamRTPManager streamRTPManager)
    {
        if (streamRTPManager == null)
            return localSSRC;
        return ((RTPSessionMgr) manager).getLocalSSRC();
    }

    /**
     * Gets the <tt>ReceiveStream</tt>s associated with/related to a neomedia
     * <tt>MediaStream</tt> (specified in the form of a
     * <tt>StreamRTPManager</tt> instance for the purposes of and in the terms
     * of <tt>RTPManagerImpl</tt>).
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> to which the
     * returned <tt>ReceiveStream</tt>s are to be related
     * @return the <tt>ReceiveStream</tt>s related to/associated with the
     * specified <tt>streamRTPManager</tt>
     */
    public synchronized Vector<ReceiveStream> getReceiveStreams(
            StreamRTPManager streamRTPManager)
    {
        StreamRTPManagerDesc streamRTPManagerDesc
            = getStreamRTPManagerDesc(streamRTPManager, false);
        Vector<ReceiveStream> receiveStreams = null;

        if (streamRTPManagerDesc != null)
        {
            Vector<?> managerReceiveStreams = manager.getReceiveStreams();

            if (managerReceiveStreams != null)
            {
                receiveStreams
                    = new Vector<ReceiveStream>(managerReceiveStreams.size());
                for (Object s : managerReceiveStreams)
                {
                    ReceiveStream receiveStream = (ReceiveStream) s;
                    /*
                     * FMJ stores the synchronization source (SSRC) identifiers
                     * as 32-bit signed values.
                     */
                    int receiveSSRC = (int) receiveStream.getSSRC();

                    if (streamRTPManagerDesc.containsReceiveSSRC(receiveSSRC))
                        receiveStreams.add(receiveStream);
                }
            }
        }
        return receiveStreams;
    }

    /**
     * Gets the <tt>SendStream</tt>s associated with/related to a neomedia
     * <tt>MediaStream</tt> (specified in the form of a
     * <tt>StreamRTPManager</tt> instance for the purposes of and in the terms
     * of <tt>RTPManagerImpl</tt>).
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> to which the
     * returned <tt>SendStream</tt>s are to be related
     * @return the <tt>SendStream</tt>s related to/associated with the specified
     * <tt>streamRTPManager</tt>
     */
    public synchronized Vector<SendStream> getSendStreams(
            StreamRTPManager streamRTPManager)
    {
        Vector<?> managerSendStreams = manager.getSendStreams();
        Vector<SendStream> sendStreams = null;

        if (managerSendStreams != null)
        {
            sendStreams = new Vector<SendStream>(managerSendStreams.size());
            for (SendStreamDesc sendStreamDesc : this.sendStreams)
                if (managerSendStreams.contains(sendStreamDesc.sendStream))
                {
                    SendStream sendStream
                        = sendStreamDesc.getSendStream(streamRTPManager, false);

                    if (sendStream != null)
                        sendStreams.add(sendStream);
                }
        }
        return sendStreams;
    }

    private synchronized StreamRTPManagerDesc getStreamRTPManagerDesc(
            StreamRTPManager streamRTPManager,
            boolean create)
    {
        for (StreamRTPManagerDesc s : streamRTPManagers)
            if (s.streamRTPManager == streamRTPManager)
                return s;

        StreamRTPManagerDesc s;

        if (create)
        {
            s = new StreamRTPManagerDesc(streamRTPManager);
            streamRTPManagers.add(s);
        }
        else
            s = null;
        return s;
    }

    /**
     * Gets the current active <tt>RTCPTerminationStrategy</tt> which is to
     * inspect and modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     *
     * @return the <tt>RTCPTerminationStrategy</tt> which is to inspect and
     * modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     */
    public RTCPTerminationStrategy getRTCPTerminationStrategy()
    {
        return rtcpTerminationStrategy;
    }

    /**
     * Sets the current active <tt>RTCPTerminationStrategy</tt> which is to
     * inspect and modify RTCP traffic between multiple <tt>MediaStream</tt>s.
     *
     * @param rtcpTerminationStrategy the <tt>RTCPTerminationStrategy</tt> which
     * is to inspect and modify RTCP traffic between multiple
     * <tt>MediaStream</tt>s.
     */
    public void setRTCPTerminationStrategy(
            RTCPTerminationStrategy rtcpTerminationStrategy)
    {
        if (this.rtcpTerminationStrategy != rtcpTerminationStrategy)
        {
            this.rtcpTerminationStrategy = rtcpTerminationStrategy;
            onRTCPTerminationStrategyChanged();
        }
    }

    /**
     * Notifies this instance that {@link #rtcpTerminationStrategy} has changed.
     */
    private void onRTCPTerminationStrategyChanged()
    {
        RTCPTerminationStrategy rtcpTerminationStrategy
            = getRTCPTerminationStrategy();

        if (rtcpTerminationStrategy != null)
        {
            rtcpTerminationStrategy.setRTPTranslator(this);
            delegatingRTCPReportBuilder.setDelegate(
                    rtcpTerminationStrategy.getRTCPReportBuilder());
        }
        else
        {
            delegatingRTCPReportBuilder.setDelegate(null);
        }
    }

    public synchronized void initialize(
            StreamRTPManager streamRTPManager,
            RTPConnector connector)
    {
        if (this.connector == null)
        {
            this.connector = new RTPConnectorImpl(this);

            // Override the default FMJ RTCP builder factory.
            if (manager instanceof RTPSessionMgr)
            {
                ((RTPSessionMgr) manager).setRTCPReportBuilder(
                        this.delegatingRTCPReportBuilder);
            }

            manager.initialize(this.connector);
        }

        StreamRTPManagerDesc streamRTPManagerDesc
            = getStreamRTPManagerDesc(streamRTPManager, true);
        RTPConnectorDesc connectorDesc = streamRTPManagerDesc.connectorDesc;

        if ((connectorDesc == null) || (connectorDesc.connector != connector))
        {
            if (connectorDesc != null)
                this.connector.removeConnector(connectorDesc);
            streamRTPManagerDesc.connectorDesc
                = connectorDesc
                    = (connector == null)
                        ? null
                        : new RTPConnectorDesc(streamRTPManagerDesc, connector);
            if (connectorDesc != null)
                this.connector.addConnector(connectorDesc);
        }
    }

    /**
     * Logs information about an RTCP packet using {@link #logger} for debugging
     * purposes.
     *
     * @param obj the object which is the source of the log request
     * @param methodName the name of the method on <tt>obj</tt> which is the
     * source of the log request
     * @param buffer the <tt>byte</tt>s which (possibly) represent an RTCP
     * packet to be logged for debugging purposes
     * @param offset the position within <tt>buffer</tt> at which the valid data
     * begins
     * @param length the number of bytes in <tt>buffer</tt> which constitute the
     * valid data
     */
    static void logRTCP(
            Object obj, String methodName,
            byte[] buffer, int offset, int length)
    {
        /*
         * Do the bytes in the specified buffer resemble (a header of) an RTCP
         * packet?
         */
        if (length >= 8 /* BYE */)
        {
            byte b0 = buffer[offset];
            int v = (b0 & 0xc0) >>> 6;

            if (v == RTCPHeader.VERSION)
            {
                byte b1 = buffer[offset + 1];
                int pt = b1 & 0xff;

                if (pt == 203 /* BYE */)
                {
                    // Verify the length field.
                    int rtcpLength
                        = (readUnsignedShort(buffer, offset + 2) + 1) * 4;

                    if (rtcpLength <= length)
                    {
                        int sc = b0 & 0x1f;
                        int off = offset + 4;

                        for (int i = 0, end = offset + length;
                                (i < sc) && (off + 4 <= end);
                                i++, off += 4)
                        {
                            int ssrc = readInt(buffer, off);

                            logger.trace(
                                    obj.getClass().getName() + '.' + methodName
                                        + ": RTCP BYE SSRC/CSRC "
                                        + Long.toString(ssrc & 0xffffffffl));
                        }
                    }
                }
            }
        }
    }

    /**
     * Notifies this instance that an RTP or RTCP packet has been received from
     * a peer represented by a specific <tt>PushSourceStreamDesc</tt>.
     *
     * @param streamDesc a <tt>PushSourceStreamDesc</tt> which identifies the
     * peer from which an RTP or RTCP packet has been received
     * @param buffer the buffer which contains the bytes of the received RTP or
     * RTCP packet
     * @param offset the zero-based index in <tt>buffer</tt> at which the bytes
     * of the received RTP or RTCP packet begin
     * @param length the number of bytes in <tt>buffer</tt> beginning at
     * <tt>offset</tt> which represent the received RTP or RTCP packet
     * @return the number of bytes in <tt>buffer</tt> beginning at
     * <tt>offset</tt> which represent the received RTP or RTCP packet
     * @throws IOException if an I/O error occurs while the method processes the
     * specified RTP or RTCP packet
     */
    synchronized int didRead(
            PushSourceStreamDesc streamDesc,
            byte[] buffer, int offset, int length)
        throws IOException
    {
        boolean data = streamDesc.data;
        StreamRTPManagerDesc streamRTPManagerDesc
            = streamDesc.connectorDesc.streamRTPManagerDesc;
        Format format = null;

        if (data)
        {
            /*
             * Ignore RTP packets coming from peers whose MediaStream's
             * direction does not allow receiving.
             */
            if (!streamRTPManagerDesc
                    .streamRTPManager
                        .getMediaStream()
                            .getDirection()
                                .allowsReceiving())
            {
                /*
                 * FIXME We are ignoring RTP packets received from peers who we
                 * do not want to receive from ONLY in the sense that we are not
                 * translating/forwarding them to the other peers. Do not we
                 * want to not receive them locally as well?
                 */
                return length;
            }

            /*
             * Do the bytes in the specified buffer resemble (a header of) an
             * RTP packet?
             */
            if ((length >= 12)
                    && (/* v */ ((buffer[offset] & 0xc0) >>> 6)
                            == RTPHeader.VERSION))
            {
                int ssrc = readInt(buffer, offset + 8);

                if (!streamRTPManagerDesc.containsReceiveSSRC(ssrc))
                {
                    if (findStreamRTPManagerDescByReceiveSSRC(
                                ssrc,
                                streamRTPManagerDesc)
                            == null)
                    {
                        streamRTPManagerDesc.addReceiveSSRC(ssrc);
                    }
                    else
                    {
                        return 0;
                    }
                }

                int pt = buffer[offset + 1] & 0x7f;

                format = streamRTPManagerDesc.getFormat(pt);
            }
        }
        else if (logger.isTraceEnabled())
        {
            logRTCP(this, "read", buffer, offset, length);
        }

        /*
         * XXX A deadlock between PushSourceStreamImpl.removeStreams and
         * createFakeSendStreamIfNecessary has been reported. Since the latter
         * method is disabled at the time of this writing, do not even try to
         * execute it and thus avoid the deadlock in question. 
         */
        if (CREATE_FAKE_SEND_STREAM_IF_NECESSARY)
            createFakeSendStreamIfNecessary();

        OutputDataStreamImpl outputStream
            = data
                ? connector.getDataOutputStream()
                : connector.getControlOutputStream();

        if (outputStream != null)
        {
            outputStream.write(
                    buffer, offset, length,
                    format,
                    streamRTPManagerDesc);
        }

        return length;
    }

    /**
     * Reads an <tt>int</tt> from a specific <tt>byte</tt> buffer starting at a
     * specific offset. The implementation is the same as
     * {@link DataInputStream#readInt()}.
     *
     * @param buf the <tt>byte</tt> buffer to read an <tt>int</tt> from
     * @param off the zero-based offset in <tt>buf</tt> to start reading an
     * <tt>int</tt> from
     * @return an <tt>int</tt> read from the specified <tt>buf</tt> starting at
     * the specified <tt>off</tt>
     */
    public static int readInt(byte[] buf, int off)
    {
        return
            ((buf[off++] & 0xff) << 24)
                | ((buf[off++] & 0xff) << 16)
                | ((buf[off++] & 0xff) << 8)
                | (buf[off] & 0xff);
    }

    /**
     * Reads a 16-bit unsigned value from a specific <tt>byte</tt> buffer
     * starting at a specific offset and returns it as an <tt>int</tt>.
     *
     * @param buf the <tt>byte</tt> buffer to read a 16-bit unsigned value from
     * @param off the zero-based offset in <tt>buf</tt> to start reading a
     * 16-bit unsigned value from
     * @return an <tt>int</tt> read from the specified <tt>buf</tt> as a 16-bit
     * unsigned value starting at the specified <tt>off</tt>
     */
    public static int readUnsignedShort(byte[] buf, int off)
    {
        return ((buf[off++] & 0xff) << 8) | (buf[off] & 0xff);
    }

    /**
     * Removes a <tt>ReceiveStreamListener</tt> to no longer be notified about
     * <tt>ReceiveStreamEvent</tt>s related to a specific neomedia
     * <tt>MediaStream</tt> (expressed as a <tt>StreamRTPManager</tt> for the
     * purposes of and in the terms of <tt>RTPTranslator</tt>). Since
     * {@link #addReceiveStreamListener(StreamRTPManager,
     * ReceiveStreamListener)} does not add equal
     * <tt>ReceiveStreamListener</tt>s, a single removal is enough to reverse
     * multiple additions of equal <tt>ReceiveStreamListener</tt>s.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> which specifies
     * the neomedia <tt>MediaStream</tt> with which the
     * <tt>ReceiveStreamEvent</tt>s delivered to the specified <tt>listener</tt>
     * are to be related
     * @param listener the <tt>ReceiveStreamListener</tt> to no longer be
     * notified about <tt>ReceiveStreamEvent</tt>s related to the specified
     * <tt>streamRTPManager</tt>
     */
    public synchronized void removeReceiveStreamListener(
            StreamRTPManager streamRTPManager,
            ReceiveStreamListener listener)
    {
        StreamRTPManagerDesc streamRTPManagerDesc
            = getStreamRTPManagerDesc(streamRTPManager, false);

        if (streamRTPManagerDesc != null)
            streamRTPManagerDesc.removeReceiveStreamListener(listener);
    }

    /**
     * Removes a <tt>RemoteListener</tt> to no longer be notified about
     * <tt>RemoteEvent</tt>s received by this <tt>RTPTranslatorImpl</tt>.
     * Though the request is being made by a specific <tt>StreamRTPManager</tt>,
     * the addition of the specified <tt>listener</tt> and the deliveries of the
     * <tt>RemoteEvent</tt>s are performed irrespective of any
     * <tt>StreamRTPManager</tt> so the removal follows the same logic.
     *
     * @param streamRTPManager the <tt>StreamRTPManager</tt> which is requesting
     * the removal of the specified <tt>RemoteListener</tt>
     * @param listener the <tt>RemoteListener</tt> to no longer be notified
     * about <tt>RemoteEvent</tt>s received by this <tt>RTPTranslatorImpl</tt>
     */
    public void removeRemoteListener(
            StreamRTPManager streamRTPManager,
            RemoteListener listener)
    {
        manager.removeRemoteListener(listener);
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality. (Additionally,
     * {@link #addSendStreamListener(StreamRTPManager, SendStreamListener)} is
     * not implemented for the same reason.)
     */
    public void removeSendStreamListener(
            StreamRTPManager streamRTPManager,
            SendStreamListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality. (Additionally,
     * {@link #addSessionListener(StreamRTPManager, SessionListener)} is not
     * implemented for the same reason.)
     */
    public void removeSessionListener(
            StreamRTPManager streamRTPManager,
            SessionListener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Notifies this <tt>ReceiveStreamListener</tt> about a specific event
     * related to a <tt>ReceiveStream</tt>.
     *
     * @param event a <tt>ReceiveStreamEvent</tt> which contains the specifics
     * of the event this <tt>ReceiveStreamListener</tt> is being notified about
     * @see ReceiveStreamListener#update(ReceiveStreamEvent)
     */
    public void update(ReceiveStreamEvent event)
    {
        /*
         * Because NullPointerException was seen during testing, be thorough
         * with the null checks.
         */
        if (event != null)
        {
            ReceiveStream receiveStream = event.getReceiveStream();

            if (receiveStream != null)
            {
                /*
                 * FMJ stores the synchronization source (SSRC) identifiers as
                 * 32-bit signed values.
                 */
                int receiveSSRC = (int) receiveStream.getSSRC();
                StreamRTPManagerDesc streamRTPManagerDesc
                    = findStreamRTPManagerDescByReceiveSSRC(receiveSSRC, null);

                if (streamRTPManagerDesc != null)
                {
                    for (ReceiveStreamListener listener
                            : streamRTPManagerDesc.getReceiveStreamListeners())
                    {
                        listener.update(event);
                    }
                }
            }
        }
    }

    /**
     * Notifies this <tt>RTPTranslator</tt> that a <tt>buffer</tt> from a
     * <tt>source</tt> will be written into a <tt>destination</tt>.
     *
     * @param source the source of <tt>buffer</tt>
     * @param buffer the bytes from <tt>source</tt> which are to be written into
     * <tt>destination</tt>
     * @param offset the offset in <tt>buffer</tt> at which the bytes to be
     * written begin
     * @param length the number of bytes in <tt>buffer</tt> beginning at
     * <tt>offset</tt> which represent the bytes to be written
     * @param destination the destination into which <tt>buffer</tt> is to be
     * written
     * @param data <tt>true</tt> for data/RTP or <tt>false</tt> for control/RTCP
     * @return <tt>true</tt> if the writing is to continue or <tt>false</tt> if
     * the writing is to abort
     */
    boolean willWrite(
            StreamRTPManagerDesc source,
            byte[] buffer, int offset, int length,
            StreamRTPManagerDesc destination,
            boolean data)
    {
        MediaStream src
            = (source == null)
                ? null
                : source.streamRTPManager.getMediaStream();
        MediaStream dst = destination.streamRTPManager.getMediaStream();

        return willWrite(src, buffer, offset, length, dst, data);
    }

    /**
     * Writes an <tt>RTCPFeedbackMessage</tt> into a destination identified by
     * a specific <tt>MediaStream</tt>.
     *
     * @param controlPayload
     * @param destination
     * @return <tt>true</tt> if the <tt>controlPayload</tt> was written
     * into the <tt>destination</tt>; otherwise, <tt>false</tt>
     */
    public boolean writeControlPayload(
            Payload controlPayload,
            MediaStream destination)
    {
        RTPConnectorImpl connector = this.connector;

        return
            (connector == null)
                ? false
                : connector.writeControlPayload(
                    controlPayload,
                    destination);
    }

    /**
    * Sets the <tt>SSRCFactory</tt> which is to generate new synchronization
    * source (SSRC) identifiers.
    *
    * @param ssrcFactory the <tt>SSRCFactory</tt> which is to generate new
    * synchronization source (SSRC) identifiers or <tt>null</tt> if this
    * <tt>MediaStream</tt> is to employ internal logic to generate new
    * synchronization source (SSRC) identifiers
    */
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        RTPManager manager = this.manager;
        if (manager instanceof
            org.jitsi.impl.neomedia.jmfext.media.rtp.RTPSessionMgr)
        {
            ((org.jitsi.impl.neomedia.jmfext.media.rtp.RTPSessionMgr)manager)
                  .setSSRCFactory(ssrcFactory);
        }
    }

    /**
     * Returns a list of <tt>StreamRTPManager</tt>s currently attached to
     * this <tt>RTPTranslator</tt>.
     * @return a list of <tt>StreamRTPManager</tt>s currently attached to
     * this <tt>RTPTranslator</tt>.
     */
    List<StreamRTPManager> getStreamRTPManagers()
    {
        List<StreamRTPManager> ret
                = new ArrayList<StreamRTPManager>(streamRTPManagers.size());
        for (StreamRTPManagerDesc streamRTPManagerDesc : streamRTPManagers)
        {
            ret.add(streamRTPManagerDesc.streamRTPManager);
        }

        return ret;
    }

    /**
     * Gets the <tt>RTCPFeedbackMessageSender</tt> which should be used for
     * sending RTCP Feedback Messages from this <tt>RTPTranslator</tt>.
     * @return the <tt>RTCPFeedbackMessageSender</tt> which should be used for
     * sending RTCP Feedback Messages from this <tt>RTPTranslator</tt>.
     */
    public RTCPFeedbackMessageSender getRtcpFeedbackMessageSender()
    {
        return rtcpFeedbackMessageSender;
    }

    /**
     * Sets the local SSRC for this <tt>RTPTranslatorImpl</tt>.
     * @param localSSRC the SSRC to set.
     */
    public void setLocalSSRC(long localSSRC)
    {
        this.localSSRC = localSSRC;
    }
}
