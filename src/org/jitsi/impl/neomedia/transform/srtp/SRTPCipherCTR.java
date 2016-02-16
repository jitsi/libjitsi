
package org.jitsi.impl.neomedia.transform.srtp;

public interface SRTPCipherCTR
{
    public void init(byte[] key);
    public void process(byte[] data, int off, int len, byte[] iv);
}
