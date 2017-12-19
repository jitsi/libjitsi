package org.jitsi.impl.neomedia.transform;

import org.easymock.*;
import org.jitsi.impl.neomedia.rtcp.*;
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
    protected final MediaStream mockStream = PowerMock.createMock(MediaStream.class);
    protected final TimeProvider timeProvider = PowerMock.createMock(TimeProvider.class);

    protected final Runnable workReadyCallback = PowerMock.createMock(Runnable.class);

    protected final RetransmissionRequesterDelegate retransmissionRequester = new RetransmissionRequesterDelegate(mockStream, timeProvider);
    protected final long senderSsrc = 424242L;
    protected byte payloadType = 107;

    protected static RawPacket createPacket(long ssrc, byte pt, int seqNum)
    {
        RawPacket packet = PowerMock.createMock(RawPacket.class);
        expect(packet.getPayloadType()).andReturn(pt).anyTimes();
        expect(packet.getSSRCAsLong()).andReturn(ssrc).anyTimes();
        expect(packet.getSequenceNumber()).andReturn(seqNum).anyTimes();

        return packet;
    }

    @Before
    public void setUp()
    {
        retransmissionRequester.setWorkReadyCallback(workReadyCallback);
        retransmissionRequester.setSenderSsrc(senderSsrc);

        MediaFormat mediaFormat = PowerMock.createMock(MediaFormat.class);
        expect(mockStream.getFormat(payloadType)).andReturn(mediaFormat).anyTimes();
        expect(mediaFormat.getEncoding()).andReturn(Constants.VP8).anyTimes();
    }

    @Test
    public void testReverseTransform()
        throws TransmissionFailedException
    {
        long ssrc = 12345L;

        expect(timeProvider.getTime()).andReturn(0L).anyTimes();

        // Initial packet (seq num 10)
        RawPacket packet10 = createPacket(ssrc, payloadType, 10);

        // Second packet (seq num 12)
        RawPacket packet12 = createPacket(ssrc, payloadType, 12);

        workReadyCallback.run();
        PowerMock.expectLastCall();

        Capture<RawPacket> nackPacketCapture = Capture.newInstance();
        mockStream.injectPacket(capture(nackPacketCapture), eq(false), eq((TransformEngine)null));
        PowerMock.expectLastCall();

        replayAll();

        assertFalse(retransmissionRequester.hasWork());
        retransmissionRequester.reverseTransform(packet10);
        assertFalse(retransmissionRequester.hasWork());

        retransmissionRequester.reverseTransform(packet12);
        assertTrue(retransmissionRequester.hasWork());

        retransmissionRequester.doWork();

        assertTrue(nackPacketCapture.hasCaptured());
        RawPacket capturedNackPacket = nackPacketCapture.getValue();
        Collection<Integer> lostPackets = NACKPacket.getLostPackets(capturedNackPacket);
        assertEquals(1, lostPackets.size());
        assertTrue(lostPackets.contains(11));
        assertEquals(ssrc, NACKPacket.getSourceSSRC(capturedNackPacket));

        verifyAll();
    }
}