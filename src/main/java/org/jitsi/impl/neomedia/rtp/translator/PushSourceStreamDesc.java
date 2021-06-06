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

import javax.media.protocol.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Describes a <tt>PushSourceStream</tt> associated with an endpoint from which
 * an <tt>RTPTranslatorImpl</tt> is translating.
 *
 * @author Lyubomir Marinov
 */
class PushSourceStreamDesc
{
    /**
     * The endpoint <tt>RTPConnector</tt> which owns {@link #stream}.
     */
    public final RTPConnectorDesc connectorDesc;

    /**
     * <tt>true</tt> if this instance represents a data/RTP stream or
     * <tt>false</tt> if this instance represents a control/RTCP stream
     */
    public final boolean data;

    /**
     * The <tt>PushSourceStream</tt> associated with an endpoint from which an
     * <tt>RTPTranslatorImpl</tt> is translating.
     */
    public final PushSourceStream stream;

    /**
     * The <tt>PushBufferStream</tt> control over {@link #stream}, if available,
     * which may provide Buffer properties other than <tt>data</tt>,
     * <tt>length</tt> and <tt>offset</tt> such as <tt>flags</tt>.
     */
    public final PushBufferStream streamAsPushBufferStream;

    /**
     * Initializes a new <tt>PushSourceStreamDesc</tt> instance which is to
     * describe a specific endpoint <tt>PushSourceStream</tt> for an
     * <tt>RTPTranslatorImpl</tt>.
     *
     * @param connectorDesc the endpoint <tt>RTPConnector</tt> which owns the
     * specified <tt>stream</tt>
     * @param stream the endpoint <tt>PushSourceStream</tt> to be described by
     * the new instance for an <tt>RTPTranslatorImpl</tt>
     * @param data <tt>true</tt> if the specified <tt>stream</tt> is a data/RTP
     * stream or <tt>false</tt> if the specified <tt>stream</tt> is a
     * control/RTCP stream
     */
    public PushSourceStreamDesc(
            RTPConnectorDesc connectorDesc,
            PushSourceStream stream,
            boolean data)
    {
        this.connectorDesc = connectorDesc;
        this.stream = stream;
        this.data = data;

        streamAsPushBufferStream
            = (PushBufferStream)
                stream.getControl(
                        AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME);
    }
}
