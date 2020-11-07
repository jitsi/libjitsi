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
package org.jitsi.impl.neomedia.transform.dtls;

import java.util.*;
import java.util.concurrent.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.utils.event.*;

/**
 * Gathers properties of {@link DtlsControlImpl} which it shares with
 * {@link DtlsTransformEngine} and {@link DtlsPacketTransformer} i.e. assigning
 * a value to a {@code DtlsControlImpl} property triggers assignments to the
 * respective properties of {@code DtlsTransformEngine} and
 * {@code DtlsPacketTransfomer}.
 *
 * @author Lyubomir Marinov
 */
class Properties
    extends PropertyChangeNotifier
{
    /**
     * The <tt>RTPConnector</tt> which uses the <tt>TransformEngine</tt> of this
     * <tt>SrtpControl</tt>.
     */
    public static final String CONNECTOR_PNAME
        = Properties.class.getName() + ".connector";

    public static final String MEDIA_TYPE_PNAME
        = Properties.class.getName() + ".mediaType";

    /**
     * Whether rtcp-mux is in use.
     */
    public static final String RTCPMUX_PNAME
        = Properties.class.getName() + ".rtcpmux";

    /**
     * The value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     */
    public static final String SETUP_PNAME
        = Properties.class.getName() + ".setup";

    /**
     * The actual {@code Map} of property names to property values represented
     * by this instance. Stores only assignable properties i.e. {@code final}s
     * are explicitly defined (e.g. {@link #srtpDisabled}).
     */
    private final Map<String,Object> properties = new ConcurrentHashMap<>();

    /**
     * Indicates whether this <tt>DtlsControl</tt> will work in DTLS/SRTP or
     * pure DTLS mode.
     */
    private final boolean srtpDisabled;

    /**
     * Initializes a new {@code Properties} instance.
     *
     * @param srtpDisabled {@code true} to specify pure DTLS without SRTP
     * extensions or {@code false} to specify DTLS/SRTP.
     */
    public Properties(boolean srtpDisabled)
    {
        this.srtpDisabled = srtpDisabled;
    }

    /**
     * Gets the value of the property with a specific name.
     *
     * @param name the name of the property to get the value of
     * @return the value of the property with the specified {@code name}
     */
    public Object get(String name)
    {
        return properties.get(name);
    }

    /**
     * Gets the value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     *
     * @return the value of the <tt>setup</tt> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol
     * (SDP)&quot; which determines whether this instance acts as a DTLS client
     * or a DTLS server.
     */
    public DtlsControl.Setup getSetup()
    {
        return (DtlsControl.Setup) get(SETUP_PNAME);
    }

    /**
     * Indicates if SRTP extensions are disabled which means we're working in
     * pure DTLS mode.
     *
     * @return <tt>true</tt> if SRTP extensions must be disabled.
     */
    public boolean isSrtpDisabled()
    {
        return srtpDisabled;
    }

    /**
     * Sets the value of the property with a specific name.
     *
     * @param name the name of the property to set the value of
     * @param value the value to set on the property with the specified
     * {@code name}
     */
    public void put(String name, Object value)
    {
        // XXX ConcurrentHashMap does't allow null values and we want to allow
        // them. (It doesn't allow null keys either and we don't want to allow
        // them.)
        Object oldValue
            = (value == null)
                ? properties.remove(name)
                : properties.put(name, value);

        if (!Objects.equals(oldValue, value))
            firePropertyChange(name, oldValue, value);
    }
}
