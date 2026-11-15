#!/bin/sh
set -eu

TOGETL_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export TOGETL_ROOT
. "$TOGETL_ROOT/scripts/java-env.sh"

cd "$TOGETL_ROOT"
"$TOGETL_ROOT/build.sh"

mkdir -p out/test-classes
find src/test/java -name '*.java' | sort > out/test-sources.txt

"$JAVAC" $JAVAC_FLAGS -encoding UTF-8 -Xlint:all -cp "out/classes:lib/*" \
    -d out/test-classes @out/test-sources.txt

# Extra arguments are passed to TestMain, e.g. --include=file-tests,memory-tests
exec "$JAVA" -cp "out/classes:out/test-classes:lib/*" net.nuke24.togetl.test.TestMain "$@"
