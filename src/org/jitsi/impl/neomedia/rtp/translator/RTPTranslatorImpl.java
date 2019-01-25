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
package org.jitsi.impl.neomedia.rtp.translator;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.RTPHeader;

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
    private static final Logger LOGGER
        = Logger.getLogger(RTPTranslatorImpl.class);

    /**
     * Logs information about an RTCP packet using {@link #LOGGER} for debugging
     * purposes.
     *
     * @param obj the object which is the source of the log request
     * @param methodName the name of the method on <tt>obj</tt> which is the
     * source of the log request
     * @param buf the <tt>byte</tt>s which (possibly) represent an RTCP
     * packet to be logged for debugging purposes
     * @param off the position within <tt>buf</tt> at which the valid data
     * begins
     * @param len the number of bytes in <tt>buf</tt> which constitute the valid
     * data
     */
    static void logRTCP(
            Object obj, String methodName,
            byte[] buf, int off, int len)
    {
        // Do the bytes in the specified buffer resemble (a header of) an RTCP
        // packet?
        if (len >= 8 /* BYE */)
        {
            byte b0 = buf[off];
            int v = (b0 & 0xc0) >>> 6;

            if (v == RTCPHeader.VERSION)
            {
                byte b1 = buf[off + 1];
                int pt = b1 & 0xff;

                if (pt == 203 /* BYE */)
                {
                    // Verify the length field.
                    int rtcpLength
                        = (RTPUtils.readUint16AsInt(buf, off + 2) + 1) * 4;

                    if (rtcpLength <= len)
                    {
                        int sc = b0 & 0x1f;
                        int o = off + 4;

                        for (int i = 0, end = off + len;
                                i < sc && o + 4 <= end;
                                ++i, o += 4)
                        {
                            int ssrc = RTPUtils.readInt(buf, o);

                            LOGGER.trace(
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
     * The <tt>RTPConnector</tt> which is used by {@link #manager} and which
     * delegates to the <tt>RTPConnector</tt>s of the <tt>StreamRTPManager</tt>s
     * attached to this instance.
     */
    private RTPConnectorImpl connector;

    /**
     * A local SSRC for this <tt>RTPTranslator</tt>. This overrides the SSRC of
     * the <tt>RTPManager</tt> and it does not deal with SSRC collisions.
     * TAG(cat4-local-ssrc-hurricane).
     */
    private long localSSRC = -1;

    /**
     * The <tt>ReadWriteLock</tt> which synchronizes the access to and/or
     * modification of the state of this instance. Replaces
     * <tt>synchronized</tt> blocks in order to reduce the number of exclusive
     * locks and, therefore, the risks of superfluous waiting.
     */
    private final ReadWriteLock _lock = new ReentrantReadWriteLock();

    /**
     * The <tt>RTPManager</tt> which implements the actual RTP management of
     * this instance.
     */
    private final RTPManager manager = RTPManager.newInstance();

    /**
     * An instance which can be used to send RTCP Feedback Messages, using
     * as 'packet sender SSRC' the SSRC of (the <tt>RTPManager</tt> of) this
     * <tt>RTPTranslator</tt>.
     */
    private final RTCPFeedbackMessageSender rtcpFeedbackMessageSender
        = new RTCPFeedbackMessageSender(this);

    /**
     * The <tt>SendStream</tt>s created by the <tt>RTPManager</tt> and the
     * <tt>StreamRTPManager</tt>-specific views to them.
     */
    private final List<SendStreamDesc> sendStreams = new LinkedList<>();

    /**
     * The list of <tt>StreamRTPManager</tt>s i.e. <tt>MediaStream</tt>s which
     * this instance forwards RTP and RTCP traffic between.
     */
    private final List<StreamRTPManagerDesc> streamRTPManagers
        = new ArrayList<>();

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
    public void addFormat(
            StreamRTPManager streamRTPManager,
            Format format, int payloadType)
    {
        Lock lock = _lock.writeLock();
        StreamRTPManagerDesc desc;

        lock.lock();
        try
        {

        // XXX RTPManager.addFormat is NOT thread-safe. It appears we have
        // decided to provide thread-safety at least on our side. Which may be
        // insufficient in all use cases but it still sounds reasonable in our
        // current use cases.
        manager.addFormat(format, payloadType);

        desc = getStreamRTPManagerDesc(streamRTPManager, true);

        }
        finally
        {
            lock.unlock();
        }

        // StreamRTPManager.addFormat is thread-safe.
        desc.addFormat(format,payloadType);
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
    public void addReceiveStreamListener(
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
     * Closes a specific <tt>SendStream</tt>.
     *
     * @param sendStreamDesc a <tt>SendStreamDesc</tt> instance that specifies
     * the <tt>SendStream</tt> to be closed
     */
    void closeSendStream(SendStreamDesc sendStreamDesc)
    {
        // XXX Here we could potentially start with a read lock and upgrade to
        // a write lock, if the sendStreamDesc is in the sendStreams collection,
        // but does it worth it?
        Lock lock = _lock.writeLock();

        lock.lock();
        try
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
                // Refer to MediaStreamImpl#stopSendStreams(
                // Iterable<SendStream>, boolean) for an explanation about the
                // swallowing of the exception.
                LOGGER.error("Failed to close send stream", npe);
            }
            sendStreams.remove(sendStreamDesc);
        }

        }
        finally
        {
            lock.unlock();
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
    public SendStream createSendStream(
            StreamRTPManager streamRTPManager,
            DataSource dataSource, int streamIndex)
        throws IOException,
               UnsupportedFormatException
    {
        // XXX Here we could potentially start with a read lock and upgrade to
        // a write lock, if the sendStreamDesc is not in sendStreams collection,
        // but does it worth it?
        Lock lock = _lock.writeLock();
        SendStream ret;

        lock.lock();
        try
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
            }
        }
        ret
            = (sendStreamDesc == null)
                ? null
                : sendStreamDesc.getSendStream(streamRTPManager, true);

        }
        finally
        {
            lock.unlock();
        }

        return ret;
    }

    /**
     * Notifies this instance that an RTP or RTCP packet has been received from
     * a peer represented by a specific <tt>PushSourceStreamDesc</tt>.
     *
     * @param streamDesc a <tt>PushSourceStreamDesc</tt> which identifies the
     * peer from which an RTP or RTCP packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or
     * RTCP packet
     * @param off the zero-based index in <tt>buf</tt> at which the bytes of the
     * received RTP or RTCP packet begin
     * @param len the number of bytes in <tt>buf</tt> beginning at <tt>off</tt>
     * which represent the received RTP or RTCP packet
     * @param flags <tt>Buffer.FLAG_XXX</tt>
     * @return the number of bytes in <tt>buf</tt> beginning at <tt>off</tt>
     * which represent the received RTP or RTCP packet
     * @throws IOException if an I/O error occurs while the method processes the
     * specified RTP or RTCP packet
     */
    int didRead(
            PushSourceStreamDesc streamDesc,
            byte[] buf, int off, int len,
            int flags)
        throws IOException
    {
        Lock lock = _lock.readLock();

        lock.lock();
        try
        {

        boolean data = streamDesc.data;
        StreamRTPManagerDesc streamRTPManager
            = streamDesc.connectorDesc.streamRTPManagerDesc;
        Format format = null;

        if (data)
        {
            // Ignore RTP packets coming from peers whose MediaStream's
            // direction does not allow receiving.
            if (!streamRTPManager.streamRTPManager.getMediaStream()
                    .getDirection().allowsReceiving())
            {
                // FIXME We are ignoring RTP packets received from peers who we
                // do not want to receive from ONLY in the sense that we are not
                // translating/forwarding them to the other peers. Do not we
                // want to not receive them locally as well?
                return len;
            }

            // We flag an RTP packet with Buffer.FLAG_SILENCE when we want to
            // ignore its payload. Because the payload may have skipped
            // decryption as a result of the flag, it is unwise to
            // translate/forward it.
            if ((flags & Buffer.FLAG_SILENCE) == Buffer.FLAG_SILENCE)
                return len;

            // Do the bytes in the specified buffer resemble (a header of) an
            // RTP packet?
            if ((len >= RTPHeader.SIZE)
                    && (/* v */ ((buf[off] & 0xc0) >>> 6) == RTPHeader.VERSION))
            {
                int ssrc = RTPUtils.readInt(buf, off + 8);

                if (!streamRTPManager.containsReceiveSSRC(ssrc))
                {
                    if (findStreamRTPManagerDescByReceiveSSRC(
                                ssrc,
                                streamRTPManager)
                            == null)
                    {
                        streamRTPManager.addReceiveSSRC(ssrc);
                    }
                    else
                    {
                        return 0;
                    }
                }

                int pt = buf[off + 1] & 0x7f;

                format = streamRTPManager.getFormat(pt);

                // Pass the packet to the feedback message sender to give it
                // a chance to inspect the received packet and decide whether
                // or not it should keep asking for a key frame or stop.
                rtcpFeedbackMessageSender.maybeStopRequesting(
                    streamRTPManager, ssrc & 0xffff_ffffL, buf, off, len);
            }
        }
        else if (LOGGER.isTraceEnabled())
        {
            logRTCP(this, "read", buf, off, len);
        }

        OutputDataStreamImpl outputStream
            = data
                ? connector.getDataOutputStream()
                : connector.getControlOutputStream();

        if (outputStream != null)
        {
            outputStream.write(buf, off, len, format, streamRTPManager);
        }

        }
        finally
        {
            lock.unlock();
        }

        return len;
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    @Override
    public void dispose()
    {
        Lock lock = _lock.writeLock();

        lock.lock();
        try
        {
            rtcpFeedbackMessageSender.dispose();

            manager.removeReceiveStreamListener(this);
            try
            {
                manager.dispose();
            }
            catch (Throwable t)
            {
                if (t instanceof ThreadDeath)
                {
                    throw (ThreadDeath) t;
                }
                else
                {
                    // RTPManager.dispose() often throws at least a
                    // NullPointerException in relation to some RTP BYE.
                    LOGGER.error("Failed to dispose of RTPManager", t);
                }
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Releases the resources allocated by this instance for the purposes of the
     * functioning of a specific <tt>StreamRTPManager</tt> in the course of its
     * execution and prepares that <tt>StreamRTPManager</tt> to be garbage
     * collected (as far as this <tt>RTPTranslatorImpl</tt> is concerned).
     */
    public void dispose(StreamRTPManager streamRTPManager)
    {
        // XXX Here we could potentially start with a read lock and upgrade to
        // a write lock, if the streamRTPManager is in the streamRTPManagers
        // collection. Not sure about the up/down grading performance hit
        // though.
        Lock lock = _lock.writeLock();

        lock.lock();
        try
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
                break;
            }
        }

        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamRTPManager findStreamRTPManagerByReceiveSSRC(int receiveSSRC)
    {
        StreamRTPManagerDesc desc
            = findStreamRTPManagerDescByReceiveSSRC(
                    receiveSSRC,
                    /* exclusion */ null);

        return (desc == null) ? null : desc.streamRTPManager;
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
    private StreamRTPManagerDesc findStreamRTPManagerDescByReceiveSSRC(
            int receiveSSRC,
            StreamRTPManagerDesc exclusion)
    {
        Lock lock = _lock.readLock();
        StreamRTPManagerDesc ret = null;

        lock.lock();
        try
        {

        for (int i = 0, count = streamRTPManagers.size(); i < count; i++)
        {
            StreamRTPManagerDesc s = streamRTPManagers.get(i);

            if ((s != exclusion) && s.containsReceiveSSRC(receiveSSRC))
            {
                ret = s;
                break;
            }
        }

        }
        finally
        {
            lock.unlock();
        }

        return ret;
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
        // if (streamRTPManager == null)
        //    return localSSRC;
        // return ((RTPSessionMgr) manager).getLocalSSRC();

        // XXX(gp) it makes (almost) no sense to use the FMJ SSRC because, at
        // least in the case of jitsi-videobridge, it's not announced to the
        // peers, resulting in Chrome's discarding the RTP/RTCP packets with
        // ((RTPSessionMgr) manager).getLocalSSRC(); as the media sender SSRC.
        // This makes the ((RTPSessionMgr) manager).getLocalSSRC() useless in
        // 95% of the use cases (hence the "almost" in the beginning of this
        // comment).
        return localSSRC;
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
    public Vector<ReceiveStream> getReceiveStreams(
            StreamRTPManager streamRTPManager)
    {
        Lock lock = _lock.readLock();
        Vector<ReceiveStream> receiveStreams = null;

        lock.lock();
        try
        {

        StreamRTPManagerDesc streamRTPManagerDesc
            = getStreamRTPManagerDesc(streamRTPManager, false);

        if (streamRTPManagerDesc != null)
        {
            Vector<?> managerReceiveStreams = manager.getReceiveStreams();

            if (managerReceiveStreams != null)
            {
                receiveStreams = new Vector<>(managerReceiveStreams.size());
                for (Object s : managerReceiveStreams)
                {
                    ReceiveStream receiveStream = (ReceiveStream) s;
                    // FMJ stores the synchronization source (SSRC) identifiers
                    // as 32-bit signed values.
                    int receiveSSRC = (int) receiveStream.getSSRC();

                    if (streamRTPManagerDesc.containsReceiveSSRC(receiveSSRC))
                        receiveStreams.add(receiveStream);
                }
            }
        }

        }
        finally
        {
            lock.unlock();
        }

        return receiveStreams;
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
    public Vector<SendStream> getSendStreams(
            StreamRTPManager streamRTPManager)
    {
        Lock lock = _lock.readLock();
        Vector<SendStream> sendStreams = null;

        lock.lock();
        try
        {

        Vector<?> managerSendStreams = manager.getSendStreams();

        if (managerSendStreams != null)
        {
            sendStreams = new Vector<>(managerSendStreams.size());
            for (SendStreamDesc sendStreamDesc : this.sendStreams)
            {
                if (managerSendStreams.contains(sendStreamDesc.sendStream))
                {
                    SendStream sendStream
                        = sendStreamDesc.getSendStream(streamRTPManager, false);

                    if (sendStream != null)
                        sendStreams.add(sendStream);
                }
            }
        }

        }
        finally
        {
            lock.unlock();
        }

        return sendStreams;
    }

    private StreamRTPManagerDesc getStreamRTPManagerDesc(
            StreamRTPManager streamRTPManager,
            boolean create)
    {
        Lock lock = create ? _lock.writeLock() : _lock.readLock();
        StreamRTPManagerDesc ret = null;

        lock.lock();
        try
        {

        for (StreamRTPManagerDesc s : streamRTPManagers)
        {
            if (s.streamRTPManager == streamRTPManager)
            {
                ret = s;
                break;
            }
        }

        if (ret == null && create)
        {
            ret = new StreamRTPManagerDesc(streamRTPManager);
            streamRTPManagers.add(ret);
        }

        }
        finally
        {
            lock.unlock();
        }

        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<StreamRTPManager> getStreamRTPManagers()
    {
        List<StreamRTPManager> ret = new ArrayList<>(streamRTPManagers.size());

        for (StreamRTPManagerDesc streamRTPManagerDesc : streamRTPManagers)
        {
            ret.add(streamRTPManagerDesc.streamRTPManager);
        }
        return ret;
    }

    public void initialize(
            StreamRTPManager streamRTPManager,
            RTPConnector connector)
    {
        Lock w = _lock.writeLock();
        Lock r = _lock.readLock();
        Lock lock; // the lock which is to eventually be unlocked

        w.lock();
        lock = w;
        try
        {
            if (this.connector == null)
            {
                this.connector = new RTPConnectorImpl(this);
                manager.initialize(this.connector);
            }

            StreamRTPManagerDesc streamRTPManagerDesc
                = getStreamRTPManagerDesc(streamRTPManager, true);

            // We got the connector and the streamRTPManagerDesc. We can now
            // downgrade the lock on this translator.
            r.lock();
            w.unlock();
            lock = r;

            // We're managing access to the streamRTPManagerDesc.
            synchronized (streamRTPManagerDesc)
            {
                RTPConnectorDesc connectorDesc
                    = streamRTPManagerDesc.connectorDesc;

                if (connectorDesc == null
                        || connectorDesc.connector != connector)
                {
                    if (connectorDesc != null)
                    {
                        // The connector is thread-safe.
                        this.connector.removeConnector(connectorDesc);
                    }

                    streamRTPManagerDesc.connectorDesc
                        = connectorDesc
                        = (connector == null)
                            ? null
                            : new RTPConnectorDesc(
                                    streamRTPManagerDesc,
                                    connector);
                }
                if (connectorDesc != null)
                {
                    // The connector is thread-safe.
                    this.connector.addConnector(connectorDesc);
                }
            }
        }
        finally
        {
            lock.unlock();
        }
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
    public void removeReceiveStreamListener(
            StreamRTPManager streamRTPManager,
            ReceiveStreamListener listener)
    {
        StreamRTPManagerDesc desc
            = getStreamRTPManagerDesc(streamRTPManager, false);

        if (desc != null)
            desc.removeReceiveStreamListener(listener);
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
     * Sets the local SSRC for this <tt>RTPTranslatorImpl</tt>.
     * @param localSSRC the SSRC to set.
     */
    public void setLocalSSRC(long localSSRC)
    {
        this.localSSRC = localSSRC;
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
     * Notifies this <tt>ReceiveStreamListener</tt> about a specific event
     * related to a <tt>ReceiveStream</tt>.
     *
     * @param event a <tt>ReceiveStreamEvent</tt> which contains the specifics
     * of the event this <tt>ReceiveStreamListener</tt> is being notified about
     * @see ReceiveStreamListener#update(ReceiveStreamEvent)
     */
    @Override
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
     * @param pkt the packet from the <tt>source</tt> which are to be written into the
     * <tt>destination</tt>
     * @param destination the destination into which <tt>buffer</tt> is to be
     * written
     * @param data <tt>true</tt> for data/RTP or <tt>false</tt> for control/RTCP
     * @return <tt>true</tt> if the writing is to continue or <tt>false</tt> if
     * the writing is to abort
     */
    boolean willWrite(
            StreamRTPManagerDesc source,
            RawPacket pkt,
            StreamRTPManagerDesc destination,
            boolean data)
    {
        MediaStream src
            = (source == null)
                ? null
                : source.streamRTPManager.getMediaStream();
        MediaStream dst = destination.streamRTPManager.getMediaStream();

        return willWrite(src, pkt, dst, data);
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
                : connector.writeControlPayload(controlPayload, destination);
    }

    /**
     * Provides access to the underlying <tt>SSRCCache</tt> that holds
     * statistics information about each SSRC that we receive.
     *
     * @return the underlying <tt>SSRCCache</tt> that holds statistics
     * information about each SSRC that we receive.
     */
    @Override
    public SSRCCache getSSRCCache()
    {
        return ((RTPSessionMgr) manager).getSSRCCache();
    }
}
