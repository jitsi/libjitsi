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
package org.jitsi.impl.neomedia.imgstreaming;

import java.awt.geom.*;
import java.awt.image.*;

/**
 * Provides utility functions used by the <tt>imgstreaming</tt> package(s).
 *
 * @author Sebastien Vincent
 */
public class ImgStreamingUtils
{
    /**
     * Get a scaled <tt>BufferedImage</tt>.
     *
     * Mainly inspired by:
     * http://java.developpez.com/faq/gui/?page=graphique_general_images
     * #GRAPHIQUE_IMAGE_redimensionner
     *
     * @param src source image
     * @param width width of scaled image
     * @param height height of scaled image
     * @param type <tt>BufferedImage</tt> type
     * @return scaled <tt>BufferedImage</tt>
     */
    public static BufferedImage getScaledImage(BufferedImage src,
                                               int width,
                                               int height,
                                               int type)
    {
        double scaleWidth = width / ((double)src.getWidth());
        double scaleHeight = height / ((double)src.getHeight());
        AffineTransform tx = new AffineTransform();

        // Skip rescaling if input and output size are the same.
        if ((Double.compare(scaleWidth, 1) != 0)
                || (Double.compare(scaleHeight, 1) != 0))
            tx.scale(scaleWidth, scaleHeight);

        AffineTransformOp op
            = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage dst = new BufferedImage(width, height, type);

        return op.filter(src, dst);
    }

    /**
     * Get raw bytes from ARGB <tt>BufferedImage</tt>.
     *
     * @param src ARGB <tt>BufferImage</tt>
     * @param output output buffer, if not null and if its length is at least
     * image's (width * height) * 4, method will put bytes in it.
     * @return raw bytes or null if src is not an ARGB
     * <tt>BufferedImage</tt>
     */
    public static byte[] getImageBytes(BufferedImage src, byte output[])
    {
        if(src.getType() != BufferedImage.TYPE_INT_ARGB)
            throw new IllegalArgumentException("src.type");

        WritableRaster raster = src.getRaster();
        int width = src.getWidth();
        int height = src.getHeight();
        int size = width * height * 4;
        int off = 0;
        int pixel[] = new int[4];
        byte data[] = null;

        if(output == null || output.length < size)
        {
            /* allocate our bytes array */
            data = new byte[size];
        }
        else
        {
            /* use output */
            data = output;
        }

        for(int y = 0 ; y < height ; y++)
            for(int x = 0 ; x < width ; x++)
            {
                raster.getPixel(x, y, pixel);
                data[off++] = (byte)pixel[0];
                data[off++] = (byte)pixel[1];
                data[off++] = (byte)pixel[2];
                data[off++] = (byte)pixel[3];
            }

        return data;
    }
}
