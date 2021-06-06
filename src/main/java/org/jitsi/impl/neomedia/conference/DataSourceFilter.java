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
package org.jitsi.impl.neomedia.conference;

import javax.media.protocol.*;

/**
 * Represents a filter which determines whether a specific <tt>DataSource</tt>
 * is to be selected or deselected by the caller of the filter.
 *
 * @author Lyubomir Marinov
 */
public interface DataSourceFilter
{
    /**
     * Determines whether a specific <tt>DataSource</tt> is accepted by this
     * filter i.e. whether the caller of this filter should include it in its
     * selection.
     *
     * @param dataSource the <tt>DataSource</tt> to be checked whether it is
     * accepted by this filter
     * @return <tt>true</tt> if this filter accepts the specified
     * <tt>DataSource</tt> i.e. if the caller of this filter should include it
     * in its selection; otherwise, <tt>false</tt>
     */
    public boolean accept(DataSource dataSource);
}
