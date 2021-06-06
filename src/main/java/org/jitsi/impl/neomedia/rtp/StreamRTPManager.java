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
package org.jitsi.impl.neomedia.rtp;

import java.io.*;
import java.util.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.service.neomedia.*;

/**
 * Implements the <tt>RTPManager</tt> interface as used by a
 * <tt>MediaStream</tt>.
 *
 * @author Lyubomir Marinov
 */
public class StreamRTPManager
{
    /**
     * The <tt>MediaStream</tt> that uses this <tt>StreamRTPManager</tt>
     */
    private final MediaStream stream;

    /**
     * The <tt>RTPManager</tt> this instance is to delegate to when it is not
     * attached to an <tt>RTPTranslator</tt>.
     */
    private final RTPManager manager;

    /**
     * The <tt>RTPTranslator</tt> which this instance is attached to and which
     * forwards the RTP and RTCP flows of the <tt>MediaStream</tt> associated
     * with this instance to other <tt>MediaStream</tt>s.
     */
    private final RTPTranslatorImpl translator;

    /**
     * Initializes a new <tt>StreamRTPManager</tt> instance which is,
     * optionally, attached to a specific <tt>RTPTranslator</tt> which is to
     * forward the RTP and RTCP flows of the associated <tt>MediaStream</tt> to
     * other <tt>MediaStream</tt>s.
     *
     * @param stream the <tt>MediaStream</tt> that created this
     * <tt>StreamRTPManager</tt>.
     * @param translator the <tt>RTPTranslator</tt> to attach the new instance
     * to or <tt>null</tt> if the new instance is to not be attached to any
     * <tt>RTPTranslator</tt>
     */
    public StreamRTPManager(MediaStream stream, RTPTranslator translator)
    {
        this.stream = stream;
        this.translator = (RTPTranslatorImpl) translator;

        manager = (this.translator == null) ? RTPManager.newInstance() : null;
    }

    public void addFormat(Format format, int payloadType)
    {
        if (translator == null)
            manager.addFormat(format, payloadType);
        else
            translator.addFormat(this, format, payloadType);
    }

    public void addReceiveStreamListener(ReceiveStreamListener listener)
    {
        if (translator == null)
            manager.addReceiveStreamListener(listener);
        else
            translator.addReceiveStreamListener(this, listener);
    }

    public void addRemoteListener(RemoteListener listener)
    {
        if (translator == null)
            manager.addRemoteListener(listener);
        else
            translator.addRemoteListener(this, listener);
    }

    public void addSendStreamListener(SendStreamListener listener)
    {
        if (translator == null)
            manager.addSendStreamListener(listener);
        else
            translator.addSendStreamListener(this, listener);
    }

    public void addSessionListener(SessionListener listener)
    {
        if (translator == null)
            manager.addSessionListener(listener);
        else
            translator.addSessionListener(this, listener);
    }

    public SendStream createSendStream(DataSource dataSource, int streamIndex)
        throws IOException,
               UnsupportedFormatException
    {
        if (translator == null)
            return manager.createSendStream(dataSource, streamIndex);
        else
            return translator.createSendStream(this, dataSource, streamIndex);
    }

    public void dispose()
    {
        if (translator == null)
            manager.dispose();
        else
            translator.dispose(this);
    }

    /**
     * Gets a control of a specific type over this instance. Invokes
     * {@link #getControl(String)}.
     *
     * @param controlType a <tt>Class</tt> which specifies the type of the
     * control over this instance to get
     * @return a control of the specified <tt>controlType</tt> over this
     * instance if this instance supports such a control; otherwise,
     * <tt>null</tt>
     */
    @SuppressWarnings("unchecked")
    public <T> T getControl(Class<T> controlType)
    {
        return (T) getControl(controlType.getName());
    }

    /**
     * Gets a control of a specific type over this instance.
     *
     * @param controlType a <tt>String</tt> which specifies the type (i.e. the
     * name of the class) of the control over this instance to get
     * @return a control of the specified <tt>controlType</tt> over this
     * instance if this instance supports such a control; otherwise,
     * <tt>null</tt>
     */
    public Object getControl(String controlType)
    {
        if (translator == null)
            return manager.getControl(controlType);
        else
            return translator.getControl(this, controlType);
    }

    public GlobalReceptionStats getGlobalReceptionStats()
    {
        if (translator == null)
            return manager.getGlobalReceptionStats();
        else
            return translator.getGlobalReceptionStats(this);
    }

    public GlobalTransmissionStats getGlobalTransmissionStats()
    {
        if (translator == null)
            return manager.getGlobalTransmissionStats();
        else
            return translator.getGlobalTransmissionStats(this);
    }

    public long getLocalSSRC()
    {
        if (translator == null)
        {
            return
                ((net.sf.fmj.media.rtp.RTPSessionMgr) manager).getLocalSSRC();
        }
        else
            return translator.getLocalSSRC(this);
    }

    /**
     * Returns the <tt>MediaStream</tt> that uses this <tt>StreamRTPManager</tt>
     * @return the <tt>MediaStream</tt> that uses this <tt>StreamRTPManager</tt>
     */
    public MediaStream getMediaStream()
    {
        return stream;
    }

    @SuppressWarnings("rawtypes")
    public Vector getReceiveStreams()
    {
        if (translator == null)
            return manager.getReceiveStreams();
        else
            return translator.getReceiveStreams(this);
    }

    @SuppressWarnings("rawtypes")
    public Vector getSendStreams()
    {
        if (translator == null)
            return manager.getSendStreams();
        else
            return translator.getSendStreams(this);
    }

    public void initialize(RTPConnector connector)
    {
        if (translator == null)
            manager.initialize(connector);
        else
            translator.initialize(this, connector);
    }

    public void removeReceiveStreamListener(ReceiveStreamListener listener)
    {
        if (translator == null)
            manager.removeReceiveStreamListener(listener);
        else
            translator.removeReceiveStreamListener(this, listener);
    }

    public void removeRemoteListener(RemoteListener listener)
    {
        if (translator == null)
            manager.removeRemoteListener(listener);
        else
            translator.removeRemoteListener(this, listener);
    }

    public void removeSendStreamListener(SendStreamListener listener)
    {
        if (translator == null)
            manager.removeSendStreamListener(listener);
        else
            translator.removeSendStreamListener(this, listener);
    }

    public void removeSessionListener(SessionListener listener)
    {
        if (translator == null)
            manager.removeSessionListener(listener);
        else
            translator.removeSessionListener(this, listener);
    }

    /**
     * Sets the <tt>SSRCFactory</tt> to be utilized by this instance to generate
     * new synchronization source (SSRC) identifiers.
     *
     * @param ssrcFactory the <tt>SSRCFactory</tt> to be utilized by this
     * instance to generate new synchronization source (SSRC) identifiers or
     * <tt>null</tt> if this instance is to employ internal logic to generate
     * new synchronization source (SSRC) identifiers
     */
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        if (translator == null)
        {
            RTPManager m = this.manager;

            if (m instanceof
                    org.jitsi.impl.neomedia.jmfext.media.rtp.RTPSessionMgr)
            {
                org.jitsi.impl.neomedia.jmfext.media.rtp.RTPSessionMgr sm
                   = (org.jitsi.impl.neomedia.jmfext.media.rtp.RTPSessionMgr) m;

                sm.setSSRCFactory(ssrcFactory);
            }
        }
        else
        {
            translator.setSSRCFactory(ssrcFactory);
        }

    }
}
