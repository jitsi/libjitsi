/*
 * Copyright @ 2018 Atlassian Pty Ltd
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

/**
 * Represents a time series point. The <tt>toString</tt> method outputs
 * the time series point in influx DB line protocol format.
 *
 * @author George Politis
 */
public interface TimeSeriesPoint
{
    /**
     * Adds a key in the time series point.
     */
    TimeSeriesPoint addKey(String key, Object value);

    /**
     * Adds a field in the time series point.
     */
    TimeSeriesPoint addField(String key, Object value);
}
