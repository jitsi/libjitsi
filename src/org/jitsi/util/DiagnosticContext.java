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

import java.util.*;
import java.util.concurrent.*;

/**
 * A {@link DiagnosticContext} implementation backed by a
 * {@link ConcurrentHashMap}.
 *
 * @author George Politis
 */
public class DiagnosticContext
{
    /**
     * The store for this diagnostic context.
     */
    private final Map<String, Object> ctxKeys = new ConcurrentHashMap<>();

    /**
     * Puts a variable in the diagnostic context store.
     */
    public void put(String key, Object val)
    {
        if (key != null && val != null)
        {
            ctxKeys.put(key, val);
        }
    }

    /**
     * Makes a new time series point.
     */
    public TimeSeriesPoint makeTimeSeriesPoint(String timeSeriesName)
    {
        return new TimeSeriesPoint(timeSeriesName);
    }

    /**
     * Represents a time series point. The <tt>toString</tt> method outputs
     * the time series point in influx DB line protocol format.
     */
    public class TimeSeriesPoint
    {
        private final String timeSeriesName;

        private final Map<String, Object> keys;

        private final Map<String, Object> fields;

        private long tsMs = -1;

        /**
         * Ctor.
         */
        public TimeSeriesPoint(String timeSeriesName)
        {
            this.timeSeriesName = timeSeriesName;
            this.keys = new HashMap<>(ctxKeys /* snapshot of the ctx keys */);
            this.fields = new HashMap<>();
        }

        /**
         * Adds a key to the time series point.
         */
        public TimeSeriesPoint addKey(String key, Object value)
        {
            if (key != null && value != null)
            {
                keys.put(key, value);
            }
            return this;
        }

        /**
         * Adds a field to the time series point.
         */
        public TimeSeriesPoint addField(String key, Object value)
        {
            if (key != null && value != null)
            {
                fields.put(key, value);
            }
            return this;
        }

        /**
         * Sets the timestamp of the time series point.
         */
        public TimeSeriesPoint setTimestampMs(long tsMs)
        {
            this.tsMs = tsMs;
            return this;
        }

        /**
         * Prints the time series point in influx DB line protocol format.
         */
        public String toString()
        {
            StringBuilder sb = new StringBuilder(timeSeriesName);
            for (Map.Entry<String, Object> keyEntry : keys.entrySet())
            {
                sb.append(",")
                    .append(keyEntry.getKey())
                    .append("=")
                    .append(keyEntry.getValue());
            }

            if (!fields.isEmpty())
            {
                boolean isFirstField = true;
                for (Map.Entry<String, Object> fieldEntry : fields.entrySet())
                {
                    sb.append(isFirstField ? " " : ",")
                        .append(fieldEntry.getKey())
                        .append("=")
                        .append(fieldEntry.getValue());

                    isFirstField = false;
                }
            }

            if (tsMs != -1)
            {
                sb.append(" ").append(tsMs);
            }

            return sb.toString();
        }
    }
}
