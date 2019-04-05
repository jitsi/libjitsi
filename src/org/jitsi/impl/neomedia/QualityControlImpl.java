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
package org.jitsi.impl.neomedia;

import java.awt.*;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.utils.logging.*;

/**
 * Implements {@link QualityControl} for the purposes of
 * {@link VideoMediaStreamImpl}.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class QualityControlImpl
    implements QualityControl
{
    /**
     * The <tt>Logger</tt> used by the <tt>QualityControlImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(QualityControlImpl.class);

    /**
     * This is the local settings from the configuration panel.
     */
    private QualityPreset localSettingsPreset;

    /**
     * The maximum values for resolution, framerate, etc.
     */
    private QualityPreset maxPreset;

    /**
     * The current used preset.
     */
    private QualityPreset preset;

    /**
     * Sets the preset.
     *
     * @param preset the desired video settings
     */
    private void setRemoteReceivePreset(QualityPreset preset)
    {
        QualityPreset preferredSendPreset = getPreferredSendPreset();

        if (preset.compareTo(preferredSendPreset) > 0)
            this.preset = preferredSendPreset;
        else
        {
            this.preset = preset;

            Dimension resolution;

            if (logger.isInfoEnabled()
                    && (preset != null)
                    && ((resolution = preset.getResolution()) != null))
            {
                logger.info(
                        "video send resolution: " + resolution.width + "x"
                            + resolution.height);
            }
        }
    }

    /**
     * The current preset.
     *
     * @return the current preset
     */
    public QualityPreset getRemoteReceivePreset()
    {
        return preset;
    }

    /**
     * The minimum resolution values for remote part.
     *
     * @return minimum resolution values for remote part.
     */
    public QualityPreset getRemoteSendMinPreset()
    {
        /* We do not support such a value at the time of this writing. */
        return null;
    }

    /**
     * The max resolution values for remote part.
     *
     * @return max resolution values for remote part.
     */
    public QualityPreset getRemoteSendMaxPreset()
    {
        return maxPreset;
    }

    /**
     * Does nothing specific locally.
     *
     * @param preset the max preset
     */
    public void setPreferredRemoteSendMaxPreset(QualityPreset preset)
    {
        setRemoteSendMaxPreset(preset);
    }

    /**
     * Changes remote send preset, the one we will receive.
     *
     * @param preset
     */
    public void setRemoteSendMaxPreset(QualityPreset preset)
    {
        this.maxPreset = preset;
    }

    /**
     * Gets the local setting of capture.
     *
     * @return the local setting of capture
     */
    private QualityPreset getPreferredSendPreset()
    {
        if(localSettingsPreset == null)
        {
            DeviceConfiguration devCfg
                = NeomediaServiceUtils
                    .getMediaServiceImpl()
                        .getDeviceConfiguration();

            localSettingsPreset
                = new QualityPreset(
                        devCfg.getVideoSize(),
                        devCfg.getFrameRate());
        }
        return localSettingsPreset;
    }

    /**
     * Sets maximum resolution.
     *
     * @param res
     */
    public void setRemoteReceiveResolution(Dimension res)
    {
        setRemoteReceivePreset(new QualityPreset(res));
    }
}
