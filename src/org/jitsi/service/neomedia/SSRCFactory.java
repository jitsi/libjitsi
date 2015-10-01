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
 * Declares a factory of synchronization source (SSRC) identifiers.
 *
 * @author Lyubomir Marinov
 */
public interface SSRCFactory
{
    /**
     * Generates a new synchronization source (SSRC) identifier. If the returned
     * synchronization source (SSRC) identifier is found to not be globally
     * unique within the associated RTP session, the method will be invoked
     * again.
     *
     * @param cause a <tt>String</tt> which specified the cause of the
     * invocation of the method
     * @return a randomly chosen <tt>int</tt> value which is to be utilized as a
     * new synchronization source (SSRC) identifier should it be found to be
     * globally unique within the associated RTP session or
     * <tt>Long.MAX_VALUE</tt> if this <tt>SSRCFactory</tt> has cancelled the
     * operation
     */
    public long generateSSRC(String cause);
}
