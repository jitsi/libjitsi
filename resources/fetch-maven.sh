#!/usr/bin/env bash
if ! command -v unzip &> /dev/null; then
    echo "$0 requires unzip"
fi;

VER=3.3.0
PROJECT_DIR="$(realpath "$(dirname "$0")/../")"
EXTRACT_DEST="$PROJECT_DIR/target/latest-maven"

mkdir -p "$EXTRACT_DEST"
mvn org.apache.maven.plugins:maven-dependency-plugin:$VER:copy \
    -Dartifact=org.jitsi:libjitsi:LATEST:jar \
    -DoutputDirectory="$EXTRACT_DEST"

unzip -o "$EXTRACT_DEST/*.jar" "linux-*" "darwin-*" "win32-*" -d "$EXTRACT_DEST"
mkdir -p "$PROJECT_DIR/src/main/resources"
cp -r "$EXTRACT_DEST/"{darwin,linux,win32}-* "$PROJECT_DIR/src/main/resources" || true
