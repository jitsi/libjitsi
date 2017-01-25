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
package org.jitsi.impl.neomedia.codec;

import org.jitsi.service.neomedia.*;

/**
 * Represents a RED block.
 *
 * @author George Politis
 */
public class REDBlock
    implements ByteArrayBuffer
{
    /**
     *The byte buffer that holds the RED block.
     */
    private final byte[] buf;

    /**
     * The length in the buffer of this RED block.
     */
    private int len;

    /**
     * The offset in the buffer where this RED block starts.
     */
    private int off;

    /**
     * The payload type of this RED block.
     */
    private final byte pt;

    /**
     * Ctor.
     *
     * @param off the offset in the buffer where this RED block starts
     * @param len the length of this RED block
     * @param pt the payload type of this RED block
     */
    public REDBlock(byte[] buf, int off, int len, byte pt)
    {
        this.buf = buf;
        this.pt = pt;
        this.off = off;
        this.len = len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getBuffer()
    {
        return buf;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLength()
    {
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLength(int len)
    {
        this.len = len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOffset(int off)
    {
        this.off = off;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInvalid()
    {
        return buf == null || buf.length < off + len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOffset()
    {
        return off;
    }

    /**
     * Gets the payload type of this RED block.
     *
     * @return the payload type of this RED block
     */
    public byte getPayloadType()
    {
        return pt;
    }
}
