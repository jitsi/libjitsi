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
 * An interface used to pass additional format parameters (received via
 * SDP/Jingle) to codecs.
 *
 * @author Boris Grozev
 */
public interface FormatParametersAwareCodec extends Control
{
    /**
     * Sets the format parameters to <tt>fmtps</tt>
     *
     * @param fmtps The format parameters to set
     */
    public void setFormatParameters(Map<String, String> fmtps);
}
