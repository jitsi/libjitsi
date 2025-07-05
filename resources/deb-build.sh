#!/usr/bin/env bash
if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <VERSION> <DIST> <ARCH> <GPG_ID>"
    echo "  VERSION: Source package version, e.g. 2.14.123-gcaffee"
    echo "  DIST:    Debian/Ubuntu distribution name (e.g. focal or bullseye)"
    echo "  ARCH:    Architecture (e.g. amd64, aarch64)"
    echo "  GPG_ID:  id for package signing"
    exit 1
fi;

set -e
set -x
VERSION=$1
DIST=$2
ARCH=$3
GPG_ID=$4
PROJECT_DIR="$(realpath "$(dirname "$0")/../")"
cd "${PROJECT_DIR}" || exit
# export for sbuildrc sourcing
export BUILD_DIR=${PROJECT_DIR}/target/debian/${DIST}
mkdir -p "${BUILD_DIR}"

# use tmpfs for sbuild
sudo tee -a /etc/fstab < "${PROJECT_DIR}/resources/sbuild-tmpfs"

if [[ "${ARCH}" != "amd64" ]]; then
  mk-sbuild "${DIST}" --target "${ARCH}" --type=file --debootstrap-include=ca-certificates || sbuild-update -udc "${DIST}"-amd64-"${ARCH}"

  # union-type= is not valid for type=file, remove to prevent warnings
  sudo sed -i s/union-type=.*//g "/etc/schroot/chroot.d/sbuild-${DIST}-amd64-${ARCH}"
else
  if debian-distro-info --all | grep -Fqxi "${DIST}"; then
    export DEBOOTSTRAP_MIRROR=${DEBOOTSTRAP_MIRROR:-$UBUNTUTOOLS_DEBIAN_MIRROR}
  elif ubuntu-distro-info --all | grep -Fqxi "${DIST}"; then
    export DEBOOTSTRAP_MIRROR=${DEBOOTSTRAP_MIRROR:-$UBUNTUTOOLS_UBUNTU_MIRROR}
  fi
  mk-sbuild "${DIST}" --type=file --debootstrap-include=ca-certificates || sbuild-update -udc "${DIST}"-amd64

  # union-type= is not valid for type=file, remove to prevent warnings
  sudo sed -i s/union-type=.*//g "/etc/schroot/chroot.d/sbuild-${DIST}-amd64"
fi

mvn -B versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false
"${PROJECT_DIR}/resources/deb-gen-source.sh" "${VERSION}" "${DIST}"
export SBUILD_CONFIG="${PROJECT_DIR}/resources/sbuildrc"
if [[ "${ARCH}" != "amd64" ]]; then
  sbuild --dist "${DIST}" --no-arch-all --host "${ARCH}" "${PROJECT_DIR}"/../libjitsi_*.dsc
else
  sbuild --dist "${DIST}" --arch-all "${PROJECT_DIR}"/../libjitsi_*.dsc
  cp "${PROJECT_DIR}"/../libjitsi_* "$BUILD_DIR"
fi

debsign -S -e"${GPG_ID}" "${BUILD_DIR}"/*.changes --re-sign -p"${PROJECT_DIR}"/resources/gpg-wrap.sh

#make build files readable for Windows and archivable for GitHub Actions
rename 's|:|-|g' "$BUILD_DIR"/*.build
