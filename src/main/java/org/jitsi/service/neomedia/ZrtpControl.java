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

/**
 * ZRTP based SRTP MediaStream encryption control.
 *
 * @author Damian Minkov
 */
public interface ZrtpControl
    extends SrtpControl
{
    /**
     * Gets the cipher information for the current media stream.
     *
     * @return the cipher information string.
     */
    public String getCipherString();

    /**
     * Gets the negotiated ZRTP protocol version.
     *
     * @return the <tt>int</tt> representation of the negotiated ZRTP protocol
     * version.
     */
    public int getCurrentProtocolVersion();

    /**
     * Returns the zrtp hello hash String.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * <code>0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS</code>.
     * @return String the zrtp hello hash.
     */
    public String getHelloHash(int index);

    /**
     * Gets the ZRTP Hello Hash data - separate strings.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * <code>0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS</code>.
     * @return String array containing the version string at offset 0, the Hello
     * hash value as hex-digits at offset 1. Hello hash is available immediately
     * after class instantiation. Returns <tt>null</tt> if ZRTP is not available.
     */
    public String[] getHelloHashSep(int index);

    /**
     * Gets the number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    public int getNumberSupportedVersions();

    /**
     * Gets the peer's Hello Hash data as a <tt>String</tt>.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer
     * Hello hash is available after we received a Hello packet from our peer.
     * If peer's hello hash is not available, returns <tt>null</tt>.
     */
    public String getPeerHelloHash();

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during
     * ZRTP processing. The ZID data can be retrieved after ZRTP receives the
     * first Hello packet from the other party.
     *
     * @return the ZID data as a <tt>byte</tt> array.
     */
    public byte[] getPeerZid();

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during
     * ZRTP processing as a <tt>String</tt>. The ZID data can be retrieved after
     * ZRTP receives the first Hello packet from the other party.
     *
     * @return the ZID data as a <tt>String</tt>.
     */
    public String getPeerZidString();

    /**
     * Gets the SAS for the current media stream.
     *
     * @return the four character ZRTP SAS.
     */
    public String getSecurityString();

    /**
     * Returns the timeout value in milliseconds that we will wait and fire
     * timeout secure event if call is not secured.
     *
     * @return the timeout value in milliseconds that we will wait and fire
     * timeout secure event if call is not secured.
     */
    public long getTimeoutValue();

    /**
     * Gets the status of the SAS verification.
     *
     * @return <tt>true</tt> when the SAS has been verified.
     */
    public boolean isSecurityVerified();

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    public void setSASVerification(boolean verified);
}
