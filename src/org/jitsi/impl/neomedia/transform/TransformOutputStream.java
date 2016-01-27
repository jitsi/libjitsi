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

/**
 * Defines the public application programming interface (API) of an
 * {@link OutputDataStream} which applies transformations via a
 * {@link PacketTransformer} to the data written into it.
 *
 * @author Lyubomir Marinov
 */
public interface TransformOutputStream
    extends OutputDataStream
{
    /**
     * Gets the {@code PacketTransformer} used by this instance to transform
     * {@code RawPacket}s.
     *
     * @return the {@code PacketTransformer} used by this instance to transform
     * {@code RawPacket}s
     */
    public PacketTransformer getTransformer();

    /**
     * Sets the {@code PacketTransformer} to be used by this instance to
     * transform {@code RawPacket}s.
     *
     * @param transformer the {@code PacketTransformer} to be used by this
     * instance to transform {@code RawPacket}s
     */
    public void setTransformer(PacketTransformer transformer);
}
