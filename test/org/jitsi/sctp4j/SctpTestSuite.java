/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.sctp4j;

import org.junit.runner.*;
import org.junit.runners.*;

/**
 * Test suite for SCTP.
 *
 * @author Pawel Domas
 */
@RunWith(Suite.class)
@Suite.SuiteClasses(
    {
        SctpNativeWrapperTest.class,
        SctpTransferTest.class
    })
public class SctpTestSuite
{
}
