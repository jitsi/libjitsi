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

import org.jitsi.impl.neomedia.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.utils.*;

/**
 * Controls SRTP encryption in the MediaStream.
 *
 * @author Damian Minkov
 */
public interface SrtpControl
{
    public static final String RTP_SAVP = "RTP/SAVP";

    public static final String RTP_SAVPF = "RTP/SAVPF";

    /**
     * Adds a <tt>cleanup()</tt> method to
     * <tt>org.jitsi.impl.neomedia.transform.TransformEngine</tt> which is to go
     * in hand with the <tt>cleanup()</tt> method of <tt>SrtpControl</tt>.
     *
     * @author Lyubomir Marinov
     */
    public interface TransformEngine
        extends org.jitsi.impl.neomedia.transform.TransformEngine
    {
        /**
         * Cleans up this <tt>TransformEngine</tt> and prepares it for garbage
         * collection.
         */
        public void cleanup();
    }

    /**
     * Cleans up this <tt>SrtpControl</tt> and its <tt>TransformEngine</tt>.
     *
     * @param user the {@Object} which requests the clean-up and is supposedly
     * currently using this {@code SrtpControl} (i.e. has already used
     * {@link #registerUser(Object)}).
     */
    public void cleanup(Object user);

    /**
     * Gets the default secure/insecure communication status for the supported
     * call sessions.
     *
     * @return default secure communication status for the supported call
     * sessions.
     */
    public boolean getSecureCommunicationStatus();

    /**
     * Gets the <tt>SrtpControlType</tt> of this instance.
     *
     * @return the <tt>SrtpControlType</tt> of this instance
     */
    public SrtpControlType getSrtpControlType();

    /**
     * Returns the <tt>SrtpListener</tt> which listens for security events.
     *
     * @return the <tt>SrtpListener</tt> which listens for security events
     */
    public SrtpListener getSrtpListener();

    /**
     * Returns the transform engine currently used by this stream.
     *
     * @return the RTP stream transformation engine
     */
    public TransformEngine getTransformEngine();

    /**
     * Indicates if the key exchange method is dependent on secure transport of
     * the signaling channel.
     *
     * @return <tt>true</tt> when secure signaling is required to make the
     * encryption secure; <tt>false</tt>, otherwise.
     */
    public boolean requiresSecureSignalingTransport();

    /**
     * Sets the <tt>RTPConnector</tt> which is to use or uses this SRTP engine.
     *
     * @param connector the <tt>RTPConnector</tt> which is to use or uses this
     * SRTP engine
     */
    public void setConnector(AbstractRTPConnector connector);

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session.
     */
    public void setMasterSession(boolean masterSession);

    /**
     * Sets the multistream data, which means that the master stream has
     * successfully started and this will start all other streams in this
     * session.
     *
     * @param master The security control of the master stream.
     */
    public void setMultistream(SrtpControl master);

    /**
     * Sets a <tt>SrtpListener</tt> that will listen for security events.
     *
     * @param srtpListener the <tt>SrtpListener</tt> that will receive the
     * events
     */
    public void setSrtpListener(SrtpListener srtpListener);

    /**
     * Starts and enables zrtp in the stream holding this control.
     *
     * @param mediaType the media type of the stream this control controls.
     */
    public void start(MediaType mediaType);

    /**
     * Registers <tt>user</tt> as an instance which is currently using this
     * <tt>SrtpControl</tt>.
     *
     * @param user the {@code Object} which is currently using this
     * {@code SrtpControl}
     */
    public void registerUser(Object user);
}
