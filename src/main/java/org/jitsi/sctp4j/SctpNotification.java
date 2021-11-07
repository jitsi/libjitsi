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
package org.jitsi.sctp4j;

import java.nio.*;

/**
 * Partially implemented SCTP notifications for which the native wrapper
 * currently registers for.
 *
 * @author Pawel Domas
 */
public class SctpNotification
{
    /********  Notifications  **************/
    /*
        union sctp_notification {
            struct sctp_tlv {
                uint16_t sn_type;
                uint16_t sn_flags;
                uint32_t sn_length;
            } sn_header;
            struct sctp_assoc_change sn_assoc_change;
            struct sctp_paddr_change sn_paddr_change;
            struct sctp_remote_error sn_remote_error;
            struct sctp_shutdown_event sn_shutdown_event;
            struct sctp_adaptation_event sn_adaptation_event;
            struct sctp_pdapi_event sn_pdapi_event;
            struct sctp_authkey_event sn_auth_event;
            struct sctp_sender_dry_event sn_sender_dry_event;
            struct sctp_send_failed_event sn_send_failed_event;
            struct sctp_stream_reset_event sn_strreset_event;
            struct sctp_assoc_reset_event  sn_assocreset_event;
            struct sctp_stream_change_event sn_strchange_event;
        };
    */

    /* notification types */

    public static final int SCTP_ASSOC_CHANGE                = 0x0001;
    public static final int SCTP_PEER_ADDR_CHANGE            = 0x0002;
    public static final int SCTP_REMOTE_ERROR                = 0x0003;
    public static final int SCTP_SEND_FAILED                 = 0x0004;
    public static final int SCTP_SHUTDOWN_EVENT              = 0x0005;
    public static final int SCTP_ADAPTATION_INDICATION       = 0x0006;
    public static final int SCTP_PARTIAL_DELIVERY_EVENT      = 0x0007;
    public static final int SCTP_AUTHENTICATION_EVENT        = 0x0008;
    public static final int SCTP_STREAM_RESET_EVENT          = 0x0009;
    /**
     * When the SCTP implementation has no user data anymore to send or
     * retransmit this notification is given to the user.
     */
    public static final int SCTP_SENDER_DRY_EVENT            = 0x000a;
    public static final int SCTP_NOTIFICATIONS_STOPPED_EVENT = 0x000b;
    public static final int SCTP_ASSOC_RESET_EVENT           = 0x000c;
    public static final int SCTP_STREAM_CHANGE_EVENT         = 0x000d;
    public static final int SCTP_SEND_FAILED_EVENT           = 0x000e;

    public final int sn_type;
    public final int sn_flags;
    public final int sn_length;

    protected final ByteBuffer buffer;

    private SctpNotification(byte[] data)
    {
        // FIXME: unsigned types
        this.buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        this.sn_type = buffer.getChar();
        this.sn_flags = buffer.getChar();
        this.sn_length = buffer.getInt();
    }

    @Override
    public String toString()
    {
        switch (sn_type)
        {
            case SCTP_ASSOC_CHANGE:
                return "SCTP_ASSOC_CHANGE";
            case SCTP_PEER_ADDR_CHANGE:
                return "SCTP_PEER_ADDR_CHANGE";
            case SCTP_REMOTE_ERROR:
                return "SCTP_REMOTE_ERROR";
            case SCTP_SEND_FAILED:
                return "SCTP_SEND_FAILED";
            case SCTP_SHUTDOWN_EVENT:
                return "SCTP_SHUTDOWN_EVENT";
            case SCTP_ADAPTATION_INDICATION:
                return "SCTP_ADAPTATION_INDICATION";
            case SCTP_PARTIAL_DELIVERY_EVENT:
                return "SCTP_PARTIAL_DELIVERY_EVENT";
            case SCTP_AUTHENTICATION_EVENT:
                return "SCTP_AUTHENTICATION_EVENT";
            case SCTP_STREAM_RESET_EVENT:
                return "SCTP_STREAM_RESET_EVENT";
            case SCTP_SENDER_DRY_EVENT:
                return "SCTP_SENDER_DRY_EVENT";
            case SCTP_NOTIFICATIONS_STOPPED_EVENT:
                return "SCTP_NOTIFICATIONS_STOPPED_EVENT";
            case SCTP_ASSOC_RESET_EVENT:
                return "SCTP_ASSOC_RESET_EVENT";
            case SCTP_STREAM_CHANGE_EVENT:
                return "SCTP_STREAM_CHANGE_EVENT";
            case SCTP_SEND_FAILED_EVENT:
                return "SCTP_SEND_FAILED_EVENT";
        }
        return "SCTP_NOTIFICATION_0x" + Integer.toHexString(sn_type);
    }

    public static SctpNotification parse(byte[] data)
    {
        int type = (data[1] & 0xFF) << 8 | (data[0] & 0xFF);
        switch (type)
        {
            case SCTP_ASSOC_CHANGE:
                return new AssociationChange(data);
            case SCTP_PEER_ADDR_CHANGE:
                return new PeerAddressChange(data);
            case SCTP_SEND_FAILED:
                return new SendFailed(data);
            case SCTP_SENDER_DRY_EVENT:
                return new SenderDry(data);
            case SCTP_STREAM_RESET_EVENT:
                return new StreamReset(data);
            default:
                return new SctpNotification(data);
        }
    }

    /**
     * Association change event
     * struct sctp_assoc_change {
     *     uint16_t sac_type;
     *     uint16_t sac_flags;
     *     uint32_t sac_length;
     *     uint16_t sac_state;
     *     uint16_t sac_error;
     *     uint16_t sac_outbound_streams;
     *     uint16_t sac_inbound_streams;
     *     sctp_assoc_t sac_assoc_id; //uint32_t
     *     uint8_t sac_info[]; // not available yet
     * };
     */
    public static class AssociationChange
        extends SctpNotification
    {
        /* sac_state values */
        public static final int SCTP_COMM_UP        = 0x0001;
        public static final int SCTP_COMM_LOST      = 0x0002;
        public static final int SCTP_RESTART        = 0x0003;
        public static final int SCTP_SHUTDOWN_COMP  = 0x0004;
        public static final int SCTP_CANT_STR_ASSOC = 0x0005;

        /* sac_info values */
        public static final int SCTP_ASSOC_SUPPORTS_PR        = 0x01;
        public static final int SCTP_ASSOC_SUPPORTS_AUTH      = 0x02;
        public static final int SCTP_ASSOC_SUPPORTS_ASCONF    = 0x03;
        public static final int SCTP_ASSOC_SUPPORTS_MULTIBUF  = 0x04;
        public static final int SCTP_ASSOC_SUPPORTS_RE_CONFIG = 0x05;
        public static final int SCTP_ASSOC_SUPPORTS_MAX       = 0x05;

        public final int state;

        public final int error;

        public final int outboundStreams;

        public final int inboundStreams;

        public final long assocId;

        private AssociationChange(byte[] data)
        {
            super(data);
            // FIXME: UINT types
            this.state = buffer.getChar();
            this.error = buffer.getChar();
            this.outboundStreams = buffer.getChar();
            this.inboundStreams = buffer.getChar();
            this.assocId = buffer.getInt();
        }

        @Override
        public String toString()
        {
            String str = super.toString();

            str += ":assocId:0x" + Long.toHexString(assocId);

            // type
            switch (state)
            {
                case SCTP_COMM_UP:
                    str += ",COMM_UP";
                    break;
                case SCTP_COMM_LOST:
                    str += ",COMM_LOST";
                    break;
                case SCTP_RESTART:
                    str += ",RESTART";
                    break;
                case SCTP_SHUTDOWN_COMP:
                    str += ",SHUTDOWN_COMP";
                    break;
                case SCTP_CANT_STR_ASSOC:
                    str += ",CANT_STR_ASSOC";
                    break;
                default:
                    str += ",0x" + Integer.toHexString(state);
                    break;
            }
            // in/out streams supported
            str += ",(in/out)(" + inboundStreams
                + "/" + outboundStreams +")";
            // error
            str += ",err0x" + Integer.toHexString(error);

            return str;
        }
    }

    /**
     * Address event
     *   struct sctp_paddr_change {
     *       uint16_t spc_type;
     *       uint16_t spc_flags;
     *       uint32_t spc_length;
     *       struct sockaddr_storage spc_aaddr;
     *       uint32_t spc_state;
     *       uint32_t spc_error;
     *       sctp_assoc_t spc_assoc_id; //uint32_t
     *       uint8_t spc_padding[4];
     *   };
     *
     */
    public static class PeerAddressChange
        extends SctpNotification
    {
        /* paddr state values */
        public static final int SCTP_ADDR_AVAILABLE   = 0x0001;
        public static final int SCTP_ADDR_UNREACHABLE = 0x0002;
        public static final int SCTP_ADDR_REMOVED     = 0x0003;
        public static final int SCTP_ADDR_ADDED       = 0x0004;
        public static final int SCTP_ADDR_MADE_PRIM   = 0x0005;
        public static final int SCTP_ADDR_CONFIRMED   = 0x0006;

        public final int state;

        public final long error;

        public final long assocId;

        private PeerAddressChange(byte[] data)
        {
            super(data);

            // Skip struct sockaddr_storage
            int sockAddrStorageLen = data.length - 24;
            buffer.position(buffer.position() + sockAddrStorageLen);

            this.state = buffer.getInt();
            this.error = buffer.getInt();
            this.assocId = buffer.getInt();
        }

        @Override
        public String toString()
        {
            String base = super.toString();

            base += ",assocId:0x" + Long.toHexString(assocId);

            switch (state)
            {
                case SCTP_ADDR_AVAILABLE:
                    base += ",ADDR_AVAILABLE";
                    break;
                case SCTP_ADDR_UNREACHABLE:
                    base += ",ADDR_UNREACHABLE";
                    break;
                case SCTP_ADDR_REMOVED:
                    base += ",ADDR_REMOVED";
                    break;
                case SCTP_ADDR_ADDED:
                    base += ",ADDR_ADDED";
                    break;
                case SCTP_ADDR_MADE_PRIM:
                    base += ",ADDR_MADE_PRIM";
                    break;
                case SCTP_ADDR_CONFIRMED:
                    base += ",ADDR_CONFIRMED";
                    break;
                default:
                    base += "," + Integer.toHexString(state);
                    break;
            }

            // Error
            base += ",err:" + Long.toHexString(error);

            return base;
        }
    }

    /**
     * SCTP send failed event
     *
     * struct sctp_send_failed_event {
     *     uint16_t ssfe_type;
     *     uint16_t ssfe_flags;
     *     uint32_t ssfe_length;
     *     uint32_t ssfe_error;
     *     struct sctp_sndinfo ssfe_info;
     *     sctp_assoc_t ssfe_assoc_id;
     *     uint8_t  ssfe_data[];
     * };
     *
     * struct sctp_sndinfo {
     *     uint16_t snd_sid;
     *     uint16_t snd_flags;
     *     uint32_t snd_ppid;
     *     uint32_t snd_context;
     *     sctp_assoc_t snd_assoc_id; // uint32
     * };
     *
     */
    public static class SendFailed
        extends SctpNotification
    {
        /* flag that indicates state of data */

        /**
         * Inqueue never on wire.
         */
        public static final int SCTP_DATA_UNSENT = 0x0001;

        /**
         * On wire at failure.
         */
        public static final int SCTP_DATA_SENT = 0x0002;

        public final long error;

        private SendFailed(byte[] data)
        {
            super(data);

            this.error = buffer.getInt();
        }

        @Override
        public String toString()
        {
            String base = super.toString();

            if((sn_flags & SCTP_DATA_SENT) > 0)
                base += ",DATA_SENT";

            if((sn_flags & SCTP_DATA_UNSENT) > 0)
                base += ",DATA_UNSENT";

            // error
            base += ",err0x" + Long.toHexString(error);

            return base;
        }
    }

    /**
     * SCTP sender dry event
     *
     * struct sctp_sender_dry_event {
     *     uint16_t sender_dry_type;
     *     uint16_t sender_dry_flags;
     *     uint32_t sender_dry_length;
     *     sctp_assoc_t sender_dry_assoc_id;
     * };
     */
    public static class SenderDry
        extends SctpNotification
    {
        private final long assocId;

        private SenderDry(byte[] data)
        {
            super(data);

            this.assocId = buffer.getInt();
        }

        @Override
        public String toString()
        {
            String base = super.toString();

            base += ",assocID:0x" + Long.toHexString(assocId);

            return base;
        }
    }

    /**
     * Stream reset event
     *
     * struct sctp_stream_reset_event {
     *     uint16_t strreset_type;
     *     uint16_t strreset_flags;
     *     uint32_t strreset_length;
     *     sctp_assoc_t strreset_assoc_id;
     *     uint16_t strreset_stream_list[];
     * };
     */
    public static class StreamReset
        extends SctpNotification
    {
        /* flags in stream_reset_event (strreset_flags) */
        public static final int SCTP_STREAM_RESET_INCOMING_SSN = 0x0001;
        public static final int SCTP_STREAM_RESET_OUTGOING_SSN = 0x0002;
        public static final int SCTP_STREAM_RESET_DENIED       = 0x0004;
        public static final int SCTP_STREAM_RESET_FAILED       = 0x0008;
        public static final int SCTP_STREAM_CHANGED_DENIED     = 0x0010;

        public static final int SCTP_STREAM_RESET_INCOMING     = 0x00000001;
        public static final int SCTP_STREAM_RESET_OUTGOING     = 0x00000002;

        private StreamReset(byte[] data)
        {
            super(data);
        }
    }
}
