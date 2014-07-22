/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.rtcp.termination.strategies;

import net.sf.fmj.media.rtp.*;
import org.jitsi.impl.neomedia.rtcp.*;

/**
* Created by gp on 11/07/14.
*/
class FeedbackCacheEntry
{
    long lastUpdate;

    RTCPReportBlock[] reports;
    RTCPREMBPacket remb;
}
