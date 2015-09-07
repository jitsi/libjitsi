/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.jmfext.media.renderer.video;

import java.awt.*;
import java.awt.image.*;

import javax.media.Buffer;

/**
 * Canvas that draws the video buffer using Java's built-in <tt>Graphics</tt>.
 * 
 * @author Ingo Bauersachs
 */
public class Java2DRendererVideoComponent
    extends Canvas
{
    private static final long serialVersionUID = -3229966946026776451L;
    private MemoryImageSource mis;
    private Dimension size = new Dimension(0, 0);

    /**
     * Draws the current image as prepared by the
     * {@link #process(Buffer, Dimension)}
     * 
     * @param g the graphics context to draw to.
     */
    @Override
    public void paint(Graphics g)
    {
        if (this.mis != null)
        {
            g.drawImage(this.createImage(mis), 0, 0,
                getWidth(), getHeight(), null);
        }
    }

    /**
     * Updates the image to be drawn on the graphics context.
     * 
     * @param buffer the RAW image data.
     * @param size the dimension of the image in the buffer.
     */
    void process(Buffer buffer, Dimension size)
    {
        if (mis == null || !this.size.equals(size))
        {
            this.size = size;
            mis =
                new MemoryImageSource(size.width, size.height,
                    (int[]) buffer.getData(), buffer.getOffset(), size.width);
        }
        else
        {
            mis.newPixels((int[]) buffer.getData(), ColorModel.getRGBdefault(),
                buffer.getOffset(), size.width);
        }

        this.repaint();
    }

}
