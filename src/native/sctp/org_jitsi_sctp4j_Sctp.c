#include "org_jitsi_sctp4j_Sctp.h"
#include <usrsctp.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>

// errno returned after connect call on success
#define SCTP_EINPROGRESS EINPROGRESS

// Name of the class that contains callback methods.
#define SCTP_CLASS "org/jitsi/sctp4j/Sctp"

// Struct used to identify Sctp sockets
struct sctp_socket
{
    // Socket object created by SCTP stack
    struct socket *sock;

    int localPort;
};

// Java Virtual Machine instance
JavaVM* jvm;

int callOnSctpOutboundPacket( void*   socketPtr, void*   data,
                               size_t  length,    uint8_t tos,
                               uint8_t set_df )
{
    JNIEnv* jniEnv;

    int hadToAttach = 0;

    int getEnvStat;

    jclass sctpClass;
    jmethodID outboundCallback;

    jlong sctpPtr;
    jbyteArray jBuff;
    jint jtos;
    jint jset_df;

    jint result;

    getEnvStat = (*jvm)->GetEnv(jvm, (void **)&jniEnv, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) 
    {
        hadToAttach = 1;

        if ((*jvm)->AttachCurrentThread(jvm, (void **) &jniEnv, NULL) != 0)
        {
          printf("Failed to attach new thread\n");
          return -1;
        }
    }
    else if (getEnvStat == JNI_EVERSION)
    {
        printf("GetEnv: version not supported\n");
        return -1;
    }
    else if (getEnvStat == JNI_OK)
    {
        // OK
    }

    sctpClass = (*jniEnv)->FindClass(jniEnv, SCTP_CLASS);
    if(!sctpClass)
    {
        printf("Failed to get SCTP class\n");
        return -1;
    }


    outboundCallback = (*jniEnv)->GetStaticMethodID(
        jniEnv, sctpClass, "onSctpOutboundPacket", "(J[BII)I");

    if(!outboundCallback)
    {
        printf("Failed to get onSctpOutboundPacket method\n");
        return -1;
    }

    sctpPtr = (jlong)(long)socketPtr;

    jBuff = (*jniEnv)->NewByteArray(jniEnv, length);
    (*jniEnv)->SetByteArrayRegion(jniEnv, jBuff, 0, length, (jbyte*) data);

    jtos = (jint)tos;

    jset_df = (jint)set_df;

    result = (jint)(*jniEnv)->CallStaticIntMethod(
        jniEnv, sctpClass, outboundCallback, sctpPtr, jBuff, jtos, jset_df);

    // FIXME: not sure if jBuff should be released
    // Release byte array
    //(*jniEnv)->ReleaseByteArrayElements(jniEnv, jBuff, packetDataPtr,
      //  JNI_ABORT/*free the buffer without copying back the possible changes */);
    (*jniEnv)->DeleteLocalRef(jniEnv, jBuff);

    if(hadToAttach)
    {
        if ((*jvm)->DetachCurrentThread(jvm) != 0)
        {
            printf("Failed to deattach the thread\n");
        }
    }

    return (int)result;
}

void callOnSctpInboundPacket( void*    socketPtr, void*    data,
                              size_t   length,    uint16_t sid,
                              uint16_t ssn,       uint16_t tsn,
                              uint32_t ppid,      uint16_t context,
                              int      flags )
{
    JNIEnv* jniEnv;
    
    int hadToAttach = 0;

    int getEnvStat;
    
    jclass sctpClass;
    jmethodID inboundCallback;

    jlong sctpPtr;

    jbyteArray jBuff;

    jint jsid;
    jint jssn;
    jint jtsn;
    jlong jppid;
    jint jcontext;

    getEnvStat = (*jvm)->GetEnv(jvm, (void **)&jniEnv, JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED)
    {
        hadToAttach = 1;

        if ((*jvm)->AttachCurrentThread(jvm, (void **) &jniEnv, NULL) != 0)
        {
            printf("Failed to attach new thread\n");
            return;
        }
    }
    else if (getEnvStat == JNI_EVERSION) 
    {
        printf("GetEnv: version not supported\n");
        return;
    }
    else if (getEnvStat == JNI_OK) 
    {
        // OK
    }

    sctpClass = (*jniEnv)->FindClass(jniEnv, SCTP_CLASS);
    if(!sctpClass)
    {
        printf("Failed to get SCTP class\n");
        return;
    }

    inboundCallback = (*jniEnv)->GetStaticMethodID(
        jniEnv, sctpClass, "onSctpInboundPacket", "(J[BIIIJII)V");

    if(!inboundCallback)
    {
        printf("Failed to get onSctpInboundPacket method\n");
        return;
    }

    sctpPtr = (jlong)(long)socketPtr;

    jBuff = (*jniEnv)->NewByteArray(jniEnv, length);
    (*jniEnv)->SetByteArrayRegion(jniEnv, jBuff, 0, length, (jbyte*) data);

    jsid = (jint)sid;
    jssn = (jint)ssn;
    jtsn = (jint)tsn;
    jppid = (jlong)ntohl(ppid);
    jcontext = (jint)context;

    (*jniEnv)->CallStaticVoidMethod(
        jniEnv, sctpClass, inboundCallback, sctpPtr, jBuff, jsid, jssn, jtsn,
        jppid, jcontext, (jint)flags);

    // Release byte array
    //(*jniEnv)->ReleaseByteArrayElements(jniEnv, jBuff, packetDataPtr,
      //  JNI_ABORT/*free the buffer without copying back the possible changes */);
    (*jniEnv)->DeleteLocalRef(jniEnv, jBuff);

    if(hadToAttach)
    {
        if ((*jvm)->DetachCurrentThread(jvm) != 0)
        {
            printf("Failed to deattach the thread\n");
        }
    }
}

static int onSctpOutboundPacket(void*   addr, void*     data, size_t  length,
                                uint8_t tos,  uint8_t set_df )
{
    if(data && length)
    {
        if(callOnSctpOutboundPacket(addr, data, length, tos, set_df) == 0)
        {
            return 0;
        }
    }

    //FIXME: not sure about this value, but an error for now
    return -1;
}

void debugSctpPrintf(const char *format, ...)
{
    va_list ap;

    va_start(ap, format);
    vprintf(format, ap);
    va_end(ap);
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_init
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1init
  (JNIEnv *env, jclass class, jint port)
{
    int status = (*env)->GetJavaVM(env, &jvm);
    if(status != 0)
    {
        return JNI_FALSE;
    }

    // First argument is udp_encapsulation_port, which is not releveant for our
    // AF_CONN use of sctp.
    usrsctp_init((int)port, onSctpOutboundPacket, debugSctpPrintf);

#ifdef SCTP_DEBUG
    usrsctp_sysctl_set_sctp_debug_on(SCTP_DEBUG_ALL);
#endif

    // TODO(ldixon): Consider turning this on/off.
    usrsctp_sysctl_set_sctp_ecn_enable(0);
    
    //usrsctp_sysctl_set_sctp_blackhole(2);
    
    //usrsctp_sysctl_set_sctp_nr_outgoing_streams_default(32);

    return JNI_TRUE;
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    on_network_in
 * Signature: (J[BII)V
 */
JNIEXPORT void JNICALL Java_org_jitsi_sctp4j_Sctp_on_1network_1in
  (JNIEnv     *env,         jclass class,  jlong ptr,
   jbyteArray jbytesPacket, jint   offset, jint  len)
{
    struct sctp_socket* sock;
    jbyte* packetDataPtr;
    
    sock = (struct sctp_socket*)(long)ptr;
    
    packetDataPtr = (*env)->GetByteArrayElements(
        env, jbytesPacket, JNI_FALSE/* not a copy */);

    packetDataPtr += offset;

    usrsctp_conninput(sock, (char*)packetDataPtr, len, 0);

    (*env)->ReleaseByteArrayElements(env, jbytesPacket, packetDataPtr,
        JNI_ABORT/*free the buffer without copying back the possible changes */);
}

// This is the callback called from usrsctp when data has been received, after
// a packet has been interpreted and parsed by usrsctp and found to contain
// payload data. It is called by a usrsctp thread. It is assumed this function
// will free the memory used by 'data'.
int onSctpInboundPacket(struct socket* sock, union sctp_sockstore addr,
                        void*  data, size_t length, struct sctp_rcvinfo rcv,
                        int flags, void* ulp_info)
{
    if(data)
    {
        if (flags & MSG_NOTIFICATION)
        {
            callOnSctpInboundPacket(
                ulp_info, data, length,
                0,  0, 0, 0, 0, flags );
        }
        else
        {
            // Pass packet data to Java
            callOnSctpInboundPacket(
                ulp_info,
                data,         length,
                rcv.rcv_sid,  rcv.rcv_ssn,     rcv.rcv_tsn,
                rcv.rcv_ppid, rcv.rcv_context, flags );
        }
        free(data);
    }
    return (1);
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_send
 * Signature: (J[BIIZII)I
 */
JNIEXPORT jint JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1send
  (JNIEnv   *env,    jclass     class,
   jlong    ptr,     jbyteArray jdata, jint  offset, jint len,
   jboolean ordered, jint       sid,   jint  ppid )
{
    struct sctp_socket* sctpSocket;
    struct sctp_sndinfo sndinfo;

    // data using SCTP.
    ssize_t send_res = 0;  // result from usrsctp_sendv.
    //struct sctp_sendv_spa spa;

    jbyte* dataPtr;

    sctpSocket = (struct sctp_socket*)((long)ptr);    

    //memset(&spa, 0, sizeof(struct sctp_sendv_spa));
    //spa.sendv_flags |= SCTP_SEND_SNDINFO_VALID;
    //spa.sendv_sndinfo.snd_sid = sid;//params.ssrc;
    //spa.sendv_sndinfo.snd_ppid = htonl(ppid);//talk_base::HostToNetwork32(GetPpid(params.type));

    // Ordered implies reliable.
    //if (ordered != JNI_TRUE)
    //{
    //    spa.sendv_sndinfo.snd_flags |= SCTP_UNORDERED;
        /*if (params.max_rtx_count >= 0 || params.max_rtx_ms == 0)
        {
            spa.sendv_flags |= SCTP_SEND_PRINFO_VALID;
            spa.sendv_prinfo.pr_policy = SCTP_PR_SCTP_RTX;
            spa.sendv_prinfo.pr_value = params.max_rtx_count;
        }
        else
        {
            spa.sendv_flags |= SCTP_SEND_PRINFO_VALID;
            spa.sendv_prinfo.pr_policy = SCTP_PR_SCTP_TTL;
            spa.sendv_prinfo.pr_value = params.max_rtx_ms;
        }*/
    //}

    dataPtr = (*env)->GetByteArrayElements(env, jdata, JNI_FALSE/* not a copy */);

    dataPtr += offset;

    // We don't fragment.
    //send_res = usrsctp_sendv(sctpSocket->sock, dataPtr, dataLength,
      //                       NULL, 0, &spa, sizeof(spa),
        //                     SCTP_SENDV_SPA, 0);


    sndinfo.snd_sid = sid;
    sndinfo.snd_flags = 0;//8;
    sndinfo.snd_ppid = htonl(ppid);
    sndinfo.snd_context = 0;
    sndinfo.snd_assoc_id = 0;
    if(ordered != JNI_TRUE)
    {
        sndinfo.snd_flags |= SCTP_UNORDERED;
    }

    send_res = usrsctp_sendv(
        sctpSocket->sock, dataPtr, len, NULL, 0, (void *)&sndinfo,
        (socklen_t)sizeof(struct sctp_sndinfo), SCTP_SENDV_SNDINFO, 0);

    /*send_res = usrsctp_sendv(
        sctpSocket->sock, dataPtr, dataLength, 
        NULL, 0, NULL, 0, SCTP_SENDV_NOINFO, 0);*/

    (*env)->ReleaseByteArrayElements(env, jdata, dataPtr, 
        JNI_ABORT/*free the buffer without copying back the possible changes */);                             

    if (send_res < 0) 
    {
        perror("Sctp send error: ");
    }

    return (jint)send_res;
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usersctp_socket
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_org_jitsi_sctp4j_Sctp_usersctp_1socket
  (JNIEnv *env, jclass class, jint localPort)
{
    struct sctp_socket* sctpSocket = malloc(sizeof(struct sctp_socket));
    struct socket *sock;
    struct linger linger_opt;
    struct sctp_assoc_value stream_rst;
    uint32_t nodelay = 1;
    size_t i;

    int event_types[] = {SCTP_ASSOC_CHANGE,
                         SCTP_PEER_ADDR_CHANGE,
                         SCTP_SEND_FAILED_EVENT,
                         SCTP_SENDER_DRY_EVENT,
                         SCTP_STREAM_RESET_EVENT};
    struct sctp_event event;

    // Register this class as an address for usrsctp. This is used by SCTP to
    // direct the packets received (by the created socket) to this class.
    usrsctp_register_address((void*)sctpSocket);

    sock = usrsctp_socket( AF_CONN, SOCK_STREAM, IPPROTO_SCTP,
                           onSctpInboundPacket,  NULL, 0, (void*)sctpSocket);
    if (sock == NULL)
    {
        perror("userspace_socket");
        free(sctpSocket);
        return 0;
    }

    // Make the socket non-blocking. Connect, close, shutdown etc will not block
    // the thread waiting for the socket operation to complete.
    if (usrsctp_set_non_blocking(sock, 1) < 0)
    {
        perror("Failed to set SCTP to non blocking.");
        free(sctpSocket);
        return 0;
    }

    // This ensures that the usrsctp close call deletes the association. This
    // prevents usrsctp from calling OnSctpOutboundPacket with references to
    // this class as the address.
    linger_opt.l_onoff = 1;
    linger_opt.l_linger = 0;
    if (usrsctp_setsockopt(sock, SOL_SOCKET, SO_LINGER, &linger_opt,
                           sizeof(linger_opt)))
    {
        perror("Failed to set SO_LINGER.");
        free(sctpSocket);
        return 0;
    }

    // Enable stream ID resets.
    stream_rst.assoc_id = SCTP_ALL_ASSOC;
    stream_rst.assoc_value = 1;
    if (usrsctp_setsockopt(sock, IPPROTO_SCTP, SCTP_ENABLE_STREAM_RESET,
                           &stream_rst, sizeof(stream_rst)))
    {
        perror("Failed to set SCTP_ENABLE_STREAM_RESET.");
        free(sctpSocket);
        return 0;
    }

    // Nagle.
    if (usrsctp_setsockopt(sock, IPPROTO_SCTP, SCTP_NODELAY, &nodelay,
                           sizeof(nodelay)))
    {
        perror("Failed to set SCTP_NODELAY.");
        free(sctpSocket);
        return 0;
    }

    // Subscribe to SCTP event notifications.
    memset(&event, 0, sizeof(struct sctp_event));
    event.se_assoc_id = SCTP_ALL_ASSOC;
    event.se_on = 1;
    for (i = 0; i < 5; i++)
    {
        event.se_type = event_types[i];
        if (usrsctp_setsockopt(sock, IPPROTO_SCTP, SCTP_EVENT, &event,
                               sizeof(event)) < 0)
        {
            printf("Failed to set SCTP_EVENT type: %i\n", event.se_type);
            free(sctpSocket);
            return 0;
        }
    }

    sctpSocket->sock = sock;
    sctpSocket->localPort = (int)localPort;

    return (jlong)((long)sctpSocket);
}

struct sockaddr_conn getSctpSockAddr(int port, void* adr)
{
    struct sockaddr_conn sconn;
    memset(&sconn, 0, sizeof(struct sockaddr_conn));
    sconn.sconn_family = AF_CONN;
#ifdef HAVE_SCONN_LEN
    sconn.sconn_len = sizeof(struct sockaddr_conn);
#endif
    sconn.sconn_port = htons(port);
    sconn.sconn_addr = adr;
    return sconn;
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_listen
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1listen
  (JNIEnv *env, jclass class, jlong ptr)
{
    struct sctp_socket* sctpSocket;
    struct sockaddr_conn sconn;

    sctpSocket = (struct sctp_socket*)((long)ptr);    

    sconn = getSctpSockAddr(sctpSocket->localPort, (void*)sctpSocket);

    /* Bind server socket */
    if (usrsctp_bind( sctpSocket->sock,
                      (struct sockaddr *) &sconn,
                      sizeof(struct sockaddr_conn)) < 0)
    {
        perror("usrsctp_bind");
    }

    /* Make server side passive... */
    if (usrsctp_listen(sctpSocket->sock, 1) < 0)
    {
        perror("usrsctp_listen");
    }
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_accept
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1accept
  (JNIEnv *env, jclass class, jlong ptr)
{
    struct sctp_socket* sctpSocket;
    struct socket* acceptedSocket;

    sctpSocket = (struct sctp_socket*)((long)ptr);

    if((acceptedSocket = usrsctp_accept(sctpSocket->sock, NULL, NULL))
            == NULL)
    {
        //perror("usrsctp_accept");
        return JNI_FALSE;
    }
    usrsctp_close(sctpSocket->sock);
    sctpSocket->sock = acceptedSocket;
    return JNI_TRUE;
}

int connectSctp(struct sctp_socket *sctp_socket, int remotePort)
{
    struct socket* sock;
    struct sockaddr_conn sconn;
    int connect_result;

    sock = sctp_socket->sock;

    sconn = getSctpSockAddr(sctp_socket->localPort, (void*)sctp_socket);

    if (usrsctp_bind( sock,
                      (struct sockaddr *)&sconn,
                      sizeof(struct sockaddr_conn)) < 0)
    {
        perror("usrsctp_bind");
        return 0;
    }

    sconn = getSctpSockAddr(remotePort, (void*)sctp_socket);
    connect_result = usrsctp_connect(
        sock, (struct sockaddr *)&sconn, sizeof(struct sockaddr_conn));

    if (connect_result < 0 && errno != SCTP_EINPROGRESS)
    {
        perror("usrsctp_connect");
        return 0;
    }
    return 1;
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_connect
 * Signature: (JI)Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1connect
  (JNIEnv *env, jclass class, jlong ptr, jint remotePort)
{
    struct sctp_socket* sctpSocket;

    sctpSocket = (struct sctp_socket*)((void*)ptr);
    // Try connecting the socket
    if(connectSctp(sctpSocket, (int)remotePort))
    {
        return JNI_TRUE;
    }
    else
    {
        return JNI_FALSE;
    }
}

void closeSocket(struct sctp_socket* sctp)
{
    usrsctp_close(sctp->sock);
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1close
  (JNIEnv *env, jclass class, jlong ptr)
{
    struct sctp_socket* sctp;
    sctp = (struct sctp_socket*)((long)ptr);

    closeSocket(sctp);

    free(sctp);
}

/*
 * Class:     org_jitsi_sctp4j_Sctp
 * Method:    usrsctp_finish
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_jitsi_sctp4j_Sctp_usrsctp_1finish
  (JNIEnv *env, jclass class)
{
    if(usrsctp_finish() != 0)
    {
        return JNI_TRUE;
    }
    else 
    {
        return JNI_FALSE;
    }
}

