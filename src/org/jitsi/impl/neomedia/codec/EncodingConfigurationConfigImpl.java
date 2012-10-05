/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.codec;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.*;

import java.util.*;


/**
 * An EncodingConfiguration implementation that synchronizes it's preferences
 * with a ConfigurationService.
 *
 * @author Boris Grozev
 */
public class EncodingConfigurationConfigImpl
       extends EncodingConfigurationImpl
{
    /**
     * The <tt>Logger</tt> used by this <tt>EncodingConfigurationConfigImpl</tt>
     * instance for logging output.
     */
    private final Logger logger
            = Logger.getLogger(EncodingConfigurationConfigImpl.class);

    /**
     * Holds the prefix that will be used to store properties
     */
    private String propPrefix;

    /**
     * The <tt>ConfigurationService</tt> instance that will be used to
     * store properties
     */
    private ConfigurationService configurationService
            = LibJitsi.getConfigurationService();

    /**
     * Constructor. Loads the configuration from <tt>prefix</tt>
     *
     * @param prefix the prefix to use when loading and storing properties
     */
    public EncodingConfigurationConfigImpl(String prefix)
    {
        propPrefix = prefix;
        loadConfig();
    }

    /**
     * Loads the properties stored under <tt>this.propPrefix</tt>
     */
    private void loadConfig()
    {
        Map<String, String> properties = new HashMap<String, String>();

        for (String pName :
               configurationService.getPropertyNamesByPrefix(propPrefix, false))
            properties.put(pName, configurationService.getString(pName));

        loadProperties(properties);
    }

    /**
     * Sets the preference associated with <tt>encoding</tt> to
     * <tt>priority</tt>, and stores the appropriate property in the
     * configuration service.
     *
     * @param encoding the <tt>MediaFormat</tt> specifying the encoding to set
     * the priority of
     * @param priority a positive <tt>int</tt> indicating the priority of
     * <tt>encoding</tt> to set
     *
     * @see EncodingConfigurationImpl#setPriority(MediaFormat, int)
     */
    @Override
    public void setPriority(MediaFormat encoding, int priority)
    {
        super.setPriority(encoding, priority);
        configurationService.setProperty(
                propPrefix+"."+getEncodingPreferenceKey(encoding),
                priority);
    }
}
