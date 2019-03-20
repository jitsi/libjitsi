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
package org.jitsi.service.neomedia.codec;

import java.util.*;

import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;

/**
 * A base class that manages encoding configurations. It holds information
 * about supported formats.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class EncodingConfiguration
{
    /**
     * The <tt>Logger</tt> used by this <tt>EncodingConfiguration</tt> instance
     * for logging output.
     */
    private final Logger logger
        = Logger.getLogger(EncodingConfiguration.class);

    /**
     * The <tt>Comparator</tt> which sorts the sets according to the settings in
     * <tt>encodingPreferences</tt>.
     */
    private final Comparator<MediaFormat> encodingComparator
        = new Comparator<MediaFormat>()
                {
                    public int compare(MediaFormat s1, MediaFormat s2)
                    {
                        return compareEncodingPreferences(s1, s2);
                    }
                };

    /**
     * That's where we keep format preferences matching SDP formats to integers.
     * We keep preferences for both audio and video formats here in case we'd
     * ever need to compare them to one another. In most cases however both
     * would be decorelated and other components (such as the UI) should present
     * them separately.
     */
    protected final Map<String, Integer> encodingPreferences
        = new HashMap<String, Integer>();

    /**
     * The cache of supported <tt>AudioMediaFormat</tt>s ordered by decreasing
     * priority.
     */
    private Set<MediaFormat> supportedAudioEncodings;

    /**
     * The cache of supported <tt>VideoMediaFormat</tt>s ordered by decreasing
     * priority.
     */
    private Set<MediaFormat> supportedVideoEncodings;

    /**
     * Updates the codecs in the supported sets according to the preferences in
     * encodingPreferences. If the preference value is <tt>0</tt>, the codec is
     * disabled.
     */
    private void updateSupportedEncodings()
    {
        /*
         * If they need updating, their caches are invalid and need rebuilding
         * next time they are requested.
         */
        supportedAudioEncodings = null;
        supportedVideoEncodings = null;
    }

    /**
     * Gets the <tt>Set</tt> of enabled available <tt>MediaFormat</tt>s with the
     * specified <tt>MediaType</tt> sorted in decreasing priority.
     *
     * @param type the <tt>MediaType</tt> of the <tt>MediaFormat</tt>s to get
     * @return a <tt>Set</tt> of enabled available <tt>MediaFormat</tt>s with
     * the specified <tt>MediaType</tt> sorted in decreasing priority
     */
    private Set<MediaFormat> updateSupportedEncodings(MediaType type)
    {
        Set<MediaFormat> enabled
            = new TreeSet<MediaFormat>(encodingComparator);

        for (MediaFormat format : getAllEncodings(type))
        {
            if (getPriority(format) > 0)
                enabled.add(format);
        }
        return enabled;
    }

    /**
     * Sets <tt>pref</tt> as the preference associated with <tt>encoding</tt>.
     * Use this method for both audio and video encodings and don't worry if
     * preferences are equal since we rarely need to compare prefs of video
     * encodings to those of audio encodings.
     *
     * @param encoding the SDP int of the encoding whose pref we're setting.
     * @param clockRate clock rate
     * @param pref a positive int indicating the preference for that encoding.
     */
    protected abstract void setEncodingPreference(
            String encoding, double clockRate,
            int pref);

    /**
     * Sets <tt>priority</tt> as the preference associated with
     * <tt>encoding</tt>. Use this method for both audio and video encodings and
     * don't worry if the preferences are equal since we rarely need to compare
     * the preferences of video encodings to those of audio encodings.
     *
     * @param encoding the <tt>MediaFormat</tt> specifying the encoding to set
     * the priority of
     * @param priority a positive <tt>int</tt> indicating the priority of
     * <tt>encoding</tt> to set
     */
    public void setPriority(MediaFormat encoding, int priority)
    {
        String encodingEncoding = encoding.getEncoding();

        /*
         * Since we'll be remembering the priority in the ConfigurationService
         * by associating it with a property name/key based on encoding and
         * clock rate only, it does not make sense to store the MediaFormat in
         * encodingPreferences because MediaFormat is much more specific than
         * just encoding and clock rate.
         */
        setEncodingPreference(
                encodingEncoding, encoding.getClockRate(),
                priority);

        updateSupportedEncodings();
    }

    /**
     * Get the priority for a <tt>MediaFormat</tt>.
     *
     * @param encoding the <tt>MediaFormat</tt>
     * @return the priority
     */
    public int getPriority(MediaFormat encoding)
    {
        /*
         * Directly returning encodingPreference.get(encoding) will throw a
         * NullPointerException if encodingPreferences does not contain a
         * mapping for encoding.
         */
        Integer priority
            = encodingPreferences.get(getEncodingPreferenceKey(encoding));

        return (priority == null) ? 0 : priority;
    }

    /**
     * Returns all the available encodings for a specific <tt>MediaType</tt>.
     * This includes disabled ones (ones with priority 0).
     *
     * @param type the <tt>MediaType</tt> we would like to know the available
     * encodings of
     * @return array of <tt>MediaFormat</tt> supported for the
     * <tt>MediaType</tt>
     */
    public abstract MediaFormat[] getAllEncodings(MediaType type);

    /**
     * Returns the supported <tt>MediaFormat</tt>s i.e. the enabled available
     * <tt>MediaFormat</tt>s, sorted in decreasing priority. Returns only the
     * formats of type <tt>type</tt>.
     *
     * @param type the <tt>MediaType</tt> of the supported <tt>MediaFormat</tt>s
     * to get
     * @return an array of the supported <tt>MediaFormat</tt>s i.e. the enabled
     * available <tt>MediaFormat</tt>s sorted in decreasing priority. Returns
     * only the formats of type <tt>type</tt>.
     */
    public MediaFormat[] getEnabledEncodings(MediaType type)
    {
        Set<MediaFormat> supportedEncodings;

        switch (type)
        {
        case AUDIO:
            if (supportedAudioEncodings == null)
                supportedAudioEncodings = updateSupportedEncodings(type);
            supportedEncodings = supportedAudioEncodings;
            break;
        case VIDEO:
            if (supportedVideoEncodings == null)
                supportedVideoEncodings = updateSupportedEncodings(type);
            supportedEncodings = supportedVideoEncodings;
            break;
        default:
            return new MediaFormat[0];
        }

        return
            supportedEncodings.toArray(
                    new MediaFormat[supportedEncodings.size()]);
    }

    /**
     * Compares the two formats for order. Returns a negative integer, zero, or
     * a positive integer as the first format has been assigned a preference
     * higher, equal to, or greater than the one of the second.
     *
     * @param enc1 the first format to compare for preference.
     * @param enc2 the second format to compare for preference
     * @return a negative integer, zero, or a positive integer as the first
     * format has been assigned a preference higher, equal to, or greater than
     * the one of the second
     */
    protected abstract
            int compareEncodingPreferences(MediaFormat enc1, MediaFormat enc2);


    /**
     * Gets the key in {@link #encodingPreferences} which is associated with the
     * priority of a specific <tt>MediaFormat</tt>.
     *
     * @param encoding the <tt>MediaFormat</tt> to get the key in
     * {@link #encodingPreferences} of
     * @return the key in {@link #encodingPreferences} which is associated with
     * the priority of the specified <tt>encoding</tt>
     */
    protected String getEncodingPreferenceKey(MediaFormat encoding)
    {
        return encoding.getEncoding() + "/" + encoding.getClockRateString();
    }

    /**
     * Stores the format preferences in this instance in the given <tt>Map</tt>,
     * using <tt>prefix</tt> as a prefix to the key.
     * Entries in the format (prefix+formatName, formatPriority) will be added
     * to <tt>properties</tt>, one for each available format.
     * Note that a "." is not automatically added to <tt>prefix</tt>.
     *
     * @param properties The <tt>Map</tt> where entries will be added.
     * @param prefix The prefix to use.
     */
    public void storeProperties(Map<String, String> properties, String prefix)
    {
        for(MediaType mediaType : MediaType.values())
        {
            for(MediaFormat mediaFormat: getAllEncodings(mediaType))
            {
                properties.put(
                        prefix + getEncodingPreferenceKey(mediaFormat),
                        Integer.toString(getPriority(mediaFormat)));
            }
        }
    }

    /**
     * Stores the format preferences in this instance in the given <tt>Map</tt>.
     * Entries in the format (formatName, formatPriority) will be added to
     * <tt>properties</tt>, one for each available format.
     *
     * @param properties The <tt>Map</tt> where entries will be added.
     */
    public void storeProperties(Map<String,String> properties)
    {
        storeProperties(properties, "");
    }

    /**
     * Parses a <tt>Map<String, String></tt> and updates the format preferences
     * according to it. Does not use a prefix.
     *
     * @param properties The <tt>Map</tt> to parse.
     *
     * @see EncodingConfiguration#loadProperties(java.util.Map, String)
     */
    public void loadProperties(Map<String, String> properties)
    {
        loadProperties(properties, "");
    }

    /**
     * Parses a <tt>Map<String, String></tt> and updates the format preferences
     * according to it. For each entry, if it's key does not begin with
     * <tt>prefix</tt>, its ignored. If the key begins with <tt>prefix</tt>,
     * look for an encoding name after the last ".", and interpret the key
     * value as preference.
     *
     * @param properties The <tt>Map</tt> to parse.
     * @param prefix The prefix to use.
     */
    public void loadProperties(Map<String, String> properties, String prefix)
    {
        for(Map.Entry<String, String> entry : properties.entrySet())
        {
            String pName = entry.getKey();
            String prefStr = entry.getValue();
            String fmtName;

            if(!pName.startsWith(prefix))
                continue;

            if(pName.contains("."))
                fmtName = pName.substring(pName.lastIndexOf('.') + 1);
            else
                fmtName = pName;

            // legacy
            if (fmtName.contains("sdp"))
            {
                fmtName = fmtName.replaceAll("sdp", "");
                /*
                 * If the current version of the property name is also
                 * associated with a value, ignore the value for the legacy
                 * one.
                 */
                if (properties.containsKey(pName.replaceAll("sdp", "")))
                    continue;
            }

            int preference = -1;
            String encoding;
            double clockRate;

            try
            {
                preference = Integer.parseInt(prefStr);

                int encodingClockRateSeparator = fmtName.lastIndexOf('/');

                if (encodingClockRateSeparator > -1)
                {
                    encoding
                        = fmtName.substring(0, encodingClockRateSeparator);
                    clockRate
                        = Double.parseDouble(
                                fmtName.substring(
                                        encodingClockRateSeparator + 1));
                }
                else
                {
                    encoding = fmtName;
                    clockRate = MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED;
                }
            }
            catch (NumberFormatException nfe)
            {
                logger.warn(
                        "Failed to parse format ("
                            + fmtName
                            + ") or preference ("
                            + prefStr
                            + ").",
                        nfe);
                continue;
            }
            setEncodingPreference(encoding, clockRate, preference);
        }

        // now update the arrays so that they are returned by order of
        // preference.
        updateSupportedEncodings();
    }

    /**
     * Load the preferences stored in <tt>encodingConfiguration</tt>
     *
     * @param encodingConfiguration the <tt>EncodingConfiguration</tt> to load
     * preferences from.
     */
    public void loadEncodingConfiguration(
            EncodingConfiguration encodingConfiguration)
    {
        Map<String, String> properties = new HashMap<String, String>();

        encodingConfiguration.storeProperties(properties);
        loadProperties(properties);
    }

    /**
     * Returns <tt>true</tt> if there is at least one enabled format for media
     * type <tt>type</tt>.
     *
     * @param mediaType The media type, MediaType.AUDIO or MediaType.VIDEO
     * @return <tt>true</tt> if there is at least one enabled format for media
     * type <tt>type</tt>.
     */
    public boolean hasEnabledFormat(MediaType mediaType)
    {
        return (getEnabledEncodings(mediaType).length > 0);
    }
}
