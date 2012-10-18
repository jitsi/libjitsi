/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.audionotifier;

import java.net.*;
import java.util.concurrent.*;

/**
 * An abstract base implementation of {@link SCAudioClip}. 
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public abstract class AbstractSCAudioClip
    implements SCAudioClip
{
    protected final AudioNotifierService audioNotifier;

    private boolean isInvalid;

    private boolean isLooping;

    private int loopInterval;

    private boolean started = false;
    
    private final Object sync = new Object();

    protected final URL url;

    protected AbstractSCAudioClip(
            URL url,
            AudioNotifierService audioNotifier)
    {
        this.url = url;
        this.audioNotifier = audioNotifier;
    }

    protected void enterRunInPlayThread()
    {
        // TODO Auto-generated method stub
    }

    protected void enterRunOnceInPlayThread()
    {
        // TODO Auto-generated method stub
    }

    protected void exitRunInPlayThread()
    {
        // TODO Auto-generated method stub
    }

    protected void exitRunOnceInPlayThread()
    {
        // TODO Auto-generated method stub
    }

    /**
     * Returns the loop interval if this audio is looping.
     * @return the loop interval if this audio is looping
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
    public void internalStop()
    {
        synchronized (sync) 
        {
            if (url != null && started) 
            {
                started = false;
                sync.notifyAll();
            }
        }
    }

    /**
     * Returns TRUE if this audio is invalid, FALSE otherwise.
     *
     * @return TRUE if this audio is invalid, FALSE otherwise
     */
    public boolean isInvalid()
    {
        return isInvalid;
    }

    /**
     * Returns TRUE if this audio is currently playing in loop, FALSE otherwise.
     * @return TRUE if this audio is currently playing in loop, FALSE otherwise.
     */
    public boolean isLooping()
    {
        return isLooping;
    }

    protected boolean isStarted()
    {
        return started;
    }

    /**
     * {@inheritDoc}
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

        setLoopInterval(loopInterval);
        setIsLooping(loopInterval >= 0);

        if ((url != null) && !audioNotifier.isMute())
        {
            started = true;
            new Thread()
                    {
                        @Override
                        public void run()
                        {
                            runInPlayThread(loopCondition);
                        }
                    }.start();
        }
    }

    private void runInPlayThread(Callable<Boolean> loopCondition)
    {
        enterRunInPlayThread();
        try
        {
            while (started)
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

                if(isLooping())
                {
                    synchronized(sync)
                    {
                        if (started)
                        {
                            try
                            {
                                /*
                                 * Only wait if longer than 0; otherwise, we
                                 * will wait forever.
                                 */
                                if(getLoopInterval() > 0)
                                    sync.wait(getLoopInterval());
                            }
                            catch (InterruptedException e)
                            {
                            }
                        }
                    }

                    if (started)
                    {
                        if (loopCondition == null)
                        {
                            /*
                             * The interface contract is that this audio plays
                             * once only if the loopCondition is null.
                             */
                            break;
                        }
                        else
                        {
                            boolean loop = false;

                            try
                            {
                                loop = loopCondition.call();
                            }
                            catch (Throwable t)
                            {
                                if (t instanceof ThreadDeath)
                                    throw (ThreadDeath) t;
                            }
                            if (!loop)
                            {
                                /*
                                 * The loopCondition failed to evaluate to true
                                 * so the loop will not continue.
                                 */
                                break;
                            }
                        }
                    }
                    else
                        break;
                }
                else
                    break;
            }
        }
        finally
        {
            exitRunInPlayThread();
        }
    }

    protected abstract boolean runOnceInPlayThread();

    /**
     * Marks this audio as invalid or not.
     *
     * @param isInvalid TRUE to mark this audio as invalid, FALSE otherwise
     */
    public void setInvalid(boolean isInvalid)
    {
        this.setIsInvalid(isInvalid);
    }

    /**
     * @param isInvalid the isInvalid to set
     */
    public void setIsInvalid(boolean isInvalid)
    {
        this.isInvalid = isInvalid;
    }

    /**
     * @param isLooping the isLooping to set
     */
    public void setIsLooping(boolean isLooping)
    {
        this.isLooping = isLooping;
    }

    /**
     * @param loopInterval the loopInterval to set
     */
    public void setLoopInterval(int loopInterval)
    {
        this.loopInterval = loopInterval;
    }

    /**
     * {@inheritDoc}
     */
    public void stop()
    {
        internalStop();
        setIsLooping(false);
    }
}
