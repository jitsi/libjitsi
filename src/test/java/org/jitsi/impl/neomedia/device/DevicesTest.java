/*
 * Copyright @ 2026 - present 8x8, Inc.
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
package org.jitsi.impl.neomedia.device;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;

import javax.media.*;

import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.junit.jupiter.api.*;

public class DevicesTest
{
    private static final String PROP_PREFIX = "org.jitsi.test.audioSystem.";

    private static final String PROP_LIST
        = PROP_PREFIX + CaptureDevices.PROP_DEVICE + "_list";

    private static final int THREADS = 8;

    private static final int ITERATIONS = 200;

    private final CaptureDeviceInfo2 silence = createDevice(
        "AudioSilenceCaptureDevice", "audiosilence:");

    private final CaptureDeviceInfo2 noTransferData = createDevice(
        "AudioSilenceCaptureDevice:noTransferData",
        "audiosilence:noTransferData");

    /**
     * Same order as {@link AudioSilenceSystem} registers them.
     */
    private final List<CaptureDeviceInfo2> activeDevices
        = Arrays.asList(silence, noTransferData);

    private ConfigurationService cfg;

    private AudioSystem audioSystem;

    private static CaptureDeviceInfo2 createDevice(String name, String locator)
    {
        return new CaptureDeviceInfo2(
            name, new MediaLocator(locator), new Format[0], null, null, null);
    }

    @BeforeEach
    public void setUp()
    {
        LibJitsi.start();
        cfg = LibJitsi.getConfigurationService();
        cfg.removeProperty(PROP_LIST);

        audioSystem = mock(AudioSystem.class);
        when(audioSystem.getPropertyName(anyString()))
            .thenAnswer(i -> PROP_PREFIX + i.getArgument(0));
    }

    @AfterEach
    public void tearDown()
    {
        cfg.removeProperty(PROP_LIST);
    }

    @Test
    public void firstDeviceIsPreferredOnEmptyPreferences()
    {
        Devices devices = new CaptureDevices(audioSystem);

        assertSame(silence, devices.getSelectedDevice(activeDevices));
        assertEquals(
            "[\"AudioSilenceCaptureDevice\", "
                + "\"AudioSilenceCaptureDevice:noTransferData\"]",
            cfg.getString(PROP_LIST));
    }

    @Test
    public void concurrentSelectionKeepsPreferenceOrder()
        throws Exception
    {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        try
        {
            for (int i = 0; i < ITERATIONS; i++)
            {
                cfg.removeProperty(PROP_LIST);

                Devices devices = new CaptureDevices(audioSystem);
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<CaptureDeviceInfo2>> results = new ArrayList<>();

                for (int t = 0; t < THREADS; t++)
                {
                    results.add(executor.submit(() ->
                    {
                        barrier.await();
                        return devices.getSelectedDevice(activeDevices);
                    }));
                }

                for (Future<CaptureDeviceInfo2> result : results)
                {
                    assertSame(
                        silence,
                        result.get(10, TimeUnit.SECONDS),
                        "iteration " + i);
                }
                assertSame(
                    silence,
                    devices.getSelectedDevice(activeDevices),
                    "iteration " + i + ", preferences "
                        + cfg.getString(PROP_LIST));
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }
}
