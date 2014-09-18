/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.control;

import javax.media.*;
import java.util.*;

/**
 * An interface used to pass additional attributes (received via
 * SDP/Jingle) to codecs.
 *
 * @author Damian Minkov
 */
public interface AdvancedAttributesAwareCodec
    extends Control
{
    /**
     * Sets the additional attributes to <tt>attributes</tt>
     *
     * @param attributes The additional attributes to set
     */
    public void setAdvancedAttributes(Map<String, String> attributes);
}
