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
package org.jitsi.impl.neomedia.protocol;

import javax.media.protocol.*;

/**
 * Implements a <tt>SourceStream</tt> which wraps a specific
 * <tt>SourceStream</tt>.
 *
 * @param <T> the very type of the <tt>SourceStream</tt> wrapped by
 * <tt>SourceStreamDelegate</tt>
 *
 * @author Lyubomir Marinov
 */
public class SourceStreamDelegate<T extends SourceStream>
    implements SourceStream
{

    /**
     * The <tt>SourceStreamDelegate</tt> wrapped by this instance.
     */
    protected final T stream;

    /**
     * Initializes a new <tt>SourceStreamDelegate</tt> instance which is to
     * wrap a specific <tt>SourceStream</tt>.
     *
     * @param stream the <tt>SourceStream</tt> the new instance is to
     * wrap
     */
    public SourceStreamDelegate(T stream)
    {
        this.stream = stream;
    }

    /**
     * Implements {@link SourceStream#endOfStream()}. Delegates to the wrapped
     * <tt>SourceStream</tt>.
     *
     * @return <tt>true</tt> if the wrapped <tt>SourceStream</tt> has reached
     * the end the content it makes available
     */
    public boolean endOfStream()
    {
        return stream.endOfStream();
    }

    /**
     * Implements {@link SourceStream#getContentDescriptor()}. Delegates to the
     * wrapped <tt>SourceStream</tt>.
     *
     * @return a <tt>ContentDescriptor</tt> which describes the content made
     * available by the wrapped <tt>SourceStream</tt>
     */
    public ContentDescriptor getContentDescriptor()
    {
        return stream.getContentDescriptor();
    }

    /**
     * Implements {@link SourceStream#getContentLength()}. Delegates to the
     * wrapped <tt>SourceStream</tt>.
     *
     * @return the length of the content made available by the wrapped
     * <tt>SourceStream</tt>
     */
    public long getContentLength()
    {
        return stream.getContentLength();
    }

    /**
     * Implements {@link Controls#getControl(String)}. Delegates to the wrapped
     * <tt>SourceStream</tt>.
     *
     * @param controlType a <tt>String</tt> value which specifies the type of
     * the control to be retrieved
     * @return an <tt>Object</tt> which represents the control of the wrapped
     * <tt>SourceStream</tt> of the specified type if such a control is
     * available; otherwise, <tt>null</tt>
     */
    public Object getControl(String controlType)
    {
        return stream.getControl(controlType);
    }

    /**
     * Implements {@link Controls#getControls()}. Delegates to the wrapped
     * <tt>SourceStream</tt>.
     *
     * @return an array of <tt>Object</tt>s which represent the controls
     * available for the wrapped <tt>SourceStream</tt>
     */
    public Object[] getControls()
    {
        return stream.getControls();
    }
}
