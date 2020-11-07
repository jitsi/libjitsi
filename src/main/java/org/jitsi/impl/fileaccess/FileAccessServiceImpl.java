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
package org.jitsi.impl.fileaccess;

import java.io.*;
import java.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.*;
import org.jitsi.utils.logging.*;

/**
 * Default FileAccessService implementation.
 *
 * @author Alexander Pelov
 * @author Lyubomir Marinov
 */
public class FileAccessServiceImpl implements FileAccessService
{
    /**
     * The <tt>Logger</tt> used by the <tt>FileAccessServiceImpl</tt> class and
     * its instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(FileAccessServiceImpl.class);

    /**
     * The file prefix for all temp files.
     */
    public static final String TEMP_FILE_PREFIX = "SIPCOMM";

    /**
     * The file suffix for all temp files.
     */
    public static final String TEMP_FILE_SUFFIX = "TEMP";

    private String profileDirLocation;
    private String cacheDirLocation;
    private String logDirLocation;
    private String scHomeDirName;

    /**
     * The indicator which determines whether {@link #initialize()} has been
     * invoked on this instance. Introduced to delay the initialization of the
     * state of this instance until it is actually necessary.
     */
    private boolean initialized = false;

    public FileAccessServiceImpl()
    {
    }

    /**
     * This method returns a created temporary file. After you close this file
     * it is not guaranteed that you will be able to open it again nor that it
     * will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException
     *             If the file cannot be created
     */
    @Override
    public File getTemporaryFile()
        throws IOException
    {
        File retVal = null;

        try
        {
            logger.logEntry();

            retVal = TempFileManager.createTempFile(TEMP_FILE_PREFIX,
                    TEMP_FILE_SUFFIX);
        }
        finally
        {
            logger.logExit();
        }

        return retVal;
    }

    /**
     * Returns the temporary directory.
     *
     * @return the created temporary directory
     * @throws IOException if the temporary directory cannot not be created
     */
    @Override
    public File getTemporaryDirectory() throws IOException
    {
        File file = getTemporaryFile();

        if (!file.delete())
        {
            throw new IOException("Could not create temporary directory, "
                    + "because: could not delete temporary file.");
        }
        if (!file.mkdirs())
        {
            throw new IOException("Could not create temporary directory");
        }

        return file;
    }

    /**
     * Please use {@link #getPrivatePersistentFile(String, FileCategory)}.
     */
    @Deprecated
    @Override
    public File getPrivatePersistentFile(String fileName)
        throws Exception
    {
        return this.getPrivatePersistentFile(fileName, FileCategory.PROFILE);
    }

    /**
     * This method returns a file specific to the current user. It may not
     * exist, but it is guaranteed that you will have the sufficient rights to
     * create it.
     *
     * This file should not be considered secure because the implementor may
     * return a file accessible to everyone. Generally it will reside in current
     * user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName
     *            The name of the private file you wish to access
     * @param category
     *            The classification of the file.
     * @return The file
     * @throws Exception if we faile to create the file.
     */
    @Override
    public File getPrivatePersistentFile(String fileName, FileCategory category)
        throws Exception
    {
        logger.logEntry();

        File file = null;
        try
        {
            file = accessibleFile(getFullPath(category), fileName);
            if (file == null)
            {
                throw new SecurityException("Insufficient rights to access "
                    + "this file in current user's home directory: "
                    + new File(getFullPath(category), fileName).getPath());
            }
        }
        finally
        {
            logger.logExit();
        }

        return file;
    }

    /**
     * Please use {@link #getPrivatePersistentDirectory(String, FileCategory)}
     */
    @Deprecated
    @Override
    public File getPrivatePersistentDirectory(String dirName)
        throws Exception
    {
        return getPrivatePersistentDirectory(dirName, FileCategory.PROFILE);
    }

    /**
     * This method creates a directory specific to the current user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accessible to everyone. Generally it will reside
     * in current user's homedir, but it may as well reside in a shared
     * directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName
     *            The name of the private directory you wish to access.
     * @param category
     *            The classification of the directory.
     * @return The created directory.
     * @throws Exception
     *             Thrown if there is no suitable location for the persistent
     *             directory.
     */
    @Override
    public File getPrivatePersistentDirectory(String dirName,
        FileCategory category) throws Exception
    {
        File dir = new File(getFullPath(category), dirName);
        if (dir.exists())
        {
            if (!dir.isDirectory())
            {
                throw new RuntimeException("Could not create directory "
                        + "because: A file exists with this name:"
                        + dir.getAbsolutePath());
            }
        }
        else
        {
            if (!dir.mkdirs())
            {
                throw new IOException("Could not create directory");
            }
        }

        return dir;
    }

    /**
     * Returns the full path corresponding to a file located in the
     * sip-communicator config home and carrying the specified name.
     * @param category The classification of the file or directory.
     * @return the config home location of a a file with the specified name.
     */
    private File getFullPath(FileCategory category)
    {
        initialize();

        // bypass the configurationService here to remove the dependency
        String directory;

        switch (category)
        {
            case CACHE:
                directory = this.cacheDirLocation;
                break;
            case LOG:
                directory = this.logDirLocation;
                break;
            default:
                directory = this.profileDirLocation;
                break;
        }

        return new File(directory, this.scHomeDirName);
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
        if (retval == null){
            return retval;
        }

        if (retval.trim().length() == 0){
            return null;
        }
        return retval;
    }

    /**
     * Checks if a file exists and if it is writable or readable. If not -
     * checks if the user has a write privileges to the containing directory.
     *
     * If those conditions are met it returns a File in the directory with a
     * fileName. If not - returns null.
     *
     * @param homedir the location of the sip-communicator home directory.
     * @param fileName the name of the file to create.
     * @return Returns null if the file does not exist and cannot be created.
     *         Otherwise - an object to this file
     * @throws IOException
     *             Thrown if the home directory cannot be created
     */
    private static File accessibleFile(File homedir, String fileName)
            throws IOException
    {
        File file = null;

        try
        {
            logger.logEntry();

            file = new File(homedir, fileName);
            if (file.canRead() || file.canWrite())
            {
                return file;
            }

            if (!homedir.exists())
            {
                if (logger.isDebugEnabled())
                    logger.debug("Creating home directory : "
                        + homedir.getAbsolutePath());
                if (!homedir.mkdirs())
                {
                    String message = "Could not create the home directory : "
                            + homedir.getAbsolutePath();

                    if (logger.isDebugEnabled())
                        logger.debug(message);
                    throw new IOException(message);
                }
                if (logger.isDebugEnabled())
                    logger.debug("Home directory created : "
                        + homedir.getAbsolutePath());
            }
            else if (!homedir.canWrite())
            {
                file = null;
            }

            if(file != null && !file.getParentFile().exists())
            {
                if (!file.getParentFile().mkdirs())
                {
                    String message = "Could not create the parent directory : "
                        + homedir.getAbsolutePath();

                    logger.debug(message);
                    throw new IOException(message);
                }
            }

        }
        finally
        {
            logger.logExit();
        }

        return file;
    }

    /**
     * Returns the default download directory.
     *
     * @return the default download directory
     * @throws IOException if it I/O error occurred
     */
    @Override
    public File getDefaultDownloadDirectory()
        throws IOException
    {
        // For Windows use the intended API to get the correct location.
        // Everything else is prone to failure due to folder redirection and
        // roaming profiles.
        if (OSUtils.IS_WINDOWS)
        {
            if (getMajorOSVersion() < 6)
            {
                char[] pszPath = new char[Shell32.MAX_PATH];
                int hResult = Shell32.INSTANCE.SHGetFolderPath(
                        null,
                        Shell32.CSIDL_MYDOCUMENTS,
                        null,
                        Shell32.SHGFP_TYPE_CURRENT, pszPath);

                if (hResult == Shell32.S_OK)
                {
                    String path = new String(pszPath);
                    return new File(path.substring(0, path.indexOf('\0')));
                }
            }
            else
            {
                // FOLDERID_Downloads
                GUID g = new GUID();
                g.data1 = 0x374DE290;
                g.data2 = 0x123F;
                g.data3 = 0x4565;
                g.data4 = new byte[] { (byte) 0x91, 0x64,
                        0x39, (byte) 0xC4, (byte) 0x92, 0x5E, 0x46, 0x7B };

                PointerByReference pszPath = new PointerByReference();
                int hResult = Shell32.INSTANCE.SHGetKnownFolderPath(
                        g,
                        Shell32.KF_FLAG_INIT | Shell32.KF_FLAG_CREATE,
                        null,
                        pszPath);

                if (hResult == Shell32.S_OK)
                {
                    File f = new File(pszPath.getValue().getWideString(0));
                    Ole32.INSTANCE.CoTaskMemFree(pszPath.getValue());
                    return f;
                }
            }
        }

        // For all other operating systems we return the Downloads folder.
        return new File(getSystemProperty("user.home"), "Downloads");
    }

    private static class HANDLE extends PointerType implements NativeMapped
    {}

    private static class HWND extends HANDLE
    {}

    public static class GUID extends Structure
    {
        //public static class ByValue extends GUID implements Structure.ByValue {}
        public int data1;
        public short data2;
        public short data3;
        public byte[] data4;

        @Override
        protected List<String> getFieldOrder()
        {
            return
                Arrays.asList(
                        new String[] { "data1", "data2", "data3", "data4" });
        }
    }

    private static Map<String, Object> OPT;

    static
    {
        if(OSUtils.IS_WINDOWS)
        {
            OPT = new HashMap<>();
            OPT.put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
            OPT.put(
                    Library.OPTION_FUNCTION_MAPPER,
                    W32APIFunctionMapper.UNICODE);
        }
    }

    private static interface Shell32 extends Library
    {
        public static final int MAX_PATH = 260;
        public static final int CSIDL_MYDOCUMENTS = 5;
        public static final int SHGFP_TYPE_CURRENT = 0;
        public static final int S_OK = 0;
        public static final int KF_FLAG_INIT = 0x00000800;
        public static final int KF_FLAG_CREATE = 0x00008000;

        static Shell32 INSTANCE = (Shell32)Native.loadLibrary("shell32",
                Shell32.class, OPT);

        /**
         * http://msdn.microsoft.com/en-us/library/bb762181(VS.85).aspx
         */
        public int SHGetFolderPath(HWND hwndOwner, int nFolder, HANDLE hToken,
                int dwFlags, char[] pszPath);

        /**
         * http://msdn.microsoft.com/en-us/library/bb762188(v=vs.85).aspx 
         */
        public int SHGetKnownFolderPath(GUID rfid, int dwFlags, HANDLE hToken,
                PointerByReference pszPath);
    }

    private interface Ole32 extends Library
    {
        static Ole32 INSTANCE = (Ole32)Native.loadLibrary("Ole32",
                Ole32.class, OPT);

        public void CoTaskMemFree(Pointer p);
    }

    /**
     * Gets the major version of the executing operating system as defined by
     * the <tt>os.version</tt> system property.
     *
     * @return the major version of the executing operating system as defined by
     * the <tt>os.version</tt> system property
     */
    private static int getMajorOSVersion()
    {
        String osVersion = System.getProperty("os.version");
        int majorOSVersion;

        if ((osVersion != null) && (osVersion.length() > 0))
        {
            int majorOSVersionEnd = osVersion.indexOf('.');
            String majorOSVersionString
                = (majorOSVersionEnd > -1)
                    ? osVersion.substring(0, majorOSVersionEnd)
                    : osVersion;

            majorOSVersion = Integer.parseInt(majorOSVersionString);
        }
        else
            majorOSVersion = 0;
        return majorOSVersion;
    }

    /**
     * Creates a failsafe transaction which can be used to safely store
     * informations into a file.
     *
     * @param file The file concerned by the transaction, null if file is null.
     *
     * @return A new failsafe transaction related to the given file.
     */
    @Override
    public FailSafeTransaction createFailSafeTransaction(File file)
    {
        return (file == null) ? null : new FailSafeTransactionImpl(file);
    }

    /**
     * Initializes this instance if it has not been initialized yet i.e. acts as
     * a delayed constructor of this instance. Introduced because this
     * <tt>FileAccessServiceImpl</tt> queries <tt>System</tt> properties that
     * may not be set yet at construction time and, consequently, throws an
     * <tt>IllegalStateException</tt> which could be avoided.
     */
    private synchronized void initialize()
    {
        if (initialized)
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();

        profileDirLocation
            = cfg != null
                ? cfg.getScHomeDirLocation()
                : getSystemProperty(
                        ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);
        if (profileDirLocation == null)
        {
            throw new IllegalStateException(
                    ConfigurationService.PNAME_SC_HOME_DIR_LOCATION);
        }

        scHomeDirName
            = cfg != null
                ? cfg.getScHomeDirName()
                : getSystemProperty(
                        ConfigurationService.PNAME_SC_HOME_DIR_NAME);
        if (scHomeDirName == null)
        {
            throw new IllegalStateException(
                    ConfigurationService.PNAME_SC_HOME_DIR_NAME);
        }

        String cacheDir
            = getSystemProperty(
                    ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION);
        cacheDirLocation = (cacheDir == null) ? profileDirLocation : cacheDir;

        String logDir
            = getSystemProperty(
                    ConfigurationService.PNAME_SC_LOG_DIR_LOCATION);
        logDirLocation = (logDir == null) ? profileDirLocation : logDir;

        initialized = true;
    }
}
