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
package org.jitsi.impl.neomedia.jmfext.media.protocol.wasapi;

/**
 * Provides notifications when an audio endpoint device is added or removed,
 * when the state or properties of an endpoint device change, or when there is a
 * change in the default role assigned to an endpoint device.
 *
 * @author Lyubomir Marinov
 */
public interface IMMNotificationClient
{
    void OnDefaultDeviceChanged(int flow, int role, String pwstrDefaultDevice);

    void OnDeviceAdded(String pwstrDeviceId);

    void OnDeviceRemoved(String pwstrDeviceId);

    void OnDeviceStateChanged(String pwstrDeviceId, int dwNewState);

    void OnPropertyValueChanged(String pwstrDeviceId, long key);
}
