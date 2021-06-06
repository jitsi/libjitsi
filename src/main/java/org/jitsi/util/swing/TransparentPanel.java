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
package org.jitsi.util.swing;

import java.awt.*;

import javax.swing.*;

/**
 * Represents a <tt>JPanel</tt> which sets its <tt>opaque</tt> property to
 * <tt>false</tt> during its initialization.
 *
 * @author Yana Stamcheva
 */
public class TransparentPanel
    extends JPanel
{
    private static final long serialVersionUID = 0L;

    /**
     * Initializes a new <tt>TransparentPanel</tt> instance.
     */
    public TransparentPanel()
    {
        setOpaque(false);
    }

    /**
     * Initializes a new <tt>TransparentPanel</tt> instance which is to use a
     * specific <tt>LayoutManager</tt>.
     *
     * @param layout the <tt>LayoutManager</tt> to be used by the new instance
     */
    public TransparentPanel(LayoutManager layout)
    {
        super(layout);

        setOpaque(false);
    }
}
