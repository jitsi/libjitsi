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
package org.jitsi.util.function;

import org.jitsi.service.neomedia.*;

/**
 * @author George Politis
 */
public abstract class RawPacketTransformation
    extends AbstractFunction<RawPacket, RawPacket>
{
    /**
     * An identity {@link RawPacketTransformation} that returns the original
     * packet.
     */
    public final static RawPacketTransformation identity
        = new RawPacketTransformation()
    {
        @Override
        public RawPacket apply(RawPacket rawPacket)
        {
            return rawPacket;
        }
    };
}
