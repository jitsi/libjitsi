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
package org.jitsi.impl.neomedia.conference;

import java.lang.ref.*;

import javax.media.*;
import javax.media.protocol.*;

/**
 * Describes additional information about a specific input audio
 * <tt>SourceStream</tt> of an <tt>AudioMixer</tt> so that the
 * <tt>AudioMixer</tt> can, for example, quickly discover the output
 * <tt>AudioMixingPushBufferDataSource</tt> in the mix of which the contribution
 * of the <tt>SourceStream</tt> is to not be included.
 * <p>
 * Private to <tt>AudioMixer</tt> and <tt>AudioMixerPushBufferStream</tt> but
 * extracted into its own file for the sake of clarity.
 *
 * @author Lyubomir Marinov
 */
class InStreamDesc
{
    /**
     * The <tt>Buffer</tt> into which media data is to be read from
     * {@link #inStream}.
     */
    private SoftReference<Buffer> buffer;

    /**
     * The <tt>DataSource</tt> which created the <tt>SourceStream</tt> described
     * by this instance and additional information about it.
     */
    public final InDataSourceDesc inDataSourceDesc;

    /**
     * The <tt>SourceStream</tt> for which additional information is described
     * by this instance.
     */
    private SourceStream inStream;

    /**
     * Initializes a new <tt>InStreamDesc</tt> instance which is to describe
     * additional information about a specific input audio <tt>SourceStream</tt>
     * of an <tt>AudioMixer</tt>. Associates the specified <tt>SourceStream</tt>
     * with the <tt>DataSource</tt> which created it and additional information
     * about it.
     *
     * @param inStream a <tt>SourceStream</tt> for which additional
     * information is to be described by the new instance
     * @param inDataSourceDesc the <tt>DataSource</tt> which created the
     * <tt>SourceStream</tt> to be described by the new instance and additional
     * information about it
     */
    public InStreamDesc(
            SourceStream inStream,
            InDataSourceDesc inDataSourceDesc)
    {
        this.inStream = inStream;
        this.inDataSourceDesc = inDataSourceDesc;
    }

    /**
     * Gets the <tt>Buffer</tt> into which media data is to be read from the
     * <tt>SourceStream</tt> described by this instance.
     *
     * @param create the indicator which determines whether the <tt>Buffer</tt>
     * is to be created in case it does not exist
     * @return the <tt>Buffer</tt> into which media data is to be read from the
     * <tt>SourceStream</tt> described by this instance
     */
    public Buffer getBuffer(boolean create)
    {
        Buffer buffer = (this.buffer == null) ? null : this.buffer.get();

        if ((buffer == null) && create)
        {
            buffer = new Buffer();
            setBuffer(buffer);
        }
        return buffer;
    }

    /**
     * Gets the <tt>SourceStream</tt> described by this instance.
     *
     * @return the <tt>SourceStream</tt> described by this instance
     */
    public SourceStream getInStream()
    {
        return inStream;
    }

    /**
     * Gets the <tt>AudioMixingPushBufferDataSource</tt> in which the mix
     * contribution of the <tt>SourceStream</tt> described by this instance is
     * to not be included.
     *
     * @return the <tt>AudioMixingPushBufferDataSource</tt> in which the mix
     * contribution of the <tt>SourceStream</tt> described by this instance is
     * to not be included
     */
    public AudioMixingPushBufferDataSource getOutDataSource()
    {
        return inDataSourceDesc.outDataSource;
    }

    /**
     * Sets the <tt>Buffer</tt> into which media data is to be read from the
     * <tt>SourceStream</tt> described by this instance.
     *
     * @param buffer the <tt>Buffer</tt> into which media data is to be read
     * from the <tt>SourceStream</tt> described by this instance
     */
    public void setBuffer(Buffer buffer)
    {
        this.buffer
            = (buffer == null) ? null : new SoftReference<Buffer>(buffer);
    }

    /**
     * Sets the <tt>SourceStream</tt> to be described by this instance.
     *
     * @param inStream the <tt>SourceStream</tt> to be described by this
     * instance
     */
    public void setInStream(SourceStream inStream)
    {
        if (this.inStream != inStream)
        {
            this.inStream = inStream;

            /*
             * Since the inStream has changed, one may argue that the Buffer
             * of the old value is not optimal for the new value.
             */
            setBuffer(null);
        }
    }
}
