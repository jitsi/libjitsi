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
package org.jitsi.service.neomedia;

import org.jitsi.service.neomedia.event.*;

import java.util.*;

/**
 * Provides an abstract, base implementation of {@link SrtpControl} to
 * facilitate implementers.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractSrtpControl<T extends SrtpControl.TransformEngine>
    implements SrtpControl
{
    private final SrtpControlType srtpControlType;

    /**
     * The <tt>SrtpListener</tt> listening to security events (to be) fired by
     * this <tt>SrtpControl</tt> instance.
     */
    private SrtpListener srtpListener;

    protected T transformEngine;

    /**
     * The {@code Object}s currently registered as users of this
     * {@code SrtpControl} (through {@link #registerUser(Object)}).
     */
    private final Set<Object> users = new HashSet<>();

    /**
     * Initializes a new <tt>AbstractSrtpControl</tt> instance with a specific
     * <tt>SrtpControlType</tt>.
     *
     * @param srtpControlType the <tt>SrtpControlType</tt> of the new instance
     */
    protected AbstractSrtpControl(SrtpControlType srtpControlType)
    {
        if (srtpControlType == null)
            throw new NullPointerException("srtpControlType");

        this.srtpControlType = srtpControlType;
    }

    /**
     * {@inheritDoc}
     *
     * Invokes {@link #doCleanup()} if there are no more users (advertised via
     * {@link #registerUser(Object)}) of this {@code SrtpControl}.
     */
    @Override
    public void cleanup(Object user)
    {
        synchronized (users)
        {
            if (users.remove(user) && users.isEmpty())
                doCleanup();
        }
    }

    /**
     * Initializes a new <tt>TransformEngine</tt> instance to be associated with
     * and used by this <tt>SrtpControl</tt> instance.
     *
     * @return a new <tt>TransformEngine</tt> instance to be associated with and
     * used by this <tt>SrtpControl</tt> instance
     */
    protected abstract T createTransformEngine();

    /**
     * Prepares this {@code SrtpControl} for garbage collection.
     */
    protected void doCleanup()
    {
        if (transformEngine != null)
        {
            transformEngine.cleanup();
            transformEngine = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public SrtpControlType getSrtpControlType()
    {
        return srtpControlType;
    }

    /**
     * {@inheritDoc}
     */
    public SrtpListener getSrtpListener()
    {
        return srtpListener;
    }

    /**
     * {@inheritDoc}
     */
    public T getTransformEngine()
    {
        if (transformEngine == null)
            transformEngine = createTransformEngine();
        return transformEngine;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>AbstractSrtpControl</tt> does nothing because
     * support for multistream mode is the exception rather than the norm. 
     */
    public void setMasterSession(boolean masterSession)
    {
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>AbstractSrtpControl</tt> does nothing because
     * support for multistream mode is the exception rather than the norm. 
     */
    public void setMultistream(SrtpControl master)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void setSrtpListener(SrtpListener srtpListener)
    {
        this.srtpListener = srtpListener;
    }

    /**
     * {@inheritDoc}
     */
    public void registerUser(Object user)
    {
        synchronized (users)
        {
            users.add(user);
        }
    }
}
