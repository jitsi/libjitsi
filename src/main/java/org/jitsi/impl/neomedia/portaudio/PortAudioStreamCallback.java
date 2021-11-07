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
package org.jitsi.impl.neomedia.portaudio;

import java.nio.*;

/**
 *
 * @author Lyubomir Marinov
 */
public interface PortAudioStreamCallback
{
    /**
     * &quot;Abort&quot; result code.
     */
    public static final int RESULT_ABORT = 2;

    /**
     * &quot;Complete&quot; result code.
     */
    public static final int RESULT_COMPLETE = 1;

    /**
     * &quot;Continue&quot; result code.
     */
    public static final int RESULT_CONTINUE = 0;

    /**
     * Callback.
     *
     * @param input input <tt>ByteBuffer</tt>
     * @param output output <tt>ByteBuffer</tt>
     * @return
     */
    public int callback(ByteBuffer input, ByteBuffer output);

    /**
     * Finished callback.
     */
    public void finishedCallback();
}
