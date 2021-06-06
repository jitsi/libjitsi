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
 * An abstract base implementation of {@link SCAudioClip} which is provided in
 * order to aid implementers by allowing them to extend
 * <tt>AbstractSCAudioClip</tt> and focus on the task of playing actual audio
 * once.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public abstract class AbstractSCAudioClip
    implements SCAudioClip
{
    /**
     * The thread pool used by the <tt>AbstractSCAudioClip</tt> instances in
     * order to reduce the impact of thread creation/initialization.
     */
    private static ExecutorService executorService;

    /**
     * The <tt>AudioNotifierService</tt> which has initialized this instance.
     * <tt>AbstractSCAudioClip</tt> monitors its <tt>mute</tt> property/state in
     * order to silence the played audio as appropriate/necessary.
     */
    protected final AudioNotifierService audioNotifier;

    private Runnable command;

    /**
     * The indicator which determines whether this instance was marked invalid.
     */
    private boolean invalid;

    /**
     * The indicator which determines whether this instance plays the audio it
     * represents in a loop.
     */
    private boolean looping;

    /**
     * The interval of time in milliseconds between consecutive plays of this
     * audio in a loop. If negative, this audio is played once only. If
     * non-negative, this audio may still be played once only if the
     * <tt>loopCondition</tt> specified to {@link #play(int, Callable)} is
     * <tt>null</tt> or its invocation fails.
     */
    private int loopInterval;

    /**
     * The indicator which determines whether the playback of this audio is
     * started.
     */
    private boolean started;

    /**
     * The <tt>Object</tt> used for internal synchronization purposes which
     * arise because this instance does the actual playback of audio in a
     * separate thread.
     * <p>
     * The synchronization root is exposed to extenders in case they would like
     * to, for example, get notified as soon as possible when this instance gets
     * stopped.
     */
    protected final Object sync = new Object();

    /**
     * The <tt>String</tt> uri of the audio to be played by this instance.
     * <tt>AbstractSCAudioClip</tt> does not use it and just remembers it in
     * order to make it available to extenders.
     */
    protected final String uri;

    protected AbstractSCAudioClip(
            String uri,
            AudioNotifierService audioNotifier)
    {
        this.uri = uri;
        this.audioNotifier = audioNotifier;
    }

    /**
     * Notifies this instance that its execution in its background/separate
     * thread dedicated to the playback of this audio is about to start playing
     * this audio for the first time. Regardless of whether this instance is to
     * be played once or multiple times in a loop, the method is called once in
     * order to allow extenders/implementers to perform one-time initialization
     * before this audio starts playing. The <tt>AbstractSCAudioClip</tt>
     * implementation does nothing.
     */
    protected void enterRunInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate
     * thread dedicated to the playback of this audio is about the start playing
     * this audio once. If this audio is to be played in a loop, the method is
     * invoked at the beginning of each iteration of the loop. Allows
     * extenders/implementers to perform per-loop iteration initialization. The
     * <tt>AbstractSCAudioClip</tt> implementation does nothing.
     */
    protected void enterRunOnceInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate
     * thread dedicated to the playback of this audio is about to stop playing
     * this audio once. Regardless of whether this instance is to be played once
     * or multiple times in a loop, the method is called once in order to allow
     * extenders/implementers to perform one-time cleanup after this audio stops
     * playing. The <tt>AbstractSCAudioClip</tt> implementation does nothing.
     */
    protected void exitRunInPlayThread()
    {
    }

    /**
     * Notifies this instance that its execution in its background/separate
     * thread dedicated to the playback of this audio is about to stop playing
     * this audio. If this audio is to be played in a loop, the method is called
     * at the end of each iteration of the loop. Allows extenders/implementers
     * to perform per-loop iteraction cleanup. The <tt>AbstractSCAudioClip</tt>
     * implementation does nothing.
     */
    protected void exitRunOnceInPlayThread()
    {
    }

    /**
     * Gets the interval of time in milliseconds between consecutive plays of
     * this audio.
     *
     * @return the interval of time in milliseconds between consecutive plays of
     * this audio. If negative, this audio will not be played in a loop and will
     * be played once only.
     */
    public int getLoopInterval()
    {
        return loopInterval;
    }

    /**
     * Stops this audio without setting the isLooping property in the case of
     * a looping audio. The AudioNotifier uses this method to stop the audio
     * when setMute(true) is invoked. This allows us to restore all looping
     * audios when the sound is restored by calling setMute(false).
     */
    protected void internalStop()
    {
        boolean interrupted = false;

        synchronized (sync)
        {
            started = false;
            sync.notifyAll();

            while (command != null)
            {
                try
                {
                    /*
                     * Technically, we do not need a timeout. If a notifyAll()
                     * is not called to wake us up, then we will likely already
                     * be in trouble. Anyway, use a timeout just in case.
                     */
                    sync.wait(500);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
        }

        if (interrupted)
            Thread.currentThread().interrupt();
    }

    /**
     * Determines whether this instance is invalid. <tt>AbstractSCAudioClip</tt>
     * does not use the <tt>invalid</tt> property/state of this instance and
     * merely remembers the value which was set on it by
     * {@link #setInvalid(boolean)}. The default value is <tt>false</tt> i.e.
     * this instance is valid by default.
     *
     * @return <tt>true</tt> if this instance is invalid; otherwise,
     * <tt>false</tt>
     */
    public boolean isInvalid()
    {
        return invalid;
    }

    /**
     * Determines whether this instance plays the audio it represents in a loop.
     *
     * @return <tt>true</tt> if this instance plays the audio it represents in a
     * loop; <tt>false</tt>, otherwise
     */
    public boolean isLooping()
    {
        return looping;
    }

    /**
     * Determines whether this audio is started i.e. a <tt>play</tt> method was
     * invoked and no subsequent <tt>stop</tt> has been invoked yet.
     *
     * @return <tt>true</tt> if this audio is started; otherwise, <tt>false</tt>
     */
    public boolean isStarted()
    {
        synchronized (sync)
        {
            return started;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #play(int, Callable)} with <tt>loopInterval</tt>
     * <tt>-1</tt> and <tt>loopCondition</tt> <tt>null</tt> in order to conform
     * with the contract for the behavior of this method specified by the
     * interface <tt>SCAudioClip</tt>.
     */
    public void play()
    {
        play(-1, null);
    }

    /**
     * {@inheritDoc}
     */
    public void play(int loopInterval, final Callable<Boolean> loopCondition)
    {
        if ((loopInterval >= 0) && (loopCondition == null))
            loopInterval = -1;

        synchronized (sync)
        {
            if (command != null)
                return;

            setLoopInterval(loopInterval);
            setLooping(loopInterval >= 0);

            /*
             * We use a thread pool shared among all AbstractSCAudioClip
             * instances in order to reduce the impact of thread
             * creation/initialization.
             */
            ExecutorService executorService;

            synchronized (AbstractSCAudioClip.class)
            {
                if (AbstractSCAudioClip.executorService == null)
                {
                    AbstractSCAudioClip.executorService
                        = Executors.newCachedThreadPool();
                }
                executorService = AbstractSCAudioClip.executorService;
            }

            try
            {
                started = false;
                command
                    = new Runnable()
                    {
                        public void run()
                        {
                            try
                            {
                                synchronized (sync)
                                {
                                    /*
                                     * We have to wait for
                                     * play(int,Callable<Boolean>) to let go of
                                     * sync i.e. be ready with setting up the
                                     * whole AbstractSCAudioClip state;
                                     * otherwise, this Runnable will most likely
                                     * prematurely seize to exist.
                                     */
                                    if (!equals(command))
                                        return;
                                }

                                runInPlayThread(loopCondition);
                            }
                            finally
                            {
                                synchronized (sync)
                                {
                                    if (equals(command))
                                    {
                                        command = null;
                                        started = false;
                                        sync.notifyAll();
                                    }
                                }
                            }
                        }
                    };
                executorService.execute(command);
                started = true;
            }
            finally
            {
                if (!started)
                    command = null;
                sync.notifyAll();
            }
        }
    }

    /**
     * Runs in a background/separate thread dedicated to the actual playback of
     * this audio and plays this audio once or in a loop.
     *
     * @param loopCondition a <tt>Callback&lt;Boolean&gt;</tt> which represents
     * the condition on which this audio will play more than once. If
     * <tt>null</tt>, this audio will play once only. If an invocation of
     * <tt>loopCondition</tt> throws a <tt>Throwable</tt>, this audio will
     * discontinue playing.
     */
    private void runInPlayThread(Callable<Boolean> loopCondition)
    {
        enterRunInPlayThread();
        try
        {
            boolean interrupted = false;

            while (isStarted())
            {
                if (audioNotifier.isMute())
                {
                    /*
                     * If the AudioNotifierService has muted the sounds, we will
                     * have to really wait a bit in order to not fall into a
                     * busy wait.
                     */
                    synchronized (sync)
                    {
                        try
                        {
                            sync.wait(500);
                        }
                        catch (InterruptedException ie)
                        {
                            interrupted = true;
                        }
                    }
                }
                else
                {
                    enterRunOnceInPlayThread();
                    try
                    {
                        if (!runOnceInPlayThread())
                            break;
                    }
                    finally
                    {
                        exitRunOnceInPlayThread();
                    }
                }

                if(!isLooping())
                    break;

                synchronized (sync)
                {
                    /*
                     * We may have waited to acquire sync. Before beginning the
                     * wait for loopInterval, make sure we should continue.
                     */
                    if (!isStarted())
                        break;

                    try
                    {
                        int loopInterval = getLoopInterval();

                        /*
                         * XXX The value 0 means that this instance should loop
                         * playing without waiting but it means infinity to
                         * Object.wait(long).
                         */
                        if (loopInterval > 0)
                            sync.wait(loopInterval);
                    }
                    catch (InterruptedException ie)
                    {
                        interrupted = true;
                    }
                }

                /*
                 * After this audio has been played once, loopCondition should
                 * be consulted to approve each subsequent iteration of the
                 * loop. Before invoking loopCondition which may take noticeable
                 * time to execute, make sure that this instance has not been
                 * stopped while it waited for loopInterval.
                 */
                if (!isStarted())
                    break;

                if (loopCondition == null)
                {
                    /*
                     * The interface contract is that this audio plays once
                     * only if the loopCondition is null.
                     */
                    break;
                }

                /*
                 * The contract of the SCAudioClip interface with respect to
                 * loopCondition is that the loop will continue only if
                 * loopCondition successfully and explicitly evaluates to true.
                 */
                boolean loop = false;

                try
                {
                    loop = loopCondition.call();
                }
                catch (Throwable t)
                {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;

                    /*
                     * If loopCondition fails to successfully and explicitly
                     * evaluate to true, this audio should seize to play in a
                     * loop. Otherwise, there is a risk that whoever requested
                     * this audio to be played in a loop and provided the
                     * loopCondition will continue to play it forever.
                     */
                }
                if (!loop)
                {
                    /*
                     * The loopCondition failed to successfully and explicitly
                     * evaluate to true so the loop will not continue.
                     */
                    break;
                }
            }

            if (interrupted)
                Thread.currentThread().interrupt();
        }
        finally
        {
            exitRunInPlayThread();
        }
    }

    /**
     * Plays this audio once.
     *
     * @return <tt>true</tt> if subsequent plays of this audio and,
     * respectively, the method are to be invoked if this audio is to be played
     * in a loop; otherwise, <tt>false</tt>. The value reflects an
     * implementation-specific loop condition, is not dependent on
     * <tt>loopInterval</tt> and <tt>loopCondition</tt> and is combined with the
     * latter in order to determine whether there will be a subsequent iteration
     * of the playback loop.
     */
    protected abstract boolean runOnceInPlayThread();

    /**
     * Sets the indicator which determines whether this instance is invalid.
     * <tt>AbstractSCAudioClip</tt> does not use the <tt>invalid</tt>
     * property/state of this instance and merely remembers the value which was
     * set on it so that it can be retrieved by {@link #isInvalid()}. The
     * default value is <tt>false</tt> i.e. this instance is valid by default.
     *
     * @param invalid <tt>true</tt> to mark this instance invalid or
     * <tt>false</tt> to mark it valid
     */
    public void setInvalid(boolean invalid)
    {
        this.invalid = invalid;
    }

    /**
     * Sets the indicator which determines whether this audio is to play in a
     * loop. Generally, public invocation of the method is not necessary because
     * the looping is controlled by the <tt>loopInterval</tt> property of this
     * instance and the <tt>loopInterval</tt> and <tt>loopCondition</tt>
     * parameters of {@link #play(int, Callable)} anyway.
     *
     * @param  looping <tt>true</tt> to mark this instance that it should play
     *      the audio it represents in a loop; otherwise, <tt>false</tt>
     */
    public void setLooping(boolean looping)
    {
        synchronized (sync)
        {
            if (this.looping != looping)
            {
                this.looping = looping;
                sync.notifyAll();
            }
        }
    }

    /**
     * Sets the interval of time in milliseconds between consecutive plays of
     * this audio in a loop. If negative, this audio is played once only. If
     * non-negative, this audio may still be played once only if the
     * <tt>loopCondition</tt> specified to {@link #play(int, Callable)} is
     * <tt>null</tt> or its invocation fails.
     *
     * @param loopInterval the interval of time in milliseconds between
     * consecutive plays of this audio in a loop to be set on this instance
     */
    public void setLoopInterval(int loopInterval)
    {
        synchronized (sync)
        {
            if (this.loopInterval != loopInterval)
            {
                this.loopInterval = loopInterval;
                sync.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop()
    {
        internalStop();
        setLooping(false);
    }
}
