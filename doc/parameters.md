# MediaServiceImpl

##### net.java.sip.communicator.service.media.DISABLE\_AUDIO\_SUPPORT=*boolean*

Indicates whether the detection of audio CaptureDevices is to be disabled. The default value is false i.e. the detection of the audio CaptureDevices is enabled.

##### net.java.sip.communicator.impl.neomedia.audiosystem.DISABLED=*boolean*

Indicates whether the method DeviceConfiguration#setAudioSystem(AudioSystem, boolean) is to be considered disabled for the user i.e. the user is not presented with user interface which allows selecting a particular AudioSystem

##### net.java.sip.communicator.service.media.DISABLE\_VIDEO\_SUPPORT=*boolean*

Indicates whether the detection of video CaptureDevices is to be disabled. The default value is false i.e. the detection of the video CaptureDevices is enabled.

##### net.java.sip.communicator.impl.neomedia.dynamicPayloadTypePreferences

The prefix of the property names the values of which specify the dynamic payload type preferences.

# ActiveSpeakerDetector

##### org.jitsi.impl.neomedia.ActiveSpeakerDetectorImpl.implClassName=*String*

Specifies the class name of the algorithm implementation for the detection/identification of the active/dominant speaker in a multipoint conference to be used by ActiveSpeakerDetectorImpl. The default value is null. If the specified value is null or the initialization of an instance of the specified class fails, ActiveSpeakerDetectorImpl falls back to a list of well-known algorithm implementations.

# JNIEncoder

##### net.java.sip.communicator.impl.neomedia.codec.video.h264.defaultProfile=*String*

Specifies the H.264 (encoding) profile to be used in the absence of negotiation. Though it seems that RFC 3984 "RTP Payload Format for H.264 Video" specifies the baseline profile as the default, we have till the time of this writing defaulted to the main profile and we do not currently want to change from the main to the base profile unless we really have to.

##### org.jitsi.impl.neomedia.codec.video.h264.defaultIntraRefresh=*boolean*

Specifies whether Periodic Intra Refresh is to be used by default. The default value is true. The value may be overridden by #setAdditionalCodecSettings(Map).

##### org.jitsi.impl.neomedia.codec.video.h264.keyint=*int*

Specifies the maximum GOP (group of pictures) size i.e. the maximum interval between keyframes. FFmpeg calls it gop\_size, x264 refers to it as keyint or i\_keyint\_max.

##### org.jitsi.impl.neomedia.codec.video.h264.preset=*String*
    
Specifies the x264 preset to be used by JNIEncoder. A preset is a collection of x264 options that will provide a certain encoding speed to compression ratio. A slower preset will provide better compression i.e.

# RecorderRtpImpl

##### org.jitsi.impl.neomedia.recording.RecorderRtpImpl.PERFORM\_ASD=*boolean*

Controls whether the recorder should perform active speaker detection.

# OutputDataStreamImpl

##### org.jitsi.impl.neomedia.rtp.translator.RTPTranslatorImpl.removeRTPHeaderExtensions=*boolean*

Indicates whether the RTP header extension(s) are to be removed from received RTP packets prior to relaying them. The default value is false.

# DtlsControllImpl

##### org.jitsi.impl.neomedia.transform.dtls.DtlsControlImpl.verifyAndValidateCertificate=*boolean*

Specifies whether {@code DtlsControlImpl is to tear down the media session if the fingerprint does not match the hashed certificate. The default value is {@code true.

# DtlsPacketTransformer

##### org.jitsi.impl.neomedia.transform.dtls.DtlsPacketTransformer.dropUnencryptedPkts=*boolean*

Indicates whether unencrypted packets sent or received through DtlsPacketTransformer are to be dropped. The default value is false.

# AES

##### org.jitsi.impl.neomedia.transform.srtp.AES.factoryClassName=*String*

Specifies the name of the class to instantiate as a BlockCipherFactory implementation to be used by the class AES to initialize BlockCiphers.

# SRTPCryptoContext

##### org.jitsi.impl.neomedia.transform.srtp.SRTPCryptoContext.checkReplay=*boolean*

Indicates whether protection against replay attacks is to be activated. The default value is true.

# FECReceiver

##### org.jitsi.impl.neomedia.transform.fec.AbstractFECReceiver.FEC\_BUFF\_SIZE=*int*

Specifies the value of #FEC\_BUFF\_SIZE.

##### org.jitsi.impl.neomedia.transform.fec.AbstractFECReceiver.MEDIA\_BUFF\_SIZE=*int*

Specifies the value of #MEDIA\_BUFF\_SIZE.

# KeyFrameRequester

##### net.java.sip.communicator.impl.neomedia.codec.video.h264.preferredKeyFrameRequester=*rtcp|signaling*

Specifies the preferred KeyFrameRequester to be used.

# AudioMediaStream

##### org.jitsi.service.neomedia.AudioMediaStream.DISABLE\_DTMF\_HANDLING=*boolean*

Controls whether handling of RFC4733 DTMF packets should be disabled or enabled. If disabled, packets will not be processed or dropped (regardless of whether there is a payload type number registered for the telephone-event format).

# VideoMediaStream

##### org.jitsi.service.neomedia.VideoMediaStream.REQUEST\_RETRANSMISSIONS=*boolean*

Controls whether VideoMediaStream should request retransmissions for lost RTP packets using RTCP NACK.

# MaxPacketsPerMillisPolicy

##### org.jitsi.impl.neomedia.MaxPacketsPerMillisPolicy.PACKET\_QUEUE\_CAPACITY=*int*
