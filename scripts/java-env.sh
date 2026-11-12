#!/bin/sh
# Shared Java toolchain discovery. Source this after setting TOGETL_ROOT.
set -eu

if [ -z "${TOGETL_ROOT:-}" ]; then
    echo "java-env.sh: TOGETL_ROOT must be set before sourcing" >&2
    exit 1
fi

if [ -n "${JAVAC:-}" ]; then
    :
elif [ -x "$TOGETL_ROOT/.toolchain/jdk8/bin/javac" ]; then
    JAVAC="$TOGETL_ROOT/.toolchain/jdk8/bin/javac"
else
    JAVAC=javac
fi

if [ -n "${JAVA:-}" ]; then
    :
elif [ -x "$TOGETL_ROOT/.toolchain/jdk8/bin/java" ]; then
    JAVA="$TOGETL_ROOT/.toolchain/jdk8/bin/java"
else
    JAVA=java
fi

JAVAC_VERSION=$("$JAVAC" -version 2>&1 | sed -n 's/^javac \([0-9][0-9.]*\).*/\1/p')
case "$JAVAC_VERSION" in
    1.8*) JAVAC_FLAGS="-source 1.8 -target 1.8" ;;
    *)    JAVAC_FLAGS="--release 8" ;;
esac

export TOGETL_ROOT JAVAC JAVA JAVAC_FLAGS
