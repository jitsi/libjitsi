/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Implements {@link ActiveSpeakerDetector} with inspiration from the paper
 * &quot;Dominant Speaker Identification for Multipoint Videoconferencing&quot;
 * by Ilana Volfin and Israel Cohen.
 *
 * @author Lyubomir Marinov
 */
public class DominantSpeakerIdentification
    extends AbstractActiveSpeakerDetector
{
    private static final double C1 = 3;

    private static final double C2 = 2;

    private static final double C3 = 0;

    /**
     * The interval in milliseconds of the activation of the identification of
     * the dominant speaker in a multipoint conference.
     */
    private static final long DECISION_INTERVAL = 300;

    @SuppressWarnings("unused")
    private static final int IMMEDIATE_THRESHOLD = 0;

    /**
     * The interval of time without a call to {@link Speaker#levelChanged(int)}
     * after which <tt>DominantSpeakerIdentification</tt> assumes that there
     * will be no report of a <tt>Speaker</tt>'s level within a certain
     * time-frame. 
     */
    private static final long LEVEL_IDLE_TIMEOUT = 30;

    private static final int LONG_COUNT = 1;

    /**
     * The maximum value of audio level supported by
     * <tt>DominantSpeakerIdentification</tt>.
     */
    private static final int MAX_LEVEL = 127;

    /**
     * The minimum value of audio level supported by
     * <tt>DominantSpeakerIdentification</tt>.
     */
    private static final int MIN_LEVEL = 0;

    private static final int N1 = 13;

    private static final int N1_BASED_MEDIUM_THRESHOLD = N1 / 2 - 1;

    private static final int N2 = 5;

    private static final int N2_BASED_LONG_THRESHOLD = N2 - 1;

    private static final int N3 = 10;

    /**
     * The interval of time without a call to {@link Speaker#levelChanged(int)}
     * after which <tt>DominantSpeakerIdentification</tt> assumes that a
     * non-dominant <tt>Speaker</tt> is to be automatically removed from
     * {@link #speakers}.
     */
    private static final long SPEAKER_IDLE_TIMEOUT = 12 * 60 * 1000;

    /**
     * The pool of <tt>Thread</tt>s which run
     * <tt>DominantSpeakerIdentification</tt>s.
     */
    private static final ExecutorService threadPool
        = ExecutorUtils.newCachedThreadPool(
                true,
                "DominantSpeakerIdentification");

    public static long binomialCoefficient(int n, int r)
    {
        int m = n - r; // r = Math.max(r, n - r);

        if (r < m)
            r = m;

        long t = 1;

        for (int i = n, j = 1; i > r; i--, j++)
            t = t * i / j;

        return t;
    }

    @SuppressWarnings("unused")
    private static int binomialCoefficientAlt(int n, int m)
    {
        int[] b = new int[n + 1];

        b[0] = 1;
        for (int i = 1; i <= n; ++i)
        {
            b[i] = 1;
            for (int j = i - 1; j > 0; --j)
                b[j] += b[j - 1];
        }
        return b[m];
    }

    private static boolean computeBigs(
            byte[] littles,
            byte[] bigs,
            int threshold)
    {
        int bigLength = bigs.length;
        int littleLengthPerBig = littles.length / bigLength;
        boolean changed = false;

        for (int b = 0, l = 0; b < bigLength; b++)
        {
            byte sum = 0;

            for (int lEnd = l + littleLengthPerBig; l < lEnd; l++)
            {
                if (littles[l] > threshold)
                    sum++;
            }
            if (bigs[b] != sum)
            {
                bigs[b] = sum;
                changed = true;
            }
        }
        return changed;
    }

    private static double computeSpeechActivityScore(
            int vL,
            int nR,
            double p,
            double lambda)
    {
        double speechActivityScore
            = Math.log(binomialCoefficient(nR, vL)) + vL * Math.log(p)
                + (nR - vL) * Math.log(1 - p) - Math.log(lambda) + lambda * vL;

        /*
         * FIXME (1) We are going to use speechActivityScore as the argument of
         * a logarithmic function and the latter is undefined for negative
         * arguments. (2) Additionally, we will be dividing by
         * speechActivityScore and we are not sure what we are going to do with
         * the result of such a division. 
         */
        if (speechActivityScore <= 0)
            speechActivityScore = 0.00000001;
        return speechActivityScore;
    }

    /**
     * The background thread which repeatedly makes the (global) decision about
     * speaker switches.
     */
    private DecisionMaker decisionMaker;

    /**
     * The synchronization source identifier/SSRC of the dominant speaker in
     * this multipoint conference.
     */
    private Long dominantSSRC;

    /**
     * The last/latest time at which this <tt>DominantSpeakerIdentification</tt>
     * made a (global) decision about speaker switches. The (global) decision
     * about switcher switches should be made every {@link #DECISION_INTERVAL}
     * milliseconds.
     */
    private long lastDecisionTime;

    /**
     * The last/latest time at which this <tt>DominantSpeakerIdentification</tt>
     * notified the <tt>Speaker</tt>s who have not received or measured audio
     * levels for a certain time (i.e. {@link #LEVEL_IDLE_TIMEOUT}) that they
     * will very likely not have a level within a certain time-frame of the
     * algorithm.
     */
    private long lastLevelIdleTime;

    private final double[] relativeSpeechActivities = new double[3];

    /**
     * The <tt>Speaker</tt>s in the multipoint conference associated with this
     * <tt>ActiveSpeakerDetector</tt>.
     */
    private final Map<Long,Speaker> speakers = new HashMap<Long,Speaker>();

    /**
     * Initializes a new <tt>DominantSpeakerIdentification</tT> instance.
     */
    public DominantSpeakerIdentification()
    {
    }

    synchronized void decisionMakerExited(DecisionMaker decisionMaker)
    {
        if (this.decisionMaker == decisionMaker)
            this.decisionMaker = null;
    }

    /**
     * Gets the <tt>Speaker</tt> in this multipoint conference identified by a
     * specific SSRC. If no such <tt>Speaker</tt> exists, a new <tt>Speaker</tt>
     * is initialized with the specified <tt>ssrc</tt>, added to this multipoint
     * conference and returned.
     *
     * @param ssrc the SSRC identifying the <tt>Speaker</tt> to return
     * @return the <tt>Speaker</tt> in this multipoint conference identified by
     * the specified <tt>ssrc</tt>
     */
    private synchronized Speaker getOrCreateSpeaker(long ssrc)
    {
        Long key = Long.valueOf(ssrc);
        Speaker speaker = speakers.get(key);

        if (speaker == null)
        {
            speaker = new Speaker(ssrc);
            speakers.put(key, speaker);

            maybeStartDecisionMaker();
        }

        return speaker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void levelChanged(long ssrc, int level)
    {
        Speaker speaker = getOrCreateSpeaker(ssrc);

        if (speaker != null)
            speaker.levelChanged(level);
    }

    private synchronized void makeDecision()
    {
        int speakerCount = speakers.size();
        Long newDominantSSRC;

        if (speakerCount == 0)
        {
            /*
             * If there are no Speakers in a multipoint conference, then there
             * are no speaker switch events to detect.
             */
            newDominantSSRC = null;
        }
        else if (speakerCount == 1)
        {
            /*
             * If there is a single Speaker in a multipoint conference, then
             * his/her speech surely dominates.
             */
            newDominantSSRC = speakers.keySet().iterator().next();
        }
        else
        {
            Speaker dominantSpeaker
                = (dominantSSRC == null)
                    ? null
                    : speakers.get(dominantSSRC);

            /*
             * If there is no dominant speaker, nominate one at random and then
             * let the other speakers compete with the nominated one.
             */
            if (dominantSpeaker == null)
            {
                Map.Entry<Long,Speaker> s
                    = speakers.entrySet().iterator().next();

                dominantSpeaker = s.getValue();
                newDominantSSRC = s.getKey();
            }
            else
            {
                newDominantSSRC = null;
            }

            dominantSpeaker.evaluateSpeechActivityScores();

            double[] relativeSpeechActivities = this.relativeSpeechActivities;
            /*
             * If multiple speakers cause speaker switches, they compete among
             * themselves by their relative speech activities in the middle
             * time-interval.
             */
            double newDominantC2 = C2;

            for (Map.Entry<Long,Speaker> s : speakers.entrySet())
            {
                Speaker speaker = s.getValue();

                /*
                 * The dominant speaker does not compete with itself. In other
                 * words, there is no use detecting a speaker switch from the
                 * dominant speaker to the dominant speaker. Technically, the
                 * relative speech activities are all zeroes for the dominant
                 * speaker.
                 */
                if (speaker == dominantSpeaker)
                    continue;

                speaker.evaluateSpeechActivityScores();

                /*
                 * Compute the relative speech activities for the immediate,
                 * medium and long time-intervals.
                 */
                for (int interval = 0;
                        interval < relativeSpeechActivities.length;
                        ++interval)
                {
                    relativeSpeechActivities[interval]
                        = Math.log(
                                speaker.getSpeechActivityScore(interval)
                                    / dominantSpeaker.getSpeechActivityScore(
                                            interval));
                }

                double c1 = relativeSpeechActivities[0];
                double c2 = relativeSpeechActivities[1];
                double c3 = relativeSpeechActivities[2];

                if ((c1 > C1) && (c2 > C2) && (c3 > C3) && (c2 > newDominantC2))
                {
                    /*
                     * If multiple speakers cause speaker switches, they compete
                     * among themselves by their relative speech activities in
                     * the middle time-interval.
                     */
                    newDominantC2 = c2;
                    newDominantSSRC = s.getKey();
                }
            }
        }
        if ((newDominantSSRC != null) && (newDominantSSRC != dominantSSRC))
        {
            dominantSSRC = newDominantSSRC;
            fireActiveSpeakerChanged(dominantSSRC);
        }
    }

    /**
     * Starts a background thread which is to repeatedly make the (global)
     * decision about speaker switches if such a background thread has not been
     * started yet and if the current state of this
     * <tt>DominantSpeakerIdentification</tt> justifies the start of such a
     * background thread (e.g. there is at least one <tt>Speaker</tt> in this
     * multipoint conference). 
     */
    private synchronized void maybeStartDecisionMaker()
    {
        if ((this.decisionMaker == null) && !speakers.isEmpty())
        {
            DecisionMaker decisionMaker = new DecisionMaker(this);
            boolean scheduled = false;

            this.decisionMaker = decisionMaker;
            try
            {
                threadPool.execute(decisionMaker);
                scheduled = true;
            }
            finally
            {
                if (!scheduled && (this.decisionMaker == decisionMaker))
                    this.decisionMaker = null;
            }
        }
    }

    private long runInDecisionMaker()
    {
        long now = System.currentTimeMillis();
        long levelIdleTimeout = LEVEL_IDLE_TIMEOUT - (now - lastLevelIdleTime);
        long sleep = 0;

        if (levelIdleTimeout <= 0)
        {
            if (lastLevelIdleTime != 0)
                timeoutIdleLevels(now);
            lastLevelIdleTime = now;
        }
        else
        {
            sleep = levelIdleTimeout;
        }

        long decisionTimeout = DECISION_INTERVAL - (now - lastDecisionTime);

        if (decisionTimeout <= 0)
        {
            /*
             * The identification of the dominant active speaker may be a
             * time-consuming ordeal so the time of the last decision is the
             * time of the beginning of a decision iteration.
             */
            lastDecisionTime = now;
            makeDecision();
            /*
             * The identification of the dominant active speaker may be a
             * time-consuming ordeal so the timeout to the next decision
             * iteration should be computed after the end of the decision
             * iteration.
             */
            decisionTimeout = DECISION_INTERVAL - (now - lastDecisionTime);
        }
        if ((decisionTimeout > 0) && (sleep > decisionTimeout))
            sleep = decisionTimeout;

        return sleep;
    }

    long runInDecisionMaker(DecisionMaker decisionMaker)
    {
        synchronized (this)
        {
            if (this.decisionMaker != decisionMaker)
                return -1;
        }

        return runInDecisionMaker();
    }

    /**
     * Notifies the <tt>Speaker</tt>s in this multipoint conference who have not
     * received or measured audio levels for a certain time (i.e.
     * {@link #LEVEL_IDLE_TIMEOUT}) that they will very likely not have a level
     * within a certain time-frame of the <tt>DominantSpeakerIdentification</tt>
     * algorithm. Additionally, removes the non-dominant <tt>Speaker</tt>s who
     * have not received or measured audio levels for far too long (i.e.
     * {@link #SPEAKER_IDLE_TIMEOUT}).
     *
     * @param now the time at which the timing out is being detected
     */
    private synchronized void timeoutIdleLevels(long now)
    {
        Iterator<Map.Entry<Long,Speaker>> i = speakers.entrySet().iterator();

        while (i.hasNext())
        {
            Speaker speaker = i.next().getValue();
            long lastLevelChangedTime = speaker.getLastLevelChangedTime();

            /*
             * Remove a non-dominant Speaker if he/she has been idle for far too
             * long.
             */
            if ((lastLevelChangedTime + SPEAKER_IDLE_TIMEOUT < now)
                    && ((dominantSSRC == null)
                            || (speaker.ssrc != dominantSSRC)))
            {
                i.remove();
                continue;
            }

            if (lastLevelChangedTime + LEVEL_IDLE_TIMEOUT < now)
                speaker.levelTimedOut();
        }
    }

    /**
     * Represents the background thread which repeatedly makes the (global)
     * decision about speaker switches. Weakly references an associated
     * <tt>DominantSpeakerIdentification</tt> instance in order to eventually
     * detect that the multipoint conference has actually expired and that the
     * background <tt>Thread</tt> should perish.
     */
    private static class DecisionMaker
        implements Runnable
    {
        /**
         * The <tt>DominantSpeakerIdentification</tt> instance which is
         * repeatedly run into this background thread in order to make the
         * (global) decision about speaker switches. It is a
         * <tt>WeakReference</tt> in order to eventually detect that the
         * mulipoint conference has actually expired and that this background
         * <tt>Thread</tt> should perish.
         */
        private final WeakReference<DominantSpeakerIdentification> algorithm;

        /**
         * Initializes a new <tt>DecisionMaker</tt> instance which is to
         * repeatedly run a specific <tt>DominantSpeakerIdentification</tt>
         * into a background thread in order to make the (global) decision about
         * speaker switches.
         *
         * @param algorithm the <tt>DominantSpeakerIdentification</tt> to be
         * repeatedly run by the new instance in order to make the (global)
         * decision about speaker switches
         */
        public DecisionMaker(DominantSpeakerIdentification algorithm)
        {
            this.algorithm
                = new WeakReference<DominantSpeakerIdentification>(algorithm);
        }

        /**
         * Repeatedly runs {@link #algorithm} i.e. makes the (global) decision
         * about speaker switches until the multipoint conference expires.
         */
        @Override
        public void run()
        {
            try
            {
                do
                {
                    DominantSpeakerIdentification algorithm
                        = this.algorithm.get();

                    if (algorithm == null)
                    {
                        break;
                    }
                    else
                    {
                        long sleep = algorithm.runInDecisionMaker(this);

                        /*
                         * A negative sleep value is explicitly supported i.e.
                         * expected and is contracted to mean that this
                         * DecisionMaker is instructed by the algorithm to
                         * commit suicide.
                         */
                        if (sleep < 0)
                        {
                            break;
                        }
                        else if (sleep > 0)
                        {
                            /*
                             * Before sleeping, make the currentThread release
                             * its reference to the associated
                             * DominantSpeakerIdnetification instance.
                             */
                            algorithm = null;
                            try
                            {
                                Thread.sleep(sleep);
                            }
                            catch (InterruptedException ie)
                            {
                                // Continue with the next iteration.
                            }
                        }
                    }
                }
                while (true);
            }
            finally
            {
                /*
                 * Notify the algorithm that this background thread will no
                 * longer run it in order to make the (global) decision about
                 * speaker switches. Subsequently, the algorithm may decide to
                 * swap another background thread to run the same task.
                 */
                DominantSpeakerIdentification algorithm = this.algorithm.get();

                if (algorithm != null)
                    algorithm.decisionMakerExited(this);
            }
        }
    }

    /**
     * Represents a speaker in a multipoint conference identified by
     * synchronization source identifier/SSRC.
     */
    private static class Speaker
    {
        private final byte[] immediates = new byte[LONG_COUNT * N3 * N2];

        private double immediateSpeechActivityScore;

        private long lastLevelChangedTime = System.currentTimeMillis();

        private final byte[] longs = new byte[LONG_COUNT];

        private double longSpeechActivityScore;

        private final byte[] mediums = new byte[LONG_COUNT * N3];

        private double mediumSpeechActivityScore;

        /**
         * The synchronization source identifier/SSRC of this <tt>Speaker</tt>
         * which is unique within a multipoint conference.
         */
        public final long ssrc;

        /**
         * Initializes a new <tt>Speaker</tt> instance identified by a specific
         * synchronization source identifier/SSRC.
         *
         * @param ssrc the synchronization source identifier/SSRC of the new
         * instance
         */
        public Speaker(long ssrc)
        {
            this.ssrc = ssrc;
        }

        private void computeImmediates(int level)
        {
            /*
             * Ensure the specified audio level falls within the supported audio
             * level range.
             */
            if (level < MIN_LEVEL)
                level = MIN_LEVEL;
            else if (level > MAX_LEVEL)
                level = MAX_LEVEL;

            System.arraycopy(
                    immediates, 0,
                    immediates, 1,
                    immediates.length - 1);
            immediates[0] = (byte) (level / N1);
        }

        private void computeImmediateSpeechActivityScore()
        {
            immediateSpeechActivityScore
                = computeSpeechActivityScore(immediates[0], N1, 0.5, 0.78);
        }

        private boolean computeLongs()
        {
            return computeBigs(mediums, longs, N2_BASED_LONG_THRESHOLD);
        }

        private void computeLongSpeechActivityScore()
        {
            longSpeechActivityScore
                = computeSpeechActivityScore(longs[0], N3, 0.5, 47);
        }

        private boolean computeMediums()
        {
            return computeBigs(immediates, mediums, N1_BASED_MEDIUM_THRESHOLD);
        }

        private void computeMediumSpeechActivityScore()
        {
            mediumSpeechActivityScore
                = computeSpeechActivityScore(mediums[0], N2, 0.5, 24);
        }

        synchronized void evaluateSpeechActivityScores()
        {
            computeImmediateSpeechActivityScore();
            if (computeMediums())
            {
                computeMediumSpeechActivityScore();
                if (computeLongs())
                    computeLongSpeechActivityScore();
            }
        }

        public synchronized long getLastLevelChangedTime()
        {
            return lastLevelChangedTime;
        }

        double getSpeechActivityScore(int index)
        {
            switch (index)
            {
            case 0:
                return immediateSpeechActivityScore;
            case 1:
                return mediumSpeechActivityScore;
            case 2:
                return longSpeechActivityScore;
            default:
                throw new IllegalArgumentException("index " + index);
            }
        }

        /**
         * Notifies this <tt>Speaker</tt> that a new audio level has been
         * received or measured.
         *
         * @param level the audio level which has been received or measured for
         * this <tt>Speaker</tt>
         */
        public void levelChanged(int level)
        {
            levelChanged(level, System.currentTimeMillis());
        }

        /**
         * Notifies this <tt>Speaker</tt> that a new audio level has been
         * received or measured at a specific time.
         *
         * @param level the audio level which has been received or measured for
         * this <tt>Speaker</tt>
         * @param time the time at which the specified <tt>leve</tt> has been
         * received or measured
         */
        private synchronized void levelChanged(int level, long time)
        {
            lastLevelChangedTime = time;

            computeImmediates(level);
        }

        /**
         * Notifies this <tt>Speaker</tt> that no new audio level has been
         * received or measured for a certain time which very likely means that
         * this <tt>Speaker</tt> will not have a level within a certain
         * time-frame of a <tt>DominantSpeakerIdentification</tt> algorithm.
         */
        public synchronized void levelTimedOut()
        {
            levelChanged(MIN_LEVEL, lastLevelChangedTime);
        }
    }
}
