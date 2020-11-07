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
package org.jitsi.util.event;

import java.util.*;

/**
 * Defines the notification support informing about changes in the availability
 * of visual <tt>Component</tt>s representing video such as adding and
 * removing.
 *
 * @author Lyubomir Marinov
 */
public interface VideoListener
    extends EventListener
{

    /**
     * Notifies that a visual <tt>Component</tt> representing video has been
     * added to the provider this listener has been added to.
     *
     * @param event a <tt>VideoEvent</tt> describing the added visual
     * <tt>Component</tt> representing video and the provider it was added into
     */
    void videoAdded(VideoEvent event);

    /**
     * Notifies that a visual <tt>Component</tt> representing video has been
     * removed from the provider this listener has been added to.
     *
     * @param event a <tt>VideoEvent</tt> describing the removed visual
     * <tt>Component</tt> representing video and the provider it was removed
     * from
     */
    void videoRemoved(VideoEvent event);

    /**
     * Notifies about an update to a visual <tt>Component</tt> representing
     * video.
     *
     * @param event a <tt>VideoEvent</tt> describing the visual
     * <tt>Component</tt> related to the update and the details of the specific
     * update
     */
    void videoUpdate(VideoEvent event);
}
