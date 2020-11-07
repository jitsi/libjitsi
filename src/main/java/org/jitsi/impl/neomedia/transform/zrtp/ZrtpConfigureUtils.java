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
package org.jitsi.impl.neomedia.transform.zrtp;

import gnu.java.zrtp.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;

public class ZrtpConfigureUtils
{
    public static <T extends Enum<T>>String getPropertyID(T algo)
    {
        Class<T> clazz = algo.getDeclaringClass();
        return "net.java.sip.communicator." + clazz.getName().replace('$', '_');
    }

    public static ZrtpConfigure getZrtpConfiguration()
    {
        ZrtpConfigure active = new ZrtpConfigure();
        setupConfigure(ZrtpConstants.SupportedPubKeys.DH2K, active);
        setupConfigure(ZrtpConstants.SupportedHashes.S256, active);
        setupConfigure(ZrtpConstants.SupportedSymCiphers.AES1, active);
        setupConfigure(ZrtpConstants.SupportedSASTypes.B32, active);
        setupConfigure(ZrtpConstants.SupportedAuthLengths.HS32, active);

        return active;
    }

    private static <T extends Enum<T>> void
        setupConfigure(T algo, ZrtpConfigure active)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String savedConf = null;

        if (cfg != null)
        {
            String id = ZrtpConfigureUtils.getPropertyID(algo);

            savedConf = cfg.getString(id);
        }
        if (savedConf == null)
            savedConf = "";

        Class <T> clazz = algo.getDeclaringClass();
        String savedAlgos[] = savedConf.split(";");

        // Configure saved algorithms as active
        for (String str : savedAlgos)
        {
            try
            {
                T algoEnum = Enum.valueOf(clazz, str);

                if (algoEnum != null)
                    active.addAlgo(algoEnum);
            }
            catch (IllegalArgumentException iae)
            {
                // Ignore it and continue the loop.
            }
        }
    }
}
