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
package org.jitsi.service.audionotifier;

import java.util.concurrent.*;

/**
 * Represents an audio clip which could be played (optionally, in a loop) and
 * stopped..
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public interface SCAudioClip
{
    /**
     * Starts playing this audio once only. The method behaves as if
     * {@link #play(int, Callable)} was invoked with a negative
     * <tt>loopInterval</tt> and/or <tt>null</tt> <tt>loopCondition</tt>.
     */
    public void play();

    /**
     * Starts playing this audio. Optionally, the playback is looped.
     *
     * @param loopInterval the interval of time in milliseconds between
     * consecutive plays of this audio. If negative, this audio is played once
     * only and <tt>loopCondition</tt> is ignored.
     * @param loopCondition a <tt>Callable</tt> which is called at the beginning
     * of each iteration of looped playback of this audio except the first one
     * to determine whether to continue the loop. If <tt>loopInterval</tt> is
     * negative or <tt>loopCondition</tt> is <tt>null</tt>, this audio is played
     * once only.
     */
    public void play(int loopInterval, Callable<Boolean> loopCondition);

    /**
     * Stops playing this audio.
     */
    public void stop();

    /**
     * Determines whether this audio is started i.e. a <tt>play</tt> method was
     * invoked and no subsequent <tt>stop</tt> has been invoked yet.
     *
     * @return <tt>true</tt> if this audio is started; otherwise, <tt>false</tt>
     */
    public boolean isStarted();
}
