/*
 * Copyright @ 2017 Atlassian Pty Ltd
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

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A {@link DiagnosticContext} implementation backed by a
 * {@link ConcurrentHashMap}.
 *
 * @author George Politis
 */
public class DiagnosticContext
    extends ConcurrentHashMap<String, Object>
{
    /**
     * Makes a new time series point without a timestamp. This is recommended
     * for time series where the exact timestamp value isn't important and can
     * be deduced via other means (i.e. Java logging timestamps).
     *
     * @param timeSeriesName the name of the time series
     */
    public TimeSeriesPoint makeTimeSeriesPoint(String timeSeriesName)
    {
        return makeTimeSeriesPoint(timeSeriesName, Instant.now());
    }

    /**
     * Makes a new time series point with a timestamp. This is recommended for
     * time series where it's important to have the exact timestamp value.
     *
     * @param timeSeriesName the name of the time series
     * @param tsMs the timestamp of the time series point (in millis)
     */
    public TimeSeriesPoint makeTimeSeriesPoint(String timeSeriesName, long tsMs)
    {
        return makeTimeSeriesPoint(
            timeSeriesName, Instant.ofEpochMilli(tsMs));
    }

    /**
     * Makes a new time series point with a timestamp. This is recommended for
     * time series where it's important to have the exact timestamp value.
     *
     * @param timeSeriesName the name of the time series
     * @param instant the timestamp of the time series point (in millis)
     */
    public TimeSeriesPoint makeTimeSeriesPoint(
        String timeSeriesName, Instant instant)
    {
        return new TimeSeriesPoint(this)
            .addField("series", timeSeriesName)
            .addField("time",
                instant.getEpochSecond() + "." + instant.getNano());
    }

    public class TimeSeriesPoint
        extends HashMap<String, Object>
    {
        TimeSeriesPoint(Map<String, Object> m)
        {
            super(m);
        }

        /**
         * Adds a field to the time series point.
         */
        public TimeSeriesPoint addField(String key, Object value)
        {
            super.put(key, value);
            return this;
        }
    }
}
