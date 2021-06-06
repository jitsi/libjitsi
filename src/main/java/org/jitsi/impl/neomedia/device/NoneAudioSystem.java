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
package org.jitsi.impl.neomedia.device;

/**
 * Implements an <tt>AudioSystem</tt> without any devices which allows the user
 * to select to use no audio capture, notification and playback.
 *
 * @author Lyubomir Marinov
 */
public class NoneAudioSystem
    extends AudioSystem
{
    public static final String LOCATOR_PROTOCOL = "none";

    public NoneAudioSystem()
        throws Exception
    {
        super(LOCATOR_PROTOCOL);
    }

    @Override
    protected void doInitialize()
        throws Exception
    {
    }

    @Override
    public String toString()
    {
        return "None";
    }
}
