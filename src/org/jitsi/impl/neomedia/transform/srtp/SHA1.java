/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.transform.srtp;

import org.bouncycastle.crypto.*;
import org.bouncycastle.crypto.digests.*;

class SHA1
{
    static Digest createDigest()
    {
        // TODO Auto-generated method stub
        return new SHA1Digest();
    }
}
