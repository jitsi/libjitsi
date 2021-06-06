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
package org.jitsi.service.neomedia;

import java.awt.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.utils.*;

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
    public Component createEncodingControls(
            MediaType mediaType,
            EncodingConfiguration encodingConfiguration);

    /**
     * Returns the <tt>MediaService</tt> instance
     *
     * @return the <tt>MediaService</tt> instance
     */
    public MediaService getMediaService();
}
