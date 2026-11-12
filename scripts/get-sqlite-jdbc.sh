#!/bin/sh
# Pinned SQLite JDBC driver.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION="3.46.1.3"
JAR="sqlite-jdbc-$VERSION.jar"
URL="https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$VERSION/$JAR"
SHA256="4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa"

mkdir -p "$ROOT/lib"
if [ -f "$ROOT/lib/$JAR" ]; then
    echo "$SHA256  $ROOT/lib/$JAR" | sha256sum -c -
    echo "SQLite JDBC already present: lib/$JAR"
    exit 0
fi

echo "Downloading $URL"
curl -fL --retry 3 -o "$ROOT/lib/$JAR" "$URL"
echo "$SHA256  $ROOT/lib/$JAR" | sha256sum -c -
echo "Installed lib/$JAR"
