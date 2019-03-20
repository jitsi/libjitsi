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
package org.jitsi.service.neomedia.recording;

import org.jitsi.utils.*;
import org.json.simple.*;

/**
 * Represents an event related to media recording, such as a new SSRC starting
 * to be recorded.
 *
 * @author Boris Grozev
 * @author Vladimir Marinov
 */
public class RecorderEvent
{
    /**
     * The type of this <tt>RecorderEvent</tt>.
     */
    private Type type = Type.OTHER;

    /**
     * A timestamp for this <tt>RecorderEvent</tt>.
     */
    private long instant = -1;

    /**
     * The SSRC associated with this <tt>RecorderEvent</tt>.
     */
    private long ssrc = -1;

    /**
     * The SSRC of an audio stream associated with this <tt>RecorderEvent</tt>.
     */
    private long audioSsrc = -1;

    /**
     * An RTP timestamp for this <tt>RecorderEvent</tt>.
     */
    private long rtpTimestamp = -1;

    /**
     * An NTP timestamp (represented as a double in seconds)
     * for this <tt>RecorderEvent</tt>.
     */
    private double ntpTime = -1.0;

    /**
     * Duration associated with this <tt>RecorderEvent</tt>.
     */
    private long duration = -1;

    /**
     * An aspect ratio associated with this <tt>RecorderEvent</tt>.
     */
    private AspectRatio aspectRatio = AspectRatio.ASPECT_RATIO_UNKNOWN;

    /**
     * A file name associated with this <tt>RecorderEvent</tt>.
     */
    private String filename;

    /**
     * The media type associated with this <tt>RecorderEvent</tt>.
     */
    private MediaType mediaType = null;

    /**
     * The name of the participant associated with this <tt>RecorderEvent</tt>.
     */
    private String participantName = null;

    /**
     * A textual description of the participant associated with this
     * <tt>RecorderEvent</tt>. (human readable)
     */
    private String participantDescription = null;

    private String endpointId = null;

    private boolean disableOtherVideosOnTop = false;

    /**
     * Constructs a <tt>RecorderEvent</tt>.
     */
    public RecorderEvent()
    {
    }

    /**
     * Constructs a <tt>RecorderEvent</tt> and tries to parse its fields from
     * <tt>json</tt>.
     * @param json a JSON object, containing fields with which to initialize
     * the fields of this <tt>RecorderEvent</tt>.
     */
    public RecorderEvent(JSONObject json)
    {
        Object o = json.get("type");
        if (o != null)
            type = Type.parseString(o.toString());

        o = json.get("instant");
        if (o != null &&
                (o instanceof Long || o instanceof Integer))
            instant = (Long)o;

        o = json.get("ssrc");
        if (o != null &&
                (o instanceof Long || o instanceof Integer))
            ssrc = (Long)o;

        o = json.get("audioSsrc");
        if (o != null &&
                (o instanceof Long || o instanceof Integer))
            audioSsrc = (Long)o;

        o = json.get("ntpTime");
        if (o != null &&
                (o instanceof Long || o instanceof Integer))
            ntpTime = (Long) o;

        o = json.get("duration");
        if (o != null &&
                (o instanceof Long || o instanceof Integer))
            duration = (Long) o;

        o = json.get("aspectRatio");
        if (o != null)
            aspectRatio = AspectRatio.parseString(o.toString());

        o = json.get("filename");
        if (o != null)
            filename = o.toString();

        o = json.get("participantName");
        if (o != null && o instanceof String)
            participantName = (String) o;

        o = json.get("participantDescription");
        if (o != null && o instanceof String)
            participantDescription = (String) o;

        o = json.get("endpointId");
        if (o != null && o instanceof String)
            endpointId = (String) o;

        o = json.get("mediaType");
        if (o != null)
        {
            try
            {
                mediaType = MediaType.parseString(o.toString());
            }
            catch (IllegalArgumentException iae)
            {
                //complain?
            }
        }
        
        o = json.get("disableOtherVideosOnTop");
        if (o != null)
        {
            if (o instanceof Boolean)
                disableOtherVideosOnTop = (Boolean) o;
            else if (o instanceof String)
                disableOtherVideosOnTop = Boolean.valueOf((String) o);
        }
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public long getInstant()
    {
        return instant;
    }

    public void setInstant(long instant)
    {
        this.instant = instant;
    }

    public long getRtpTimestamp()
    {
        return rtpTimestamp;
    }

    public void setRtpTimestamp(long rtpTimestamp)
    {
        this.rtpTimestamp = rtpTimestamp;
    }

    public long getSsrc()
    {
        return ssrc;
    }

    public void setSsrc(long ssrc)
    {
        this.ssrc = ssrc;
    }

    public long getAudioSsrc()
    {
        return audioSsrc;
    }

    public void setAudioSsrc(long audioSsrc)
    {
        this.audioSsrc = audioSsrc;
    }

    public AspectRatio getAspectRatio()
    {
        return aspectRatio;
    }

    public void setAspectRatio(AspectRatio aspectRatio)
    {
        this.aspectRatio = aspectRatio;
    }

    public String getFilename()
    {
        return filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public MediaType getMediaType()
    {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType)
    {
        this.mediaType = mediaType;
    }

    public long getDuration()
    {
        return duration;
    }

    public void setDuration(long duration)
    {
        this.duration = duration;
    }

    public String getParticipantName()
    {
        return participantName;
    }

    public void setParticipantName(String participantName)
    {
        this.participantName = participantName;
    }

    public String getParticipantDescription()
    {
        return participantDescription;
    }

    public void setParticipantDescription(String participantDescription)
    {
        this.participantDescription = participantDescription;
    }
    
    public boolean getDisableOtherVideosOnTop()
    {
        return disableOtherVideosOnTop;
    }
    
    public void setDisableOtherVideosOnTop(boolean disableOtherVideosOnTop)
    {
        this.disableOtherVideosOnTop = disableOtherVideosOnTop ;
    }

    public double getNtpTime()
    {
        return ntpTime;
    }

    public void setNtpTime(double ntpTime)
    {
        this.ntpTime = ntpTime;
    }

    public String toString()
    {
        return "RecorderEvent: " + getType().toString() + " @" + getInstant()
                + "(" + getMediaType() + ")";
    }

    public void setEndpointId(String endpointId)
    {
        this.endpointId = endpointId;
    }

    public String getEndpointId()
    {
        return endpointId;
    }

    /**
     * <tt>RecorderEvent</tt> types.
     */
    public enum Type
    {
        /**
         * Indicates the start of a recording.
         */
        RECORDING_STARTED("RECORDING_STARTED"),

        /**
         * Indicates the end of a recording.
         */
        RECORDING_ENDED("RECORDING_ENDED"),

        /**
         * Indicates that the active speaker has changed. The 'audioSsrc'
         * field indicates the SSRC of the audio stream which is now considered
         * active, and the 'ssrc' field contains the SSRC of a video stream
         * associated with the now active audio stream.
         */
        SPEAKER_CHANGED("SPEAKER_CHANGED"),

        /**
         * Indicates that a new stream was added. This is different than
         * RECORDING_STARTED, because a new stream might be saved to an existing
         * recording (for example, a new audio stream might be added to a mix)
         */
        STREAM_ADDED("STREAM_ADDED"),

        /**
         * Default value.
         */
        OTHER("OTHER");

        private String name;

        private Type(String name)
        {
            this.name = name;
        }

        public String toString()
        {
            return name;
        }

        public static Type parseString(String str)
        {
            for (Type type : Type.values())
                if (type.toString().equals(str))
                    return type;
            return OTHER;
        }
    }

    /**
     * Video aspect ratio.
     */
    public enum AspectRatio
    {
        ASPECT_RATIO_16_9("16_9", 16./9),
        ASPECT_RATIO_4_3("4_3", 4./3),
        ASPECT_RATIO_UNKNOWN("UNKNOWN", 1.);

        public double scaleFactor;
        private String stringValue;

        private AspectRatio(String stringValue, double scaleFactor)
        {
            this.scaleFactor = scaleFactor;
            this.stringValue = stringValue;
        }

        @Override
        public String toString()
        {
            return stringValue;
        }

        public static AspectRatio parseString(String str)
        {
            for (AspectRatio aspectRatio : AspectRatio.values())
                if (aspectRatio.toString().equals(str))
                    return aspectRatio;
            return ASPECT_RATIO_UNKNOWN;
        }
    }

}
