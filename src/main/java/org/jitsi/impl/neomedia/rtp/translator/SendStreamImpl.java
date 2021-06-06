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
import java.lang.reflect.*;

import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.rtcp.*;

import org.jitsi.impl.neomedia.rtp.*;

/**
 * Implements a <tt>SendStream</tt> which is an endpoint-specific view of an
 * actual <tt>SendStream</tt> of the <tt>RTPManager</tt> of an
 * <tt>RTPTranslatorImpl</tt>. When the last endpoint-specific view of an actual
 * <tt>SendStream</tt> is closed, the actual <tt>SendStream</tt> is closed.
 *
 * @author Lyubomir Marinov
 */
class SendStreamImpl
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

    @Override
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

    @Override
    public DataSource getDataSource()
    {
        return sendStreamDesc.sendStream.getDataSource();
    }

    @Override
    public Participant getParticipant()
    {
        return sendStreamDesc.sendStream.getParticipant();
    }

    @Override
    public SenderReport getSenderReport()
    {
        return sendStreamDesc.sendStream.getSenderReport();
    }

    @Override
    public TransmissionStats getSourceTransmissionStats()
    {
        return sendStreamDesc.sendStream.getSourceTransmissionStats();
    }

    @Override
    public long getSSRC()
    {
        return sendStreamDesc.sendStream.getSSRC();
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public int setBitRate(int bitRate)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Not implemented because there are currently no uses of the underlying
     * functionality.
     */
    @Override
    public void setSourceDescription(SourceDescription[] sourceDescription)
    {
        // TODO Auto-generated method stub
    }

    @Override
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

    @Override
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
