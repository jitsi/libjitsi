package org.jitsi.impl.neomedia.rtp.translator;

import javax.media.rtp.*;

/**
 * Created by gp on 18/07/14.
 */
public interface Payload
{
    public abstract void writeTo(OutputDataStream stream);
}
