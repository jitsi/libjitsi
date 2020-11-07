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
package org.jitsi.service.audionotifier;

/**
 * The AudioNotifierService is meant to be used to control all sounds in the
 * application. An audio could be created by calling the createAudio method.
 * In order to stop all sounds in the application one could call the setMute
 * method. To check whether the sound is currently enabled the isMute method
 * could be used.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public interface AudioNotifierService
{
    /**
     * Checks whether the playback and notification configuration
     * share the same device.
     * @return are audio out and notifications using the same device.
     */
    public boolean audioOutAndNotificationsShareSameDevice();

    /**
     * Creates an SCAudioClip and returns it. By default using notification
     * device.
     * @param uri the uri, which will be the source of the audio
     * @return the created SCAudioClip, that could be played.
     */
    public SCAudioClip createAudio(String uri);

    /**
     * Creates an SCAudioClip and returns it.
     * @param uri the uri, which will be the source of the audio
     * @param playback use or not the playback device.
     * @return the created SCAudioClip, that could be played.
     */
    public SCAudioClip createAudio(String uri, boolean playback);

    /**
     * Specifies if currently the sound is off.
     *
     * @return TRUE if currently the sound is off, FALSE otherwise
     */
    public boolean isMute();

    /**
     * Stops/Restores all currently playing sounds.
     *
     * @param isMute mute or not currently playing sounds
     */
    public void setMute(boolean isMute);
}
