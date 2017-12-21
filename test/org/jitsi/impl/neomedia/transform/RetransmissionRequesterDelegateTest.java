package org.jitsi.impl.neomedia.transform;

import org.easymock.*;
import org.jitsi.impl.neomedia.rtcp.*;
import org.jitsi.impl.neomedia.rtp.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.junit.*;
import org.powermock.api.easymock.*;

import java.util.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.powermock.api.easymock.PowerMock.*;

public class RetransmissionRequesterDelegateTest
{
    protected MediaStream mockStream;
    protected TimeProvider timeProvider;
    protected Runnable workReadyCallback;
    protected RetransmissionRequesterDelegate retransmissionRequester;

    protected final long SENDER_SSRC = 424242L;
    protected byte PAYLOAD_TYPE = 107;

    // Copied from RetransmissionRequesterDelegate
    // TODO: should we just make these values public in the delegate?
    // or pass them in?
    protected final int MAX_REQUESTS = 10;
    protected final int RE_REQUEST_INTERVAL_MILLIS = 150;
    protected final int MAX_MISSING = 100;

    protected static RawPacket createPacket(long ssrc, byte pt, int seqNum)
    {
        RawPacket packet = PowerMock.createMock(RawPacket.class);
        expect(packet.getPayloadType()).andReturn(pt).anyTimes();
        expect(packet.getSSRCAsLong()).andReturn(ssrc).anyTimes();
        expect(packet.getSequenceNumber()).andReturn(seqNum).anyTimes();

        return packet;
    }

    protected static RawPacket createRtxPacket(long ssrc, byte pt, int seqNum)
    {
        RawPacket packet = PowerMock.createMock(RawPacket.class);
        expect(packet.getPayloadType()).andReturn(pt).anyTimes();
        expect(packet.getSSRCAsLong()).andReturn(ssrc).anyTimes();
        expect(packet.getOriginalSequenceNumber()).andReturn(seqNum).anyTimes();

        return packet;
    }

    protected void setTime(long timeMillis)
    {
        PowerMock.reset(timeProvider);
        expect(timeProvider.getTime()).andReturn(timeMillis).anyTimes();
        PowerMock.replay(timeProvider);
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

    @Before
    public void setUp()
    {
        mockStream = PowerMock.createMock(MediaStream.class);
        timeProvider = PowerMock.createMock(TimeProvider.class);
        workReadyCallback = PowerMock.createMock(Runnable.class);
        retransmissionRequester = new RetransmissionRequesterDelegate(mockStream, timeProvider);

        retransmissionRequester.setWorkReadyCallback(workReadyCallback);
        retransmissionRequester.setSenderSsrc(SENDER_SSRC);

        MediaFormat mediaFormat = PowerMock.createMock(MediaFormat.class);
        expect(mockStream.getFormat(PAYLOAD_TYPE)).andReturn(mediaFormat).anyTimes();
        expect(mediaFormat.getEncoding()).andReturn(Constants.VP8).anyTimes();
    }

    @After
    public void tearDown()
    {
        verifyAll();
    }

    /**
     * Pass packets in with a gap of 1, make sure a nack is sent
     */
    @Test
    public void testBasicMissingPacket()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        workReadyCallback.run();
        PowerMock.expectLastCall();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall();

        replayAll();

        setTime(0L);

        assertFalse(retransmissionRequester.hasWork());
        retransmissionRequester.reverseTransform(packet10);
        assertFalse(retransmissionRequester.hasWork());

        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());

        retransmissionRequester.doWork();
        assertFalse(retransmissionRequester.hasWork());

        assertTrue(nackPacketCapture.hasCaptured());
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 11);
    }

    /**
     * Test that when packets are processed sequentially (without gaps),
     * no nacks are sent
     */
    @Test
    public void testSequentialPacketsDontGenerateNack()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);
        RawPacket packet11 = createPacket(ssrc, PAYLOAD_TYPE, 11);
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        replayAll();

        setTime(0L);

        retransmissionRequester.reverseTransform(packet10);
        assertFalse(retransmissionRequester.hasWork());

        setTime(10L);

        retransmissionRequester.reverseTransform(packet11);
        assertFalse(retransmissionRequester.hasWork());

        setTime(20L);

        retransmissionRequester.reverseTransform(packet12);
        assertFalse(retransmissionRequester.hasWork());
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

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        workReadyCallback.run();
        PowerMock.expectLastCall();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        long startTime = 0;
        setTime(startTime);

        retransmissionRequester.reverseTransform(packet10);
        retransmissionRequester.reverseTransform(packet12);
        // The first time we call 'hasWork/doWork' will be for the first transmission
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        setTime(startTime + RE_REQUEST_INTERVAL_MILLIS);

        // Now getTime will return a time in the future and the retransmission
        // should be sent
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();
        assertTrue(nackPacketCapture.hasCaptured());
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 11);
    }

    /**
     * Test that nack retransmissions give up after the right amount of time
     */
    @Test
    public void testRetransmissionTimeout()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        workReadyCallback.run();
        PowerMock.expectLastCall();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        long startTime = 0;
        setTime(startTime);

        retransmissionRequester.reverseTransform(packet10);
        retransmissionRequester.reverseTransform(packet12);
        // The first time we call 'hasWork/doWork' will be for the first transmission
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        for (int i = 1; i < MAX_REQUESTS; ++i)
        {
            setTime(startTime + i * RE_REQUEST_INTERVAL_MILLIS);
            assertTrue(retransmissionRequester.hasWork());
            retransmissionRequester.doWork();
        }
        // The next time we check for work, there should be none
        assertFalse(retransmissionRequester.hasWork());
    }

    /**
     * Test multiple missing packets
     */
    @Test
    public void testMultipleMissingPackets()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        // Third packet (seq num 15)
        RawPacket packet15 = createPacket(ssrc, PAYLOAD_TYPE, 15);

        workReadyCallback.run();
        PowerMock.expectLastCall().times(2);

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        setTime(0);

        retransmissionRequester.reverseTransform(packet10);
        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        retransmissionRequester.reverseTransform(packet15);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        assertTrue(nackPacketCapture.hasCaptured());
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

        // ssrc1 packets
        RawPacket ssrc1packet10 = createPacket(ssrc1, PAYLOAD_TYPE, 10);
        RawPacket ssrc1packet12 = createPacket(ssrc1, PAYLOAD_TYPE, 12);

        // ssrc3 packets
        RawPacket ssrc2packet10 = createPacket(ssrc2, PAYLOAD_TYPE, 10);
        RawPacket ssrc2packet12 = createPacket(ssrc2, PAYLOAD_TYPE, 12);

        workReadyCallback.run();
        PowerMock.expectLastCall().times(2);

        Capture<RawPacket> nackPacketCapture = Capture.newInstance(CaptureType.ALL);
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        setTime(0);

        retransmissionRequester.reverseTransform(ssrc1packet10);
        retransmissionRequester.reverseTransform(ssrc2packet10);
        assertFalse(retransmissionRequester.hasWork());

        retransmissionRequester.reverseTransform(ssrc1packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        retransmissionRequester.reverseTransform(ssrc2packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        assertEquals(2, nackPacketCapture.getValues().size());

        RawPacket capturedNackPacket1 = nackPacketCapture.getValues().get(0);
        verifyNackPacket(capturedNackPacket1, ssrc1,11);

        RawPacket capturedNackPacket2 = nackPacketCapture.getValues().get(1);
        verifyNackPacket(capturedNackPacket2, ssrc2,11);
    }

    /**
     * Test that a big enough gap triggers a reset
     */
    @Test
    public void testBigGapTriggersReset()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);

        // Big jump
        RawPacket bigJumpPacket = createPacket(ssrc, PAYLOAD_TYPE, 12 + MAX_MISSING + 1);

        workReadyCallback.run();
        PowerMock.expectLastCall();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall();

        replayAll();

        setTime(0L);

        retransmissionRequester.reverseTransform(packet10);

        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        retransmissionRequester.reverseTransform(bigJumpPacket);
        assertFalse(retransmissionRequester.hasWork());
    }

    /**
     * Test that the reception of a missing packet prevents it from being
     * nacked again
     */
    @Test
    public void testReceiveMissingPacket()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);
        RawPacket packet11 = createPacket(ssrc, PAYLOAD_TYPE, 11);
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);
        RawPacket packet15 = createPacket(ssrc, PAYLOAD_TYPE, 15);

        workReadyCallback.run();
        PowerMock.expectLastCall().anyTimes();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        long startTime = 0L;
        setTime(startTime);

        retransmissionRequester.reverseTransform(packet10);
        // Don't pass in packet 11 yet
        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        retransmissionRequester.reverseTransform(packet15);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        setTime(startTime + RE_REQUEST_INTERVAL_MILLIS);

        retransmissionRequester.reverseTransform(packet11);
        // It should still have work, but the next nack shouldn't include
        // packet 11
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        assertTrue(nackPacketCapture.hasCaptured());
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 13, 14);
    }

    /**
     * Test that an rtx packet is properly recognized
     */
    @Test
    public void testReceiveRtx()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;
        long rtxSsrc = 2468L;
        byte rtxPayloadType = 109;
        MediaFormat rtxMediaFormat = PowerMock.createMock(MediaFormat.class);
        expect(rtxMediaFormat.getEncoding()).andReturn(Constants.RTX).anyTimes();

        RTPEncodingDesc mockRtpEncodingDesc = PowerMock.createMock(RTPEncodingDesc.class);
        expect(mockRtpEncodingDesc.getPrimarySSRC()).andReturn(ssrc).anyTimes();
        MediaStreamTrackReceiver mockMediaStreamTrackReceiver = PowerMock.createMock(MediaStreamTrackReceiver.class);
        expect(mockMediaStreamTrackReceiver.findRTPEncodingDesc(anyObject(RawPacket.class))).andReturn(mockRtpEncodingDesc).anyTimes();
        expect(mockStream.getFormat(rtxPayloadType)).andReturn(rtxMediaFormat).anyTimes();
        expect(mockStream.getMediaStreamTrackReceiver()).andReturn(mockMediaStreamTrackReceiver).anyTimes();

        RawPacket packet10 = createPacket(ssrc, PAYLOAD_TYPE, 10);
        RawPacket rtxPacket11 = createRtxPacket(rtxSsrc, rtxPayloadType, 11);
        RawPacket packet12 = createPacket(ssrc, PAYLOAD_TYPE, 12);
        RawPacket packet15 = createPacket(ssrc, PAYLOAD_TYPE, 15);

        workReadyCallback.run();
        PowerMock.expectLastCall().anyTimes();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall().anyTimes();

        replayAll();

        long startTime = 0L;
        setTime(startTime);

        retransmissionRequester.reverseTransform(packet10);
        // Don't pass in packet 11 yet
        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        retransmissionRequester.reverseTransform(packet15);
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        setTime(startTime + RE_REQUEST_INTERVAL_MILLIS);

        retransmissionRequester.reverseTransform(rtxPacket11);
        // It should still have work, but the next nack shouldn't include
        // packet 11
        assertTrue(retransmissionRequester.hasWork());
        retransmissionRequester.doWork();

        assertTrue(nackPacketCapture.hasCaptured());
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        verifyNackPacket(capturedNackPacket, ssrc, 13, 14);
    }

}