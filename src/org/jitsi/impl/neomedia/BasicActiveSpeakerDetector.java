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
package org.jitsi.impl.neomedia;

import java.util.*;
import java.util.concurrent.locks.*;

import org.jitsi.service.neomedia.*;

/**
 * {@inheritDoc}
 * <p>
 * For each stream, it keeps the last {@link History#SIZE} levels and uses them
 * to compute a score.
 * </p>
 * <p>
 * At all times one stream (indicated in {@link #active}) is considered active,
 * while the rest are considered 'competing'.
 * </p>
 * <p>
 * When a new audio level is received for a stream, its score is recomputed, and
 * the scores of all streams are examined in order to determine if one of the
 * competing streams should replace the then active stream.
 * </p>
 * <p>
 * In order to be eligible to replace the active stream, a competing stream has
 * to:
 * 1. Have a score at least {@link #ACTIVE_COEF} times as much as the score of
 * the currently active stream.
 * 2. Have score at least {@link #MIN_NEW_ACTIVE_SCORE}.
 * 3. Have had its audio level updated in the last
 * {@link #MAX_NEW_ACTIVE_SILENT_INTERVAL} milliseconds.
 * 4. Have updated its audio level at least {@link #MIN_NEW_ACTIVE_SIZE} times.
 * </p>
 * <p>
 * In order to actually replace the active, a competing stream has to have the
 * highest score amongst all eligible streams.
 * </p>
 * <p>
 * These rules and the constant values were chosen based on a few not very
 * thorough tests in a conference. Some justification for the rules:
 * 1. Helps to avoid often changing the active when there are two streams with
 * similar levels.
 * 2. This is to prevent switching the active stream away during times of
 * &quot;silence&quot;. Without this threshold we observed the following:
 * someone's microphone generates noise with levels above the levels of the
 * active speaker. When the active speaker pauses speaking, the one with the
 * higher noise becomes active.
 * 3. This is for the case when someone quits the conference shouting ;)
 * 4. This is because of the way we compute scores. Just-added streams might
 * have an uncharacteristically high score.
 * </p>
 *
 * @author Boris Grozev
 */
public class BasicActiveSpeakerDetector
    extends AbstractActiveSpeakerDetector
{
    //TODO clean histories for very old streams (dont keep hitting MAX_NEW_ACTIVE_SILENT_INTERVAL)

    private static final double ACTIVE_COEF = 1.15;

    private static final int MAX_NEW_ACTIVE_SILENT_INTERVAL = 1000; //ms

    private static final double MIN_NEW_ACTIVE_SCORE = 120.;

    private static final int MIN_NEW_ACTIVE_SIZE = 20;

    private History active;

    private final Object activeSyncRoot = new Object();

    private final Map<Long, History> histories = new HashMap<Long, History>();

    private final ReadWriteLock historiesLock = new ReentrantReadWriteLock();

    private History getHistory(long ssrc)
    {
        History history = null;

        Lock readLock = historiesLock.readLock();

        readLock.lock();
        try
        {
            history = histories.get(ssrc);
        }
        finally
        {
            readLock.unlock();
        }

        if (history == null)
        {
            history = new History(ssrc);
            Lock writeLock = historiesLock.writeLock();

            writeLock.lock();
            try
            {
                histories.put(ssrc, history);
            }
            finally
            {
                writeLock.unlock();
            }
        }

        return history;
    }

    @Override
    public void levelChanged(long ssrc, int level)
    {
        History history = getHistory(ssrc);

        history.update(level);

        updateActive();
    }

    private History setInitialActive(ArrayList<History> histories)
    {
        History bestHistory = null;
        Double bestScore = 0.;

        for (History h : histories)
        {
            if (h.score >= bestScore)
            {
                bestHistory = h;
                bestScore = h.score;
            }
        }

        synchronized (activeSyncRoot)
        {
            active = bestHistory;
        }

        return bestHistory;
    }

    private void updateActive()
    {
        History active;
        synchronized (activeSyncRoot)
        {
            active = this.active;
        }

        ArrayList<History> histories;
        Lock readLock = historiesLock.readLock();

        readLock.lock();
        try
        {
            histories = new ArrayList<History>(this.histories.values());
        }
        finally
        {
            readLock.unlock();
        }

        if (histories.isEmpty())
            return;

        if (active == null)
        {
            active = setInitialActive(histories);
            if (active != null)
                fireActiveSpeakerChanged(active.ssrc);
            return;
        }

        History newActive = active;
        for (History history : histories)
        {
            if (history.lastUpdate != -1
                    && history.lastUpdate + MAX_NEW_ACTIVE_SILENT_INTERVAL
                        < System.currentTimeMillis()) //rule 4 in class javadoc
            {
                history.reset();
            }

            if (history.score > active.score * ACTIVE_COEF //rule 1
                  && history.score > newActive.score //highest score among eligible
                  && history.size >= MIN_NEW_ACTIVE_SIZE //rule 3
                  && history.score >= MIN_NEW_ACTIVE_SCORE) //rule 2
                newActive = history;
        }

        if (newActive != active)
        {
            synchronized (activeSyncRoot)
            {
                this.active = newActive;
            }
            fireActiveSpeakerChanged(newActive.ssrc);
        }
    }

    private static class History
    {
        private static final int C_OLDER = 1;
        private static final int C_RECENT = 2;
        private static final int SIZE = 25 + 100;

        private int head = 0;
        private int[] history = new int[SIZE];
        private long lastUpdate = -1;
        private double score = 0.;
        private int size = 0;
        private long ssrc = -1;

        private History(long ssrc)
        {
            this.ssrc = ssrc;
        }

        private synchronized void reset()
        {
            lastUpdate = -1;
            size = head = 0;
            score = 0.;
            Arrays.fill(history, 0);
        }

        private synchronized void update(int level)
        {
            //TODO compute score efficiently

            history[head] = level;

            head = (head+1)%SIZE;
            size = Math.min(size+1, SIZE);

            int sum = 0;
            for (int i=0; i<100; i++)
                sum += history[(head+i)%SIZE];

            int sum2 = 0;
            for (int i=0; i<25; i++)
                sum2 += history[(SIZE+head-1-i)%SIZE];

            score = C_OLDER*((double)sum)/100 + C_RECENT*((double)sum2)/25;
            lastUpdate = System.currentTimeMillis();
//            if(score>110)
//            {
//                System.err.println(
//                        getClass().getName() + " " + ssrc + " update(" + level
//                            + ") score=" + score);
//            }
        }
    }
}
