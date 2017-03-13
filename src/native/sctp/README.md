# Build Instructions

## Building the usrsctp static lib
1. Checkout usrsctp source:
  * `cd src/native/sctp`
  * `git clone https://github.com/sctplab/usrsctp.git`
2. Build usrsctp
  * `cd src/native/sctp/usrsctp`
  * `./configure --with-pic`
  * `make`
  
## Building the java jni wrapper lib
1. From `libjitsi/` run `ant -v -lib ~/.m2/repository/org/apache/maven/maven-ant-tasks/2.1.3 sctp -Dusrsctp=<LOCATION_OF_SCTP_DIR>`
2. When setting the location of the sctp dir, it's easiest to use an absolute path, as otherwise it will be taken relative to a directoy a few levels below.  NOTE: If you don't pass your sctp dir in correctly, the build will end up using the system usrsctp lib (if there is one)
3. A new libjnsctp.so will be put in `libjitsi/lib/native/<your_arch>`

## Cross-compiling for 32 bit
Couple changes to the above steps when compiling a 32 bit library on a 64 bit linux machine:

1. When running configure as part of building usrsctp, do: `./configure --with-pic CFLAGS="$CFLAGS -m32"`
2. When building the java jni wrapper lib, do: `ant -lib ~/.m2/repository/org/apache/maven/maven-ant-tasks/2.1.3 sctp -Dusrsctp=/home/brian/src/usrsctp/ -Darch=32`
