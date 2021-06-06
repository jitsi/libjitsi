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
package org.jitsi.util;

import java.io.*;
import java.util.*;

/**
 * Defines the different permit extension file.
 *
 * @author Alexandre Maillard
 * @author Dmitri Melnikov
 * @author Vincent Lucas
 */
public class SoundFileUtils
{
    /**
     * Different extension of a sound file
     */
    public final static String wav = "wav";
    public final static String mid = "midi";
    public final static String mp2 = "mp2";
    public final static String mp3 = "mp3";
    public final static String mod = "mod";
    public final static String ram = "ram";
    public final static String wma = "wma";
    public final static String ogg = "ogg";
    public final static String gsm = "gsm";
    public final static String aif = "aiff";
    public final static String au = "au";

    /**
     * The file extension and the format of call recording to be used by
     * default.
     */
    public static final String DEFAULT_CALL_RECORDING_FORMAT = mp3;

    /**
     * Checks whether this file is a sound file.
     *
     * @param f <tt>File</tt> to check
     * @return <tt>true</tt> if it's a sound file, <tt>false</tt> otherwise
     */
    public static boolean isSoundFile(File f)
    {
        String ext = getExtension(f);

        if (ext != null)
        {
            return
                ext.equals(wma)
                    || ext.equals(wav)
                    || ext.equals(ram)
                    || ext.equals(ogg)
                    || ext.equals(mp3)
                    || ext.equals(mp2)
                    || ext.equals(mod)
                    || ext.equals(mid)
                    || ext.equals(gsm)
                    || ext.equals(au);
        }
        return false;
    }

    /**
     * Checks whether this file is a sound file.
     *
     * @param f <tt>File</tt> to check
     * @param soundFormats The sound formats to restrict the file name
     * extension. If soundFormats is null, then every sound format defined by
     * SoundFileUtils is correct.
     *
     * @return <tt>true</tt> if it's a sound file conforming to the format given
     * in parameters (if soundFormats is null, then every sound format defined
     * by SoundFileUtils is correct), <tt>false</tt> otherwise.
     */
    public static boolean isSoundFile(File f, String[] soundFormats)
    {
        // If there is no specific filters, then compare the file to all sound
        // extension available.
        if(soundFormats == null)
        {
            return SoundFileUtils.isSoundFile(f);
        }
        // Compare the file extension to the sound formats provided in
        // parameter.
        else
        {
            String ext = getExtension(f);

            // If the file has an extension
            if (ext != null)
            {
                return (Arrays.binarySearch(
                            soundFormats,
                            ext,
                            String.CASE_INSENSITIVE_ORDER) > -1);
            }
        }
        return false;
    }

    /**
     * Gets the file extension.
     * TODO: There are at least 2 other methods like this scattered around
     * the SC code, we should move them all to util package.
     *
     * @param f which wants the extension
     * @return Return the extension as a String
     */
    public static String getExtension(File f)
    {
        String s = f.getName();
        int i = s.lastIndexOf('.');
        String ext = null;

        if ((i > 0) &&  (i < s.length() - 1))
            ext = s.substring(i+1).toLowerCase();
        return ext;
    }
}
