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
package org.jitsi.impl.neomedia.control;

import javax.media.*;

import org.jitsi.utils.logging.*;

/**
 * Provides an abstract implementation of <tt>Controls</tt> which facilitates
 * implementers by requiring them to only implement
 * {@link Controls#getControls()}.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractControls
    implements Controls
{

    /**
     * The <tt>Logger</tt> used by the <tt>AbstractControls</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(AbstractControls.class);

    /**
     * Implements {@link Controls#getControl(String)}. Invokes
     * {@link #getControls()} and then looks for a control of the specified type
     * in the returned array of controls.
     *
     * @param controlType a <tt>String</tt> value naming the type of the control
     * of this instance to be retrieved
     * @return an <tt>Object</tt> which represents the control of this instance
     * with the specified type
     */
    public Object getControl(String controlType)
    {
        return getControl(this, controlType);
    }

    /**
     * Gets the control of a specific <tt>Controls</tt> implementation of a
     * specific type if such a control is made available through
     * {@link Controls#getControls()}; otherwise, returns <tt>null</tt>.
     *
     * @param controlsImpl the implementation of <tt>Controls</tt> which is to
     * be queried for its list of controls so that the control of the specified
     * type can be looked for
     * @param controlType a <tt>String</tt> value which names the type of the
     * control to be retrieved
     * @return an <tt>Object</tt> which represents the control of
     * <tt>controlsImpl</tt> of the specified <tt>controlType</tt> if such a
     * control is made available through <tt>Controls#getControls()</tt>;
     * otherwise, <tt>null</tt>
     */
    public static Object getControl(Controls controlsImpl, String controlType)
    {
        Object[] controls = controlsImpl.getControls();

        if ((controls != null) && (controls.length > 0))
        {
            Class<?> controlClass;

            try
            {
                controlClass = Class.forName(controlType);
            }
            catch (ClassNotFoundException cnfe)
            {
                controlClass = null;
                logger.warn(
                        "Failed to find control class " + controlType,
                        cnfe);
            }
            if (controlClass != null)
            {
                for (Object control : controls)
                {
                    if (controlClass.isInstance(control))
                        return control;
                }
            }
        }
        return null;
    }

    /**
     * Returns an instance of a specific <tt>Class</tt> which is either a
     * control of a specific <tt>Controls</tt> implementation or the
     * <tt>Controls</tt> implementation itself if it is an instance of the
     * specified <tt>Class</tt>. The method is similar to
     * {@link #getControl(Controls, String)} in querying the specified
     * <tt>Controls</tt> implementation about a control of the specified
     * <tt>Class</tt> but is different in looking at the type hierarchy of the
     * <tt>Controls</tt> implementation for the specified <tt>Class</tt>.
     *
     * @param controlsImpl the <tt>Controls</tt> implementation to query
     * @param controlType the runtime type of the instance to be returned
     * @return an instance of the specified <tt>controlType</tt> if such an
     * instance can be found among the controls of the specified
     * <tt>controlsImpl</tt> or <tt>controlsImpl</tt> is an instance of the
     * specified <tt>controlType</tt>; otherwise, <tt>null</tt>
     */
    @SuppressWarnings("unchecked")
    public static <T> T queryInterface(
            Controls controlsImpl,
            Class<T> controlType)
    {
        T control;

        if (controlsImpl == null)
        {
            control = null;
        }
        else
        {
            control = (T) controlsImpl.getControl(controlType.getName());
            if ((control == null) && controlType.isInstance(controlsImpl))
                control = (T) controlsImpl;
        }
        return control;
    }

    /**
     * Returns an instance of a specific <tt>Class</tt> which is either a
     * control of a specific <tt>Controls</tt> implementation or the
     * <tt>Controls</tt> implementation itself if it is an instance of the
     * specified <tt>Class</tt>. The method is similar to
     * {@link #getControl(Controls, String)} in querying the specified
     * <tt>Controls</tt> implementation about a control of the specified
     * <tt>Class</tt> but is different in looking at the type hierarchy of the
     * <tt>Controls</tt> implementation for the specified <tt>Class</tt>.
     *
     * @param controlsImpl the <tt>Controls</tt> implementation to query
     * @param controlType the runtime type of the instance to be returned
     * @return an instance of the specified <tt>controlType</tt> if such an
     * instance can be found among the controls of the specified
     * <tt>controlsImpl</tt> or <tt>controlsImpl</tt> is an instance of the
     * specified <tt>controlType</tt>; otherwise, <tt>null</tt>
     */
    public static Object queryInterface(
            Controls controlsImpl,
            String controlType)
    {
        Object control;

        if (controlsImpl == null)
        {
            control = null;
        }
        else
        {
            control = controlsImpl.getControl(controlType);
            if (control == null)
            {
                Class<?> controlClass;

                try
                {
                    controlClass = Class.forName(controlType);
                }
                catch (ClassNotFoundException cnfe)
                {
                    controlClass = null;
                    logger.warn(
                            "Failed to find control class " + controlType,
                            cnfe);
                }
                if ((controlClass != null)
                        && controlClass.isInstance(controlsImpl))
                {
                    control = controlsImpl;
                }
            }
        }
        return control;
    }
}
