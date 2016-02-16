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
package org.jitsi.impl.configuration;

import java.io.*;
import java.util.*;

import org.jitsi.util.xml.*;

/**
 *
 * @author Lyubomir Marinov
 */
@SuppressWarnings("rawtypes")
public abstract class DatabaseConfigurationStore
    extends HashtableConfigurationStore<Hashtable>
{
    /**
     * Initializes a new <tt>DatabaseConfigurationStore</tt> instance.
     */
    protected DatabaseConfigurationStore()
    {
        this(new Hashtable());
    }

    /**
     * Initializes a new <tt>DatabaseConfigurationStore</tt> instance with a
     * specific runtime <tt>Hashtable</tt> storage.
     *
     * @param properties the <tt>Hashtable</tt> which is to become the runtime
     * storage of the new instance
     */
    protected DatabaseConfigurationStore(Hashtable properties)
    {
        super(properties);
    }

    /**
     * Removes all property name-value associations currently present in this
     * <tt>ConfigurationStore</tt> instance and deserializes new property
     * name-value associations from its underlying database (storage).
     *
     * @throws IOException if there is an input error while reading from the
     * underlying database (storage)
     */
    protected abstract void reloadConfiguration()
        throws IOException;

    /**
     * Removes all property name-value associations currently present in this
     * <tt>ConfigurationStore</tt> and deserializes new property name-value
     * associations from a specific <tt>File</tt> which presumably is in the
     * format represented by this instance.
     *
     * @param file the <tt>File</tt> to be read and to deserialize new property
     * name-value associations from into this instance
     * @throws IOException if there is an input error while reading from the
     * specified <tt>file</tt>
     * @throws XMLException if parsing the contents of the specified
     * <tt>file</tt> fails
     * @see ConfigurationStore#reloadConfiguration(File)
     */
    public void reloadConfiguration(File file)
        throws IOException,
               XMLException
    {
        properties.clear();

        reloadConfiguration();
    }

    /**
     * Stores/serializes the property name-value associations currently present
     * in this <tt>ConfigurationStore</tt> instance into its underlying database
     * (storage).
     *
     * @throws IOException if there is an output error while storing the
     * properties managed by this <tt>ConfigurationStore</tt> instance into its
     * underlying database (storage)
     */
    protected void storeConfiguration()
        throws IOException
    {
    }

    /**
     * Stores/serializes the property name-value associations currently present
     * in this <tt>ConfigurationStore</tt> into a specific <tt>OutputStream</tt>
     * in the format represented by this instance.
     *
     * @param out the <tt>OutputStream</tt> to receive the serialized form of
     * the property name-value associations currently present in this
     * <tt>ConfigurationStore</tt>
     * @throws IOException if there is an output error while storing the
     * properties managed by this <tt>ConfigurationStore</tt> into the specified
     * <tt>file</tt>
     * @see ConfigurationStore#storeConfiguration(OutputStream)
     */
    public void storeConfiguration(OutputStream out)
        throws IOException
    {
        storeConfiguration();
    }
}
