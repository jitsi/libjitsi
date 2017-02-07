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

import java.net.*;

/**
 * RTP extensions are defined by RFC 5285 and they allow attaching additional
 * information to some or all RTP packets of an RTP stream. This class describes
 * RTP extensions in a way that makes them convenient for use in SDP
 * generation/parsing.
 *
 * @author Emil Ivov
 */
public class RTPExtension
{
    /**
     * The URN identifying the RTP extension that allows mixers to send to
     * conference participants the audio levels of all contributing sources.
     * Defined in RFC6465.
     */
    public static final String CSRC_AUDIO_LEVEL_URN
            = "urn:ietf:params:rtp-hdrext:csrc-audio-level";

    /**
     * The URN identifying the RTP extension that allows clients to send to
     * conference mixers the audio level of their packet payload. Defined in
     * RFC6464.
     */
    public static final String SSRC_AUDIO_LEVEL_URN
            = "urn:ietf:params:rtp-hdrext:ssrc-audio-level";

    /**
     * The URN identifying the abs-send-time RTP extension.
     * Defined at
     * {@link "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"}
     */
    public static final String ABS_SEND_TIME_URN
            = "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time";

    /**
     * The URN which identifies the framemarking RTP extension defined at
     * {@link "https://tools.ietf.org/html/draft-ietf-avtext-framemarking-03"}
     */
    public static final String FRAME_MARKING_URN
            = "urn:ietf:params:rtp-hdrext:framemarking";

    /**
     * The URN which identifies the Original Header Block RTP extension defined
     * in {@link "https://tools.ietf.org/html/draft-ietf-perc-double-02"}.
     */
    public static final String ORIGINAL_HEADER_BLOCK_URN
        = "urn:ietf:params:rtp-hdrext:ohb";

    /**
     * The direction that this extension will be transmitted in.
     */
    private MediaDirection direction = MediaDirection.SENDRECV;

    /**
     * The <tt>URI</tt> identifier of this extension.
     */
    private final URI extensionURI;

    /**
     * Extension specific attributes.
     */
    private String extensionAttributes = null;

    /**
     * Creates an <tt>RTPExtension</tt> instance for the specified
     * <tt>extensionURI</tt> using a default <tt>SENDRECV</tt> direction and no
     * extension attributes.
     *
     * @param extensionURI the <tt>URI</tt> (possibly a URN) of the RTP
     * extension that we'd like to create.
     */
    public RTPExtension(URI extensionURI)
    {
        this(extensionURI, MediaDirection.SENDRECV);
    }

    /**
     * Creates an <tt>RTPExtension</tt> instance for the specified
     * <tt>extensionURI</tt> and <tt>direction</tt>.
     *
     * @param extensionURI the <tt>URI</tt> (possibly a URN) of the RTP
     * extension that we'd like to create.
     * @param direction a <tt>MediaDirection</tt> instance indication how this
     * extension will be transmitted.
     */
    public RTPExtension(URI extensionURI, MediaDirection direction)
    {
        this(extensionURI, direction, null);
    }

    /**
     * Creates an <tt>RTPExtension</tt> instance for the specified
     * <tt>extensionURI</tt> using a default <tt>SENDRECV</tt> direction and
     * <tt>extensionAttributes</tt>.
     *
     * @param extensionURI the <tt>URI</tt> (possibly a URN) of the RTP
     * extension that we'd like to create.
     * @param extensionAttributes any attributes that we'd like to add to this
     * extension.
     */
    public RTPExtension(URI extensionURI, String extensionAttributes)
    {
        this(extensionURI, MediaDirection.SENDRECV, extensionAttributes);
    }

    /**
     * Creates an <tt>RTPExtension</tt> instance for the specified
     * <tt>extensionURI</tt> and <tt>direction</tt> and sets the specified
     * <tt>extensionAttributes</tt>.
     *
     * @param extensionURI the <tt>URI</tt> (possibly a URN) of the RTP
     * extension that we'd like to create.
     * @param direction a <tt>MediaDirection</tt> instance indication how this
     * extension will be transmitted.
     * @param extensionAttributes any attributes that we'd like to add to this
     * extension.
     */
    public RTPExtension(URI            extensionURI,
                        MediaDirection direction,
                        String         extensionAttributes)
    {
        this.extensionURI = extensionURI;
        this.direction = direction;
        this.extensionAttributes = extensionAttributes;
    }

    /**
     * Returns the direction that the corresponding <tt>MediaDevice</tt>
     * supports for this extension. By default RTP extension headers inherit
     * the direction of a stream. When explicitly specified <tt>SENDONLY</tt>
     * direction indicates an ability to attach the extension in outgoing RTP
     * packets; a <tt>RECVONLY</tt> direction indicates a desire to receive
     * the extension in incoming packets; a <tt>SENDRECV</tt> direction
     * indicates both.  An <tt>INACTIVE</tt> direction indicates neither, but
     * later re-negotiation may make an extension active.
     *
     * @return the direction that the corresponding <tt>MediaDevice</tt>
     * supports for this extension.
     */
    public MediaDirection getDirection()
    {
        return direction;
    }

    /**
     * Returns the <tt>URI</tt> that identifies the format and meaning of this
     * extension.
     *
     * @return the <tt>URI</tt> (possibly a URN) that identifies the format and
     * meaning of this extension.
     */
    public URI getURI()
    {
        return extensionURI;
    }

    /**
     * Returns the extension attributes associated with this
     * <tt>RTPExtension</tt> or <tt>null</tt> if this extension does not have
     * any.
     *
     * @return A <tt>String</tt> containing the extension attributes associated
     * with this <tt>RTPExtension</tt> or <tt>null</tt> if this extension does
     * not have any.
     */
    public String getExtensionAttributes()
    {
        return extensionAttributes;
    }

    /**
     * Returns a <tt>String</tt> representation of this <tt>RTPExtension</tt>'s
     * <tt>URI</tt>.
     *
     * @return a <tt>String</tt> representation of this <tt>RTPExtension</tt>'s
     * <tt>URI</tt>.
     */
    @Override
    public String toString()
    {
        return extensionURI.toString() + ";" + getDirection();
    }

    /**
     * Returns <tt>true</tt> if and only if <tt>o</tt> is an instance of
     * <tt>RTPExtension</tt> and <tt>o</tt>'s <tt>URI</tt> is equal to this
     * extension's <tt>URI</tt>. The method returns <tt>false</tt> otherwise.
     *
     * @param o the <tt>Object</tt> that we'd like to compare to this
     * <tt>RTPExtension</tt>.
     *
     * @return <tt>true</tt> when <tt>o</tt>'s <tt>URI</tt> is equal to this
     * extension's <tt>URI</tt> and <tt>false</tt> otherwise.
     */
    @Override
    public boolean equals(Object o)
    {
        return (o instanceof RTPExtension)
            && ((RTPExtension)o).getURI().equals(getURI());
    }

    /**
     * Returns the hash code of this extension instance which is actually the
     * hash code of the <tt>URI</tt> that this extension is encapsulating.
     *
     * @return the hash code of this extension instance which is actually the
     * hash code of the <tt>URI</tt> that this extension is encapsulating.
     */
    @Override
    public int hashCode()
    {
        return getURI().hashCode();
    }
}
