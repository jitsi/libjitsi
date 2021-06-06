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
package org.jitsi.service.neomedia.event;

/**
 * The purpose of a <tt>DTMFListener</tt> is to notify implementors when new
 * DMTF tones are received by this MediaService implementation.
 *
 * @author Emil Ivov
 */
public interface DTMFListener
{

    /**
     * Indicates that we have started receiving a <tt>DTMFTone</tt>.
     *
     * @param event the <tt>DTMFToneEvent</tt> instance containing the
     * <tt>DTMFTone</tt>
     */
    public void dtmfToneReceptionStarted(DTMFToneEvent event);

    /**
     * Indicates that reception of a DTMF tone has stopped.
     *
     * @param event the <tt>DTMFToneEvent</tt> instance containing the
     * <tt>DTMFTone</tt>
     */
    public void dtmfToneReceptionEnded(DTMFToneEvent event);
}
