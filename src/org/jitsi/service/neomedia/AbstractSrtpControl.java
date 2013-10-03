/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia;

import org.jitsi.service.neomedia.event.*;

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
     * The implementation of <tt>AbstractSrtpControl</tt> cleans up its
     * associated <tt>TransformEngine</tt> (if any).
     */
    public void cleanup()
    {
        if (transformEngine != null)
        {
            transformEngine.cleanup();
            transformEngine = null;
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
}
