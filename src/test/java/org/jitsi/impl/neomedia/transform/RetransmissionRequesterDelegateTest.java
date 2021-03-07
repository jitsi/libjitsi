/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.jitsi.impl.neomedia.transform;

import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.utils.*;
import org.junit.*;
import org.mockito.*;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author bbaldino
 */
public class RetransmissionRequesterDelegateTest
{
    protected MediaStream mockStream;
    protected TimeProvider timeProvider;
    protected Runnable workReadyCallback;
    protected RetransmissionRequesterDelegate retransmissionRequester;

    protected final long SENDER_SSRC = 424242L;
    protected byte PAYLOAD_TYPE = 107;

    protected void setTime(long timeMillis)
    {
        when(timeProvider.currentTimeMillis()).thenReturn(timeMillis);
    }

    protected void verifyNackPacket(RawPacket nackPacket, long ssrc, Integer... nackedSeqNums)
    {
        Collection<Integer> lostPackets = NACKPacket.getLostPackets(nackPacket);
        assertEquals(nackedSeqNums.length, lostPackets.size());
        for (Integer nackedSeqNum : nackedSeqNums)
        {
            assertTrue(lostPackets.contains(nackedSeqNum));

        }
        assertEquals(ssrc, NACKPacket.getSourceSSRC(nackPacket));
    }

    protected void expectHasWorkReady(RetransmissionRequesterDelegate retransmissionRequester)
    {
        assertEquals(0, retransmissionRequester.getTimeUntilNextRun());
    }

    protected void expectNoWorkReady(RetransmissionRequesterDelegate retransmissionRequester)
    {
        assertTrue(retransmissionRequester.getTimeUntilNextRun() > 0);
    }

    @Before
    public void setUp()
    {
        mockStream = mock(MediaStream.class);
        timeProvider = mock(TimeProvider.class);
        workReadyCallback = mock(Runnable.class);
        retransmissionRequester = new RetransmissionRequesterDelegate(mockStream, timeProvider);

        retransmissionRequester.setWorkReadyCallback(workReadyCallback);
        retransmissionRequester.setSenderSsrc(SENDER_SSRC);

        MediaFormat mediaFormat = mock(MediaFormat.class);
        when(mockStream.getFormat(PAYLOAD_TYPE)).thenReturn(mediaFormat);
        when(mediaFormat.getEncoding()).thenReturn(Constants.VP8);
    }

    /**
     * Pass packets in with a gap of 1, make sure a nack is sent
     */
    @Test
    public void testBasicMissingPacket()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        workReadyCallback.run();
        ArgumentCaptor<RawPacket> nackPacketCapture
            = ArgumentCaptor.forClass(RawPacket.class);
        doNothing()
            .when(mockStream)
            .injectPacket(nackPacketCapture.capture(), eq(false), eq(null));

        setTime(0L);

        expectNoWorkReady(retransmissionRequester);
        retransmissionRequester.packetReceived(ssrc, 10);
        expectNoWorkReady(retransmissionRequester);

        retransmissionRequester.packetReceived(ssrc, 12);
        expectHasWorkReady(retransmissionRequester);

        retransmissionRequester.run();
        expectNoWorkReady(retransmissionRequester);

        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 11);
    }

    /**
     * Test that when packets are processed sequentially (without gaps),
     * no nacks are sent
     */
    @Test
    public void testSequentialPacketsDontGenerateNack()
    {
        long ssrc = 12345L;

        setTime(0L);

        retransmissionRequester.packetReceived(ssrc, 10);
        expectNoWorkReady(retransmissionRequester);

        setTime(10L);

        retransmissionRequester.packetReceived(ssrc, 11);
        expectNoWorkReady(retransmissionRequester);

        setTime(20L);

        retransmissionRequester.packetReceived(ssrc, 12);
        expectNoWorkReady(retransmissionRequester);
    }


    /**
     * Test that nacks are re-sent after the 'interval' amount of time
     * has passed
     */
    @Test
    public void testResendNack()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        workReadyCallback.run();

        ArgumentCaptor<RawPacket> nackPacketCapture
            = ArgumentCaptor.forClass(RawPacket.class);
        doNothing()
            .when(mockStream)
            .injectPacket(nackPacketCapture.capture(), eq(false), eq(null));

        long startTime = 0;
        setTime(startTime);

        retransmissionRequester.packetReceived(ssrc, 10);
        retransmissionRequester.packetReceived(ssrc, 12);
        // The first time we call 'hasWork/doWork' will be for the first transmission
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        setTime(startTime + RetransmissionRequesterDelegate.RE_REQUEST_AFTER_MILLIS);

        // Now currentTimeMillis will return a time in the future and the retransmission
        // should be sent
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 11);
    }

    /**
     * Test that nack retransmissions give up after the right amount of time
     */
    @Test
    public void testRetransmissionTimeout()
    {
        long ssrc = 12345L;

        workReadyCallback.run();

        long startTime = 0;
        setTime(startTime);

        retransmissionRequester.packetReceived(ssrc, 10);
        retransmissionRequester.packetReceived(ssrc, 12);
        // The first time we call 'hasWork/doWork' will be for the first transmission
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        for (int i = 1; i < RetransmissionRequesterDelegate.MAX_REQUESTS; ++i)
        {
            setTime(startTime + i * RetransmissionRequesterDelegate.RE_REQUEST_AFTER_MILLIS);
            expectHasWorkReady(retransmissionRequester);
            retransmissionRequester.run();
        }
        // The next time we check for work, there should be none
        expectNoWorkReady(retransmissionRequester);
    }

    /**
     * Test multiple missing packets
     */
    @Test
    public void testMultipleMissingPackets()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        workReadyCallback.run();
        ArgumentCaptor<RawPacket> nackPacketCapture
            = ArgumentCaptor.forClass(RawPacket.class);
        doNothing()
            .when(mockStream)
            .injectPacket(nackPacketCapture.capture(), eq(false), eq(null));

        setTime(0);

        retransmissionRequester.packetReceived(ssrc, 10);
        retransmissionRequester.packetReceived(ssrc, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        retransmissionRequester.packetReceived(ssrc, 15);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 11, 13, 14);
    }

    /**
     * Test that gaps in multiple ssrcs result in nacks for each stream
     */
    @Test
    public void testMultipleStreams()
        throws TransmissionFailedException
    {
        long ssrc1 = 12345L;
        long ssrc2 = 54321L;

        workReadyCallback.run();
        ArgumentCaptor<RawPacket> nackPacketCapture
            = ArgumentCaptor.forClass(RawPacket.class);
        doNothing()
            .when(mockStream)
            .injectPacket(nackPacketCapture.capture(), eq(false), eq(null));

        setTime(0);

        retransmissionRequester.packetReceived(ssrc1, 10);
        retransmissionRequester.packetReceived(ssrc2, 10);
        expectNoWorkReady(retransmissionRequester);

        retransmissionRequester.packetReceived(ssrc1, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        retransmissionRequester.packetReceived(ssrc2, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        assertEquals(2, nackPacketCapture.getAllValues().size());

        RawPacket capturedNackPacket1 = nackPacketCapture.getAllValues().get(0);
        verifyNackPacket(capturedNackPacket1, ssrc1,11);

        RawPacket capturedNackPacket2 = nackPacketCapture.getAllValues().get(1);
        verifyNackPacket(capturedNackPacket2, ssrc2,11);
    }

    /**
     * Test that a big enough gap triggers a reset
     */
    @Test
    public void testBigGapTriggersReset()
    {
        long ssrc = 12345L;

        workReadyCallback.run();

        setTime(0L);

        retransmissionRequester.packetReceived(ssrc, 10);

        retransmissionRequester.packetReceived(ssrc, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        // Have the next packet be a big jump from the last one
        int bigJumpSeqNum = 12 + RetransmissionRequesterDelegate.MAX_MISSING + 1;
        retransmissionRequester.packetReceived(ssrc, bigJumpSeqNum);
        expectNoWorkReady(retransmissionRequester);
    }

    /**
     * Test that the reception of a missing packet prevents it from being
     * nacked again
     */
    @Test
    public void testReceiveMissingPacket()
    {
        long ssrc = 12345L;

        workReadyCallback.run();

        long startTime = 0L;
        setTime(startTime);

        retransmissionRequester.packetReceived(ssrc, 10);
        // Don't pass in packet 11 yet
        retransmissionRequester.packetReceived(ssrc, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        setTime(startTime + RetransmissionRequesterDelegate.RE_REQUEST_AFTER_MILLIS);

        retransmissionRequester.packetReceived(ssrc, 11);
        expectNoWorkReady(retransmissionRequester);
    }

    /**
     * Test that the reception of a missing packet prevents it from being
     * nacked again, but other missing packets are still tracked correctly
     */
    @Test
    public void testReceiveMissingPacketOthersStillMissing()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        workReadyCallback.run();
        ArgumentCaptor<RawPacket> nackPacketCapture
            = ArgumentCaptor.forClass(RawPacket.class);
        doNothing()
            .when(mockStream)
            .injectPacket(nackPacketCapture.capture(), eq(false), eq(null));

        long startTime = 0L;
        setTime(startTime);

        retransmissionRequester.packetReceived(ssrc, 10);
        // Don't pass in packet 11 yet
        retransmissionRequester.packetReceived(ssrc, 12);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        retransmissionRequester.packetReceived(ssrc, 15);
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        setTime(startTime + RetransmissionRequesterDelegate.RE_REQUEST_AFTER_MILLIS);

        retransmissionRequester.packetReceived(ssrc, 11);
        // It should still have work, but the next nack shouldn't include
        // packet 11
        expectHasWorkReady(retransmissionRequester);
        retransmissionRequester.run();

        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 13, 14);
    }
}
