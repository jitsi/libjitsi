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
package org.jitsi.impl.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import javax.xml.parsers.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.xml.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.w3c.dom.*;

/**
 * Tests ConfiguratioService persistency, which means that it makes the
 * ConfigurationService store and load its properties from a file and checks
 * whether everything is going well.
 *
 * @author Emil Ivov
 */
public class TestConfigurationServicePersistency
{
    //property1 values
    private static final String property1 = "p1";

    private static final String property1Value = "p1.value";

    private static final String property1Value2 = "p1.value.2";

    private static final String property1Path = "parent.";

    //property2 values
    private static final String systemProperty = "SYSTEM_PROPERTY";

    private static final String systemPropertyValue = "I AM the SyS guy";

    private static final String systemPropertyValue2 = "sys guy's new face";

    private static final String systemPropertyPath = "parent.";

    //added_property values
    private static final String addedProperty = "ADDED_PROPERTY";

    private static final String addedPropertyValue = "added";

    private static final String addedPropertyValue2 = "and then re-aded";

    private static final String addedPropertyPath = "parent.";

    //INNER_PROPERTY values
    private static final String innerProperty = "INNER_PROPERTY";

    private static final String innerPropertyValue = "I am an insider";

    private static final String innerPropertyValue2 = "I am a modified inner";

    private static final String innerPropertyPath = "parent.innerprops.";

    /**
     * the contents of our properties file.
     */
    private static final String confFileContent =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<sip-communicator>\n" +
            "   <parent>" + "\n" +
            "      <" + property1 + " value=\"" + property1Value + "\"/>" + "\n"
            +
            "      <" + systemProperty
            + " value=\"" + systemPropertyValue + "\" system=\"true\"/>" + "\n"
            +
            "      <innerprops>" + "\n" +
            "          <" + innerProperty + " value=\"" + innerPropertyValue
            + "\"/>" + "\n" +
            "      </innerprops>" + "\n" +
            "   </parent>" + "\n" +
            "</sip-communicator>\n";

    /**
     * the contents of our second properties file that we use to test reload.
     */
    private static final String confFileContent2 =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<sip-communicator>\n" +
            "    <parent>" + "\n" +
            "       <" + property1 + " value=\"" + property1Value2 + "\"/>"
            + "\n" +
            "       <" + systemProperty
            + " value=\"" + systemPropertyValue2 + "\" system=\"true\"/>" + "\n"
            +
            "       <innerprops>" + "\n" +
            "           <" + innerProperty + " value=\"" + innerPropertyValue2
            + "\"/>" + "\n" +
            "       </innerprops>" + "\n" +
            "    </parent>" + "\n" +
            "</sip-communicator>\n";

    /**
     * the configuration file itself (created and deleted for every test)
     */
    private File confFile = null;

    /**
     * The ConfigurationService that we will be testing.
     */
    private ConfigurationService configurationService = null;

    @TempDir
    public Path testFolder;

    /**
     * Generic JUnit setUp method.
     *
     * @throws Exception if anything goes wrong.
     */
    @BeforeEach
    public void setUp() throws Exception
    {
        LibJitsi.start();
        configurationService = LibJitsi.getConfigurationService();
        confFile = testFolder.resolve("configfile").toFile();
        System.setProperty(
            ConfigurationService.PNAME_CONFIGURATION_FILE_NAME,
            confFile.getAbsolutePath());
        configurationService.purgeStoredConfiguration();

        try (FileOutputStream out = new FileOutputStream(confFile))
        {
            out.write(confFileContent.getBytes(StandardCharsets.UTF_8));
        }

        configurationService.reloadConfiguration();
    }

    /**
     * Generic JUnit tearDown method.
     */
    @AfterEach
    public void tearDown()
    {
        configurationService.purgeStoredConfiguration();

        //reset the fileNameProperty
        System.clearProperty(
            ConfigurationService.PNAME_CONFIGURATION_FILE_NAME);
        LibJitsi.stop();
    }

    /**
     * Tests whether the load method has properly loaded our conf file during
     * setup.
     */
    @Test
    public void testLoadConfiguration()
    {
        Object returnedValueObj =
            configurationService.getProperty(property1Path
                + property1);
        assertNotNull(
            returnedValueObj, "configuration not properly loaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");
        String returnedValue = returnedValueObj.toString();

        assertEquals(
            property1Value, returnedValue, "configuration not properly loaded");

        returnedValueObj =
            configurationService.getProperty(systemPropertyPath
                + systemProperty);
        assertNotNull(
            returnedValueObj, "configuration not properly loaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");

        //check whether this property was resolved in System.properties
        returnedValue = System.getProperty(systemPropertyPath + systemProperty);
        assertNotNull(returnedValue, "A system property was not resolved");
        assertEquals(
            systemPropertyValue, returnedValue,
            "A system property was not resolved");

        returnedValue = returnedValueObj.toString();
        assertEquals(
            systemPropertyValue, returnedValue,
            "configuration not properly loaded");

        //check whether inner properties are properly loaded
        returnedValueObj =
            configurationService.getProperty(innerPropertyPath
                + innerProperty);
        assertNotNull(
            returnedValueObj, "configuration not properly loaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");
        returnedValue = returnedValueObj.toString();
        assertEquals(
            innerPropertyValue, returnedValue,
            "configuration not properly loaded");
    }

    /**
     * Tests whether a configuration is properly reloaded (i.e. values of the
     * properties are updated to match those present in the file, and any
     * additional properties added since the previous load are clreared).
     *
     * @throws Exception if an error occurs during testing.
     */
    @Test
    public void testReLoadConfiguration() throws Exception
    {
        //set a new property so that we could see whether its deleted after
        //the file contents is reloaded.
        configurationService.setProperty(addedPropertyPath + addedProperty,
            addedPropertyValue);

        //write the new file
        try (FileOutputStream out = new FileOutputStream(confFile))
        {
            out.write(confFileContent2.getBytes());
        }

        configurationService.reloadConfiguration();

        //check whether normal properties are properly reloaded
        Object returnedValueObj =
            configurationService.getProperty(property1Path
                + property1);
        assertNotNull(
            returnedValueObj, "configuration not properly loaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");
        String returnedValue = returnedValueObj.toString();

        assertEquals(
            property1Value2, returnedValue,
            "configuration not properly reloaded");

        //check whether systemproperties are properly reresolved
        returnedValueObj =
            configurationService.getProperty(systemPropertyPath
                + systemProperty);
        assertNotNull(
            returnedValueObj, "configuration not properly reloaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");
        returnedValue = returnedValueObj.toString();
        assertEquals(
            systemPropertyValue2, returnedValue,
            "configuration not properly reloaded");

        //make sure that the property was re-resolved in System.properties
        returnedValue = System.getProperty(systemPropertyPath + systemProperty);
        assertNotNull(returnedValue, "A system property was not resolved");
        assertEquals(
            systemPropertyValue2, returnedValue,
            "A system property was not resolved");

        //verify that the inner property is also reloaded
        returnedValueObj =
            configurationService.getProperty(innerPropertyPath
                + innerProperty);
        assertNotNull(
            returnedValueObj, "configuration not properly reloaded");
        assertTrue(
            returnedValueObj instanceof String, "returned prop is not a String");
        returnedValue = returnedValueObj.toString();
        assertEquals(
            innerPropertyValue2, returnedValue,
            "configuration not properly reloaded");

        //make sure the property we added in the beginning is not there anymore.
        returnedValueObj =
            configurationService.getProperty(addedPropertyPath
                + addedProperty);
        assertNull(
            returnedValueObj, "reload didn't remove newly added properties");
    }

    /**
     * Test whether a configuration is properly stored in the configuration
     * file.
     *
     * @throws Exception if an error occurs during testing.
     */
    @Test
    public void testStoreConfiguration() throws Exception
    {
        //add a new property that will have to be added to the xml conf file.
        configurationService.setProperty(addedPropertyPath + addedProperty,
            addedPropertyValue2);

        //then give new values to existing properties
        configurationService.setProperty(property1Path + property1,
            property1Value2);
        configurationService.setProperty(systemPropertyPath + systemProperty,
            systemPropertyValue2);
        configurationService.setProperty(innerPropertyPath + innerProperty,
            innerPropertyValue2);

        configurationService.storeConfiguration();

        //reload the conf
        configurationService.reloadConfiguration();

        //Now reload the file and make sure it containts the updated values.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(confFile);

        Node root = document.getFirstChild();

        Element parent = XMLUtils.findChild((Element) root, "parent");

        Node property1Node = XMLUtils.findChild(parent, property1);

        Node systemPropertyNode =
            XMLUtils.findChild(parent, systemProperty);

        Node addedPropertyNode =
            XMLUtils.findChild(parent, addedProperty);

        Element innerpropNode =
            XMLUtils.findChild(parent, "innerprops");

        Node innerPropertyNode =
            XMLUtils.findChild(innerpropNode, innerProperty);

        String xmlProp1Value = XMLUtils.getAttribute(property1Node, "value");
        String xmlProp2Value =
            XMLUtils.getAttribute(systemPropertyNode, "value");
        String xmlAddedPropertyValue =
            XMLUtils.getAttribute(addedPropertyNode, "value");
        String xmlInnerPropertyValue =
            XMLUtils.getAttribute(innerPropertyNode, "value");

        assertEquals(
            property1Value2, xmlProp1Value, "property1 was incorrectly stored");
        assertEquals(
            systemPropertyValue2, xmlProp2Value,
            "System property was incorrectly stored");
        assertEquals(
            addedPropertyValue2, xmlAddedPropertyValue,
            "The added property was incorrectly stored");
        assertEquals(
            innerPropertyValue2, xmlInnerPropertyValue,
            "The inner property was incorrectly stored");
    }
}
