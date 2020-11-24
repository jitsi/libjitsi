git config --global user.email "jitsi-jenkins@jitsi.org"
git config --global user.name "Jitsi GitHub Action"

ARCH=$1
LIBROOT=$2
VCPKG_ARCH="x64"
JAVA_ARCH="amd64"
if [ "$ARCH" == "m32" ]; then
  VCPKG_ARCH="x86"
  JAVA_ARCH="i386"
fi

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-$JAVA_ARCH
export CFLAGS="-$ARCH -fPIC"
export CXXFLAGS="-$ARCH -fPIC"

cd "$LIBROOT/src/native" || exit 1
cp x86-linux.cmake vcpkg/triplets
cmake -B cmake-build-$ARCH \
  -DVCPKG_VERBOSE=ON \
  -DCMAKE_C_FLAGS="$CFLAGS" \
  -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
  -DVCPKG_TARGET_TRIPLET=$VCPKG_ARCH-linux \
  -DUSE_SYSTEM_OPUS=OFF \
  -DUSE_SYSTEM_SPEEX=OFF \
  -DUSE_SYSTEM_USRSCTP=OFF \
  -DUSE_SYSTEM_VPX=OFF

cmake --build cmake-build-$ARCH --config Release --target install --parallel
