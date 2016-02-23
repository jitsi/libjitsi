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

import ch.imvs.sdes4j.srtp.*;

/**
 * SDES based SRTP MediaStream encryption control.
 *
 * @author Ingo Bauersachs
 */
public interface SDesControl
    extends SrtpControl
{
    /**
     * Name of the config setting that supplies the default enabled cipher
     * suites. Cipher suites are comma-separated.
     */
    public static final String SDES_CIPHER_SUITES =
        "net.java.sip.communicator.service.neomedia.SDES_CIPHER_SUITES";

    /**
     * Gets the crypto attribute of the incoming MediaStream.
     *
     * @return the crypto attribute of the incoming MediaStream.
     */
    public SrtpCryptoAttribute getInAttribute();

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    public SrtpCryptoAttribute[] getInitiatorCryptoAttributes();

    /**
     * Gets the crypto attribute of the outgoing MediaStream.
     *
     * @return the crypto attribute of the outgoing MediaStream.
     */
    public SrtpCryptoAttribute getOutAttribute();

    /**
     * Gets all supported cipher suites.
     *
     * @return all supported cipher suites.
     */
    public Iterable<String> getSupportedCryptoSuites();

    /**
     * Selects the local crypto attribute from the initial offering
     * ({@link #getInitiatorCryptoAttributes()}) based on the peer's first
     * matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     * @return A SrtpCryptoAttribute when a matching cipher suite was found;
     * <tt>null</tt>, otherwise.
     */
    public SrtpCryptoAttribute initiatorSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied
     * attributes and creates the local crypto attribute. Used when the control
     * is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     * @return The local crypto attribute for the answer of the offer or
     * <tt>null</tt> if no matching cipher suite could be found.
     */
    public SrtpCryptoAttribute responderSelectAttribute(
            Iterable<SrtpCryptoAttribute> peerAttributes);

    /**
     * Sets the enabled SDES ciphers.
     *
     * @param ciphers The list of enabled ciphers.
     */
    public void setEnabledCiphers(Iterable<String> ciphers);
}
