FROM ubuntu:bionic

COPY sources.list /etc/apt/

RUN dpkg --add-architecture i386 \
    && dpkg --add-architecture arm64 \
    && dpkg --add-architecture ppc64el

RUN apt-get update && \
    apt-get install -y \
     build-essential \
     curl \
     g++-multilib \
     gcc-multilib \
     git \
     libasound-dev \
     libasound-dev:i386 \
     libasound-dev:arm64 \
     libasound-dev:ppc64el \
     libpulse-dev \
     libpulse-dev:i386 \
     libpulse-dev:arm64 \
     libpulse-dev:ppc64el \
     libx11-dev \
     libx11-dev:i386 \
     libx11-dev:arm64 \
     libx11-dev:ppc64el \
     libxext-dev \
     libxext-dev:i386 \
     libxext-dev:arm64 \
     libxext-dev:ppc64el \
     libxv-dev \
     libxv-dev:i386 \
     libxv-dev:arm64 \
     libxv-dev:ppc64el \
     openjdk-8-jdk \
     openjdk-8-jdk:i386 \
     openjdk-8-jdk:arm64 \
     openjdk-8-jdk:ppc64el \
     unzip \
     nasm \
     && \
    rm -rf /var/lib/apt/lists/*

ADD https://github.com/Kitware/CMake/releases/download/v3.18.4/cmake-3.18.4-Linux-x86_64.sh /opt/cmake.sh
RUN chmod +x /opt/cmake.sh && /opt/cmake.sh --skip-license --prefix=/usr --exclude-subdir
