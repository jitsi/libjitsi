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

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import org.jitsi.service.fileaccess.*;
import org.jitsi.service.libjitsi.*;
import org.junit.*;
import org.junit.rules.*;

/**
 * Tests for the fail safe transactions
 *
 * @author Benoit Pradelle
 */
public class TestFailSafeTransaction
{
    /**
     * The Service that we will be testing.
     */
    private FileAccessService fileAccessService = null;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    /**
     * Test data to write in the original file
     */
    private static final String origData = "this is a test for the fail safe "
        + "transaction ability in SIP Communicator";

    /**
     * Test data to add to the file
     */
    private static final String addedData = " which is the greatest IM client "
        + "in the world !";

    /**
     * Test data to never write in the file
     */
    private static final String wrongData = "all the file is damaged now !";

    @Before
    public void beforeEach()
    {
        LibJitsi.start();
        fileAccessService = LibJitsi.getFileAccessService();
    }

    @After
    public void afterEach()
    {
        LibJitsi.stop();
    }

    /**
     * Tests the commit operation
     */
    @Test
    public void testCommit() throws IOException
    {
        // setup a temp file
        File temp = testFolder.newFile();
        try (FileOutputStream out = new FileOutputStream(temp))
        {
            out.write(origData.getBytes());

            // write a modification during a transaction
            FailSafeTransaction trans = this.fileAccessService
                .createFailSafeTransaction(temp);
            trans.beginTransaction();

            out.write(addedData.getBytes());

            trans.commit();
        }

        // file content
        assertEquals("the file content isn't correct",
            origData + addedData,
            getFileContent(temp));
    }

    /**
     * Tests the rollback operation
     */
    @Test
    public void testRollback() throws IOException
    {
        // setup a temp file
        File temp = testFolder.newFile();
        byte[] origDataBytes = origData.getBytes();
        try (FileOutputStream out = new FileOutputStream(temp))
        {
            out.write(origDataBytes);
        }

        // write a modification during a transaction
        FailSafeTransaction trans = this.fileAccessService
            .createFailSafeTransaction(temp);
        trans.beginTransaction();

        try (FileOutputStream out = new FileOutputStream(temp))
        {
            out.write(wrongData.getBytes());
        }

        trans.rollback();

        // file content
        assertEquals("the file content isn't correct",
            origData,
            getFileContent(temp));
    }

    /**
     * Tests if the file is commited when we start a new transaction
     */
    @Test
    public void testCommitOnReOpen() throws IOException
    {
        // setup a temp file
        File temp = testFolder.newFile();
        try (FileOutputStream out = new FileOutputStream(temp))
        {
            out.write(origData.getBytes());
        }

        // write a modification during a transaction
        FailSafeTransaction trans = this.fileAccessService
            .createFailSafeTransaction(temp);
        trans.beginTransaction();
        try (FileOutputStream out = new FileOutputStream(temp, true))
        {
            out.write(addedData.getBytes());
        }

        // this transaction isn't closed, it should commit the changes
        trans.beginTransaction();

        // just to be sure to clean everything
        // the rollback must rollback nothing
        trans.rollback();

        // file content
        assertEquals("the file content isn't correct",
            origData + addedData,
            getFileContent(temp));
    }

    /**
     * Tests if the file is rollback-ed if the transaction is never closed
     */
    @Test
    public void testRollbackOnFailure() throws IOException
    {
        // setup a temp file
        File temp = testFolder.newFile();
        byte[] origDataBytes = origData.getBytes();
        try (FileOutputStream out = new FileOutputStream(temp))
        {

            out.write(origDataBytes);
        }

        // write a modification during a transaction
        FailSafeTransaction trans = this.fileAccessService
            .createFailSafeTransaction(temp);
        FailSafeTransaction trans2 = this.fileAccessService
            .createFailSafeTransaction(temp);
        trans.beginTransaction();

        try (FileOutputStream out = new FileOutputStream(temp))
        {
            out.write(wrongData.getBytes());
        }

        // we suppose here that SC crashed without closing properly the
        // transaction. When it restarts, the modification must have been
        // rollback-ed
        trans2.restoreFile();

        // file content
        assertEquals("the file content isn't correct",
            origData,
            getFileContent(temp));
    }

    private String getFileContent(File temp) throws IOException
    {
        return new String(Files.readAllBytes(temp.toPath()));
    }
}
