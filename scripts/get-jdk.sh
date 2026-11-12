#!/bin/sh
# Pinned JDK 8 toolchain. Adoptium Temurin 8u512-b09, linux x64.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION="8u512-b09"
ARCHIVE="OpenJDK8U-jdk_x64_linux_hotspot_8u512b09.tar.gz"
URL="https://github.com/adoptium/temurin8-binaries/releases/download/jdk${VERSION}/${ARCHIVE}"
SHA256="fb3bfa869012419b562bb4235d0d83ec4f8c1dda62ae432ba20c6c3a7e43f7e4"

DEST="$ROOT/.toolchain"

if [ -x "$DEST/jdk8/bin/javac" ]; then
    echo "JDK already present: $DEST/jdk8"
    exit 0
fi

mkdir -p "$DEST"
TMP="$DEST/$ARCHIVE"

echo "Downloading $URL"
curl -fL --retry 3 -o "$TMP" "$URL"
echo "$SHA256  $TMP" | sha256sum -c -
tar -xzf "$TMP" -C "$DEST"
rm -f "$TMP"
ln -sfn "jdk$VERSION" "$DEST/jdk8"

"$DEST/jdk8/bin/java" -version
