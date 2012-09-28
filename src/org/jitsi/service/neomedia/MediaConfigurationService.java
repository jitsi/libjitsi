/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import java.awt.*;
import org.jitsi.service.neomedia.codec.*;

/**
 * An interface that exposes the <tt>Component</tt>s used in media
 * configuration user interfaces.
 * 
 * @author Boris Grozev
 */
public interface MediaConfigurationService
{
    /**
     * Returns a <tt>Component</tt> for audio configuration
     * 
     * @return A <tt>Component</tt> for audio configuration
     */
    public Component createAudioConfigPanel();

    /**
     * Returns a <tt>Component</tt> for video configuration
     * 
     * @return A <tt>Component</tt> for video configuration
     */
    public Component createVideoConfigPanel();

    /**
     * Returns a <tt>Component</tt> for encodings configuration (either audio
     * or video)
     *
     * @param mediaType The type of media -- either MediaType.AUDIO or
     * MediaType.VIDEO
     * @param encodingConfiguration The <tt>EncodingConfiguration</tt> instance
     * to use.
     * @return The <tt>Component</tt> for encodings configuration
     */
    public Component createEncodingControls(MediaType mediaType,
            EncodingConfiguration encodingConfiguration);

    /**
     * Returns the <tt>MediaService</tt> instance
     *
     * @return the <tt>MediaService</tt> instance
     */
    public MediaService getMediaService();
}
