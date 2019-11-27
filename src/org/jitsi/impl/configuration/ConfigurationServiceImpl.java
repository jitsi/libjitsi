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

import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.jitsi.impl.configuration.xml.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;
import org.jitsi.util.xml.*;
import org.jitsi.utils.*;
import org.jitsi.utils.logging.*;

/**
 * A straightforward implementation of the <tt>ConfigurationService</tt> using
 * an XML or a .properties file for storing properties. Currently only
 * <tt>String</tt> properties are meaningfully saved (we should probably
 * consider how and whether we should take care of the rest).
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Pawel Domas
 */
public class ConfigurationServiceImpl
    implements ConfigurationService
{
    /**
     * The <tt>Logger</tt> used by this <tt>ConfigurationServiceImpl</tt>
     * instance for logging output.
     */
    private final Logger logger
        = Logger.getLogger(ConfigurationServiceImpl.class);

    /**
     * The name of the <tt>ConfigurationStore</tt> class to be used as the
     * default when no specific <tt>ConfigurationStore</tt> class is determined
     * as necessary.
     */
    private static final String DEFAULT_CONFIGURATION_STORE_CLASS_NAME
        = "net.java.sip.communicator.impl.configuration"
            + ".SQLiteConfigurationStore";

    /**
     * Name of the system file name property.
     */
    private static final String SYS_PROPS_FILE_NAME_PROPERTY
        = "net.java.sip.communicator.SYS_PROPS_FILE_NAME";

    /**
     * Name of the file containing default properties.
     */
    private static final String DEFAULT_PROPS_FILE_NAME
                                         = "jitsi-defaults.properties";

    /**
     * Name of the file containing overrides (possibly set by the deployer)
     * for any of the default properties.
     */
    private static final String DEFAULT_OVERRIDES_PROPS_FILE_NAME
                                             = "jitsi-default-overrides.properties";

    /**
     * Specify names of command line arguments which are password, so that their
     * values will be masked when 'sun.java.command' is printed to the logs.
     * Separate each name with a comma.
     */
    public static String PASSWORD_CMD_LINE_ARGS;

    /**
     * Set this filed value to a regular expression which will be used to select
     * system properties keys whose values should be masked when printed out to
     * the logs.
     */
    public static String PASSWORD_SYS_PROPS;

    /**
     * A reference to the currently used configuration file.
     */
    private File configurationFile = null;

    /**
     * A set of immutable properties deployed with the application during
     * install time. The properties in this file will be impossible to override
     * and attempts to do so will simply be ignored.
     * @see #defaultProperties
     */
    private Map<String, String> immutableDefaultProperties = new HashMap<>();

    /**
     * A set of properties deployed with the application during install time.
     * Contrary to the properties in {@link #immutableDefaultProperties} the
     * ones in this map can be overridden with call to the
     * <tt>setProperty()</tt> methods. Still, re-setting one of these properties
     * to <tt>null</tt> would cause for its initial value to be restored.
     */
    private Map<String, String> defaultProperties = new HashMap<>();

    /**
     * Our event dispatcher.
     */
    private final ChangeEventDispatcher changeEventDispatcher
        = new ChangeEventDispatcher(this);

    /**
     * A (cached) reference to a <tt>FileAccessService</tt> implementation used
     * by this <tt>ConfigurationService</tt> implementation.
     */
    private FileAccessService faService;

    /**
     * The indicator which determines whether this instance has assigned a value
     * to {@link #faService}. Introduced in order to avoid multiple attempts to
     * query for a <tt>FileAccessService</tt> implementation while still
     * delaying the initial query.
     */
    private boolean faServiceIsAssigned = false;

    /**
     * The <code>ConfigurationStore</code> implementation which contains the
     * property name-value associations of this
     * <code>ConfigurationService</code> and performs their actual storing in
     * <code>configurationFile</code>.
     */
    private ConfigurationStore store;

    public ConfigurationServiceImpl()
    {
        /*
         * XXX We explicitly delay the query for the FileAccessService
         * implementation because FileAccessServiceImpl looks for properties set
         * by methods of ConfigurationServiceImpl and we want to make sure that
         * we have given the chance to this ConfigurationServiceImpl to set
         * these properties before FileAccessServiceImpl looks for them.
         */

        try
        {
            debugPrintSystemProperties();
            preloadSystemPropertyFiles();
            loadDefaultProperties();
            reloadConfiguration();
        }
        catch (IOException ex)
        {
            logger.error("Failed to load the configuration file", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperty(String propertyName, Object property)
        throws ConfigPropertyVetoException
    {
        setProperty(propertyName, property, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperty(String propertyName,
                            Object property,
                            boolean isSystem)
        throws ConfigPropertyVetoException
    {
        Object oldValue = getProperty(propertyName);

        //first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
            changeEventDispatcher.fireVetoableChange(
                propertyName, oldValue, property);

        //no exception was thrown - lets change the property and fire a
        //change event

        if (logger.isTraceEnabled())
            logger.trace(propertyName + "( oldValue=" + oldValue
                     + ", newValue=" + property + ".");

        doSetProperty(propertyName, property, isSystem);

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error(
                "Failed to store configuration after a property change");
        }

        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
            changeEventDispatcher.firePropertyChange(
                propertyName, oldValue, property);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperties(Map<String, Object> properties)
        throws ConfigPropertyVetoException
    {
        //first check whether the changes are ok with everyone
        Map<String, Object> oldValues = new HashMap<>(properties.size());
        for (Map.Entry<String, Object> property : properties.entrySet())
        {
            String propertyName = property.getKey();
            Object oldValue = getProperty(propertyName);

            oldValues.put(propertyName, oldValue);

            if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
                changeEventDispatcher
                    .fireVetoableChange(
                        propertyName,
                        oldValue,
                        property.getValue());
        }

        for (Map.Entry<String, Object> property : properties.entrySet())
            doSetProperty(property.getKey(), property.getValue(), false);

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error(
                "Failed to store configuration after property changes");
        }

        for (Map.Entry<String, Object> property : properties.entrySet())
        {
            String propertyName = property.getKey();

            if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
                changeEventDispatcher
                    .firePropertyChange(
                        propertyName,
                        oldValues.get(propertyName),
                        property.getValue());
        }
    }

    /**
     * Performs the actual setting of a property with a specific name to a
     * specific new value without asking <code>VetoableChangeListener</code>,
     * storing into the configuration file and notifying
     * {@link PropertyChangeListener}s.
     *
     * @param propertyName the name of the property which is to be set to a
     * specific value.
     * @param property the value to be assigned to the property with the
     * specified name
     * @param isSystem <tt>true</tt> if the property with the specified name is
     * to be set as a system property; <tt>false</tt>, otherwise
     */
    private void doSetProperty(
        String propertyName, Object property, boolean isSystem)
    {
        //once set system, a property remains system even if the user
        //specified sth else

        if (isSystemProperty(propertyName))
            isSystem = true;

        // ignore requests to override immutable properties:
        if (immutableDefaultProperties.containsKey(propertyName))
            return;

        if (property == null)
        {
            store.removeProperty(propertyName);

            if (isSystem)
            {
                //we can't remove or nullset a sys prop so let's "empty" it.
                System.setProperty(propertyName, "");
            }
        }
        else
        {
            if (isSystem)
            {
                //in case this is a system property, we must only store it
                //in the System property set and keep only a ref locally.
                System.setProperty(propertyName, property.toString());
                store.setSystemProperty(propertyName);
            }
            else
            {
                store.setNonSystemProperty(propertyName, property);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeProperty(String propertyName)
    {
        List<String> childPropertyNames =
            getPropertyNamesByPrefix(propertyName, false);

        //remove all properties
        for (String pName : childPropertyNames)
        {
            removePropertyInternal(pName);
        }

        try
        {
            storeConfiguration();
        }
        catch (IOException ex)
        {
            logger.error("Failed to store configuration after "
                + "a property change");
        }
    }

    /**
     * Removes the property with the specified name. Calling
     * this method would first trigger a PropertyChangeEvent that will
     * be dispatched to all VetoableChangeListeners. In case no complaints
     * (PropertyVetoException) have been received, the property will be actually
     * changed and a PropertyChangeEvent will be dispatched.
     * All properties with prefix propertyName will also be removed.
     * <p>
     * Does not store anything.
     *
     * @param propertyName the name of the property to change.
     */
    private void removePropertyInternal(String propertyName)
    {
        Object oldValue = getProperty(propertyName);
        //first check whether the change is ok with everyone
        if (changeEventDispatcher.hasVetoableChangeListeners(propertyName))
            changeEventDispatcher.fireVetoableChange(
                propertyName, oldValue, null);

        //no exception was thrown - lets change the property and fire a
        //change event

        if (logger.isTraceEnabled())
            logger.trace("Will remove prop: " + propertyName + ".");

        store.removeProperty(propertyName);

        if (changeEventDispatcher.hasPropertyChangeListeners(propertyName))
            changeEventDispatcher.firePropertyChange(
                propertyName, oldValue, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getProperty(String propertyName)
    {
        Object result = immutableDefaultProperties.get(propertyName);

        if (result != null)
            return result;

        result = store.getProperty(propertyName);

        if (result != null)
            return result;

        return defaultProperties.get(propertyName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllPropertyNames()
    {
        return
            Collections.unmodifiableList(
                    Arrays.asList(store.getPropertyNames()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getPropertyNamesByPrefix(String prefix,
            boolean exactPrefixMatch)
    {
        HashSet<String> resultKeySet = new HashSet<>();

        //first fill in the names from the immutable default property set
        Set<String> propertyNameSet;
        String[] namesArray;

        if(immutableDefaultProperties.size() > 0)
        {
            propertyNameSet = immutableDefaultProperties.keySet();

            namesArray
                = propertyNameSet.toArray( new String[propertyNameSet.size()] );

            getPropertyNamesByPrefix(prefix,
                                     exactPrefixMatch,
                                     namesArray,
                                     resultKeySet);
        }

        //now get property names from the current store.
        getPropertyNamesByPrefix(prefix,
                                 exactPrefixMatch,
                                 store.getPropertyNames(),
                                 resultKeySet);

        //finally, get property names from mutable default property set.
        if(defaultProperties.size() > 0)
        {
            propertyNameSet = defaultProperties.keySet();
            namesArray
                = propertyNameSet.toArray(new String[propertyNameSet.size()]);

            getPropertyNamesByPrefix(prefix,
                                     exactPrefixMatch,
                                     namesArray,
                                     resultKeySet);
        }

        return new ArrayList<>(resultKeySet);
    }

    /**
     * Updates the specified <tt>String</tt> <tt>resulSet</tt> to contain all
     * property names in the <tt>names</tt> array that partially or completely
     * match the specified prefix. Depending on the value of the
     * <tt>exactPrefixMatch</tt> parameter the method will (when false)
     * or will not (when exactPrefixMatch is true) include property names that
     * have prefixes longer than the specified <tt>prefix</tt> param.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps
     * part of a property name) that we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned
     * property names should all have a prefix that is an exact match of the
     * the <tt>prefix</tt> param or whether properties with prefixes that
     * contain it but are longer than it are also accepted.
     * @param names the list of names that we'd like to search.
     *
     * @return a reference to the updated result set.
     */
    private Set<String> getPropertyNamesByPrefix(
                            String      prefix,
                            boolean     exactPrefixMatch,
                            String[]    names,
                            Set<String> resultSet)
    {
        for (String key : names)
        {
            if(exactPrefixMatch)
            {
                int ix = key.lastIndexOf('.');

                if(ix == -1)
                    continue;

                String keyPrefix = key.substring(0, ix);

                if(prefix.equals(keyPrefix))
                    resultSet.add(key);
            }
            else
            {
                if(key.startsWith(prefix))
                    resultSet.add(key);
            }
        }

        return resultSet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getPropertyNamesBySuffix(String suffix)
    {
        List<String> resultKeySet = new LinkedList<>();

        for (String key : store.getPropertyNames())
        {
            int ix = key.lastIndexOf('.');

            if (ix != -1 && suffix.equals(key.substring(ix + 1)))
                resultKeySet.add(key);
        }
        return resultKeySet;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.addPropertyChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        changeEventDispatcher.removePropertyChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPropertyChangeListener(String propertyName,
                                          PropertyChangeListener listener)
    {
        changeEventDispatcher.
            addPropertyChangeListener(propertyName, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePropertyChangeListener(String propertyName,
                                             PropertyChangeListener listener)
    {
        changeEventDispatcher.
            removePropertyChangeListener(propertyName, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addVetoableChangeListener(ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.addVetoableChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeVetoableChangeListener(ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.removeVetoableChangeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addVetoableChangeListener(String propertyName,
            ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.addVetoableChangeListener(propertyName, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeVetoableChangeListener(String propertyName,
            ConfigVetoableChangeListener listener)
    {
        changeEventDispatcher.removeVetoableChangeListener(propertyName,
            listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reloadConfiguration()
        throws IOException
    {
        this.configurationFile = null;

        File file = getConfigurationFile();

        if (file != null)
        {
            FileAccessService faService = getFileAccessService();

            if (faService != null)
            {
                // Restore the file if necessary.
                FailSafeTransaction trans
                    = faService.createFailSafeTransaction(file);
    
                try
                {
                    trans.restoreFile();
                }
                catch (Exception e)
                {
                    logger.error(
                            "Failed to restore configuration file " + file,
                            e);
                }
            }
        }

        try
        {
            store.reloadConfiguration(file);
        }
        catch (XMLException xmle)
        {
            throw new IOException(xmle);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void storeConfiguration()
        throws IOException
    {
        storeConfiguration(getConfigurationFile());
    }

    /**
     * Stores local properties in the specified configuration file.
     *
     * @param file a reference to the configuration file where properties should
     * be stored.
     * @throws IOException if there was a problem writing to the specified file.
     */
    private void storeConfiguration(File file)
        throws IOException
    {
         // If the configuration file is forcibly considered read-only, do not
         // write it.
        String readOnly
            = System.getProperty(PNAME_CONFIGURATION_FILE_IS_READ_ONLY);

        if ((readOnly != null) && Boolean.parseBoolean(readOnly))
            return;

        // write the file.
        FailSafeTransaction trans = null;

        if (file != null)
        {
            FileAccessService faService = getFileAccessService();

            if (faService != null)
                trans = faService.createFailSafeTransaction(file);
        }

        Throwable exception = null;

        try
        {
            if (trans != null)
                trans.beginTransaction();

            OutputStream stream
                = (file == null) ? null : new FileOutputStream(file);

            try
            {
                store.storeConfiguration(stream);
            }
            finally
            {
                if (stream != null)
                    stream.close();
            }

            if (trans != null)
                trans.commit();
        }
        catch (IllegalStateException | IOException e)
        {
            exception = e;
        }
        if (exception != null)
        {
            logger.error(
                    "can't write data in the configuration file",
                    exception);
            if (trans != null)
                trans.rollback();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConfigurationFilename()
    {
        try
        {
            File file =  getConfigurationFile();
            if(file != null)
                return file.getName();
        }
        catch(IOException ex)
        {
            logger.error("Error loading configuration file", ex);
        }

        return null;
    }

    /**
     * Returns the configuration file currently used by the implementation.
     * If there is no such file or this is the first time we reference it
     * a new one is created.
     * @return the configuration File currently used by the implementation.
     */
    private File getConfigurationFile()
        throws IOException
    {
        if (this.configurationFile == null)
        {
            createConfigurationFile();

            /*
             * Make sure that the properties SC_HOME_DIR_LOCATION and
             * SC_HOME_DIR_NAME are available in the store of this instance so
             * that users don't have to ask the system properties again.
             */
            getScHomeDirLocation();
            getScHomeDirName();
        }
        return this.configurationFile;
    }

    /**
     * Determines the name and the format of the configuration file to be used
     * and initializes the {@link #configurationFile} and {@link #store} fields
     * of this instance.
     */
    private void createConfigurationFile()
        throws IOException
    {

        /*
         * Choose the format of the configuration file so that the
         * performance-savvy properties format is used whenever possible and
         * only go with the slow and fat XML format when necessary.
         */
        File configurationFile = getConfigurationFile("xml", false);

        if (configurationFile == null)
        {
            /*
             * It's strange that there's no configuration file name but let it
             * play out as it did when the configuration file was in XML format.
             */
            setConfigurationStore(XMLConfigurationStore.class);
        }
        else
        {

            /*
             * Figure out the format of the configuration file by looking at its
             * extension.
             */
            String name = configurationFile.getName();
            int extensionBeginIndex = name.lastIndexOf('.');
            String extension
                = (extensionBeginIndex > -1)
                        ? name.substring(extensionBeginIndex)
                        : null;

            /*
             * Obviously, a file with the .properties extension is in the
             * properties format. Since there's no file with the .xml extension,
             * the case is simple.
             */
            if (".properties".equalsIgnoreCase(extension))
            {
                this.configurationFile = configurationFile;
                if (!(this.store instanceof PropertyConfigurationStore))
                    this.store = new PropertyConfigurationStore();
            }
            else
            {

                /*
                 * But if we're told that the configuration file name is with
                 * the .xml extension, we may also have a .properties file or
                 * the .xml extension may be only the default and not forced on
                 * us so it may be fine to create a .properties file and use the
                 * properties format anyway.
                 */
                File newConfigurationFile
                    = new File(
                            configurationFile.getParentFile(),
                            ((extensionBeginIndex > -1)
                                    ? name.substring(0, extensionBeginIndex)
                                    : name)
                                + ".properties");

                /*
                 * If there's an actual file with the .properties extension,
                 * then we've previously migrated the configuration from the XML
                 * format to the properties format. We may have failed to delete
                 * the migrated .xml file but it's fine because the .properties
                 * file is there to signal that we have to use it instead of the
                 * .xml file.
                 */
                if (newConfigurationFile.exists())
                {
                    this.configurationFile = newConfigurationFile;
                    if (!(this.store instanceof PropertyConfigurationStore))
                        this.store = new PropertyConfigurationStore();
                }
                /*
                 * Otherwise, the lack of an existing .properties file doesn't
                 * help us much and we have the .xml extension for the file name
                 * so we have to determine whether it's just the default or it's
                 * been forced on us.
                 */
                else if (getSystemProperty(PNAME_CONFIGURATION_FILE_NAME)
                            == null)
                {
                    Class<? extends ConfigurationStore>
                        defaultConfigurationStoreClass
                            = getDefaultConfigurationStoreClass();

                    /*
                     * The .xml is not forced on us so we allow ourselves to not
                     * obey the default and use the properties format. If a
                     * configuration file in the XML format exists already, we
                     * have to migrate it to the properties format.
                     */
                    if (configurationFile.exists())
                    {
                        ConfigurationStore xmlStore
                            = new XMLConfigurationStore();
                        try
                        {
                            xmlStore.reloadConfiguration(configurationFile);
                        }
                        catch (XMLException xmlex)
                        {
                            throw new IOException(xmlex);
                        }

                        setConfigurationStore(defaultConfigurationStoreClass);
                        if (this.store != null)
                            copy(xmlStore, this.store);

                        Throwable exception = null;
                        try
                        {
                            storeConfiguration(this.configurationFile);
                        }
                        catch (IllegalStateException | IOException e)
                        {
                            exception = e;
                        }
                        if (exception == null)
                        {
                            configurationFile.delete();
                        }
                        else
                        {
                            this.configurationFile = configurationFile;
                            this.store = xmlStore;
                        }
                    }
                    else
                    {
                        setConfigurationStore(defaultConfigurationStoreClass);
                    }
                }
                else
                {

                    /*
                     * The .xml extension is forced on us so we have to assume
                     * that whoever forced it knows what she wants to get so we
                     * have to obey and use the XML format.
                     */
                    this.configurationFile =
                            configurationFile.exists()
                                ? configurationFile
                                : getConfigurationFile("xml", true);
                    if (!(this.store instanceof XMLConfigurationStore))
                        this.store = new XMLConfigurationStore();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScHomeDirLocation()
    {
        //first let's check whether we already have the name of the directory
        //set as a configuration property
        String scHomeDirLocation = null;

        if (store != null)
            scHomeDirLocation = getString(PNAME_SC_HOME_DIR_LOCATION);

        if (scHomeDirLocation == null)
        {
            //no luck, check whether user has specified a custom name in the
            //system properties
            scHomeDirLocation
                = getSystemProperty(PNAME_SC_HOME_DIR_LOCATION);

            if (scHomeDirLocation == null)
                scHomeDirLocation = getSystemProperty("user.home");

            //now save all this as a configuration property so that we don't
            //have to look for it in the sys props next time and so that it is
            //available for other bundles to consult.
            if (store != null)
            {
                store.setNonSystemProperty(
                        PNAME_SC_HOME_DIR_LOCATION,
                        scHomeDirLocation);
            }
        }

        return scHomeDirLocation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getScHomeDirName()
    {
        //first let's check whether we already have the name of the directory
        //set as a configuration property
        String scHomeDirName = null;

        if (store != null)
            scHomeDirName = getString(PNAME_SC_HOME_DIR_NAME);

        if (scHomeDirName == null)
        {
            //no luck, check whether user has specified a custom name in the
            //system properties
            scHomeDirName
                = getSystemProperty(PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName == null)
                scHomeDirName = ".sip-communicator";

            //now save all this as a configuration property so that we don't
            //have to look for it in the sys props next time and so that it is
            // available for other bundles to consult.
            if (store != null)
                store
                    .setNonSystemProperty(
                        PNAME_SC_HOME_DIR_NAME,
                        scHomeDirName);
        }

        return scHomeDirName;
    }

    /**
     * Returns a reference to the configuration file that the service should
     * load. The method would try to load a file with the name
     * sip-communicator.xml unless a different one is specified in the system
     * property net.java.sip.communicator.PROPERTIES_FILE_NAME . The method
     * would first try to load the file from the current directory if it exists
     * this is not the case a load would be attempted from the
     * $HOME/.sip-communicator directory. In case it was not found there either
     * we'll look for it in all locations currently present in the $CLASSPATH.
     * In case we find it in there we will copy it to the
     * $HOME/.sip-communicator directory in case it was in a jar archive and
     * return the reference to the newly created file. In case the file is to be
     * found nowhere - a new empty file in the user home directory and returns a
     * link to that one.
     *
     * @param extension
     *            the extension of the file name of the configuration file. The
     *            specified extension may not be taken into account if the the
     *            configuration file name is forced through a system property.
     * @param create
     *            <tt>true</tt> to create the configuration file with the
     *            determined file name if it does not exist; <tt>false</tt> to
     *            only figure out the file name of the configuration file
     *            without creating it
     * @return the configuration file currently used by the implementation.
     */
    private File getConfigurationFile(String extension, boolean create)
        throws IOException
    {
        //see whether we have a user specified name for the conf file
        String pFileName = getSystemProperty(PNAME_CONFIGURATION_FILE_NAME);
        if (pFileName == null)
            pFileName = "sip-communicator." + extension;

        // try to open the file in current directory
        File configFileInCurrentDir = new File(pFileName);
        if (configFileInCurrentDir.exists())
        {
            if (logger.isDebugEnabled())
                logger.debug(
                        "Using config file in current dir: "
                            + configFileInCurrentDir.getAbsolutePath());
            return configFileInCurrentDir;
        }

        // we didn't find it in ".", try the SIP Communicator home directory
        // first check whether a custom SC home directory is specified

        File configDir
            = new File(
                    getScHomeDirLocation()
                        + File.separator
                        + getScHomeDirName());
        File configFileInUserHomeDir = new File(configDir, pFileName);

        if (configFileInUserHomeDir.exists())
        {
            if (logger.isDebugEnabled())
                logger.debug(
                "Using config file in $HOME/.sip-communicator: "
                    + configFileInUserHomeDir.getAbsolutePath());
            return configFileInUserHomeDir;
        }

        // If we are in a jar - copy config file from jar to user home.
        InputStream in
            = getClass().getClassLoader().getResourceAsStream(pFileName);

        //Return an empty file if there wasn't any in the jar
        //null check report from John J. Barton - IBM
        if (in == null)
        {
            if (create)
            {
                configDir.mkdirs();
                configFileInUserHomeDir.createNewFile();
            }
            if (logger.isDebugEnabled())
                logger.debug(
                "Created an empty file in $HOME: "
                    + configFileInUserHomeDir.getAbsolutePath());
            return configFileInUserHomeDir;
        }

        if (logger.isTraceEnabled())
            logger.trace(
            "Copying config file from JAR into "
                + configFileInUserHomeDir.getAbsolutePath());
        configDir.mkdirs();
        try
        {
            copy(in, configFileInUserHomeDir);
        }
        finally
        {
            try
            {
                in.close();
            }
            catch (IOException ioex)
            {
                /*
                 * Ignore it because it doesn't matter and, most importantly, it
                 * shouldn't prevent us from using the configuration file.
                 */
            }
        }
        return configFileInUserHomeDir;
    }

    /**
     * Gets the <tt>ConfigurationStore</tt> <tt>Class</tt> to be used as the
     * default when no specific <tt>ConfigurationStore</tt> <tt>Class</tt> is
     * determined as necessary.
     *
     * @return the <tt>ConfigurationStore</tt> <tt>Class</tt> to be used as the
     * default when no specific <tt>ConfigurationStore</tt> <tt>Class</tt> is
     * determined as necessary
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends ConfigurationStore>
        getDefaultConfigurationStoreClass()
    {
        Class<? extends ConfigurationStore> defaultConfigurationStoreClass
            = null;

        if (DEFAULT_CONFIGURATION_STORE_CLASS_NAME != null)
        {
            Class<?> clazz = null;

            try
            {
                clazz = Class.forName(DEFAULT_CONFIGURATION_STORE_CLASS_NAME);
            }
            catch (ClassNotFoundException cnfe)
            {
            }
            if ((clazz != null)
                    && ConfigurationStore.class.isAssignableFrom(clazz))
                defaultConfigurationStoreClass
                    = (Class<? extends ConfigurationStore>) clazz;
        }
        if (defaultConfigurationStoreClass == null)
            defaultConfigurationStoreClass = PropertyConfigurationStore.class;
        return defaultConfigurationStoreClass;
    }

    private static void copy(ConfigurationStore src, ConfigurationStore dest)
    {
        for (String name : src.getPropertyNames())
            if (src.isSystemProperty(name))
                dest.setSystemProperty(name);
            else
                dest.setNonSystemProperty(name, src.getProperty(name));
    }

    /**
     * Copies the contents of a specific <code>InputStream</code> as bytes into
     * a specific output <code>File</code>.
     *
     * @param inputStream
     *            the <code>InputStream</code> the contents of which is to be
     *            output in the specified <code>File</code>
     * @param outputFile
     *            the <code>File</code> to write the contents of the specified
     *            <code>InputStream</code> into
     * @throws IOException
     */
    private static void copy(InputStream inputStream, File outputFile)
        throws IOException
    {
        OutputStream outputStream = new FileOutputStream(outputFile);

        try
        {
            byte[] bytes = new byte[4 * 1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(bytes)) != -1)
                outputStream.write(bytes, 0, bytesRead);
        }
        finally
        {
            outputStream.close();
        }
    }

    /**
     * Returns the value of the specified java system property. In case the
     * value was a zero length String or one that only contained whitespaces,
     * null is returned. This method is for internal use only. Users of the
     * configuration service are to use the getProperty() or getString() methods
     * which would automatically determine whether a property is system or not.
     * @param propertyName the name of the property whose value we need.
     * @return the value of the property with name propertyName or null if
     * the value had length 0 or only contained spaces tabs or new lines.
     */
    private static String getSystemProperty(String propertyName)
    {
        String retval = System.getProperty(propertyName);
        if ((retval != null) && (retval.trim().length() == 0))
            retval = null;
        return retval;
    }

    /**
     * Returns the String value of the specified property (minus all
     * encompassing whitespaces) and null in case no property value was mapped
     * against the specified propertyName, or in case the returned property
     * string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     * @return the result of calling the property's toString method and null in
     * case there was no value mapped against the specified.
     * <tt>propertyName</tt>, or the returned string had zero length or
     * contained whitespaces only.
     */
    @Override
    public String getString(String propertyName)
    {
        Object propValue = getProperty(propertyName);
        if (propValue == null)
            return null;

        String propStrValue = propValue.toString().trim();

        return (propStrValue.length() > 0)
                    ? propStrValue
                    : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(String propertyName, String defaultValue)
    {
        String value = getString(propertyName);
        return value != null ? value : defaultValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBoolean(String propertyName, boolean defaultValue)
    {
        String stringValue = getString(propertyName);

        return (stringValue == null) ? defaultValue : Boolean
            .parseBoolean(stringValue);
    }

    /**
     * Gets a (cached) reference to a <tt>FileAccessService</tt> implementation
     * to be used by this <tt>ConfigurationService</tt> implementation.
     *
     * @return a (cached) reference to a <tt>FileAccessService</tt>
     * implementation
     */
    private synchronized FileAccessService getFileAccessService()
    {
        if (faService == null && !faServiceIsAssigned)
        {
            faService = LibJitsi.getFileAccessService();
            faServiceIsAssigned = true;
        }
        return faService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInt(String propertyName, int defaultValue)
    {
        String stringValue = getString(propertyName);
        int intValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                intValue = Integer.parseInt(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.error(propertyName
                    + " does not appear to be an integer. " + "Defaulting to "
                    + defaultValue + ".", ex);
            }
        }
        return intValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getDouble(String propertyName, double defaultValue)
    {
        String stringValue = getString(propertyName);
        double doubleValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                doubleValue = Double.parseDouble(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.error(propertyName + " does not appear to be a double. "
                             + "Defaulting to " + defaultValue + ".", ex);
            }
        }
        return doubleValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLong(String propertyName, long defaultValue)
    {
        String stringValue = getString(propertyName);
        long longValue = defaultValue;

        if ((stringValue != null) && (stringValue.length() > 0))
        {
            try
            {
                longValue = Long.parseLong(stringValue);
            }
            catch (NumberFormatException ex)
            {
                logger.error(
                    propertyName
                        + " does not appear to be a long integer. "
                        + "Defaulting to " + defaultValue + ".",
                ex);
            }
        }
        return longValue;
    }

    /**
     * Determines whether the property with the specified
     * <tt>propertyName</tt> has been previously declared as System
     *
     * @param propertyName the name of the property to verify
     * @return true if someone at some point specified that property to be
     * system. (This could have been either through a call to
     * setProperty(string, true)) or by setting the system attribute in the
     * xml conf file to true.
     */
    private boolean isSystemProperty(String propertyName)
    {
        return store.isSystemProperty(propertyName);
    }

    /**
     * Deletes the configuration file currently used by this implementation.
     */
    @Override
    public void purgeStoredConfiguration()
    {
        if (configurationFile != null)
        {
            configurationFile.delete();
            configurationFile = null;
        }
        if (store != null)
            for (String name : store.getPropertyNames())
                store.removeProperty(name);
    }

    /**
     * Goes over all system properties and outputs their names and values for
     * debug purposes. The method has no effect if the logger is at a log level
     * other than DEBUG or TRACE (FINE or FINEST).
     * * Changed that system properties are printed in INFO level and this way
     *   they are included in the beginning of every users log file.
     */
    private void debugPrintSystemProperties()
    {
        if (!logger.isInfoEnabled())
            return;

        logger.info(ConfigUtils.getSystemPropertiesDebugString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logConfigurationProperties(String excludePattern)
    {
        if (!logger.isInfoEnabled())
            return;

        Pattern exclusion = null;
        if (!StringUtils.isNullOrEmpty(excludePattern))
        {
            exclusion = Pattern.compile(
                excludePattern, Pattern.CASE_INSENSITIVE);
        }

        for (String p : getAllPropertyNames())
        {
            Object v = getProperty(p);

            // Not sure if this can happen, but just in case...
            if (v == null)
                continue;

            if (exclusion != null && exclusion.matcher(p).find())
            {
                v = "**********";
            }

            logger.info(p + "=" + v);
        }
    }

    /**
     * The method scans the contents of the SYS_PROPS_FILE_NAME_PROPERTY where
     * it expects to find a comma separated list of names of files that should
     * be loaded as system properties. The method then parses these files and
     * loads their contents as system properties. All such files have to be in
     * a location that's in the classpath.
     */
    private void preloadSystemPropertyFiles()
    {
        String propertyFilesListStr
            = System.getProperty( SYS_PROPS_FILE_NAME_PROPERTY );

        if(propertyFilesListStr == null || propertyFilesListStr.trim().length() == 0)
            return;

        StringTokenizer tokenizer
            = new StringTokenizer(propertyFilesListStr, ";,", false);

        while( tokenizer.hasMoreTokens())
        {
            String fileName = tokenizer.nextToken();
            try
            {
                fileName = fileName.trim();

                Properties fileProps = new Properties();

                InputStream stream = null;
                try
                {
                    stream = ClassLoader.getSystemResourceAsStream(fileName);
                    fileProps.load(stream);
                }
                finally
                {
                    if (stream != null)
                    {
                        stream.close();
                    }
                }

                // now set all of this file's properties as system properties
                for (Map.Entry<Object, Object> entry : fileProps.entrySet())
                    System.setProperty((String) entry.getKey(), (String) entry
                        .getValue());
            }
            catch (Exception ex)
            {
                //this is an insignificant method that should never affect
                //the rest of the application so we'll afford ourselves to
                //kind of silence all possible exceptions (which would most
                //often be IOExceptions). We will however log them in case
                //anyone would be interested.
                logger.error("Failed to load property file: " + fileName, ex);
            }
        }
    }

    /**
     * Specifies the configuration store that this instance of the configuration
     * service implementation must use.
     *
     * @param clazz the {@link ConfigurationStore} that this configuration
     * service instance instance has to use.
     *
     * @throws IOException if loading properties from the specified store fails.
     */
    private void setConfigurationStore(
            Class<? extends ConfigurationStore> clazz)
        throws IOException
    {
        String extension = null;

        if (PropertyConfigurationStore.class.isAssignableFrom(clazz))
            extension = "properties";
        else if (XMLConfigurationStore.class.isAssignableFrom(clazz))
            extension = "xml";

        this.configurationFile
                = (extension == null)
                    ? null
                    : getConfigurationFile(extension, true);

        if (!clazz.isInstance(this.store))
        {
            Throwable exception = null;

            try
            {
                this.store = clazz.newInstance();
            }
            catch (IllegalAccessException | InstantiationException e)
            {
                exception = e;
            }
            if (exception != null)
                throw new RuntimeException(exception);
        }
    }

    /**
     * Loads the default property maps from the Jitsi installation directory
     * then overrides them with the default override values.
     */
    private void loadDefaultProperties()
    {
        loadDefaultProperties(DEFAULT_PROPS_FILE_NAME);
        loadDefaultProperties(DEFAULT_OVERRIDES_PROPS_FILE_NAME);
    }

    /**
     * Tests whether the application has been launched using Java WebStart
     */
    private boolean isLaunchedByWebStart()
    {
        boolean hasJNLP;
        try
        {
            Class.forName("javax.jnlp.ServiceManager");
            hasJNLP = true;
        }
        catch (ClassNotFoundException ex)
        {
            hasJNLP = false;
        }
        String jwsVersion = System.getProperty("javawebstart.version");
        if(jwsVersion != null && jwsVersion.length() > 0)
        {
            hasJNLP = true;
        }
        return hasJNLP;
    }

    /**
     * Loads the specified default properties maps from the Jitsi installation
     * directory. Typically this file is to be called for the default properties
     * and the admin overrides.
     *
     * @param  fileName the name of the file we need to load.
     */
    private void loadDefaultProperties(String fileName)
    {
        try
        {
            Properties fileProps = new Properties();

            InputStream fileStream;
            if(OSUtils.IS_ANDROID)
            {
                fileStream
                        = getClass().getClassLoader()
                                .getResourceAsStream(fileName);
            }
            else if(isLaunchedByWebStart())
            {
                logger.info("WebStart classloader");
                fileStream
                        = Thread.currentThread().getContextClassLoader()
                                .getResourceAsStream(fileName);
            }
            else
            {
                logger.info("Normal classloader");
                fileStream = ClassLoader.getSystemResourceAsStream(fileName);
            }

            if(fileStream == null)
            {
                logger.info("failed to find " + fileName + " with class "
                    + "loader, will continue without it.");
                return;
            }

            fileProps.load(fileStream);
            fileStream.close();

            // now get those properties and place them into the mutable and
            // immutable properties maps.
            for (Map.Entry<Object, Object> entry : fileProps.entrySet())
            {
                String name  = (String) entry.getKey();
                String value = (String) entry.getValue();

                if (   name == null
                    || value == null
                    || name.trim().length() == 0)
                {
                    continue;
                }

                if (name.startsWith("*"))
                {
                    name = name.substring(1);

                    if(name.trim().length() == 0)
                    {
                        continue;
                    }

                    //it seems that we have a valid default immutable property
                    immutableDefaultProperties.put(name, value);

                    //in case this is an override, make sure we remove previous
                    //definitions of this property
                    defaultProperties.remove(name);
                }
                else
                {
                    //this property is a regular, mutable default property.
                    defaultProperties.put(name, value);

                    //in case this is an override, make sure we remove previous
                    //definitions of this property
                    immutableDefaultProperties.remove(name);
                }
            }
        }
        catch (Exception ex)
        {
            //we can function without defaults so we are just logging those.
            logger.info("No defaults property file loaded: " + fileName
                + ". Not a problem.");

            if(logger.isDebugEnabled())
                logger.debug("load exception", ex);
        }
    }
}
