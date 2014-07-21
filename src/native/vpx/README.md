# Building libjnvpx

## Debian

### Install libvpx
```
apt-get install libvpx-dev
```

### Clone the libvpx repo, because of the libmkv code
```
git clone https://chromium.googlesource.com/webm/libvpx
```

### Install a jdk and set the JAVA_HOME variable
```
apt-get install default-jdk
export JAVA_HOME=/usr/lib/jvm/default-java/
```

### Build the libjitsi code with the libvpx-debian ant target
```
ant libvpx -Dlibmkv=/path/to/libvpx/third_party/libmkv
```


## Non-debian

### Build libvpx

#### Get the code
```
git clone https://chromium.googlesource.com/webm/libvpx
```

#### Configure:

##### Mac
```
./configure --enable-pic --disable-examples --disable-docs --enable-vp8
    --disable-vp9 --enable-error-concealment --enable-realtime-only
    --enable-static --disable-shared --disable-unit-tests
    --target=universal-darwin10-gcc
```

If there are errors while linking on linux x86_64 try to add --extra-cflags='-fvisibility=protected'

#### Make:
```
make
```

### Build the libjitsi code with the libvpx ant target
Run the 'libvpx' ant target from the 'libjitsi/' directory, setting the 'libvpx'
property. 

```
libjitsi/ $ ant libvpx -Dlibvpx=/path/to/libvpx/sources
```

### Alternatively, you can build manually with something like (on mac)
```
gcc -Wall -fPIC -O2 -arch i386 -arch x86_64 \
-I/Applications/Xcode.app//Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/System/Library/Frameworks/JavaVM.framework/Versions/A/Headers/ \
-I/Users/boris/jitsi/src/libvpx/ \
-I/Users/boris/jitsi/src/libvpx/third_party/ \
org_jitsi_impl_neomedia_codec_video_VPX.c \
org_jitsi_impl_neomedia_recording_WebmWriter.cc \ 
/Users/boris/jitsi/src/libvpx/third_party/libmkv/EbmlWriter.c \
-shared -o libjnvpx.jnilib /Users/boris/jitsi/src/libvpx/libvpx.a -lstdc++
```
