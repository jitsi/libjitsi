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
package org.jitsi.impl.neomedia.rtp.translator;

/**
 * @author George Politis
 */
public interface PaddingParams
{
    /**
     * Gets the current bitrate (bps). Together with the optimal bitrate, it
     * allows to calculate the probing bitrate.
     *
     * @return the current bitrate (bps).
     */
    long getCurrentBps();

    /**
     * Gets the optimal bitrate (bps). Together with the current bitrate, it
     * allows to calculate the probing bitrate.
     *
     * @return the optimal bitrate (bps).
     */
    long getOptimalBps();

    /**
     * Gets the SSRC to protect with RTX, in case the padding budget is
     * positive.
     *
     * @return the SSRC to protect with RTX, in case the padding budget is
     * positive.
     */
    long getTargetSSRC();
}
