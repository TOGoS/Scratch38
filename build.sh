#!/bin/sh
set -eu

TOGETL_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export TOGETL_ROOT
. "$TOGETL_ROOT/scripts/java-env.sh"

cd "$TOGETL_ROOT"
mkdir -p out/classes

find src/main/java -name '*.java' | sort > out/main-sources.txt

"$JAVAC" $JAVAC_FLAGS -encoding UTF-8 -Xlint:all -cp "lib/*" \
    -d out/classes @out/main-sources.txt

echo "Built out/classes"
