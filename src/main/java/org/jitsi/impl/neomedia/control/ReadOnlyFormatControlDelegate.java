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
package org.jitsi.impl.neomedia.control;

import java.awt.*;

import javax.media.*;
import javax.media.control.*;

/**
 * Represents a wrapper of a specific <tt>FormatControl</tt> instance which does
 * not allow setting its format using {@link FormatControl#setFormat(Format)}.
 *
 * @author Lubomir Marinov
 */
public class ReadOnlyFormatControlDelegate
    implements FormatControl
{

    /**
     * The <tt>FormatControl</tt> wrapped by this instance.
     */
    private final FormatControl formatControl;

    /**
     * Initializes a new <tt>ReadOnlyFormatControlDelegate</tt> instance which
     * is to wrap a specific <tt>FormatControl</tt> in order to prevent calls to
     * its {@link FormatControl#setFormat(Format)}.
     *
     * @param formatControl the <tt>FormatControl</tt> which is to have calls to
     * its <tt>FormatControl#setFormat(Format)</tt> prevented
     */
    public ReadOnlyFormatControlDelegate(FormatControl formatControl)
    {
        this.formatControl = formatControl;
    }

    /**
     * Implements {@link Control#getControlComponent()}.
     *
     * @return a <tt>Component</tt> which represents UI associated with this
     * instance if any; otherwise, <tt>null</tt>
     */
    public Component getControlComponent()
    {
        return formatControl.getControlComponent();
    }

    /**
     * Gets the <tt>Format</tt> of the owner of this <tt>FormatControl</tt>.
     * Delegates to the wrapped <tt>FormatControl</tt>.
     *
     * @return the <tt>Format</tt> of the owner of this <tt>FormatControl</tt>
     */
    public Format getFormat()
    {
        return formatControl.getFormat();
    }

    /**
     * Gets the <tt>Format</tt>s supported by the owner of this
     * <tt>FormatControl</tt>. Delegates to the wrapped <tt>FormatControl</tt>.
     *
     * @return an array of <tt>Format</tt>s supported by the owner of this
     * <tt>FormatControl</tt>
     */
    public Format[] getSupportedFormats()
    {
        return formatControl.getSupportedFormats();
    }

    /**
     * Implements {@link FormatControl#isEnabled()}.
     *
     * @return <tt>true</tt> if this track is enabled; otherwise, <tt>false</tt>
     */
    public boolean isEnabled()
    {
        return formatControl.isEnabled();
    }

    /**
     * Implements {@link FormatControl#setEnabled(boolean)}.
     *
     * @param enabled <tt>true</tt> if this track is to be enabled; otherwise,
     * <tt>false</tt>
     */
    public void setEnabled(boolean enabled)
    {
        // Ignore the request because this instance is read-only.
    }

    /**
     * Implements {@link FormatControl#setFormat(Format)}. Not supported and
     * just returns the currently set format if the specified <tt>Format</tt> is
     * supported and <tt>null</tt> if it is not supported.
     *
     * @param format the <tt>Format</tt> to be set on this instance
     * @return the currently set <tt>Format</tt> after the attempt to set it on
     * this instance if <tt>format</tt> is supported by this instance and
     * regardless of whether it was actually set; <tt>null</tt> if
     * <tt>format</tt> is not supported by this instance
     */
    public Format setFormat(Format format)
    {
        return AbstractFormatControl.setFormat(this, format);
    }
}
