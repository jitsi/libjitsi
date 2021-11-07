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
 * Signals that a packet transmission exception of some sort has occurred. This
 * class is the general class of exceptions produced by failed or interrupted
 * transmissions.
 *
 * @author George Politis
 */
public class TransmissionFailedException
    extends Throwable
{
    /**
     * Ctor.
     *
     * @param e
     */
    public TransmissionFailedException(Exception e)
    {
        super(e);
    }
}
