#!/bin/sh
# Fetch the upstream source documents used for manual and integration checks.
# The files are gitignored; tests do not depend on them.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST="$ROOT/fixtures/upstream"

fetch() {
    name=$1
    url=$2
    sha256=$3
    path="$DEST/$name"

    mkdir -p "$DEST"
    if [ -f "$path" ]; then
        echo "$sha256  $path" | sha256sum -c -
        return
    fi

    echo "Downloading $url"
    curl -fL --retry 3 -o "$path" "$url"
    echo "$sha256  $path" | sha256sum -c -
}

fetch 3DPrintingData.tef \
    "https://gitlab.com/TOGoS/ProjectNotes2/-/raw/master/2023/3DPrinting/3DPrintingData.tef" \
    "719415b3f1ee02153977ccfcbd57e6bdd30e5b63cdfd650cfead5899bb68ed8a"

fetch 3DPrintingLog.org \
    "https://gitlab.com/TOGoS/ProjectNotes2/-/raw/master/2023/3DPrinting/3DPrintingLog.org" \
    "91ab52e1a4381ab564551067db2cb93c76ea5aaf4465ad49bbaafdaf1f88534d"

fetch namespaces.tef \
    "https://www.nuke24.net/docs/ns/namespaces.tef" \
    "6a03102a911a94f07160eeabd0e8cd843ad07166f004b7ab42c12a8adb8d9214"

fetch music.txt \
    "https://www.nuke24.net/music/music.txt" \
    "a690b646a1e765ef84ad8ad10840987ad8c0ea84d89593e53489b3990230c51a"

echo "Fixtures are present in fixtures/upstream"
