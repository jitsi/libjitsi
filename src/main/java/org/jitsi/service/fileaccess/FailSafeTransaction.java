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
package org.jitsi.service.fileaccess;

import java.io.*;

/**
 * A failsafe transaction interface. By failsafe we mean here that the file
 * concerned always stays in a coherent state.
 *
 * @author Benoit Pradelle
 */
public interface FailSafeTransaction {
    /**
     * Ensure that the file accessed is in a coherent state. This function is
     * useful to do a failsafe read without starting a transaction.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the file restoration
     */
    public void restoreFile()
        throws IllegalStateException, IOException;

    /**
     * Begins a new transaction. If a transaction is already active, commits the
     * changes and begin a new transaction.
     * A transaction can be closed by a commit or rollback operation.
     * When the transaction begins, the file is restored to a coherent state if
     * needed.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the transaction
     * creation
     */
    public void beginTransaction()
        throws IllegalStateException, IOException;

    /**
     * Closes the transaction and commit the changes. Everything written in the
     * file during the transaction is saved.
     *
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the operation
     */
    public void commit()
        throws IllegalStateException, IOException;

    /**
     * Closes the transation and cancel the changes. Everything written in the
     * file during the transaction is NOT saved.
     * @throws IllegalStateException if the file doesn't exists anymore
     * @throws IOException if an IOException occurs during the operation
     */
    public void rollback()
        throws IllegalStateException, IOException;
}
