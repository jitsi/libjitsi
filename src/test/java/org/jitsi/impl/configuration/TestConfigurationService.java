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

import java.beans.*;
import java.util.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

/**
 * Tests basic ConfiguratioService behaviour.
 *
 * @author Emil Ivov
 */
public class TestConfigurationService
{
    /**
     * The ConfigurationService that we will be testing.
     */
    private ConfigurationService configurationService = null;

    /**
     * The PropertyChangeEvent that our test listeners will capture for testing.
     * Make sure we null that upon tear down
     */
    private PropertyChangeEvent propertyChangeEvent = null;

    /**
     * The name of a property that we will be using for testing.
     */
    private static final String propertyName = "my.test.property";

    /**
     * The name of a property that we will be using for testing custom event
     * notification.
     */
    private static final String listenedPropertyName = "a.property.i.listen.to";

    /**
     * The value of the property with name propertyName.
     */
    private static final String propertyValue = "19200";

    /**
     * A new value for the property with name propertyName
     */
    private static final String propertyNewValue = "19201";

    /**
     * A PropertyChange listener impl that registers the last received event.
     */
    private PropertyChangeListener pListener =
        event -> propertyChangeEvent = event;

    /**
     * A straightforward Vetoable change listener that throws an exception
     * upon any change
     */
    ConfigVetoableChangeListener rudeVetoListener = event -> {
        throw new ConfigPropertyVetoException("Just for the fun of it", event);
    };

    /**
     * A straightforward implementation of a vetoable change listener that that
     * does not throw a veto exception and only stored the last received event.
     */
    ConfigVetoableChangeListener gentleVetoListener =
        event -> propertyChangeEvent = event;

    /**
     * Generic JUnit setUp method.
     */
    @BeforeEach
    public void setUp()
    {
        LibJitsi.start();
        configurationService = LibJitsi.getConfigurationService();
        configurationService.setProperty(propertyName, null);
        configurationService.setProperty(listenedPropertyName, null);
    }

    /**
     * Generic JUnit tearDown method.
     */
    @AfterEach
    public void tearDown()
    {
        //first remove any remaining listeners
        configurationService.removePropertyChangeListener(pListener);
        configurationService.removeVetoableChangeListener(rudeVetoListener);
        configurationService.removeVetoableChangeListener(gentleVetoListener);

        //clear used properties.
        configurationService.setProperty(propertyName, null);
        configurationService.setProperty(listenedPropertyName, null);
        propertyChangeEvent = null;
        LibJitsi.stop();
    }

    /**
     * Tests whether setting and getting properties works correctly.
     */
    @Test
    public void testSetGetProperty()
    {
        String propertyName = "my.test.property";
        Object property = "my.test.property's value";
        configurationService.setProperty(propertyName, property);

        Object actualReturn = configurationService.getProperty(propertyName);
        assertEquals(
            property, actualReturn, "a property was not properly stored");
    }

    /**
     * Tests whether removing and getting properties works correctly.
     */
    @Test
    public void testRemoveProperty()
    {
        String propertyName = "my.test.property.acc1234";
        Object property = "my.test.property's value";
        configurationService.setProperty(propertyName, property);

        Object actualReturn = configurationService.getProperty(propertyName);
        assertEquals(
            property, actualReturn, "a property was not properly stored");

        configurationService.removeProperty(propertyName);
        Object actualReturn2 = configurationService.getProperty(propertyName);

        assertNull(
            actualReturn2, "a property was not properly removed");
    }

    /**
     * Tests whether removing and getting properties works correctly.
     */
    @Test
    public void testRemovePrefixedProperty()
    {
        // this one is used in provisioning, if we have account info like:
        // net.java.sip.communicator.impl.protocol.sip.acc1sip1322067404000=acc1sip1322067404000
        // we need to remove it by provisioning this:
        // net.java.sip.communicator.impl.protocol.sip.acc1sip=${null}

        String propertyName = "my.test.property.acc1234";
        String propertyPrefixName = "my.test.property.acc";

        Object property = "my.test.property's value";
        configurationService.setProperty(propertyName, property);

        Object actualReturn = configurationService.getProperty(propertyName);
        assertEquals(
            property, actualReturn, "a property was not properly stored");

        configurationService.removeProperty(propertyPrefixName);
        Object actualReturn2 = configurationService.getProperty(propertyName);

        assertNull(
            actualReturn2, "a property was not properly removed by prefix");
    }

    /**
     * Tests whether setting and getting works alright for properties declared
     * as system and whether resolving and retrieving from the system property
     * set are done right.
     */
    @Test
    public void testSystemPoperties()
    {
        //first simply store and retrieve a system property
        String propertyName = "my.test.system.property";
        Object property = "sys.value.1";
        configurationService.setProperty(propertyName, property, true);

        Object actualReturn = configurationService.getProperty(propertyName);
        assertEquals(
            property, actualReturn, "a sys property was not properly stored");

        //now check whether you can also retrieve it from the sys property set.
        actualReturn = System.getProperty(propertyName);
        assertEquals(property,
            actualReturn, "a property was not properly stored");

        //verify that modifying it in the sys property set would affect the
        //value in the configuration service
        property = "second.sys.value";
        System.setProperty(propertyName, property.toString());
        actualReturn = configurationService.getProperty(propertyName);
        assertEquals(property,
            actualReturn, "a property was not properly stored");

        //now make sure  that modifying it in the configurationService would
        //result in the corresponding change in the system property set.
        property = "third.sys.value";
        configurationService.setProperty(propertyName, property.toString());
        actualReturn = System.getProperty(propertyName);
        assertEquals(property,
            actualReturn, "a property was not properly stored");
    }

    /**
     * Tests whether getString properly returns string values and that it
     * correctly handles corner cases.
     *
     */
    @Test
    public void testGetString()
    {
        //test a basic  scenario
        String propertyName = "my.test.property";
        Object property = "my.test.property's value";
        configurationService.setProperty(propertyName, property);

        String actualReturn = configurationService.getString(propertyName);
        assertEquals(
            property.toString(), actualReturn,
            "getString failed to retrieve a property");

        //verify that setting a non string object would not mess things up
        property = 7121979;
        configurationService.setProperty(propertyName, property);

        actualReturn = configurationService.getString(propertyName);
        assertEquals(
            property.toString(), actualReturn,
            "getString failed to retrieve a property");

        //verify that setting a whitespace only string would return null
        property = "\t\n ";
        configurationService.setProperty(propertyName, property);

        actualReturn = configurationService.getString(propertyName);
        assertNull(
            actualReturn, "getString did not trim a white space only string");
    }

    /**
     * Records a few properties with a similar prefix and verifies that they're
     * all returned by the getPropertyNamesByPrefix() method.
     *
     */
    @Test
    public void testGetPropertyNamesByPrefix()
    {
        String prefix = "this.is.a.prefix";
        String exactPrefixProp1Name = prefix + ".PROP1";
        String exactPrefixProp2Name = prefix + ".PROP3";
        String longerPrefixProp3Name = prefix + ".which.is.longer.PROP3";
        String completeMismatchProp4Name = "and.hereis.one.other.prefix.PROP4";

        configurationService.setProperty(exactPrefixProp1Name, new Object());
        configurationService.setProperty(exactPrefixProp2Name, new Object());
        configurationService.setProperty(longerPrefixProp3Name, new Object());
        configurationService.setProperty(completeMismatchProp4Name
                                         , new Object());

        //try an exact match first
        List<String> propertyNames
            = configurationService.getPropertyNamesByPrefix(prefix, true);

        assertTrue(
            propertyNames.contains(exactPrefixProp1Name),
            "Returned list did not contain all property names. "
                    + " MissingPropertyName: " + exactPrefixProp1Name);

        assertTrue(
            propertyNames.contains(exactPrefixProp2Name),
            "Returned list did not contain all property names. "
                    + " MissingPropertyName: " + exactPrefixProp2Name);

        assertEquals(
            2, propertyNames.size(),
            "Returned list contains more properties than expected. "
                + " List was: " + propertyNames);

        //try a broader search
        propertyNames
            = configurationService.getPropertyNamesByPrefix(prefix, false);

        assertTrue(
            propertyNames.contains(exactPrefixProp1Name),
            "Returned list did not contain all property names. "
                    + " MissingPropertyName: " + exactPrefixProp1Name);

        assertTrue(
            propertyNames.contains(exactPrefixProp2Name),
            "Returned list did not contain all property names. "
                    + " MissingPropertyName: " + exactPrefixProp2Name);

        assertTrue(
            propertyNames.contains(longerPrefixProp3Name),
            "Returned list did not contain all property names. "
                    + " MissingPropertyName: " + longerPrefixProp3Name);

        assertEquals(
            3, propertyNames.size(),
            "Returned list contains more properties than expected. "
                + " List was: " + propertyNames);


    }

    /**
     * Tests event notification through multicast listeners (those that are
     * registered for the whole configuration and not a single property only).
     */
    @Test
    public void testMulticastEventNotification()
    {
        propertyChangeEvent = null;

        configurationService.addPropertyChangeListener(pListener);

        // test the initial set of a property.
        try
        {
            configurationService.setProperty(propertyName, propertyValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNotNull(
            propertyChangeEvent,
            "No PropertyChangeEvent was delivered upon setProperty");

        assertNull(
            propertyChangeEvent.getOldValue(), "oldValue must be null");
        assertEquals(
            propertyValue,
            propertyChangeEvent.getNewValue(),
            "newValue is not the value we just set!");
        assertEquals(
            propertyName,
            propertyChangeEvent.getPropertyName(),
            "propertyName is not the value we just set!");

        //test setting a new value;
        propertyChangeEvent = null;
        try
        {
            configurationService.setProperty(propertyName, propertyNewValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNotNull(
            propertyChangeEvent,
            "No PropertyChangeEvent was delivered upon setProperty");

        assertEquals(
            propertyValue,
            propertyChangeEvent.getOldValue(), "incorrect oldValue");
        assertEquals(
            propertyNewValue,
            propertyChangeEvent.getNewValue(),
            "newValue is not the value we just set!");


        //test remove
        propertyChangeEvent = null;
        configurationService.removePropertyChangeListener(pListener);

        try
        {
            configurationService.setProperty(propertyName, propertyValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNull(
            propertyChangeEvent,
            "A PropertyChangeEvent after unregistering a listener.");
    }

    /**
     * Test event dispatch to vetoable listeners registered for the whole
     * configuration.
     */
    @Test
    public void testMulticastEventNotificationToVetoableListeners()
    {
        String propertyValue = "19200";
        String propertyNewValue = "19201";
        propertyChangeEvent = null;

        configurationService.addVetoableChangeListener(gentleVetoListener);

        // test the initial set of a property.
        try
        {
            configurationService.setProperty(propertyName, propertyValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNotNull(
            propertyChangeEvent, "No PropertyChangeEvent was delivered "
                    + "to VetoableListeners upon setProperty");

        assertNull(
            propertyChangeEvent.getOldValue(), "oldValue must be null");
        assertEquals(
            propertyValue,
            propertyChangeEvent.getNewValue(),
            "newValue is not the value we just set!");
        assertEquals(
            propertyName,
            propertyChangeEvent.getPropertyName(),
            "propertyName is not the value we just set!");

        //test setting a new value;
        propertyChangeEvent = null;
        try
        {
            configurationService.setProperty(propertyName, propertyNewValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNotNull(
            propertyChangeEvent,
            "No PropertyChangeEvent was delivered to veto listener "
                + "upon setProperty");

        assertEquals(
            propertyValue,
            propertyChangeEvent.getOldValue(), "incorrect oldValue");
        assertEquals(
            propertyNewValue,
            propertyChangeEvent.getNewValue(),
            "newValue is not the value we just set!");


        //test remove
        propertyChangeEvent = null;
        configurationService.removeVetoableChangeListener(gentleVetoListener);

        try
        {
            configurationService.setProperty(propertyName, propertyValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            fail("A PropertyVetoException came from nowhere. Exc="
                + ex.getMessage());
        }

        assertNull(
            propertyChangeEvent,
            "A PropertyChangeEvent after unregistering a listener.");
    }

    /**
     * Test whether vetoing changes works as it is supposed to.
     */
    @Test
    public void testVetos()
    {
        propertyChangeEvent = null;

        configurationService.addVetoableChangeListener(rudeVetoListener);
        configurationService.addPropertyChangeListener(pListener);

        ConfigPropertyVetoException exception = null;
        try
        {
            configurationService.setProperty(propertyName, propertyValue);
        }
        catch (ConfigPropertyVetoException ex)
        {
            exception = ex;
        }

        //make sure the exception was thrown
        assertNotNull(
            exception, "A vetoable change event was not dispatched or an "
                    + "exception was not let through.");

        //make sure no further event dispatching was done
        assertNull(
            propertyChangeEvent,
            "A property change event was delivered even after " +
                    "the property change was vetoed.");

        //make sure the property did not get modified after vetoing the change
        assertNull(
            configurationService.getProperty(propertyName),
            "A property was changed even avfter vetoing the change.");

        // now let's make sure that we have the right order of event dispatching.
        propertyChangeEvent = null;
        configurationService.removeVetoableChangeListener(rudeVetoListener);

        ConfigVetoableChangeListener vcListener = event -> assertNull(
            propertyChangeEvent,
            "propertyChangeEvent was not null which means that it has "
                + "bean delivered to the propertyChangeListener prior to "
                + "being delivered to the vetoable change listener.");

        try
        {
            configurationService.setProperty(propertyName, propertyNewValue);
        }
        catch (ConfigPropertyVetoException ex1)
        {
            ex1.printStackTrace();
            fail("unexpected veto exception. message:" + ex1.getMessage());
        }
        configurationService.removeVetoableChangeListener(vcListener);
    }

    /**
     * Make sure that adding listeners for a single property name
     * only gets us events for that listeners. Removing a listener for a
     * specific property should also be proved to no obstruct event delivery to
     * the same listener had it been registered for other properties.
     *
     */
    @Test
    public void testSinglePropertyEventNotification()
    {
        String listenedPropertyValue = "19.2598";
        String listenedPropertyNewValue = "19.29581";

        //test basic selective event dispatch
        configurationService.addPropertyChangeListener(
            listenedPropertyName, pListener);

        propertyChangeEvent = null;

        configurationService.setProperty(
            propertyName, propertyValue);

        assertNull(
            propertyChangeEvent,
            "setting prop:" + propertyName + " caused an event notif. to " +
                "listener registered for prop:" + listenedPropertyName);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyValue);

        assertNotNull(
            propertyChangeEvent,
            "No event was dispatched upon modification of prop:"
                        + listenedPropertyName);

        assertNull(
            propertyChangeEvent.getOldValue(), "oldValue must be null");
        assertEquals(
            listenedPropertyValue,
            propertyChangeEvent.getNewValue(), "wrong newValue");

        //test that a generic remove only removes the generic listener
        propertyChangeEvent = null;

        configurationService.removePropertyChangeListener(pListener);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyNewValue);

        assertNotNull(
            propertyChangeEvent,
            "No event was dispatched upon modification of prop:"
                        + listenedPropertyName
                        + ". The listener was wrongfully removed.");

        assertEquals(
            listenedPropertyValue,
            propertyChangeEvent.getOldValue(), "wrong oldValue");
        assertEquals(
            listenedPropertyNewValue,
            propertyChangeEvent.getNewValue(), "wrong newValue");

        //make sure that removing the listener properly - really removes it.
        propertyChangeEvent = null;

        configurationService.removePropertyChangeListener(
            listenedPropertyName, pListener);

        configurationService.setProperty(listenedPropertyName, propertyValue);

        assertNull(
            propertyChangeEvent,
            "An event was wrongfully dispatched after removing a listener");

    }

    /**
     * Make sure that adding vetoable listeners for a single property name
     * only gets us events for that listeners. Removing a listener for a
     * specific property should also be proved to no obstruct event delivery to
     * the same listener had it been registered for other properties.
     *
     */
    @Test
    public void testSinglePropertyVetoEventNotification()
    {
        String listenedPropertyValue = "19.2598";
        String listenedPropertyNewValue = "19.29581";
        ConfigVetoableChangeListener vetoListener =
            event -> propertyChangeEvent = event;

        //test basic selective event dispatch
        configurationService.addVetoableChangeListener(
            listenedPropertyName, vetoListener);

        propertyChangeEvent = null;

        configurationService.setProperty(
            propertyName, propertyValue);

        assertNull(
            propertyChangeEvent,
            "setting prop:" + propertyName + " caused an event notif. to " +
                "listener registered for prop:" + listenedPropertyName);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyValue);

        assertNotNull(
            propertyChangeEvent,
            "No event was dispatched upon modification of prop:"
                        + listenedPropertyName);

        assertNull(
            propertyChangeEvent.getOldValue(), "oldValue must be null");
        assertEquals(
            listenedPropertyValue,
            propertyChangeEvent.getNewValue(), "wrong newValue");

        //test that a generic remove only removes the generic listener
        propertyChangeEvent = null;

        configurationService.removeVetoableChangeListener(vetoListener);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyNewValue);

        assertNotNull(
            propertyChangeEvent,
            "No event was dispatched upon modification of prop:"
                        + listenedPropertyName
                        + ". The listener was wrongfully removed.");

        assertEquals(
            listenedPropertyValue,
            propertyChangeEvent.getOldValue(), "wrong oldValue");
        assertEquals(
            listenedPropertyNewValue,
            propertyChangeEvent.getNewValue(), "wrong newValue");

        //make sure that removing the listener properly - really removes it.
        propertyChangeEvent = null;

        configurationService.removeVetoableChangeListener(
            listenedPropertyName, vetoListener);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyValue);

        assertNull(
            propertyChangeEvent,
            "An event was wrongfully dispatched after removing a listener");

        //make sure that adding a generic listener, then adding a custom prop
        //listener, then removing it - would not remove the generic listener.
        propertyChangeEvent = null;

        configurationService.addVetoableChangeListener(vetoListener);
        configurationService.addVetoableChangeListener(
            listenedPropertyName, vetoListener);
        configurationService.removeVetoableChangeListener(
            listenedPropertyName, vetoListener);

        configurationService.setProperty(
            listenedPropertyName, listenedPropertyNewValue);

        assertNotNull(
            propertyChangeEvent,
            "No event was dispatched upon modification of prop:"
                        + listenedPropertyName
                        + ". The global listener was wrongfully removed.");

        assertEquals(
            listenedPropertyName,
            propertyChangeEvent.getPropertyName(), "wrong propertyName");
        assertEquals(
            listenedPropertyValue,
            propertyChangeEvent.getOldValue(), "wrong oldValue");
        assertEquals(
            listenedPropertyNewValue,
            propertyChangeEvent.getNewValue(), "wrong newValue");
    }
}
