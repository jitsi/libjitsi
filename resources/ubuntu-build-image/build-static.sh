#!/bin/bash

export GIT_COMMITTER_EMAIL="jitsi-jenkins@jitsi.org"
export GIT_COMMITTER_NAME="libjitsi `basename $0`"

ARCH=$1
JAVA_VERSION=$2
LIBROOT=$3
case "$ARCH" in
  "x86")
    VCPKG_ARCH="x86"
    JAVA_ARCH="i386"
    export CFLAGS="-m32 -msse -msse2"
    export CXXFLAGS="-m32 -msse -msse2"
    ;;
  "x86-64"|"x86_64"|"amd64")
    VCPKG_ARCH="x64"
    JAVA_ARCH="amd64"
    export CFLAGS="-m64 -msse -msse2"
    export CXXFLAGS="-m64 -msse -msse2"
    ;;
  "arm64"|"aarch64")
    #for libvpx
    export CROSS="aarch64-linux-gnu-"
    VCPKG_ARCH="arm64"
    JAVA_ARCH="arm64"
    TOOLCHAIN=$LIBROOT/src/native/cmake/toolchains/arm64-linux.cmake
    ;;
  "ppc64el"|"ppc64le")
    ARCH="ppc64el"
    VCPKG_ARCH="ppc64le"
    JAVA_ARCH="ppc64el"
    TOOLCHAIN=$LIBROOT/src/native/cmake/toolchains/ppc64el-linux.cmake
    ;;
esac

export JAVA_HOME=/usr/lib/jvm/java-$JAVA_VERSION-openjdk-$JAVA_ARCH

cd "$LIBROOT/src/native" || exit 1

# For ppc64el, vcpkg cross-compilation is broken, use system libraries
if [ "$ARCH" = "ppc64el" ]; then
  USE_SYSTEM_LIBS=ON
  # When using system libs, we need to use the toolchain directly instead of vcpkg
  cmake -B cmake-build-$ARCH \
    -DCMAKE_TOOLCHAIN_FILE=$TOOLCHAIN \
    -DCMAKE_BUILD_TYPE=release \
    -DCMAKE_C_FLAGS="$CFLAGS" \
    -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
    -DUSE_SYSTEM_OPUS=ON \
    -DUSE_SYSTEM_SPEEX=ON \
    -DUSE_SYSTEM_USRSCTP=OFF \
    -DUSE_SYSTEM_VPX=ON
else
  cmake -B cmake-build-$ARCH \
    -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=$TOOLCHAIN \
    -DVCPKG_VERBOSE=ON \
    -DVCPKG_TARGET_TRIPLET=$VCPKG_ARCH-linux \
    -DVCPKG_BUILD_TYPE=release \
    -DCMAKE_BUILD_TYPE=release \
    -DCMAKE_C_FLAGS="$CFLAGS" \
    -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
    -DUSE_SYSTEM_OPUS=OFF \
    -DUSE_SYSTEM_SPEEX=OFF \
    -DUSE_SYSTEM_USRSCTP=OFF \
    -DUSE_SYSTEM_VPX=OFF
fi

cmake --build cmake-build-$ARCH --config Release --target install --parallel
