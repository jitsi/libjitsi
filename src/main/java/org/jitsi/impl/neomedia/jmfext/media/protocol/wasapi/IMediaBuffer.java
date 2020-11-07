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

import java.io.*;

/**
 * Defines the API of Microsoft's <tt>IMediaBuffer</tt> interface (referred to
 * as unmanaged) and allows implementing similar abstractions on the Java side
 * (referred to as managed).
 *
 * @author Lyubomir Marinov
 */
public interface IMediaBuffer
{
    int GetLength()
        throws IOException;

    int GetMaxLength()
        throws IOException;

    int pop(byte[] buffer, int offset, int length)
        throws IOException;

    int push(byte[] buffer, int offset, int length)
        throws IOException;

    int Release();

    void SetLength(int length)
        throws IOException;
}
