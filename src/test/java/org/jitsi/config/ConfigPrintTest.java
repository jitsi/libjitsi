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
package org.jitsi.config;

import org.jitsi.impl.configuration.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

/**
 * Just a playground - do not integrate.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class ConfigPrintTest
{
    @Test
    public void printTest()
    {
        ConfigurationServiceImpl.PASSWORD_SYS_PROPS = "cpu";
        ConfigurationServiceImpl.PASSWORD_CMD_LINE_ARGS = "secret,password";

        System.setProperty("sun.java.command", "secret=1234 password=23432");

        LibJitsi.start();

        ConfigurationService config = LibJitsi.getConfigurationService();

        System.err.println("********* END OF SYS PROPS *************");

        config.setProperty(
            "test.org.jitsi.jicofo.FOCUS_USER_PASSWORD", "secret12345");
        config.setProperty(
            "test.org.jitsi.jicofo.COMPONENT_SECRET", "1234");
        config.setProperty(
            "test.org.jitsi.jicofo.BLABLA", "123.44.34.5");
        config.setProperty(
            "test.org.jitsi.jicofo.COMPONENT_DOMAIN", "example.com");
        config.setProperty(
            "com.sun.setting", "true");
        config.setProperty(
            "some.empty_pass", "");
        config.setProperty(
            "some.null", null);

        config.logConfigurationProperties("(pass(w)?(or)?d?)|(secret)");
    }
}
