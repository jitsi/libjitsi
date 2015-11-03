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

/**
 * Wraps a <tt>TransformerEngine</tt> (allows the wrapped instance to be swapped
 * without modifications to the <tt>RTPConnector</tt>'s transformer engine
 * chain.
 *
 * @author George Politis
 */
public class TransformEngineWrapper<T extends TransformEngine>
    implements TransformEngine
{
    /**
     * The wrapped instance.
     */
    private T wrapped;

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        T wrapped = getWrapped();

        return wrapped == null ? null : wrapped.getRTCPTransformer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        T wrapped = getWrapped();

        return wrapped == null ? null : wrapped.getRTPTransformer();
    }

    public T getWrapped()
    {
        return wrapped;
    }

    public void setWrapped(T wrapped)
    {
        this.wrapped = wrapped;
    }
}