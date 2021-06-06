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
package org.jitsi.service.neomedia.recording;

/**
 * @author Boris Grozev
 */
public interface Synchronizer
{
    /**
     * Sets the clock rate of the RTP clock for a specific SSRC.
     * @param ssrc the SSRC for which to set the RTP clock rate.
     * @param clockRate the clock rate.
     */
    public void setRtpClockRate(long ssrc, long clockRate);

    /**
     * Sets the endpoint identifier for a specific SSRC.
     * @param ssrc the SSRC for which to set the endpoint identifier.
     * @param endpointId the endpoint identifier to set.
     */
    public void setEndpoint(long ssrc, String endpointId);

    /**
     * Notifies this <tt>Synchronizer</tt> that the RTP timestamp
     * <tt>rtpTime</tt> (for SSRC <tt>ssrc</tt>) corresponds to the
     * NTP timestamp <tt>ntpTime</tt>.
     * @param ssrc the SSRC.
     * @param rtpTime the RTP timestamp which corresponds to <tt>ntpTime</tt>.
     * @param ntpTime the NTP timestamp which corresponds to <tt>rtpTime</tt>.
     */
    public void mapRtpToNtp(long ssrc, long rtpTime, double ntpTime);

    /**
     * Notifies this <tt>Synchronizer</tt> that the local timestamp
     * <tt>localTime</tt> corresponds to the NTP timestamp <tt>ntpTime</tt>
     * (for SSRC <tt>ssrc</tt>).
     * @param ssrc the SSRC.
     * @param localTime the local timestamp which corresponds to <tt>ntpTime</tt>.
     * @param ntpTime the NTP timestamp which corresponds to <tt>localTime</tt>.
     */
    public void mapLocalToNtp(long ssrc, long localTime, double ntpTime);

    /**
     * Tries to find the local time (as returned by
     * <tt>System.currentTimeMillis()</tt>) that corresponds to the RTP
     * timestamp <tt>rtpTime</tt> for the SSRC <tt>ssrc</tt>.
     *
     * Returns -1 if the local time cannot be found (for example because not
     * enough information for the SSRC has been previously provided to the
     * <tt>Synchronizer</tt>).
     *
     * @param ssrc the SSRC with which <tt>rtpTime</tt> is associated.
     * @param rtpTime the RTP timestamp
     * @return the local time corresponding to <tt>rtpTime</tt> for SSRC
     * <tt>ssrc</tt> if it can be calculated, and -1 otherwise.
     */
    public long getLocalTime(long ssrc, long rtpTime);
}


