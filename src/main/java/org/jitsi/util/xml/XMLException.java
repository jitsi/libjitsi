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
package org.jitsi.util.xml;

/**
 * The class is used to mask any XML specific exceptions thrown during parsing
 * various descriptors.
 *
 * @author Emil Ivov
 * @version 1.0
 */
public class XMLException
    extends Exception
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Constructs a new XMLException with the specified detail message and cause.
     *
     * @param message a message specifying the reason that caused the
     * exception.
     *
     * @param cause the cause (which is saved
     * for later retrieval by the Throwable.getCause() method). (A null value is
     * permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public XMLException(String message, Throwable cause)
    {
        super (message, cause);
    }

    /**
       * Constructs a new XMLException with the specified detail message.
       *
       * @param message a message specifying the reason that caused the
       * exception.
       */
    public XMLException(String message)
    {
        super(message);
    }
}
