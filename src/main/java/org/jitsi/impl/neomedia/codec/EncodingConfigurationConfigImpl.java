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
package org.jitsi.impl.neomedia.codec;

import java.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.format.*;

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
     * Holds the prefix that will be used to store properties
     */
    private final String propPrefix;

    /**
     * The <tt>ConfigurationService</tt> instance that will be used to
     * store properties
     */
    private final ConfigurationService cfg = LibJitsi.getConfigurationService();

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
        Map<String, String> properties = new HashMap<>();

        for (String pName : cfg.getPropertyNamesByPrefix(propPrefix, false))
            properties.put(pName, cfg.getString(pName));

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

        cfg.setProperty(
                propPrefix + "." + getEncodingPreferenceKey(encoding),
                priority);
    }
}
