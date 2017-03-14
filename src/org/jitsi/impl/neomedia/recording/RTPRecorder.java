package org.jitsi.impl.neomedia.recording;

import org.jitsi.service.neomedia.RawPacket;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by msaavedra on 2/28/17.
 */
public class RTPRecorder {
    private static final ConcurrentHashMap<String, RTPRecorder> RECORDER_MAP = new ConcurrentHashMap<>();

    private String recorderName;
    private TreeSet<RawPacket> packets = new TreeSet<>(new Comparator<RawPacket>() {
        @Override
        public int compare(RawPacket o1, RawPacket o2) {
            return o1.getSequenceNumber() - o2.getSequenceNumber();
        }
    });

    private RTPRecorder(String recorderName) {
        this.recorderName = recorderName;
    }

    private void recordPacket(RawPacket rawPacket) {
        byte[] rawBuff = rawPacket.getBuffer();
        int from = rawPacket.getPayloadOffset();
        int to = rawPacket.getLength();
        byte[] copyOfRange = Arrays.copyOfRange(rawBuff, from, to);

        RawPacket packetToRecord = new RawPacket(copyOfRange, 0, copyOfRange.length);
        packetToRecord.setSequenceNumber(rawPacket.getSequenceNumber());
        packets.add(packetToRecord);
    }

    public static synchronized void savePacket(DatagramPacket datagramPacketPacket, RawPacket[] rawPackets) {
        int port = datagramPacketPacket.getPort();
        String recorderName = "" + port;
        RTPRecorder rtpRecorder = RECORDER_MAP.get(recorderName);
        if (rtpRecorder == null) {
            rtpRecorder = new RTPRecorder(recorderName);
            RECORDER_MAP.put(recorderName, rtpRecorder);
        }
        for (RawPacket rawPacket : rawPackets) {
            if (rawPacket != null) {
                rtpRecorder.recordPacket(rawPacket);
            }
        }
    }

    public static synchronized void persistRecorder(String recorderName) {
        RTPRecorder rtpRecorder = RECORDER_MAP.get(recorderName);
        if (rtpRecorder == null) {
            throw new ZeroRTPException();
        }
        persistRecorder(rtpRecorder);
        RECORDER_MAP.remove(recorderName);
    }

    private static void persistRecorder(RTPRecorder rtpRecorder) {
        try {
            String recordingFileName = System.getProperty("net.java.sip.communicator.impl.neomedia.audioSystem.recorder." + rtpRecorder.recorderName + ".fileName");
            final FileOutputStream fos = new FileOutputStream(recordingFileName, true);

            Iterator<RawPacket> rawPacketIterator = rtpRecorder.packets.iterator();
            while (rawPacketIterator.hasNext()) {
                RawPacket rawPacket = rawPacketIterator.next();
                try {
                    fos.write(rawPacket.getBuffer());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ZeroRTPException extends NullPointerException {}
}
