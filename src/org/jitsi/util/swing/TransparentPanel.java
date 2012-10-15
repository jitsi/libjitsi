/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
