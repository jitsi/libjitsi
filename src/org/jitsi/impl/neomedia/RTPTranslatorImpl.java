/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;
import javax.media.rtp.rtcp.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.RTPHeader;

import org.jitsi.impl.neomedia.protocol.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements <tt>RTPTranslator</tt> which represents an RTP translator which
 * forwards RTP and RTCP traffic between multiple <tt>MediaStream</tt>s.
 *
 * @author Lyubomir Marinov
 */
public class RTPTranslatorImpl
    implements ReceiveStreamListener,
               RTPTranslator
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
     * An array with <tt>int</tt> element type and no elements explicitly
     * defined to reduce unnecessary allocations. 
     */
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    /**
     * The name of the <tt>boolean</tt> <tt>ConfigurationService</tt> property
     * which indicates whether the RTP header extension(s) are to be removed
     * from received RTP packets prior to relaying them. The default value is
     * <tt>false</tt>.
     */
    private static final String REMOVE_RTP_HEADER_EXTENSIONS_PROPERTY_NAME
        = RTPTranslatorImpl.class.getName() + ".removeRTPHeaderExtensions";

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
    private synchronized void closeSendStream(SendStreamDesc sendStreamDesc)
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
                    = new SendStreamDesc(dataSource, streamIndex, sendStream);
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
     * collected (as far as this <tt>RTPTranlatorImpl</tt> is concerned).
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

    public synchronized void initialize(
            StreamRTPManager streamRTPManager,
            RTPConnector connector)
    {
        if (this.connector == null)
        {
            this.connector = new RTPConnectorImpl();
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
    private static void logRTCP(
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
     * <tt>offset</tt> which are allowed to be accessed
     * @param read the number of bytes in <tt>buffer</tt> beginning at
     * <tt>offset</tt> which represent the received RTP or RTCP packet
     * @return the number of bytes in <tt>buffer</tt> beginning at
     * <tt>offset</tt> which represent the received RTP or RTCP packet
     * @throws IOException if an I/O error occurs while the method processes the
     * specified RTP or RTCP packet
     */
    private synchronized int read(
            PushSourceStreamDesc streamDesc,
            byte[] buffer, int offset, int length,
            int read)
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
                return read;
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
            logRTCP(this, "read", buffer, offset, read);

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
                    buffer, offset, read,
                    format,
                    streamRTPManagerDesc);
        }

        return read;
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

    private static class OutputDataStreamDesc
    {
        public RTPConnectorDesc connectorDesc;

        public OutputDataStream stream;

        public OutputDataStreamDesc(
                RTPConnectorDesc connectorDesc,
                OutputDataStream stream)
        {
            this.connectorDesc = connectorDesc;
            this.stream = stream;
        }
    }

    private static class OutputDataStreamImpl
        implements OutputDataStream,
                   Runnable
    {
        private static final int WRITE_QUEUE_CAPACITY
            = RTPConnectorOutputStream
                .MAX_PACKETS_PER_MILLIS_POLICY_PACKET_QUEUE_CAPACITY;

        private boolean closed;

        private final boolean data;

        /**
         * The indicator which determines whether the RTP header extension(s)
         * are to be removed from received RTP packets prior to relaying them.
         * The default value is <tt>false</tt>.
         */
        private final boolean removeRTPHeaderExtensions;

        private final List<OutputDataStreamDesc> streams
            = new ArrayList<OutputDataStreamDesc>();

        private final RTPTranslatorBuffer[] writeQueue
            = new RTPTranslatorBuffer[WRITE_QUEUE_CAPACITY];

        private int writeQueueHead;

        private int writeQueueLength;

        private Thread writeThread;

        public OutputDataStreamImpl(boolean data)
        {
            this.data = data;

            // removeRTPHeaderExtensions
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            boolean removeRTPHeaderExtensions = false;

            if (cfg != null)
            {
                removeRTPHeaderExtensions
                    = cfg.getBoolean(
                            REMOVE_RTP_HEADER_EXTENSIONS_PROPERTY_NAME,
                            removeRTPHeaderExtensions);
            }
            this.removeRTPHeaderExtensions = removeRTPHeaderExtensions;
        }

        public synchronized void addStream(
                RTPConnectorDesc connectorDesc,
                OutputDataStream stream)
        {
            for (OutputDataStreamDesc streamDesc : streams)
            {
                if ((streamDesc.connectorDesc == connectorDesc)
                        && (streamDesc.stream == stream))
                {
                    return;
                }
            }
            streams.add(new OutputDataStreamDesc(connectorDesc, stream));
        }

        public synchronized void close()
        {
            closed = true;
            writeThread = null;
            notify();
        }

        private synchronized void createWriteThread()
        {
            writeThread = new Thread(this, getClass().getName());
            writeThread.setDaemon(true);
            writeThread.start();
        }

        private synchronized int doWrite(
                byte[] buffer, int offset, int length,
                Format format,
                StreamRTPManagerDesc exclusion)
        {
            boolean removeRTPHeaderExtensions = this.removeRTPHeaderExtensions;
            int written = 0;

            for (int streamIndex = 0, streamCount = streams.size();
                    streamIndex < streamCount;
                    streamIndex++)
            {
                OutputDataStreamDesc streamDesc = streams.get(streamIndex);
                StreamRTPManagerDesc streamRTPManagerDesc
                    = streamDesc.connectorDesc.streamRTPManagerDesc;

                if (streamRTPManagerDesc == exclusion)
                    continue;

                boolean write;

                if (data)
                {
                    /*
                     * TODO The removal of the RTP header extensions is an
                     * experiment inspired by
                     * https://code.google.com/p/webrtc/issues/detail?id=1095
                     * "Chrom WebRTC VP8 RTP packet retransmission does not
                     * follow RFC 4588"
                     */
                    if (removeRTPHeaderExtensions)
                    {
                        removeRTPHeaderExtensions = false;
                        length
                            = removeRTPHeaderExtensions(buffer, offset, length);
                    }

                    write
                        = willWriteData(
                                streamRTPManagerDesc,
                                buffer, offset, length,
                                format,
                                exclusion);
                }
                else
                {
                    write
                        = willWriteControl(
                                streamRTPManagerDesc,
                                buffer, offset, length,
                                format,
                                exclusion);
                }
                if (!write)
                    continue;

                int streamWritten
                    = streamDesc.stream.write(buffer, offset, length);

                if (written < streamWritten)
                    written = streamWritten;
            }
            return written;
        }

        /**
         * Removes the RTP header extension(s) from an RTP packet.
         *
         * @param buf the <tt>byte</tt>s of a datagram packet which may contain
         * an RTP packet
         * @param off the offset in <tt>buf</tt> at which the actual data in
         * <tt>buf</tt> starts
         * @param len the number of <tt>byte</tt>s in <tt>buf</tt> starting at
         * <tt>off</tt> comprising the actual data
         * @return the number of <tt>byte</tt>s in <tt>buf</tt> starting at
         * <tt>off</tt> comprising the actual data after the possible removal of
         * the RTP header extension(s)
         */
        private int removeRTPHeaderExtensions(byte[] buf, int off, int len)
        {
            /*
             * Do the bytes in the specified buffer resemble (a header of) an
             * RTP packet?
             */
            if (len >= RTPHeader.SIZE)
            {
                byte b0 = buf[off];
                int v = (b0 & 0xC0) >>> 6; /* version */

                if (v == RTPHeader.VERSION)
                {
                    boolean x = (b0 & 0x10) == 0x10; /* extension */

                    if (x)
                    {
                        int cc = b0 & 0x0F; /* CSRC count */
                        int xBegin = off + RTPHeader.SIZE + 4 * cc;
                        int xLen = 2 /* defined by profile */ + 2 /* length */;
                        int end = off + len;

                        if (xBegin + xLen < end)
                        {
                            xLen
                                += readUnsignedShort(
                                        buf,
                                        xBegin + 2 /* defined by profile */)
                                    * 4;

                            int xEnd = xBegin + xLen;

                            if (xEnd <= end)
                            {
                                // Remove the RTP header extension bytes.
                                for (int src = xEnd, dst = xBegin; src < end;)
                                    buf[dst++] = buf[src++];
                                len -= xLen;
                                // Switch off the extension bit.
                                buf[off] = (byte) (b0 & 0xEF);
                            }
                        }
                    }
                }
            }
            return len;
        }

        public synchronized void removeStreams(RTPConnectorDesc connectorDesc)
        {
            Iterator<OutputDataStreamDesc> streamIter = streams.iterator();

            while (streamIter.hasNext())
            {
                OutputDataStreamDesc streamDesc = streamIter.next();

                if (streamDesc.connectorDesc == connectorDesc)
                    streamIter.remove();
            }
        }

        public void run()
        {
            try
            {
                do
                {
                    int writeIndex;
                    byte[] buffer;
                    StreamRTPManagerDesc exclusion;
                    Format format;
                    int length;

                    synchronized (this)
                    {
                        if (closed
                                || !Thread.currentThread().equals(writeThread))
                            break;
                        if (writeQueueLength < 1)
                        {
                            boolean interrupted = false;

                            try
                            {
                                wait();
                            }
                            catch (InterruptedException ie)
                            {
                                interrupted = true;
                            }
                            if (interrupted)
                                Thread.currentThread().interrupt();
                            continue;
                        }

                        writeIndex = writeQueueHead;

                        RTPTranslatorBuffer write = writeQueue[writeIndex];

                        buffer = write.data;
                        write.data = null;
                        exclusion = write.exclusion;
                        write.exclusion = null;
                        format = write.format;
                        write.format = null;
                        length = write.length;
                        write.length = 0;

                        writeQueueHead++;
                        if (writeQueueHead >= writeQueue.length)
                            writeQueueHead = 0;
                        writeQueueLength--;
                    }

                    try
                    {
                        doWrite(buffer, 0, length, format, exclusion);
                    }
                    finally
                    {
                        synchronized (this)
                        {
                            RTPTranslatorBuffer write = writeQueue[writeIndex];

                            if ((write != null) && (write.data == null))
                                write.data = buffer;
                        }
                    }
                }
                while (true);
            }
            catch (Throwable t)
            {
                logger.error("Failed to translate RTP packet", t);
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
            finally
            {
                synchronized (this)
                {
                    if (Thread.currentThread().equals(writeThread))
                        writeThread = null;
                    if (!closed
                            && (writeThread == null)
                            && (writeQueueLength > 0))
                        createWriteThread();
                }
            }
        }

        /**
         * Notifies this instance that a specific <tt>byte</tt> buffer will be
         * written into the control <tt>OutputDataStream</tt> of a specific
         * <tt>StreamRTPManagerDesc</tt>.
         *
         * @param destination the <tt>StreamRTPManagerDesc</tt> which is the
         * destination of the write
         * @param buffer the data to be written into <tt>destination</tt>
         * @param offset the offset in <tt>buffer</tt> at which the data to be
         * written into <tt>destination</tt> starts 
         * @param length the number of <tt>byte</tt>s in <tt>buffer</tt>
         * beginning at <tt>offset</tt> which constitute the data to the written
         * into <tt>destination</tt>
         * @param format the FMJ <tt>Format</tt> of the data to be written into
         * <tt>destination</tt>
         * @param exclusion the <tt>StreamRTPManagerDesc</tt> which is exclude
         * from the write batch, possibly because it is the cause of the write
         * batch in the first place
         * @return <tt>true</tt> to write the specified data into the specified
         * <tt>destination</tt> or <tt>false</tt> to not write the specified
         * data into the specified <tt>destination</tt>
         */
        private boolean willWriteControl(
                StreamRTPManagerDesc destination,
                byte[] buffer, int offset, int length,
                Format format,
                StreamRTPManagerDesc exclusion)
        {
            boolean write = true;

            /*
             * Do the bytes in the specified buffer resemble (a header of) an
             * RTCP packet?
             */
            if (length >= 12 /* FB */)
            {
                byte b0 = buffer[offset];
                int v = (b0 & 0xc0) >>> 6; /* version */

                if (v == RTCPHeader.VERSION)
                {
                    byte b1 = buffer[offset + 1];
                    int pt = b1 & 0xff; /* payload type */

                    if ((pt == 205 /* RTPFB */) || (pt == 206 /* PSFB */))
                    {
                        // Verify the length field.
                        int rtcpLength
                            = (readUnsignedShort(buffer, offset + 2) + 1) * 4;

                        if (rtcpLength <= length)
                        {
                            int ssrcOfMediaSource = readInt(buffer, offset + 8);

                            if (destination.containsReceiveSSRC(
                                    ssrcOfMediaSource))
                            {
                                if (logger.isTraceEnabled())
                                {
                                    int fmt = b0 & 0x1f; /* feedback message type */
                                    int ssrcOfPacketSender
                                        = readInt(buffer, offset + 4);
                                    String message
                                        = getClass().getName()
                                            + ".willWriteControl: FMT " + fmt
                                            + ", PT " + pt
                                            + ", SSRC of packet sender "
                                            + Long.toString(
                                                    ssrcOfPacketSender
                                                        & 0xffffffffl)
                                            + ", SSRC of media source "
                                            + Long.toString(
                                                    ssrcOfMediaSource
                                                        & 0xffffffffl);

                                    logger.trace(message);
                                }
                            }
                            else
                            {
                                write = false;
                            }
                        }
                    }
                }
            }

            if (write && logger.isTraceEnabled())
                logRTCP(this, "doWrite", buffer, offset, length);
            return write;
        }

        /**
         * Notifies this instance that a specific <tt>byte</tt> buffer will be
         * written into the data <tt>OutputDataStream</tt> of a specific
         * <tt>StreamRTPManagerDesc</tt>.
         *
         * @param destination the <tt>StreamRTPManagerDesc</tt> which is the
         * destination of the write
         * @param buffer the data to be written into <tt>destination</tt>
         * @param offset the offset in <tt>buffer</tt> at which the data to be
         * written into <tt>destination</tt> starts 
         * @param length the number of <tt>byte</tt>s in <tt>buffer</tt>
         * beginning at <tt>offset</tt> which constitute the data to the written
         * into <tt>destination</tt>
         * @param format the FMJ <tt>Format</tt> of the data to be written into
         * <tt>destination</tt>
         * @param exclusion the <tt>StreamRTPManagerDesc</tt> which is exclude
         * from the write batch, possibly because it is the cause of the write
         * batch in the first place
         * @return <tt>true</tt> to write the specified data into the specified
         * <tt>destination</tt> or <tt>false</tt> to not write the specified
         * data into the specified <tt>destination</tt>
         */
        private boolean willWriteData(
                StreamRTPManagerDesc destination,
                byte[] buffer, int offset, int length,
                Format format,
                StreamRTPManagerDesc exclusion)
        {
            /*
             * Only write data packets to OutputDataStreams for which the
             * associated MediaStream allows sending.
             */
            if (!destination.streamRTPManager.getMediaStream().getDirection()
                    .allowsSending())
            {
                return false;
            }

            if ((format != null) && (length > 0))
            {
                Integer payloadType = destination.getPayloadType(format);

                if ((payloadType == null) && (exclusion != null))
                    payloadType = exclusion.getPayloadType(format);
                if (payloadType != null)
                {
                    int payloadTypeByteIndex = offset + 1;

                    buffer[payloadTypeByteIndex]
                        = (byte)
                            ((buffer[payloadTypeByteIndex] & 0x80)
                                | (payloadType & 0x7f));
                }
            }

            return true;
        }

        public int write(byte[] buffer, int offset, int length)
        {
            return doWrite(buffer, offset, length, null, null);
        }

        public synchronized void write(
                byte[] buffer, int offset, int length,
                Format format,
                StreamRTPManagerDesc exclusion)
        {
            if (closed)
                return;

            int writeIndex;

            if (writeQueueLength < writeQueue.length)
            {
                writeIndex
                    = (writeQueueHead + writeQueueLength) % writeQueue.length;
            }
            else
            {
                writeIndex = writeQueueHead;
                writeQueueHead++;
                if (writeQueueHead >= writeQueue.length)
                    writeQueueHead = 0;
                writeQueueLength--;
                logger.warn("Will not translate RTP packet.");
            }

            RTPTranslatorBuffer write
                = writeQueue[writeIndex];

            if (write == null)
                writeQueue[writeIndex] = write = new RTPTranslatorBuffer();

            byte[] data = write.data;

            if ((data == null) || (data.length < length))
                write.data = data = new byte[length];
            System.arraycopy(buffer, offset, data, 0, length);

            write.exclusion = exclusion;
            write.format = format;
            write.length = length;

            writeQueueLength++;

            if (writeThread == null)
                createWriteThread();
            else
                notify();
        }
    }

    private static class PushSourceStreamDesc
    {
        public final RTPConnectorDesc connectorDesc;

        /**
         * <tt>true</tt> if this instance represents a data (RTP) stream.
         */
        public final boolean data;

        public final PushSourceStream stream;

        public PushSourceStreamDesc(
                RTPConnectorDesc connectorDesc,
                PushSourceStream stream,
                boolean data)
        {
            this.connectorDesc = connectorDesc;
            this.stream = stream;
            this.data = data;
        }
    }

    private class PushSourceStreamImpl
        implements PushSourceStream,
                   Runnable,
                   SourceTransferHandler
    {
        /**
         * The indicator which determines whether {@link #close()} has been
         * invoked on this instance.
         */
        private boolean closed = false;

        private final boolean data;

        /**
         * The indicator which determines whether
         * {@link #read(byte[], int, int)} read a <tt>SourcePacket</tt> from
         * {@link #readQ} after a <tt>SourcePacket</tt> was written there.
         */
        private boolean read = false;

        /**
         * The <tt>Queue</tt> of <tt>SourcePacket</tt>s to be read out of this
         * instance via {@link #read(byte[], int, int)}.
         */
        private final Queue<SourcePacket> readQ;

        /**
         * The capacity of {@link #readQ}.
         */
        private final int readQCapacity;

        /**
         * The pool of <tt>SourcePacket</tt> instances to reduce their
         * allocations and garbage collection.
         */
        private final Queue<SourcePacket> sourcePacketPool
            = new LinkedBlockingQueue<SourcePacket>();

        private final List<PushSourceStreamDesc> streams
            = new LinkedList<PushSourceStreamDesc>();

        /**
         * The <tt>Thread</tt> which invokes
         * {@link SourceTransferHandler#transferData(PushSourceStream)} on
         * {@link #transferHandler}. 
         */
        private Thread transferDataThread;

        private SourceTransferHandler transferHandler;

        public PushSourceStreamImpl(boolean data)
        {
            this.data = data;

            readQCapacity
                = RTPConnectorOutputStream
                    .MAX_PACKETS_PER_MILLIS_POLICY_PACKET_QUEUE_CAPACITY;
            readQ = new ArrayBlockingQueue<SourcePacket>(readQCapacity);

            transferDataThread = new Thread(this, getClass().getName());
            transferDataThread.setDaemon(true);
            transferDataThread.start();
        }

        public synchronized void addStream(
                RTPConnectorDesc connectorDesc,
                PushSourceStream stream)
        {
            for (PushSourceStreamDesc streamDesc : streams)
            {
                if ((streamDesc.connectorDesc == connectorDesc)
                        && (streamDesc.stream == stream))
                {
                    return;
                }
            }
            streams.add(
                    new PushSourceStreamDesc(connectorDesc, stream, this.data));
            stream.setTransferHandler(this);
        }

        public void close()
        {
            closed = true;
            sourcePacketPool.clear();
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public boolean endOfStream()
        {
            // TODO Auto-generated method stub
            return false;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public ContentDescriptor getContentDescriptor()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public long getContentLength()
        {
            return LENGTH_UNKNOWN;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public Object getControl(String controlType)
        {
            // TODO Auto-generated method stub
            return null;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public Object[] getControls()
        {
            // TODO Auto-generated method stub
            return null;
        }

        public synchronized int getMinimumTransferSize()
        {
            int minimumTransferSize = 0;

            for (PushSourceStreamDesc streamDesc : streams)
            {
                int streamMinimumTransferSize
                    = streamDesc.stream.getMinimumTransferSize();

                if (minimumTransferSize < streamMinimumTransferSize)
                    minimumTransferSize = streamMinimumTransferSize;
            }
            return minimumTransferSize;
        }

        public int read(byte[] buffer, int offset, int length)
            throws IOException
        {
            if (closed)
                return -1;

            SourcePacket pkt;
            int pktLength;

            synchronized (readQ)
            {
                pkt = readQ.peek();
                if (pkt == null)
                    return 0;

                pktLength = pkt.getLength();
                if (length < pktLength)
                {
                    throw new IOException(
                            "Length " + length
                                + " is insuffient. Must be at least "
                                + pktLength + ".");
                }

                readQ.remove();
                read = true;
                readQ.notifyAll();
            }

            System.arraycopy(
                    pkt.getBuffer(), pkt.getOffset(),
                    buffer, offset,
                    pktLength);

            PushSourceStreamDesc streamDesc = pkt.streamDesc;
            int read = pktLength;

            pkt.streamDesc = null;
            sourcePacketPool.offer(pkt);

            if (read > 0)
            {
                read
                    = RTPTranslatorImpl.this.read(
                            streamDesc,
                            buffer, offset, length,
                            read);
            }
            return read;
        }

        public synchronized void removeStreams(RTPConnectorDesc connectorDesc)
        {
            Iterator<PushSourceStreamDesc> streamIter = streams.iterator();

            while (streamIter.hasNext())
            {
                PushSourceStreamDesc streamDesc = streamIter.next();

                if (streamDesc.connectorDesc == connectorDesc)
                {
                    streamDesc.stream.setTransferHandler(null);
                    streamIter.remove();
                }
            }
        }

        /**
         * Runs in {@link #transferDataThread} and invokes
         * {@link SourceTransferHandler#transferData(PushSourceStream)} on
         * {@link #transferHandler}.
         */
        @Override
        public void run()
        {
            try
            {
                while (!closed)
                {
                    SourceTransferHandler transferHandler
                        = this.transferHandler;

                    synchronized (readQ)
                    {
                        if (readQ.isEmpty() || (transferHandler == null))
                        {
                            try
                            {
                                readQ.wait(100);
                            }
                            catch (InterruptedException ie)
                            {
                            }
                            continue;
                        }
                    }

                    try
                    {
                        transferHandler.transferData(this);
                    }
                    catch (Throwable t)
                    {
                        if (t instanceof ThreadDeath)
                        {
                            throw (ThreadDeath) t;
                        }
                        else
                        {
                            logger.warn(
                                    "An RTP packet may have not been fully"
                                        + " handled.",
                                    t);
                        }
                    }
                }
            }
            finally
            {
                if (Thread.currentThread().equals(transferDataThread))
                    transferDataThread = null;
            }
        }

        public synchronized void setTransferHandler(
                SourceTransferHandler transferHandler)
        {
            if (this.transferHandler != transferHandler)
            {
                this.transferHandler = transferHandler;
                for (PushSourceStreamDesc streamDesc : streams)
                    streamDesc.stream.setTransferHandler(this);
            }
        }

        /**
         * {@inheritDoc}
         *
         * Implements
         * {@link SourceTransferHandler#transferData(PushSourceStream)}. This
         * instance sets itself as the <tt>transferHandler</tt> of all
         * <tt>PushSourceStream</tt>s that get added to it (i.e.
         * {@link #streams}). When either one of these pushes media data, this
         * instance pushes that media data.
         */
        public void transferData(PushSourceStream stream)
        {
            if (closed)
                return;

            PushSourceStreamDesc streamDesc = null;

            synchronized (this)
            {
                for (PushSourceStreamDesc aStreamDesc : streams)
                {
                    if (aStreamDesc.stream == stream)
                    {
                        streamDesc = aStreamDesc;
                        break;
                    }
                }
            }
            if (streamDesc == null)
                return;

            int len = stream.getMinimumTransferSize();

            if (len < 1)
                len = 2 * 1024;

            SourcePacket pkt = sourcePacketPool.poll();
            byte[] buf;

            if ((pkt == null) || ((buf = pkt.getBuffer()).length < len))
            {
                buf = new byte[len];
                pkt = new SourcePacket(buf, 0, len);
            }
            else
            {
                buf = pkt.getBuffer();
                len = buf.length;
            }

            int read = 0;

            try
            {
                read = stream.read(buf, 0, len);
            }
            catch (IOException ioe)
            {
                logger.error("Failed to read from an RTP stream!", ioe);
            }
            finally
            {
                if (read > 0)
                {
                    pkt.setLength(read);
                    pkt.setOffset(0);
                    pkt.streamDesc = streamDesc;

                    boolean yield;

                    synchronized (readQ)
                    {
                        int readQSize = readQ.size();

                        if (readQSize < 1)
                            yield = false;
                        else if (readQSize < readQCapacity)
                            yield = (this.read == false);
                        else
                            yield = true;
                        if (yield)
                            readQ.notifyAll();
                    }
                    if (yield)
                        Thread.yield();

                    synchronized (readQ)
                    {
                        if (readQ.size() >= readQCapacity)
                        {
                            readQ.remove();
                            logger.warn(
                                    "Discarded an RTP packet because the read"
                                        + " queue is full.");
                        }

                        if (readQ.offer(pkt))
                        {
                            /*
                             * TODO It appears that it is better to not yield
                             * based on whether the read method has read after
                             * the last write.
                             */
                            // this.read = false;
                        }
                        readQ.notifyAll();
                    }
                }
                else
                {
                    pkt.streamDesc = null;
                    sourcePacketPool.offer(pkt);
                }
            }
        }
    }

    private static class RTPConnectorDesc
    {
        public final RTPConnector connector;

        public final StreamRTPManagerDesc streamRTPManagerDesc;

        public RTPConnectorDesc(
                StreamRTPManagerDesc streamRTPManagerDesc,
                RTPConnector connector)
        {
            this.streamRTPManagerDesc = streamRTPManagerDesc;
            this.connector = connector;
        }
    }

    /**
     * Implements the <tt>RTPConnector</tt> with which this instance initializes
     * its <tt>RTPManager</tt>. It delegates to the <tt>RTPConnector</tt> of the
     * various <tt>StreamRTPManager</tt>s.
     */
    private class RTPConnectorImpl
        implements RTPConnector
    {
        /**
         * The <tt>RTPConnector</tt>s this instance delegates to.
         */
        private final List<RTPConnectorDesc> connectors
            = new LinkedList<RTPConnectorDesc>();

        private PushSourceStreamImpl controlInputStream;

        private OutputDataStreamImpl controlOutputStream;

        private PushSourceStreamImpl dataInputStream;

        private OutputDataStreamImpl dataOutputStream;

        public synchronized void addConnector(RTPConnectorDesc connector)
        {
            if (!connectors.contains(connector))
            {
                connectors.add(connector);
                if (this.controlInputStream != null)
                {
                    PushSourceStream controlInputStream = null;

                    try
                    {
                        controlInputStream
                            = connector.connector.getControlInputStream();
                    }
                    catch (IOException ioe)
                    {
                        throw new UndeclaredThrowableException(ioe);
                    }
                    if (controlInputStream != null)
                    {
                        this.controlInputStream.addStream(
                                connector,
                                controlInputStream);
                    }
                }
                if (this.controlOutputStream != null)
                {
                    OutputDataStream controlOutputStream = null;

                    try
                    {
                        controlOutputStream
                            = connector.connector.getControlOutputStream();
                    }
                    catch (IOException ioe)
                    {
                        throw new UndeclaredThrowableException(ioe);
                    }
                    if (controlOutputStream != null)
                    {
                        this.controlOutputStream.addStream(
                                connector,
                                controlOutputStream);
                    }
                }
                if (this.dataInputStream != null)
                {
                    PushSourceStream dataInputStream = null;

                    try
                    {
                        dataInputStream
                            = connector.connector.getDataInputStream();
                    }
                    catch (IOException ioe)
                    {
                        throw new UndeclaredThrowableException(ioe);
                    }
                    if (dataInputStream != null)
                    {
                        this.dataInputStream.addStream(
                                connector,
                                dataInputStream);
                    }
                }
                if (this.dataOutputStream != null)
                {
                    OutputDataStream dataOutputStream = null;

                    try
                    {
                        dataOutputStream
                            = connector.connector.getDataOutputStream();
                    }
                    catch (IOException ioe)
                    {
                        throw new UndeclaredThrowableException(ioe);
                    }
                    if (dataOutputStream != null)
                    {
                        this.dataOutputStream.addStream(
                                connector,
                                dataOutputStream);
                    }
                }
            }
        }

        public synchronized void close()
        {
            if (controlInputStream != null)
            {
                controlInputStream.close();
                controlInputStream = null;
            }
            if (controlOutputStream != null)
            {
                controlOutputStream.close();
                controlOutputStream = null;
            }
            if (dataInputStream != null)
            {
                dataInputStream.close();
                dataInputStream = null;
            }
            if (dataOutputStream != null)
            {
                dataOutputStream.close();
                dataOutputStream = null;
            }

            for (RTPConnectorDesc connectorDesc : connectors)
                connectorDesc.connector.close();
        }

        public synchronized PushSourceStream getControlInputStream()
            throws IOException
        {
            if (this.controlInputStream == null)
            {
                this.controlInputStream = new PushSourceStreamImpl(false);
                for (RTPConnectorDesc connectorDesc : connectors)
                {
                    PushSourceStream controlInputStream
                        = connectorDesc.connector.getControlInputStream();

                    if (controlInputStream != null)
                    {
                        this.controlInputStream.addStream(
                                connectorDesc,
                                controlInputStream);
                    }
                }
            }
            return this.controlInputStream;
        }

        public synchronized OutputDataStreamImpl getControlOutputStream()
            throws IOException
        {
            if (this.controlOutputStream == null)
            {
                this.controlOutputStream = new OutputDataStreamImpl(false);
                for (RTPConnectorDesc connectorDesc : connectors)
                {
                    OutputDataStream controlOutputStream
                        = connectorDesc.connector.getControlOutputStream();

                    if (controlOutputStream != null)
                    {
                        this.controlOutputStream.addStream(
                                connectorDesc,
                                controlOutputStream);
                    }
                }
            }
            return this.controlOutputStream;
        }

        public synchronized PushSourceStream getDataInputStream()
            throws IOException
        {
            if (this.dataInputStream == null)
            {
                this.dataInputStream = new PushSourceStreamImpl(true);
                for (RTPConnectorDesc connectorDesc : connectors)
                {
                    PushSourceStream dataInputStream
                        = connectorDesc.connector.getDataInputStream();

                    if (dataInputStream != null)
                    {
                        this.dataInputStream.addStream(
                                connectorDesc,
                                dataInputStream);
                    }
                }
            }
            return this.dataInputStream;
        }

        public synchronized OutputDataStreamImpl getDataOutputStream()
            throws IOException
        {
            if (this.dataOutputStream == null)
            {
                this.dataOutputStream = new OutputDataStreamImpl(true);
                for (RTPConnectorDesc connectorDesc : connectors)
                {
                    OutputDataStream dataOutputStream
                        = connectorDesc.connector.getDataOutputStream();

                    if (dataOutputStream != null)
                    {
                        this.dataOutputStream.addStream(
                                connectorDesc,
                                dataOutputStream);
                    }
                }
            }
            return this.dataOutputStream;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public int getReceiveBufferSize()
        {
            return -1;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public double getRTCPBandwidthFraction()
        {
            return -1;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public double getRTCPSenderBandwidthFraction()
        {
            return -1;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public int getSendBufferSize()
        {
            return -1;
        }

        public synchronized void removeConnector(RTPConnectorDesc connector)
        {
            if (connectors.contains(connector))
            {
                if (controlInputStream != null)
                    controlInputStream.removeStreams(connector);
                if (controlOutputStream != null)
                    controlOutputStream.removeStreams(connector);
                if (dataInputStream != null)
                    dataInputStream.removeStreams(connector);
                if (dataOutputStream != null)
                    dataOutputStream.removeStreams(connector);
                connectors.remove(connector);
            }
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public void setReceiveBufferSize(int receiveBufferSize)
            throws IOException
        {
            // TODO Auto-generated method stub
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public void setSendBufferSize(int sendBufferSize)
            throws IOException
        {
            // TODO Auto-generated method stub
        }
    }

    private static class RTPTranslatorBuffer
    {
        public byte[] data;

        public StreamRTPManagerDesc exclusion;

        public Format format;

        public int length;
    }

    /**
     * Describes a <tt>SendStream</tt> created by the <tt>RTPManager</tt> of
     * this instance. Contains information about the <tt>DataSource</tt> and its
     * stream index from which the <tt>SendStream</tt> has been created so that
     * various <tt>StreamRTPManager</tt> receive different views of one and the
     * same <tt>SendStream</tt>.
     */
    private class SendStreamDesc
    {
        /**
         * The <tt>DataSource</tt> from which {@link #sendStream} has been
         * created.
         */
        public final DataSource dataSource;

        /**
         * The <tt>SendStream</tt> created from the stream of
         * {@link #dataSource} at index {@link #streamIndex}.
         */
        public final SendStream sendStream;

        /**
         * The list of <tt>StreamRTPManager</tt>-specific views to
         * {@link #sendStream}.
         */
        private final List<SendStreamImpl> sendStreams
            = new LinkedList<SendStreamImpl>();

        /**
         * The number of <tt>StreamRTPManager</tt>s which have started their
         * views of {@link #sendStream}.
         */
        private int started;

        /**
         * The index of the stream of {@link #dataSource} from which
         * {@link #sendStream} has been created.
         */
        public final int streamIndex;

        public SendStreamDesc(
                DataSource dataSource, int streamIndex,
                SendStream sendStream)
        {
            this.dataSource = dataSource;
            this.sendStream = sendStream;
            this.streamIndex = streamIndex;
        }

        void close(SendStreamImpl sendStream)
        {
            boolean close = false;

            synchronized (this)
            {
                if (sendStreams.contains(sendStream))
                {
                    sendStreams.remove(sendStream);
                    close = sendStreams.isEmpty();
                }
            }
            if (close)
                RTPTranslatorImpl.this.closeSendStream(this);
        }

        public synchronized SendStreamImpl getSendStream(
                StreamRTPManager streamRTPManager,
                boolean create)
        {
            for (SendStreamImpl sendStream : sendStreams)
                if (sendStream.streamRTPManager == streamRTPManager)
                    return sendStream;
            if (create)
            {
                SendStreamImpl sendStream
                    = new SendStreamImpl(streamRTPManager, this);

                sendStreams.add(sendStream);
                return sendStream;
            }
            else
                return null;
        }

        public synchronized int getSendStreamCount()
        {
            return sendStreams.size();
        }

        synchronized void start(SendStreamImpl sendStream)
            throws IOException
        {
            if (sendStreams.contains(sendStream))
            {
                if (started < 1)
                {
                    this.sendStream.start();
                    started = 1;
                }
                else
                    started++;
            }
        }

        synchronized void stop(SendStreamImpl sendStream)
            throws IOException
        {
            if (sendStreams.contains(sendStream))
            {
                if (started == 1)
                {
                    this.sendStream.stop();
                    started = 0;
                }
                else if (started > 1)
                    started--;
            }
        }
    }

    private static class SendStreamImpl
        implements SendStream
    {
        private boolean closed;

        public final SendStreamDesc sendStreamDesc;

        private boolean started;

        public final StreamRTPManager streamRTPManager;

        public SendStreamImpl(
                StreamRTPManager streamRTPManager,
                SendStreamDesc sendStreamDesc)
        {
            this.sendStreamDesc = sendStreamDesc;
            this.streamRTPManager = streamRTPManager;
        }

        public void close()
        {
            if (!closed)
            {
                try
                {
                    if (started)
                        stop();
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }
                finally
                {
                    sendStreamDesc.close(this);
                    closed = true;
                }
            }
        }

        public DataSource getDataSource()
        {
            return sendStreamDesc.sendStream.getDataSource();
        }

        public Participant getParticipant()
        {
            return sendStreamDesc.sendStream.getParticipant();
        }

        public SenderReport getSenderReport()
        {
            return sendStreamDesc.sendStream.getSenderReport();
        }

        public TransmissionStats getSourceTransmissionStats()
        {
            return sendStreamDesc.sendStream.getSourceTransmissionStats();
        }

        public long getSSRC()
        {
            return sendStreamDesc.sendStream.getSSRC();
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public int setBitRate(int bitRate)
        {
            // TODO Auto-generated method stub
            return 0;
        }

        /**
         * Not implemented because there are currently no uses of the underlying
         * functionality.
         */
        public void setSourceDescription(SourceDescription[] sourceDescription)
        {
            // TODO Auto-generated method stub
        }

        public void start()
            throws IOException
        {
            if (closed)
            {
                throw
                    new IOException(
                            "Cannot start SendStream"
                                + " after it has been closed.");
            }
            if (!started)
            {
                sendStreamDesc.start(this);
                started = true;
            }
        }

        public void stop()
            throws IOException
        {
            if (!closed && started)
            {
                sendStreamDesc.stop(this);
                started = false;
            }
        }
    }

    private static class SourcePacket
        extends RawPacket
    {
        public PushSourceStreamDesc streamDesc;

        public SourcePacket(byte[] buf, int off, int len)
        {
            super(buf, off, len);
        }
    }

    /**
     * Describes additional information about a <tt>StreamRTPManager</tt> for
     * the purposes of <tt>RTPTranslatorImpl</tt>.
     *
     * @author Lyubomir Marinov
     */
    private static class StreamRTPManagerDesc
    {
        public RTPConnectorDesc connectorDesc;

        private final Map<Integer, Format> formats
            = new HashMap<Integer, Format>();

        /**
         * The list of synchronization source (SSRC) identifiers received by
         * {@link #streamRTPManager} (as <tt>ReceiveStream</tt>s).
         */
        private int[] receiveSSRCs = EMPTY_INT_ARRAY;

        private final List<ReceiveStreamListener> receiveStreamListeners
            = new LinkedList<ReceiveStreamListener>();

        public final StreamRTPManager streamRTPManager;

        /**
         * Initializes a new <tt>StreamRTPManagerDesc</tt> instance which is to
         * describe a specific <tt>StreamRTPManager</tt>.
         *
         * @param streamRTPManager the <tt>StreamRTPManager</tt> to be described
         * by the new instance
         */
        public StreamRTPManagerDesc(StreamRTPManager streamRTPManager)
        {
            this.streamRTPManager = streamRTPManager;
        }

        public void addFormat(Format format, int payloadType)
        {
            synchronized (formats)
            {
                formats.put(payloadType, format);
            }
        }

        /**
         * Adds a new synchronization source (SSRC) identifier to the list of
         * SSRC received by the associated <tt>StreamRTPManager</tt>.
         *
         * @param receiveSSRC the new SSRC to add to the list of SSRC received
         * by the associated <tt>StreamRTPManager</tt>
         */
        public synchronized void addReceiveSSRC(int receiveSSRC)
        {
            if (!containsReceiveSSRC(receiveSSRC))
            {
                int receiveSSRCCount = receiveSSRCs.length;
                int[] newReceiveSSRCs = new int[receiveSSRCCount + 1];

                System.arraycopy(
                        receiveSSRCs, 0,
                        newReceiveSSRCs, 0,
                        receiveSSRCCount);
                newReceiveSSRCs[receiveSSRCCount] = receiveSSRC;
                receiveSSRCs = newReceiveSSRCs;
            }
        }

        public void addReceiveStreamListener(ReceiveStreamListener listener)
        {
            synchronized (receiveStreamListeners)
            {
                if (!receiveStreamListeners.contains(listener))
                    receiveStreamListeners.add(listener);
            }
        }

        /**
         * Determines whether the list of synchronization source (SSRC)
         * identifiers received by the associated <tt>StreamRTPManager</tt>
         * contains a specific SSRC.
         *
         * @param receiveSSRC the SSRC to check whether it is contained in the
         * list of SSRC received by the associated <tt>StreamRTPManager</tt>
         * @return <tt>true</tt> if the specified <tt>receiveSSRC</tt> is
         * contained in the list of SSRC received by the associated
         * <tt>StreamRTPManager</tt>; otherwise, <tt>false</tt>
         */
        public synchronized boolean containsReceiveSSRC(int receiveSSRC)
        {
            for (int i = 0; i < receiveSSRCs.length; i++)
            {
                if (receiveSSRCs[i] == receiveSSRC)
                    return true;
            }
            return false;
        }

        public Format getFormat(int payloadType)
        {
            synchronized (formats)
            {
                return formats.get(payloadType);
            }
        }

        public Format[] getFormats()
        {
            synchronized (this.formats)
            {
                Collection<Format> formats = this.formats.values();

                return formats.toArray(new Format[formats.size()]);
            }
        }

        public Integer getPayloadType(Format format)
        {
            synchronized (formats)
            {
                for (Map.Entry<Integer, Format> entry : formats.entrySet())
                {
                    Format entryFormat = entry.getValue();

                    if (entryFormat.matches(format)
                            || format.matches(entryFormat))
                        return entry.getKey();
                }
            }
            return null;
        }

        public ReceiveStreamListener[] getReceiveStreamListeners()
        {
            synchronized (receiveStreamListeners)
            {
                return
                    receiveStreamListeners.toArray(
                            new ReceiveStreamListener[
                                    receiveStreamListeners.size()]);
            }
        }

        public void removeReceiveStreamListener(ReceiveStreamListener listener)
        {
            synchronized (receiveStreamListeners)
            {
                receiveStreamListeners.remove(listener);
            }
        }
    }
}
