#!/bin/bash
ARCH=$1
JAVA_VERSION=$2

PACKAGES=()
case "$ARCH" in
  "x86")
    DEBARCH=i386
    ;;
  "x86-64")
    DEBARCH=amd64
    ;;
  "arm64")
    DEBARCH=arm64
    GNUARCH=aarch64
    ;;
  "ppc64el")
    DEBARCH=ppc64el
    GNUARCH=powerpc64le
    ;;
esac

dpkg --add-architecture $DEBARCH

if [[ "$GNUARCH" == "" ]]; then
    PACKAGES+=(g++-multilib gcc-multilib)
else
    PACKAGES+=("libgcc-7-dev:$DEBARCH" "g++-$GNUARCH-linux-gnu" "gcc-$GNUARCH-linux-gnu")
fi;

PACKAGES+=(
     build-essential \
     autoconf \
     automake \
     libtool \
     curl \
     git \
     zip \
     unzip \
     nasm \
     g++ \
     gcc \
     "libasound2-dev:$DEBARCH" \
     "libpulse-dev:$DEBARCH" \
     "libx11-dev:$DEBARCH" \
     "libxext-dev:$DEBARCH" \
     "libxt-dev:$DEBARCH" \
     "libxv-dev:$DEBARCH" \
     "openjdk-$JAVA_VERSION-jdk:$DEBARCH")

DEBIAN_FRONTEND=noninteractive apt-get update && \
 apt-get install --no-install-recommends -y "${PACKAGES[@]}" && \
 rm -rf /var/lib/apt/lists/*
