git config --global user.email "jitsi-jenkins@jitsi.org"
git config --global user.name "Jitsi GitHub Action"

ARCH=$1
LIBROOT=$2
case "$ARCH" in
  "x86")
    VCPKG_ARCH="x86"
    JAVA_ARCH="i386"
    CMAKE_ARCH="m32"
  "x86-64")
    VCPKG_ARCH="x64"
    JAVA_ARCH="amd64"
    CMAKE_ARCH="m64"
  "arm64")
    VCPKG_ARCH="arm64"
    JAVA_ARCH="arm64"
    CMAKE_ARCH="arm64"
  "ppc64le")
    VCPKG_ARCH="ppc64le"
    JAVA_ARCH="ppc64le"
    CMAKE_ARCH="ppc64le"
esac

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-$JAVA_ARCH
export CFLAGS="-$CMAKE_ARCH -fPIC"
export CXXFLAGS="-$CMAKE_ARCH -fPIC"

cd "$LIBROOT/src/native" || exit 1
cp x86-linux.cmake vcpkg/triplets
cmake -B cmake-build-$CMAKE_ARCH \
  -DVCPKG_VERBOSE=ON \
  -DCMAKE_C_FLAGS="$CFLAGS" \
  -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
  -DVCPKG_TARGET_TRIPLET=$VCPKG_ARCH-linux \
  -DUSE_SYSTEM_OPUS=OFF \
  -DUSE_SYSTEM_SPEEX=OFF \
  -DUSE_SYSTEM_USRSCTP=OFF \
  -DUSE_SYSTEM_VPX=OFF

cmake --build cmake-build-$CMAKE_ARCH --config Release --target install --parallel
