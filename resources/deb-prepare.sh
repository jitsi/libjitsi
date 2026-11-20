#!/usr/bin/env bash
set -e
set -x
sudo apt-get update
sudo apt-get install -y distro-info

# dev-tools from backports because https://bugs.launchpad.net/ubuntu/+source/ubuntu-dev-tools/+bug/1916633
DEVTOOLS_PACKAGE=ubuntu-dev-tools
UBUNTUTOOLS_PACKAGE=python3-ubuntutools
DISTRIBUTOR=$(lsb_release -i -s)
CODENAME=$(lsb_release -c -s)
if [ "$DISTRIBUTOR" = "Ubuntu" ]; then
  case $CODENAME in
    bionic | focal)
      DEVTOOLS_PACKAGE=$DEVTOOLS_PACKAGE/$CODENAME-backports
      UBUNTUTOOLS_PACKAGE=$UBUNTUTOOLS_PACKAGE/$CODENAME-backports
      ;;
  esac
elif [ "$DISTRIBUTOR" = "Debian" ]; then
  case $CODENAME in
    stretch | buster)
      >&2 echo "Packaging needs 'ubuntu-dev-tools' >= 1.181, which is only available in Bullseye"
      exit 1
      ;;
  esac
fi

sudo apt-get install -y \
  debhelper \
  sbuild \
  schroot \
  apt-utils \
  aptitude \
  qemu-user-static \
  binfmt-support \
  "$DEVTOOLS_PACKAGE" \
  "$UBUNTUTOOLS_PACKAGE" \
  debian-archive-keyring \
  git-buildpackage \
  rename

sudo gpasswd -r sbuild
sudo usermod -a -G sbuild "$(whoami)"
