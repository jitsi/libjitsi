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
package org.jitsi.impl.neomedia.jmfext.media.protocol.imgstreaming;

import java.awt.*;

import javax.media.*;
import javax.media.control.*;

import org.jitsi.impl.neomedia.control.*;
import org.jitsi.impl.neomedia.jmfext.media.protocol.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the purposes of
 * image and desktop streaming.
 *
 * @author Sebastien Vincent
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class DataSource
    extends AbstractVideoPullBufferCaptureDevice
{
    /**
     * The <tt>ImgStreamingControl</tt> implementation which allows controlling
     * this <tt>DataSource</tt> through the "standard" FMJ/JMF
     * <tt>javax.media.Control</tt> means.
     */
    private final ImgStreamingControl imgStreamingControl
        = new ImgStreamingControl()
        {
            public Component getControlComponent()
            {
                /*
                 * This javax.media.Control implementation provides only
                 * programmatic control and not UI control.
                 */
                return null;
            }

            public void setOrigin(
                    int streamIndex,
                    int displayIndex,
                    int x, int y)
            {
                DataSource.this.setOrigin(streamIndex, displayIndex, x, y);
            }
        };

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
    }

    /**
     * Initializes a new <tt>DataSource</tt> instance from a specific
     * <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to initialize the new instance
     * from
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);
    }

    /**
     * Creates a new <tt>PullBufferStream</tt> which is to be at a specific
     * zero-based index in the list of streams of this
     * <tt>PullBufferDataSource</tt>. The <tt>Format</tt>-related information of
     * the new instance is to be abstracted by a specific
     * <tt>FormatControl</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PullBufferStream</tt>
     * in the list of streams of this <tt>PullBufferDataSource</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the
     * <tt>Format</tt>-related information of the new instance
     * @return a new <tt>PullBufferStream</tt> which is to be at the specified
     * <tt>streamIndex</tt> in the list of streams of this
     * <tt>PullBufferDataSource</tt> and which has its <tt>Format</tt>-related
     * information abstracted by the specified <tt>formatControl</tt>
     * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
     */
    @Override
    protected ImageStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        /*
         * full desktop: remainder => index
         * part of desktop: remainder => index,x,y
         */
        String remainder = getLocator().getRemainder();
        String split[] = remainder.split(",");
        int dispayIndex;
        int x;
        int y;

        if ((split != null) && (split.length > 1))
        {
            dispayIndex = Integer.parseInt(split[0]);
            x = Integer.parseInt(split[1]);
            y = Integer.parseInt(split[2]);
        }
        else
        {
            dispayIndex = Integer.parseInt(remainder);
            x = 0;
            y = 0;
        }

        ImageStream stream = new ImageStream(this, formatControl);

        stream.setDisplayIndex(dispayIndex);
        stream.setOrigin(x, y);

        return stream;
    }

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to
     * be retrieved
     * @return an <tt>Object</tt> which represents the control of the specified
     * type available for this instance if such a control is indeed available;
     * otherwise, <tt>null</tt>
     */
    @Override
    public Object getControl(String controlType)
    {
        /*
         * TODO As a matter of fact, we have to override getControls() and not
         * getControl(String). However, overriding getControls() is much more
         * complex and the ImgStreamingControl is too obscure. Besides, the
         * ImgStreamingControl implementation of this DataSource does not
         * provide UI control so it makes no sense for the caller to try to get
         * it through getControls().
         */
        if (ImgStreamingControl.class.getName().equals(controlType))
            return imgStreamingControl;
        else
            return super.getControl(controlType);
    }

    /**
     * Set the display index and the origin of the <tt>ImageStream</tt>
     * associated with a specific index in this <tt>DataSource</tt>.
     *
     * @param streamIndex the index in this <tt>DataSource</tt> of the
     * <tt>ImageStream</tt> to set the display index and the origin of
     * @param displayIndex the display index to set on the specified
     * <tt>ImageStream</tt>
     * @param x the x coordinate of the origin to set on the specified
     * <tt>ImageStream</tt>
     * @param y the y coordinate of the origin to set on the specified
     * <tt>ImageStream</tt>
     */
    public void setOrigin(int streamIndex, int displayIndex, int x, int y)
    {
        synchronized (getStreamSyncRoot())
        {
            Object[] streams = streams();

            if ((streams != null) && (streamIndex < streams.length))
            {
                ImageStream stream = (ImageStream) streams[streamIndex];

                if (stream != null)
                {
                    stream.setDisplayIndex(displayIndex);
                    stream.setOrigin(x, y);
                }
            }
        }
    }
}
