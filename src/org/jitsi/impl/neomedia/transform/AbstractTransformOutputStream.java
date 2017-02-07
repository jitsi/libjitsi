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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.service.neomedia.*;

/**
 * Facilitates the implementation of the interface
 * {@code TransformOutputStream}.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractTransformOutputStream
    implements TransformOutputStream
{
    /**
     * The {@code PacketTransformer} used by this instance to transform
     * {@code RawPacket}s.
     */
    private PacketTransformer _transformer;

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getTransformer()
    {
        return _transformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTransformer(PacketTransformer transformer)
    {
        _transformer = transformer;
    }

    /**
     * Transforms a specified array of {@code RawPacket}s using the
     * {@code PacketTransformer} associated with this instance (if any).
     *
     * @param pkts the {@code RawPacket}s to transform
     * @return an array of {@code RawPacket}s which are the result of the
     * transformation of the specified {@code pkts} using the
     * {@code PacketTransformer} associated with this instance. If there is no
     * {@code PacketTransformer} associated with this instance, returns
     * {@code pkts}.
     */
    protected RawPacket[] transform(RawPacket[] pkts)
    {
        PacketTransformer transformer = getTransformer();

        if (transformer != null)
        {
            pkts = transformer.transform(pkts);
        }
        return pkts;
    }
}
