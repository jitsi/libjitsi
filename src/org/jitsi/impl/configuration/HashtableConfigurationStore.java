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

import java.util.*;

/**
 * A simple in-memory {@link ConfigurationStore} implementation that only uses
 * a hashtable.
 *
 * @param <T> the hashtable extension that descendants are going to use.
 * @author Lyubomir Marinov
 */
@SuppressWarnings("rawtypes")
public abstract class HashtableConfigurationStore<T extends Hashtable>
    implements ConfigurationStore
{

    /**
     * The <tt>Hashtable</tt> instance which stores the property name-value
     * associations of this <tt>ConfigurationStore</tt> instance and which is
     * effectively adapted by this instance to <tt>ConfigurationStore</tt>.
     */
    protected final T properties;

    /**
     * Creates an instance of this class using <tt>properties</tt> as the set
     * of properties where it will be storing an retrieving properties.
     *
     * @param properties the map that this store will use for storing and
     * retrieving properties.
     */
    protected HashtableConfigurationStore(T properties)
    {
        this.properties = properties;
    }

    /**
     * Implements {@link ConfigurationStore#getProperty(String)}. If this
     * <tt>ConfigurationStore</tt> contains a value associated with the
     * specified property name, returns it. Otherwise, searches for a system
     * property with the specified name and returns its value.
     *
     * @param name the name of the property to get the value of
     * @return the value in this <tt>ConfigurationStore</tt> of the property
     * with the specified name; <tt>null</tt> if the property with the specified
     * name does not have an association with a value in this
     * <tt>ConfigurationStore</tt>
     * @see ConfigurationStore#getProperty(String)
     */
    public Object getProperty(String name)
    {
        Object value = properties.get(name);

        return (value != null) ? value : System.getProperty(name);
    }

    /**
     * Implements {@link ConfigurationStore#getPropertyNames()}. Gets the names
     * of the properties which have values associated in this
     * <tt>ConfigurationStore</tt>.
     *
     * @return an array of <tt>String</tt>s which specify the names of the
     * properties that have values associated in this
     * <tt>ConfigurationStore</tt>; an empty array if this instance contains no
     * property values
     * @see ConfigurationStore#getPropertyNames()
     */
    public String[] getPropertyNames()
    {
        synchronized (properties)
        {
            Set<?> propertyNames = properties.keySet();

            return propertyNames.toArray(new String[propertyNames.size()]);
        }
    }

    /**
     * Implements {@link ConfigurationStore#isSystemProperty(String)}. Considers
     * a property to be system if the system properties contain a value
     * associated with its name.
     *
     * @param name the name of a property which is to be determined whether it
     * is a system property
     * @return <tt>true</tt> if the specified name stands for a system property;
     * <tt>false</tt>, otherwise
     * @see ConfigurationStore#isSystemProperty(String)
     */
    public boolean isSystemProperty(String name)
    {
        return (System.getProperty(name) != null);
    }

    /**
     * Implements {@link ConfigurationStore#removeProperty(String)}. Removes the
     * value association in this <tt>ConfigurationStore</tt> of the property
     * with a specific name. If the property with the specified name is not
     * associated with a value in this <tt>ConfigurationStore</tt>, does
     * nothing.
     *
     * @param name the name of the property which is to have its value
     * association in this <tt>ConfigurationStore</tt> removed
     * @see ConfigurationStore#removeProperty(String)
     */
    public void removeProperty(String name)
    {
        properties.remove(name);
    }

    /**
     * Implements
     * {@link ConfigurationStore#setNonSystemProperty(String, Object)}.
     *
     * @param name the name of the non-system property to be set to the
     * specified value in this <tt>ConfigurationStore</tt>
     * @param value the value to be assigned to the non-system property with the
     * specified name in this <tt>ConfigurationStore</tt>
     * @see ConfigurationStore#setNonSystemProperty(String, Object)
     */
    @SuppressWarnings("unchecked")
    public void setNonSystemProperty(String name, Object value)
    {
        properties.put(name, value);
    }

    /**
     * Implements {@link ConfigurationStore#setSystemProperty(String)}. Since
     * system properties are managed through the <tt>System</tt> class, setting
     * a property as system in this <tt>ConfigurationStore</tt> effectively
     * removes any existing value associated with the specified property name
     * from this instance.
     *
     * @param name the name of the property to be set as a system property in
     * this <tt>ConfigurationStore</tt>
     * @see ConfigurationStore#setSystemProperty(String)
     */
    public void setSystemProperty(String name)
    {
        removeProperty(name);
    }
}
