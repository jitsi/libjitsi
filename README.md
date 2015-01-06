libjitsi Secure audio/video communication for Java applications
===============================================================

`libjitsi` is an advanced Java media library for secure real-time audio/video communication.
It allows applications to capture, playback, stream, encode/decode and encrypt audio and video flows.
It also allows for advanced features such as audio mixing, handling multiple streams, participation in audio and video conferences.
Originally `libjitsi` was part of the [Jitsi](https://jitsi.org/) client source code but we decided to spin it off so that other projects can also use it.
`libjitsi` is distributed under the terms of the [LGPL](http://www.gnu.org/licenses/lgpl.html).

Features
--------

* Video capture and rendering on Windows, Mac OS X and Linux.
* Video codecs: H.264 and H.263 (VP8 coming in early 2013)
* Audio codecs: Opus, SILK, G.722, Speex, ilbc, G.711 (PCMU, PCMA), G.729 (get your [licences](http://sipro.com/) first though).
* Security: SRTP (with ZRTP or SDES)
* RTP DTMF ([RFC 2833](http://tools.ietf.org/html/rfc2833)/[RFC 4733](http://tools.ietf.org/html/rfc4733))
* RTP audio levels ([RFC 6465](http://tools.ietf.org/html/rfc6465))
* … and all other [media features in Jitsi](https://jitsi.org/features)

Examples and API
----------------

`libjitsi` contains some basic examples that can get you started. You can check them out here:

* [AVReceive2.java](./src/org/jitsi/examples/AVReceive2.java)
* [AVTransmit2.java](./src/org/jitsi/examples/AVTransmit2.java)

Ultimately, though, you can always use Jitsi’s source code as a reference to libjitsi’s full potential.

You can also peruse the [javadocs](http://bluejimp.com/jitsi/libjitsi/javadoc/) `libjitsi’s` API.

Links
-----

* [GSoC 2014, Part 1 : ice4j tutorial](http://blog.sharedmemory.fr/en/2014/06/22/gsoc-2014-ice4j-tutorial/)
* [GSoC 2014, Part 2 : Libjitsi tutorial with ice4j](http://blog.sharedmemory.fr/en/2014/07/07/gsoc-2014-libjitsi-tutorial/)
* [Java Bells: A Jingle implementation for Java based on LibJitsi, Ice4J and Smack](https://github.com/bejayoharen/java-bells)
* [Video Conferencing Project in Java Source Code](http://matrixsust.blogspot.com/2013/07/video-conferencing-project-in-java.html)

Mailing list
------------

Despite being now technically separate, `libjitsi` is still very much part of the Jitsi community.
Technical discussions and questions about `libjitsi` are hence most welcome on the [Jitsi dev mailing list](https://jitsi.org/Development/MailingLists#dev).


Acknowledgements
----------------

`libjitsi` heavily relies on libraries such as [FMJ](http://fmj-sf.net/), [FFmpeg](http://ffmpeg.org/), [Speex](http://www.speex.org/), and others.
The `libjitsi` spin-off project would not have been possible without the support of the [NLnet Foundation](http://nlnet.nl/).

We are also grateful to [Qomtec](http://qomtec.com/) for their support in the project.
Of course, it goes without saying, that all the media features that were part of Jitsi have been implemented with the help of our [Partners](https://jitsi.org/Main/Partners) and [Contributors](https://jitsi.org/Development/TeamAndContributors).

-- the Jitsi development team

