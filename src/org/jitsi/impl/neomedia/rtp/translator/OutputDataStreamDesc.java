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

import org.jitsi.impl.neomedia.*;

import javax.media.rtp.*;
import java.util.*;

/**
 * Describes an <tt>OutputDataStream</tt> associated with an endpoint to which
 * an <tt>RTPTranslatorImpl</tt> is translating.
 *
 * @author Lyubomir Marinov
 */
class OutputDataStreamDesc
{
    /**
     * The endpoint <tt>RTPConnector</tt> which owns {@link #stream}. 
     */
    public final RTPConnectorDesc connectorDesc;

    /**
     * The <tt>OutputDataStream</tt> associated with an endpoint to which an
     * <tt>RTPTranslatorImpl</tt> is translating.
     */
    public final OutputDataStream stream;

    /**
     * Initializes a new <tt>OutputDataStreamDesc</tt> instance which is to
     * describe an endpoint <tt>OutputDataStream</tt> for an
     * <tt>RTPTranslatorImpl</tt>.
     *
     * @param connectorDesc the endpoint <tt>RTPConnector</tt> which owns the
     * specified <tt>stream</tt>
     * @param stream the endpoint <tt>OutputDataStream</tt> to be described by
     * the new instance for an <tt>RTPTranslatorImpl</tt>
     */
    public OutputDataStreamDesc(
            RTPConnectorDesc connectorDesc,
            OutputDataStream stream)
    {
        this.connectorDesc = connectorDesc;
        this.stream = stream;
    }
}
