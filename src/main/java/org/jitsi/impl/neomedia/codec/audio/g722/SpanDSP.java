/*
 * Copyright @ 2017-Present 8x8, Inc.
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
package org.jitsi.impl.neomedia.codec.audio.g722;

import com.sun.jna.*;

interface SpanDSP extends Library
{
    static SpanDSP INSTANCE = Native.load("spandsp", SpanDSP.class);

    Pointer g722_encode_init(Pointer s, int rate, int options);
    int g722_encode(Pointer s, byte[] data, short[] amp, int len);
    int g722_encode_release(Pointer s);
    int g722_encode_free(Pointer s);

    Pointer g722_decode_init(Pointer s, int rate, int options);
    int g722_decode(Pointer s, byte[] data, short[] amp, int len);
    int g722_decode_release(Pointer s);
    int g722_decode_free(Pointer s);
}
