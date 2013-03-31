/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
