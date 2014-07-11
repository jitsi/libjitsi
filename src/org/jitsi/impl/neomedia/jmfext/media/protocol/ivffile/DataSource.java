/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
 
package org.jitsi.impl.neomedia.jmfext.media.protocol.ivffile;

import java.io.IOException;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;

import org.jitsi.impl.neomedia.jmfext.media.protocol.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * Implements <tt>CaptureDevice</tt> and <tt>DataSource</tt> for the purposes of
 * ivf (vp8 raw file, extracted from webm) file streaming.
 *
 * @author Thomas Kuntz
 */
public class DataSource
    extends AbstractVideoPullBufferCaptureDevice
{
    /**
     * The format of the VP8 video contained in the IVF file.
     */
    private Format[] SUPPORTED_FORMATS = new Format[1];
    
    /**
     * The location of the IVF file this <tt>DataSource</tt> will use for the
     * VP8 frames.
     */
    private String fileLocation;
    
    /**
     * The header of the IVF file this <tt>DataSource</tt> will use for the
     * VP8 frames.
     */
    private IVFHeader ivfHeader;
    
    /**
     * doConnect allow us to initialize the DataSource with informations that
     * we couldn't have in the constructor, like the MediaLocator that give us
     * the path of the ivf file which give us information on the format 
     */
    public void doConnect() throws IOException
    {
        super.doConnect();
        this.fileLocation = getLocator().getRemainder();
        ivfHeader = new IVFHeader(this.fileLocation);
        
        /*
         * The real framerate of an ivf file is the framerate in the header of
         * the file divided by the timescale (also given in the header).
         */
        this.SUPPORTED_FORMATS[0] = new VideoFormat(
                Constants.VP8,
                ivfHeader.getDimension(),
                Format.NOT_SPECIFIED,
                Format.byteArray,
                ivfHeader.getFramerate() / ivfHeader.getTimeScale());
    }
    
    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    protected IVFStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new IVFStream(this, formatControl);
    }

    
    
    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of
     * <tt>Format</tt>s hardcoded as supported in
     * <tt>IVFCaptureDevice</tt> because the super looks them up by
     * <tt>CaptureDeviceInfo</tt> and it doesn't have some information
     * (like the framerate etc.).
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return SUPPORTED_FORMATS.clone();
    }
}
