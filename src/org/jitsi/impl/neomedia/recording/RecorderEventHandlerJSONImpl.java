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

import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;
import org.jitsi.utils.*;
import org.json.simple.*;

import java.io.*;
import java.util.*;

/**
 * Implements a <tt>RecorderEventHandler</tt> which handles
 * <tt>RecorderEvents</tt> by writing them to a file in JSON format.
 *
 * @author Boris Grozev
 */
public class RecorderEventHandlerJSONImpl
    implements RecorderEventHandler
{
    /**
     * The <tt>Logger</tt> used by the <tt>RecorderEventHandlerJSONImpl</tt>
     * class and its instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(RecorderEventHandlerJSONImpl.class);

    /**
     * Compares <tt>RecorderEvent</tt>s by their instant (e.g. timestamp).
     */
    private static final Comparator<RecorderEvent> eventComparator
            = new Comparator<RecorderEvent>() {
        @Override
        public int compare(RecorderEvent a, RecorderEvent b)
        {
            return Long.compare(a.getInstant(), b.getInstant());
        }
    };

    File file;
    private boolean closed = false;

    private final List<RecorderEvent> audioEvents
            = new LinkedList<RecorderEvent>();

    private final List<RecorderEvent> videoEvents
            = new LinkedList<RecorderEvent>();

    /**
     * {@inheritDoc}
     */
    public RecorderEventHandlerJSONImpl(String filename)
        throws IOException
    {
        file = new File(filename);
        if (!file.createNewFile())
            throw new IOException("File exists or cannot be created: " + file);

        if (!file.canWrite())
            throw new IOException("Cannot write to file: " + file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean handleEvent(RecorderEvent ev)
    {
        if (closed)
            return false;

        MediaType mediaType = ev.getMediaType();
        RecorderEvent.Type type = ev.getType();
        long duration = ev.getDuration();
        long ssrc = ev.getSsrc();

        /*
         * For a RECORDING_ENDED event without a valid instant, find it's
         * associated (i.e. with the same SSRC) RECORDING_STARTED event and
         * compute the RECORDING_ENDED instance based on its duration.
         */
        if (RecorderEvent.Type.RECORDING_ENDED.equals(type)
              && ev.getInstant() == -1
              && duration != -1)
        {
            List<RecorderEvent> events =
                    MediaType.AUDIO.equals(mediaType)
                    ? audioEvents
                    : videoEvents;

            RecorderEvent start = null;
            for (RecorderEvent e : events)
            {
                if (RecorderEvent.Type.RECORDING_STARTED.equals(e.getType())
                      && e.getSsrc() == ssrc)
                {
                    start = e;
                    break;
                }
            }

            if (start != null)
                ev.setInstant(start.getInstant() + duration);
        }

        if (MediaType.AUDIO.equals(mediaType))
            audioEvents.add(ev);
        else if (MediaType.VIDEO.equals(mediaType))
            videoEvents.add(ev);

        try
        {
            writeAllEvents();
        }
        catch (IOException ioe)
        {
            logger.warn("Failed to write recorder events to file: ", ioe);
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void close()
    {
        //XXX do we want to write everything again?
        try
        {
            writeAllEvents();
        }
        catch (IOException ioe)
        {
            logger.warn("Failed to write recorder events to file: " + ioe);
        }
        finally
        {
            closed = true;
        }
    }

    private void writeAllEvents()
            throws IOException
    {
        Collections.sort(audioEvents, eventComparator);
        Collections.sort(videoEvents, eventComparator);
        int nbAudio = audioEvents.size();
        int nbVideo = videoEvents.size();

        if (nbAudio + nbVideo > 0)
        {
            FileWriter writer = new FileWriter(file, false);

            writer.write("{\n");

            if (nbAudio > 0)
            {
                writer.write("  \"audio\" : [\n");
                writeEvents(audioEvents, writer);

                if (nbVideo > 0)
                    writer.write("  ],\n\n");
                else
                    writer.write("  ]\n\n");
            }

            if (nbVideo > 0)
            {
                writer.write("  \"video\" : [\n");
                writeEvents(videoEvents, writer);
                writer.write("  ]\n");
            }

            writer.write("}\n");

            writer.close();
        }
    }

    private void writeEvents(List<RecorderEvent> events,
                             FileWriter writer)
            throws IOException
    {
        int idx = 0;
        int size = events.size();
        for (RecorderEvent ev : events)
        {
            if (++idx == size)
                writer.write("    " + getJSON(ev) + "\n");
            else
                writer.write("    " + getJSON(ev)+",\n");
        }
    }

    @SuppressWarnings("unchecked")
    private String getJSON(RecorderEvent ev)
    {
        JSONObject json = new JSONObject();
        json.put("instant", ev.getInstant());

        json.put("type", ev.getType().toString());

        MediaType mediaType = ev.getMediaType();
        if (mediaType != null)
            json.put("mediaType", mediaType.toString());

        json.put("ssrc", ev.getSsrc());

        long audioSsrc = ev.getAudioSsrc();
        if (audioSsrc != -1)
            json.put("audioSsrc", audioSsrc);

        RecorderEvent.AspectRatio aspectRatio = ev.getAspectRatio();
        if (aspectRatio != RecorderEvent.AspectRatio.ASPECT_RATIO_UNKNOWN)
            json.put("aspectRatio", aspectRatio.toString());

        long rtpTimestamp = ev.getRtpTimestamp();
        if (rtpTimestamp != -1)
            json.put("rtpTimestamp", rtpTimestamp);

        String endpointId = ev.getEndpointId();
        if (endpointId != null)
            json.put("endpointId", endpointId);

        String filename = ev.getFilename();
        if (filename != null)
        {
            String bareFilename = filename;
            int idx = filename.lastIndexOf('/');
            int len = filename.length();
            if  (idx != -1 && idx != len-1)
                bareFilename = filename.substring(1 + idx, len);

            json.put("filename", bareFilename);
        }

        return json.toJSONString();
    }
}
