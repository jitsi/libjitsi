/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

public class TestFileAccessService
{
    /**
     * The ConfigurationService that we will be testing.
     */
    private FileAccessService fileAccessService = null;

    /**
     * Some sample data to be writen.
     */
    private static final byte[] testData =
        "The quick brown fox jumped over the lazy dog"
            .getBytes();

    /**
     * Random data to be added to the sample data.
     */
    private static final Random randomData = new Random();

    /**
     * The persistent directory's name.
     */
    private static final String dirName = "fileaccessservice.dir.tst";

    /**
     * The persistent file's name.
     */
    private static final String fileName = "fileaccessservice.tst";

    @BeforeEach
    public void beforeEach()
    {
        LibJitsi.start();
        fileAccessService = LibJitsi.getFileAccessService();
    }

    @AfterEach
    public void afterEach()
    {
        LibJitsi.stop();
    }

    /*
     * Test method for
     * 'net.java.sip.communicator.service.fileaccess.FileAccessServiceImpl.getTemporaryFile()'
     */
    @Test
    public void testCreateReadWriteTemporaryFile()
    {
        try
        {
            File tempFile = this.fileAccessService.getTemporaryFile();

            // The file should be new!
            assertEquals(tempFile.length(), 0);

            writeReadFile(tempFile);
        }
        catch (IOException e)
        {
            fail("Error while opening the temp file: " + e.getMessage());
        }
    }

    /*
     * Test method for
     * 'net.java.sip.communicator.service.fileaccess.FileAccessServiceImpl.getTemporaryFile()'
     */
    @Test
    public void testCreateTemporaryDirectory()
    {
        try
        {
            this.fileAccessService.getTemporaryDirectory();
        }
        catch (IOException e)
        {
            fail("Error creating the temp directory: " + e.getMessage());
        }
    }

    /*
     * Test method for
     * 'net.java.sip.communicator.service.fileaccess.FileAccessServiceImpl.getTemporaryFile()'
     */
    @Test
    public void testCreateReadWriteFileInTemporaryDirectory()
        throws Exception
    {
        int testFiles = 10;
        File[] files = new File[testFiles];
        byte[][] randomData = new byte[testFiles][];

        for (int i = 0; i < testFiles; i++)
        {
            File tempDir = null;
            try
            {
                tempDir = this.fileAccessService.getTemporaryDirectory();
            }
            catch (IOException e)
            {
                fail(
                    "Error creating the temp directory: " + e.getMessage());
            }

            files[i] = new File(tempDir, fileName);
            assertTrue(files[i]
                .createNewFile(), "Error creating file in temp dir");

            randomData[i] = generateRandomData();
            this.writeFile(files[i], randomData[i]);
        }

        // Read all files afterwards to ensure that temp directories
        // are different
        for (int i = 0; i < testFiles; i++)
        {
            this.readFile(files[i], randomData[i]);
        }
    }

    /*
     * Tests if it is possible to create a persistent directory.
     */
    @Test
    public void testCreatePersistentDirectory()
        throws Exception
    {
        try
        {
            this.fileAccessService.getPrivatePersistentDirectory(dirName,
                FileCategory.PROFILE);
        }
        catch (IOException e)
        {
            fail("Error creating the temp directory: " + e.getMessage());
        }
    }

    /*
     * Tests if it is possible to create a persistent directory and a create
     * file and write and read data to this file.
     */
    @Test
    public void testCreateReadWriteFileInPersistentDirectory()
        throws Exception
    {
        File privateDir = null;
        try
        {
            privateDir = this.fileAccessService
                .getPrivatePersistentDirectory(dirName,
                    FileCategory.PROFILE);
        }
        catch (IOException e)
        {
            fail(
                "Error creating the private directory: " + e.getMessage());
        }

        File file = new File(privateDir, fileName);
        if (file.exists())
        {
            assertTrue(file
                .delete(), "Persistent file exists. Delete attempt failed. "
                    + "Have you ran the tests with other user? "
                    + "Is the file locked?" + file.getAbsolutePath());
        }
        assertTrue(
            file
                .createNewFile(),
            "Error creating file in dir" + file.getAbsolutePath());
        this.writeReadFile(file);

        assertTrue(file.delete());
        assertFalse(
            file.exists(),
            "Could not clean up created file " + file.getAbsolutePath());
    }

    /*
     * Tests if it is possible for a file to be created if it does not exist
     */
    @Test
    public void testCreatePersistentFile()
    {
        try
        {
            File file = this.fileAccessService
                .getPrivatePersistentFile(fileName, FileCategory.PROFILE);

            if (!file.exists())
            {
                // Assert that we CAN create the file if it does not exist
                assertTrue(file.createNewFile());
            }

        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    /*
     * This test will always pass, because it is not guaranteed that it is
     * possible for the supplied file to be deleted. It is used in conjunction
     * with the other tests
     */
    @Test
    public void testDeletePersistentFile()
    {
        try
        {
            File file = this.fileAccessService
                .getPrivatePersistentFile(fileName, FileCategory.PROFILE);

            if (file.exists())
            {
                file.delete();
            }

        }
        catch (Exception e)
        {
        }
    }

    /*
     * Tests if it is possible for a file to be created if it does not exist
     */
    @Test
    public void testCreateReadWritePersistentFile()
    {
        try
        {
            File file = this.fileAccessService
                .getPrivatePersistentFile(fileName, FileCategory.PROFILE);

            if (!file.exists())
            {
                assertTrue(file.createNewFile());
            }

            writeReadFile(file);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    /*
     * Tests if it data actually persists between calls
     */
    @Test
    public void testPersistentFilePersistency()
    {

        try
        {
            File file = this.fileAccessService
                .getPrivatePersistentFile(fileName, FileCategory.PROFILE);

            if (!file.exists())
            {
                assertTrue(file.createNewFile());
            }

            writeReadFile(file);

            File newFile = this.fileAccessService
                .getPrivatePersistentFile(fileName, FileCategory.PROFILE);

            // Assert that those files are in fact the same
            assertEquals(file, newFile);

            // and with the same size
            assertEquals(file.length(), newFile.length());
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    private void writeReadFile(File file)
    {
        byte[] randomData = generateRandomData();
        writeFile(file, randomData);
        readFile(file, randomData);
    }

    private byte[] generateRandomData()
    {
        int rndInt = TestFileAccessService.randomData
            .nextInt(Integer.MAX_VALUE);
        return Integer.toHexString(rndInt).getBytes();
    }

    private void writeFile(File file, byte[] randomData)
    {
        assertTrue(file.canWrite());
        try (FileOutputStream output = new FileOutputStream(file))
        {
            output.write(testData);
            output.write(randomData);
            output.flush();
        }
        catch (Exception e)
        {
            fail("Could not write to file: " + e.getMessage());
        }
    }

    private void readFile(File file, byte[] randomData)
    {
        assertTrue(file.canRead());
        byte[] readBuff = new byte[testData.length + randomData.length];
        try (FileInputStream input = new FileInputStream(file))
        {
            input.read(readBuff);
        }
        catch (Exception e)
        {
            fail("Could not read from file: " + e.getMessage());
        }

        // Check if testData was correctly written
        for (int i = 0; i < testData.length; i++)
        {
            assertEquals(readBuff[i], testData[i]);
        }

        // Check if randomData was correctly written
        for (int i = 0; i < randomData.length; i++)
        {
            assertEquals(readBuff[testData.length + i], randomData[i]);
        }
    }
}
