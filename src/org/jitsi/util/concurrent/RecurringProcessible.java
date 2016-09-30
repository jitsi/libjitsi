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
package org.jitsi.util.concurrent;

/**
 * Represents an asynchronous processible task which determines the intervals
 * (the lengths of which may vary) at which it is to be invoked.
 *
 * webrtc/modules/interface/module.h
 *
 * @author Lyubomir Marinov
 */
public interface RecurringProcessible
{
    /**
     * Returns the number of milliseconds until this instance wants a worker
     * thread to call {@link #process()}. The method is called on the same
     * worker thread as Process will be called on.
     *
     * @return the number of milliseconds until this instance wants a worker
     * thread to call {@link #process()}
     */
    long getTimeUntilNextProcess();

    /**
     * Process any pending tasks such as timeouts. Called on a worker thread.
     *
     * @return ignored (and unknown) at the time of this writing
     */
    long process();
}
