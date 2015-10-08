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
package org.jitsi.util;

import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import static org.junit.Assert.*;

/**
 * Basic test for {@link PasswordUtil} class.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class PasswordUtilTest
{
    @Test
    public void testShadowPassword()
    {
        String cmdLine = "AppMain org.jitsi.videobridge.Main" +
            " --host=example.com --secret3=blablabla --port=5347" +
            " -secret=pass1 --subdomain=jvb3 --apis=rest,xmpp" +
            " secret2=23pass4234";

        cmdLine = PasswordUtil.replacePasswords(
            cmdLine,
            new String[]{"", "secret3", "secret", "secret2"});

        assertEquals("AppMain org.jitsi.videobridge.Main" +
                " --host=example.com --secret3=X --port=5347" +
                " -secret=X --subdomain=jvb3 --apis=rest,xmpp" +
                " secret2=X",
            cmdLine);
    }
}
