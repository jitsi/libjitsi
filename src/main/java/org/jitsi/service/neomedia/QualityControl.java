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
 * The quality controls we use to control other party video presets.
 *
 * @author Damian Minkov
 */
public interface QualityControl
{
    /**
     * The currently used quality preset announced as receive by remote party.
     * @return the current quality preset.
     */
    public QualityPreset getRemoteReceivePreset();

    /**
     * The minimum preset that the remote party is sending and we are receiving.
     * @return the minimum remote preset.
     */
    public QualityPreset getRemoteSendMinPreset();

    /**
     * The maximum preset that the remote party is sending and we are receiving.
     * @return the maximum preset announced from remote party as send.
     */
    public QualityPreset getRemoteSendMaxPreset();

    /**
     * Changes remote send preset. This doesn't have impact of current stream.
     * But will have on next media changes.
     * With this we can try to change the resolution that the remote part
     * is sending.
     * @param preset the new preset value.
     */
    public void setRemoteSendMaxPreset(QualityPreset preset);

    /**
     * Changes remote send preset and protocols who can handle the changes
     * will implement this for re-inviting the other party or just sending that
     * media has changed.
     * @param preset the new preset.
     * @throws MediaException
     */
    public void setPreferredRemoteSendMaxPreset(QualityPreset preset)
        throws MediaException;
}
