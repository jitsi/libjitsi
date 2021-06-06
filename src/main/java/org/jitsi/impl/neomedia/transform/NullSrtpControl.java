/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.*;

/**
 * Implements a no-op {@link SrtpControl}, i.e. one which does not perform
 * SRTP and does not have a transform engine.
 *
 * @author Boris Grozev
 */
public class NullSrtpControl
    extends AbstractSrtpControl<SrtpControl.TransformEngine>
{
    /**
     * Initializes a new {@link NullSrtpControl} instance.
     */
    public NullSrtpControl()
    {
        super(SrtpControlType.NULL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getSecureCommunicationStatus()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requiresSecureSignalingTransport()
    {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConnector(AbstractRTPConnector connector)
    {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MediaType mediaType)
    {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransformEngine createTransformEngine()
    {
        return null;
    }
}
