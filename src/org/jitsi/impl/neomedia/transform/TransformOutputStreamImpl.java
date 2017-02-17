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

import javax.media.rtp.*;

import org.jitsi.service.neomedia.*;

/**
 * Facilitates {@code OutputDataStream} in the implementation of the interface
 * {@code TransformOutputStream}.
 *
 * @author Lyubomir Marinov
 */
public class TransformOutputStreamImpl
    extends AbstractTransformOutputStream
{
    /**
     * The {@code OutputDataStream} this instance facilitates in the
     * implementation of the interface {@code TransformOutputStream}.
     */
    private final OutputDataStream _outputDataStream;

    /**
     * The view of {@link #_transformer} as a
     * {@code TransformEngineChain.PacketTransformerChain}.
     */
    private TransformEngineChain.PacketTransformerChain _transformerAsChain;

    /**
     * Initializes a new {@code TransformOutputStreamImpl} which is to
     * facilitate a specific {@code OutputDataStream} in the implementation of
     * the interface {@code TransformOutputStream}.
     *
     * @param outputDataStream the {@code OutputDataStream} the new instance is
     * to facilitate in the implementation of the interface
     * {@code TransformOutputStream}
     */
    public TransformOutputStreamImpl(OutputDataStream outputDataStream)
    {
        _outputDataStream = outputDataStream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setTransformer(PacketTransformer transformer)
    {
        if (getTransformer() != transformer)
        {
            super.setTransformer(transformer);

            transformer = getTransformer();
            _transformerAsChain
                = (transformer
                        instanceof TransformEngineChain.PacketTransformerChain)
                    ? (TransformEngineChain.PacketTransformerChain) transformer
                    : null;
        }
    }

    /**
     * Transforms a specified array of {@code RawPacket}s using the
     * {@code PacketTransformer} associated with this instance (if any).
     *
     * @param pkts the {@code RawPacket}s to transform
     * @param after
     * @return an array of {@code RawPacket}s which are the result of the
     * transformation of the specified {@code pkts} using the
     * {@code PacketTransformer} associated with this instance. If there is no
     * {@code PacketTransformer} associated with this instance, returns
     * {@code pkts}.
     */
    protected RawPacket[] transform(RawPacket[] pkts, Object after)
    {
        if (after != null)
        {
            TransformEngineChain.PacketTransformerChain transformerAsChain
                = _transformerAsChain;

            if (transformerAsChain != null)
            {
                return
                    transformerAsChain.transform(pkts, (TransformEngine) after);
            }
        }

        return transform(pkts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int write(byte[] buf, int off, int len)
    {
        return _outputDataStream.write(buf, off, len);
    }
}
