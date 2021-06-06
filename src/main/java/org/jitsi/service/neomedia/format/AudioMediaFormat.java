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
package org.jitsi.service.neomedia.format;

/**
 * The interface represents an audio format. Audio formats characterize audio
 * streams and the <tt>AudioMediaFormat</tt> interface gives access to some of its
 * properties such as encoding, clock rate, and number of channels.
 *
 * @author Emil Ivov
 */
public interface AudioMediaFormat
    extends MediaFormat
{
    /**
     * Returns the number of audio channels associated with this
     * <tt>AudioMediaFormat</tt>.
     *
     * @return the number of audio channels associated with this
     * <tt>AudioMediaFormat</tt>.
     */
    public int getChannels();
}
