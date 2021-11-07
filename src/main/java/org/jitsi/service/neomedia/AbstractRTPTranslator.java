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

/**
 * An abstract, base implementation of {@link RTPTranslator} which aid the
 * implementation of the interface.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractRTPTranslator
    implements RTPTranslator
{
    /**
     * An empty array with element type <tt>WriteFilter</tt>. Explicitly defined
     * in order to reduce unnecessary allocations and the consequent effects of
     * the garbage collector.
     */
    private static final WriteFilter[] NO_WRITE_FILTERS = new WriteFilter[0];

    /**
     * The <tt>WriteFilter</tt>s added to this <tt>RTPTranslator</tt>.
     */
    private WriteFilter[] writeFilters = NO_WRITE_FILTERS;

    /**
     * The <tt>Object</tt> which synchronizes the access to
     * {@link #writeFilters}.
     */
    private final Object writeFiltersSyncRoot = new Object();

    /**
     * {@inheritDoc}
     */
    @Override
    public void addWriteFilter(WriteFilter writeFilter)
    {
        if (writeFilter == null)
            throw new NullPointerException("writeFilter");

        synchronized (writeFiltersSyncRoot)
        {
            for (WriteFilter wf : writeFilters)
            {
                if (wf.equals(writeFilter))
                    return;
            }

            WriteFilter[] newWriteFilters
                = new WriteFilter[writeFilters.length + 1];

            if (writeFilters.length != 0)
            {
                System.arraycopy(
                        writeFilters, 0,
                        newWriteFilters, 0,
                        writeFilters.length);
            }
            newWriteFilters[writeFilters.length] = writeFilter;
            writeFilters = newWriteFilters;
        }
    }

    /**
     * Gets the <tt>WriteFilter</tt>s added to this <tt>RTPTranslator</tt>.
     *
     * @return the <tt>WriteFilter</tt>s added to this <tt>RTPTranslator</tt>
     */
    protected WriteFilter[] getWriteFilters()
    {
        synchronized (writeFiltersSyncRoot)
        {
            return
                (writeFilters.length == 0)
                    ? NO_WRITE_FILTERS
                    : writeFilters.clone();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeWriteFilter(WriteFilter writeFilter)
    {
        if (writeFilter != null)
        {
            synchronized (writeFiltersSyncRoot)
            {
                for (int i = 0; i < writeFilters.length; ++i)
                {
                    if (writeFilters[i].equals(writeFilter))
                    {
                        WriteFilter[] newWriteFilters;

                        if (writeFilters.length == 1)
                        {
                            newWriteFilters = NO_WRITE_FILTERS;
                        }
                        else
                        {
                            int newWriteFiltersLength = writeFilters.length - 1;

                            newWriteFilters
                                = new WriteFilter[newWriteFiltersLength];
                            if (i != 0)
                            {
                                System.arraycopy(
                                        writeFilters, 0,
                                        newWriteFilters, 0,
                                        i);
                            }
                            if (i != newWriteFiltersLength)
                            {
                                System.arraycopy(
                                        writeFilters, i + 1,
                                        newWriteFilters, i,
                                        newWriteFiltersLength - i);
                            }
                        }
                        writeFilters = newWriteFilters;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Notifies this <tt>RTPTranslator</tt> that a <tt>buffer</tt> from a
     * <tt>source</tt> will be written into a <tt>destination</tt>.
     *
     * @param source the source of <tt>buffer</tt>
     * @param pkt the packet from <tt>source</tt> which is to be written into
     * <tt>destination</tt>
     * @param destination the destination into which <tt>buffer</tt> is to be
     * written
     * @param data <tt>true</tt> for data/RTP or <tt>false</tt> for control/RTCP
     * @return <tt>true</tt> if the writing is to continue or <tt>false</tt> if
     * the writing is to abort
     */
    protected boolean willWrite(
            MediaStream source,
            RawPacket pkt,
            MediaStream destination,
            boolean data)
    {
        WriteFilter writeFilter = null;
        WriteFilter[] writeFilters = null;
        boolean accept = true;

        synchronized (writeFiltersSyncRoot)
        {
            if (this.writeFilters.length != 0)
            {
                if (this.writeFilters.length == 1)
                    writeFilter = this.writeFilters[0];
                else
                    writeFilters = this.writeFilters.clone();
            }
        }
        if (writeFilter != null)
        {
            accept
                = willWrite(
                        writeFilter,
                        source, pkt, destination, data);
        }
        else if (writeFilters != null)
        {
            for (WriteFilter wf : writeFilters)
            {
                accept
                    = willWrite(
                            wf,
                            source, pkt, destination, data);
                if (!accept)
                    break;
            }
        }

        return accept;
    }

    /**
     * Invokes a specific <tt>WriteFilter</tt>.
     *
     * @param source the source of <tt>buffer</tt>
     * @param pkt the packet from <tt>source</tt> which is to be written into
     * <tt>destination</tt>
     * @param destination the destination into which <tt>buffer</tt> is to be
     * written
     * @param data <tt>true</tt> for data/RTP or <tt>false</tt> for control/RTCP
     * @return <tt>true</tt> if the writing is to continue or <tt>false</tt> if
     * the writing is to abort
     */
    protected boolean willWrite(
            WriteFilter writeFilter,
            MediaStream source,
            RawPacket pkt,
            MediaStream destination,
            boolean data)
    {
        boolean accept;

        try
        {
            accept
                = writeFilter.accept(
                        source,
                        pkt,
                        destination,
                        data);
        }
        catch (Throwable t)
        {
            accept = true;
            if (t instanceof InterruptedException)
                Thread.currentThread().interrupt();
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }
        return accept;
    }
}
