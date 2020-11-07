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

import javax.media.protocol.*;
import javax.media.rtp.*;

import org.jitsi.impl.neomedia.rtp.*;

/**
 * Describes a <tt>SendStream</tt> created by the <tt>RTPManager</tt> of an
 * <tt>RTPTranslatorImpl</tt>. Contains information about the
 * <tt>DataSource</tt> and its stream index from which the <tt>SendStream</tt>
 * has been created so that various <tt>StreamRTPManager</tt> receive different
 * views of one and the same <tt>SendStream</tt>.
 *
 * @author Lyubomir Marinov
 */
class SendStreamDesc
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

    private final RTPTranslatorImpl translator;

    public SendStreamDesc(
            RTPTranslatorImpl translator,
            DataSource dataSource, int streamIndex, SendStream sendStream)
    {
        this.translator = translator;
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
            translator.closeSendStream(this);
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
