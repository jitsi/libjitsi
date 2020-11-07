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
package org.jitsi.impl.neomedia.recording;

import java.io.*;
import org.jitsi.utils.*;

public class WebmWriter
{
    static
    {
        JNIUtils.loadLibrary("jnvpx", WebmWriter.class);
    }

    /**
     * Constant corresponding to <tt>VPX_FRAME_IS_KEY</tt> from libvpx's
     * <tt>vpx/vpx_encoder.h</tt>
     */
    public static int FLAG_FRAME_IS_KEY = 0x01;

    /**
     * Constant corresponding to <tt>VPX_FRAME_IS_INVISIBLE</tt> from libvpx's
     * <tt>vpx/vpx_encoder.h</tt>
     */
    public static int FLAG_FRAME_IS_INVISIBLE = 0x04;

    private long glob;

    private native long allocCfg();

    /**
     * Free-s <tt>glob</tt> and closes the file opened for writing.
     * @param glob
     */
    private native void freeCfg(long glob);

    private native boolean openFile(long glob, String fileName);
    private native void writeWebmFileHeader(long glob, int width, int height);
    public void writeWebmFileHeader(int width, int height)
    {
        writeWebmFileHeader(glob, width, height);
    }
    private native void writeWebmBlock(long glob, FrameDescriptor fd);
    private native void writeWebmFileFooter(long glob, long hash);

    public WebmWriter(String filename)
            throws IOException
    {
        glob = allocCfg();

        if (glob == 0)
        {
            throw new IOException("allocCfg() failed");
        }

        if (openFile(glob, filename))
        {
            throw new IOException("Can not open " + filename + " for writing");
        }
    }

    public void close()
    {
        writeWebmFileFooter(glob, 0);
        freeCfg(glob); //also closes the file
    }

    public void writeFrame(FrameDescriptor fd)
    {
        writeWebmBlock(glob, fd);
    }

    public static class FrameDescriptor
    {
        public byte[] buffer;
        public int offset;
        public long length;
        public long pts;
        public int flags;
    }
}
