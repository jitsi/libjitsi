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

import org.jitsi.service.neomedia.event.*;

/**
 * Extends the <tt>MediaStream</tt> interface and adds methods specific to
 * audio streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface AudioMediaStream
    extends MediaStream
{
    /**
     * The name of the property which controls whether handling of RFC4733
     * DTMF packets should be disabled or enabled. If disabled, packets will
     * not be processed or dropped (regardless of whether there is a payload
     * type number registered for the telephone-event format).
     */
    public static String DISABLE_DTMF_HANDLING_PNAME
            = AudioMediaStream.class.getName() + ".DISABLE_DTMF_HANDLING";

    /**
     * Registers a listener that would receive notification events if the
     * remote party starts sending DTMF tones to us.
     *
     * @param listener the <tt>DTMFListener</tt> that we'd like to register.
     */
    public void addDTMFListener(DTMFListener listener);

    /**
     * Removes <tt>listener</tt> from the list of <tt>DTMFListener</tt>s
     * registered to receive events for incoming DTMF tones.
     *
     * @param listener the listener that we'd like to unregister
     */
    public void removeDTMFListener(DTMFListener listener);

    /**
     * Registers <tt>listener</tt> as the <tt>CsrcAudioLevelListener</tt> that
     * will receive notifications for changes in the levels of conference
     * participants that the remote party could be mixing.
     *
     * @param listener the <tt>CsrcAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we'd like to stop receiving notifications.
     */
    public void setCsrcAudioLevelListener(CsrcAudioLevelListener listener);

    /**
     * Sets <tt>listener</tt> as the <tt>SimpleAudioLevelListener</tt>
     * registered to receive notifications for changes in the levels of the
     * audio that this stream is sending out.
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we want to stop local audio level
     * measurements.
     */
    public void setLocalUserAudioLevelListener(
                                            SimpleAudioLevelListener listener);

    /**
     * Sets the <tt>VolumeControl</tt> which is to control the volume (level)
     * of the audio received in/by this <tt>AudioMediaStream</tt> and played
     * back.
     *
     * @param outputVolumeControl the <tt>VolumeControl</tt> which is to control
     * the volume (level) of the audio received in this
     * <tt>AudioMediaStream</tt> and played back
     */
    public void setOutputVolumeControl(VolumeControl outputVolumeControl);

    /**
     * Sets <tt>listener</tt> as the <tt>SimpleAudioLevelListener</tt>
     * registered to receive notifications for changes in the levels of the
     * party that's at the other end of this stream.
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> that we'd like to
     * register or <tt>null</tt> if we want to stop stream audio level
     * measurements.
     */
    public void setStreamAudioLevelListener(SimpleAudioLevelListener listener);

    /**
     * Starts sending the specified <tt>DTMFTone</tt> until the
     * <tt>stopSendingDTMF()</tt> method is called (Excepts for INBAND DTMF,
     * which stops by itself this is why where there is no need to call the
     * stopSendingDTMF). Callers should keep in mind the fact that calling this
     * method would most likely interrupt all audio transmission until the
     * corresponding stop method is called. Also, calling this method
     * successively without invoking the corresponding stop method between the
     * calls will simply replace the <tt>DTMFTone</tt> from the first call with
     * that from the second.
     *
     * @param tone the <tt>DTMFTone</tt> to start sending.
     * @param dtmfMethod The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @param minimalToneDuration The minimal DTMF tone duration.
     * @param maximalToneDuration The maximal DTMF tone duration.
     * @param volume The DTMF tone volume. Describes the power level of the
     *               tone, expressed in dBm0 after dropping the sign.
     */
    public void startSendingDTMF(
            DTMFTone tone,
            DTMFMethod dtmfMethod,
            int minimalToneDuration,
            int maximalToneDuration,
            int volume);

    /**
     * Interrupts transmission of a <tt>DTMFTone</tt> started with the
     * <tt>startSendingDTMF</tt> method. This method has no effect if no tone
     * is being currently sent.
     *
     * @param dtmfMethod the <tt>DTMFMethod</tt> to stop sending.
     */
    public void stopSendingDTMF(DTMFMethod dtmfMethod);
}
