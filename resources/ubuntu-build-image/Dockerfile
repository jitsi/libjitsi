FROM ubuntu:bionic

ARG ARCH=x86-64
ARG JAVA_VERSION=11

ADD https://github.com/Kitware/CMake/releases/download/v3.23.1/cmake-3.23.1-Linux-x86_64.sh /opt/cmake.sh
RUN chmod +x /opt/cmake.sh && /opt/cmake.sh --skip-license --prefix=/usr --exclude-subdir

COPY ports-sources.list /etc/apt/
RUN if [ "$ARCH" != "x86" ] && [ "$ARCH" != "x86-64" ]; then cp /etc/apt/ports-sources.list /etc/apt/sources.list; fi

COPY packages.sh /opt/
RUN /opt/packages.sh $ARCH $JAVA_VERSION
