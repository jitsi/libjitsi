#!/usr/bin/env bash
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <JAVA_HOME> <ARCH>"
    echo "  JAVA_HOME: Path to Java installation"
    echo "  ARCH: Architecture to build for (x86_64 or arm64)"
    exit 1
fi;

JAVA_HOME=$1
ARCH=$2
PROJECT_DIR="$(cd "$(dirname "$0")"; pwd -P)/../"

case $ARCH in
    "x86-64"|"x86_64")
        INSTALL_PREFIX_ARCH=x86-64
        OSX_ARCH=x86_64
        VCPKG_ARCH=x64
        ;;
    "arm64"|"aarch64")
        INSTALL_PREFIX_ARCH=aarch64
        OSX_ARCH=arm64
        VCPKG_ARCH=arm64
        ;;
esac

cmake -B "${PROJECT_DIR}/src/native/cmake-build-${OSX_ARCH}" \
    -S "${PROJECT_DIR}/src/native" \
    -DJAVA_HOME="$JAVA_HOME" \
    -DCMAKE_INSTALL_PREFIX="${PROJECT_DIR}/src/main/resources/darwin-$INSTALL_PREFIX_ARCH" \
    -DCMAKE_OSX_ARCHITECTURES="$OSX_ARCH" \
    -DVCPKG_TARGET_TRIPLET="$VCPKG_ARCH-osx"
cmake --build "${PROJECT_DIR}/src/native/cmake-build-${OSX_ARCH}" --config Release --target install --parallel
