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
package org.jitsi.impl.neomedia;

import org.junit.*;

import java.util.logging.*;

import static org.junit.Assert.*;

/**
 * @author George Politis
 */
public class SequenceNumberRewriterTest
{
    @Test
    public void rewriteSequenceNumber()
        throws Exception
    {
        SequenceNumberRewriter snr = new SequenceNumberRewriter();
        assertEquals(0, snr.delta);
        assertEquals(-1, snr.highestSent);

        // Accept first packet.
        int ret = snr.rewriteSequenceNumber(true, 0xffff - 2);
        assertEquals(0, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);
        assertEquals(ret, 0xffff - 2);

        // Retransmission.
        ret = snr.rewriteSequenceNumber(true, 0xffff - 2);
        assertEquals(0, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);
        assertEquals(ret, 0xffff - 2);

        // Retransmission & accept toggle.
        snr.rewriteSequenceNumber(false, 0xffff - 2);
        assertEquals(0, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);

        // Retransmission & accept toggle.
        ret = snr.rewriteSequenceNumber(true, 0xffff - 2);
        assertEquals(0, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);
        assertEquals(ret, 0xffff - 2);

        // Drop ordered packet.
        snr.rewriteSequenceNumber(false, 0xffff - 1);
        assertEquals(1, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);

        // Drop re-ordered packet.
        snr.rewriteSequenceNumber(false, 0xffff - 3);
        assertEquals(1, snr.delta);
        assertEquals(0xffff - 2, snr.highestSent);

        // Accept after re-ordered drop.
        ret = snr.rewriteSequenceNumber(true, 0xffff);
        assertEquals(1, snr.delta);
        assertEquals(0xffff - 1, snr.highestSent);
        assertEquals(0xffff - 1, ret);

        // Drop ordered packet.
        snr.rewriteSequenceNumber(false, 0);
        assertEquals(2, snr.delta);
        assertEquals(0xffff - 1, snr.highestSent);

        // Accept ordered packet.
        ret = snr.rewriteSequenceNumber(true, 1);
        assertEquals(2, snr.delta);
        assertEquals(0xffff, snr.highestSent);
        assertEquals(ret, 0xffff);

        // Drop ordered packets.
        for (int i = 2; i < 0xffff; i++)
        {
            snr.rewriteSequenceNumber(false, i);
            assertEquals(i + 1, snr.delta);
            assertEquals(0xffff, snr.highestSent);
        }

        // Drop ordered packet.
        snr.rewriteSequenceNumber(false, 0xffff);
        assertEquals(0, snr.delta);
        assertEquals(0xffff, snr.highestSent);

        // Accept ordered packet.
        ret = snr.rewriteSequenceNumber(true, 0);
        assertEquals(0, snr.delta);
        assertEquals(0, snr.highestSent);
        assertEquals(ret, 0);

        // Retransmission + accept toggle
        ret = snr.rewriteSequenceNumber(true, 0xffff);
        assertEquals(0, snr.delta);
        assertEquals(0, snr.highestSent);
        assertEquals(ret, 0xffff);
    }

}