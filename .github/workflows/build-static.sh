git config --global user.email "jitsi-jenkins@jitsi.org"
git config --global user.name "Jitsi GitHub Action"

ARCH=$1
DIR=$2
VCPKG_ARCH="x64"
JAVA_ARCH="amd64"
if [ "$ARCH" == "m32" ]; then
  VCPKG_ARCH="x86"
  JAVA_ARCH="i386"
fi

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-$JAVA_ARCH
export CFLAGS=-$ARCH
export CXXFLAGS=-$ARCH

cd $DIR/src/native || exit 1
cmake -B cmake-build-$ARCH \
  -DCMAKE_C_FLAGS=-$ARCH \
  -DCMAKE_CXX_FLAGS=-$ARCH \
  -DVCPKG_TARGET_TRIPLET=$VCPKG_ARCH-linux \
  -DUSE_SYSTEM_OPUS=OFF \
  -DUSE_SYSTEM_SPEEX=OFF \
  -DUSE_SYSTEM_USRSCTP=OFF \
  -DUSE_SYSTEM_VPX=OFF

cmake --build cmake-build-$ARCH --config Release -t install
