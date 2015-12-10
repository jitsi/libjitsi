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

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;

/**
 * Implements an {@link ActiveSpeakerDetector} (factory) which uses/delegates to
 * an actual algorithm implementation for the detections/identification of the
 * active/dominant speaker in a multipoint conference.
 *
 * @author Lyubomir Marinov
 */
public class ActiveSpeakerDetectorImpl
    implements ActiveSpeakerDetector
{
    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the class name of the algorithm implementation for the
     * detection/identification of the active/dominant speaker in a multipoint
     * conference to be used by <tt>ActiveSpeakerDetectorImpl</tt>. The default
     * value is <tt>null</tt>. If the specified value is <tt>null</tt> or the
     * initialization of an instance of the specified class fails,
     * <tt>ActiveSpeakerDetectorImpl</tt> falls back to a list of well-known
     * algorithm implementations.
     */
    private static final String IMPL_CLASS_NAME_PNAME
        = ActiveSpeakerDetectorImpl.class.getName() + ".implClassName";

    /**
     * The names of the classes known by <tt>ActiveSpeakerDetectorImpl</tt> to
     * implement actual algorithms for the detection/identification of the
     * active/dominant speaker in a multipoint conference. 
     */
    private static final String[] IMPL_CLASS_NAMES
        = {
            ".DominantSpeakerIdentification",
            ".BasicActiveSpeakerDetector"
        };

    /**
     * The actual algorithm implementation to be used by this instance for the
     * detection/identification of the active/dominant speaker in a multipoint
     * conference.
     */
    private final ActiveSpeakerDetector impl;

    /**
     * Initializes a new <tt>ActiveSpeakerDetectorImpl</tt> which is to use a
     * default algorithm implementation for the detection/identification of the
     * active/dominant speaker in a multipoint conference.
     */
    public ActiveSpeakerDetectorImpl()
    {
        this(getImplClassNames());
    }

    /**
     * Initializes a new <tt>ActiveSpeakerDetectorImpl</tt> which is to use the
     * first available algorithm from a specific list of algorithms (identified
     * by the names of their implementing classes) for the
     * detection/identification of the active/dominant speaker in a multipoint
     * conference.
     * 
     * @param implClassNames the class names of the algorithm implementations to
     * search through and in which the first available is to be found and used
     * for the detection/identification of the active/dominant speaker in a
     * multipoint conference
     * @throws RuntimeException if none of the algorithm implementations
     * specified by <tt>implClassNames</tt> is available
     */
    public ActiveSpeakerDetectorImpl(String... implClassNames)
    {
        ActiveSpeakerDetector impl = null;
        Throwable cause = null;

        for (String implClassName : implClassNames)
        {
            try
            {
                Class<?> implClass
                    = Class.forName(normalizeClassName(implClassName));

                if ((implClass != null)
                        && ActiveSpeakerDetector.class.isAssignableFrom(
                                implClass))
                {
                    impl = (ActiveSpeakerDetector) implClass.newInstance();
                }
            }
            catch (Throwable t)
            {
                if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                else if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else
                    cause = t;
            }
            if (impl != null)
                break;
        }
        if (impl == null)
        {
            throw new RuntimeException(
                    "Failed to initialize an actual ActiveSpeakerDetector"
                        + " implementation, tried classes: "
                        + Arrays.toString(implClassNames),
                    cause);
        }
        else
        {
            this.impl = impl;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener)
    {
        impl.addActiveSpeakerChangedListener(listener);
    }

    /**
     * Gets the actual algorithm implementation used by this instance for the
     * detection/identification of the active/dominant speaker in a multipoint
     * conference.
     *
     * @return the actual algorithm implementation used by this instance for
     * the detection/identification of the active/dominant speaker in a
     * multipoint conference
     */
    public ActiveSpeakerDetector getImpl()
    {
        return impl;
    }

    /**
     * Gets the names of the classes known by <tt>ActiveSpeakerDetectorImpl</tt>
     * to implement actual algorithms for the detection/identification of the
     * active/dominant speaker in a multipoint conference. If the
     * <tt>ConfigurationService</tt> property {@link #IMPL_CLASS_NAME_PNAME}
     * specifies a class name, it is prepended to the returned array.
     *
     * @return the names of the classes known by
     * <tt>ActiveSpeakerDetectorImpl</tt> to implement actual algorithms for the
     * detection/identification of the active/dominant speaker in a multipoint
     * conference
     */
    private static String[] getImplClassNames()
    {
        /*
         * The user is allowed to specify the class name of the (default)
         * algorithm implementation through the ConfigurationService.
         */
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String implClassName = null;

        if (cfg != null)
        {
            implClassName = cfg.getString(IMPL_CLASS_NAME_PNAME);
            if ((implClassName != null) && (implClassName.length() == 0))
                implClassName = null;
        }

        /*
         * Should the user's choice with respect to the algorithm implementation
         * fail, ActiveSpeakerDetectorImpl falls back to well-known algorithm
         * implementations.   
         */
        String[] implClassNames;

        if (implClassName == null)
        {
            implClassNames = IMPL_CLASS_NAMES;
        }
        else
        {
            List<String> implClassNameList
                = new ArrayList<>(1 + IMPL_CLASS_NAMES.length);

            implClassNameList.add(normalizeClassName(implClassName));
            for (String anImplClassName : IMPL_CLASS_NAMES)
            {
                anImplClassName = normalizeClassName(anImplClassName);
                if (!implClassNameList.contains(anImplClassName))
                    implClassNameList.add(anImplClassName);
            }

            implClassNames
                = implClassNameList.toArray(
                        new String[implClassNameList.size()]);
        }
        return implClassNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void levelChanged(long ssrc, int level)
    {
        impl.levelChanged(ssrc, level);
    }

    /**
     * Makes sure that a specific class name starts with a package name.
     *
     * @param className the class name to prefix with a package name if
     * necessary
     * @return a class name with a package name and a simple name
     */
    private static String normalizeClassName(String className)
    {
        if (className.startsWith("."))
        {
            Package pkg = ActiveSpeakerDetectorImpl.class.getPackage();

            if (pkg != null)
                className = pkg.getName() + className;
        }
        return className;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener)
    {
        impl.removeActiveSpeakerChangedListener(listener);
    }
}
