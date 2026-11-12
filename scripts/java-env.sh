#!/bin/sh
# Shared Java toolchain discovery. Source this after setting TOGETL_ROOT.
set -eu

if [ -z "${TOGETL_ROOT:-}" ]; then
    echo "java-env.sh: TOGETL_ROOT must be set before sourcing" >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/javac" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
        echo "java-env.sh: JAVA_HOME is set but does not contain executable bin/java and bin/javac: $JAVA_HOME" >&2
        exit 1
    fi
fi

if [ -n "${JAVAC:-}" ]; then
    :
elif [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
elif [ -x "$TOGETL_ROOT/.toolchain/jdk8/bin/javac" ]; then
    JAVAC="$TOGETL_ROOT/.toolchain/jdk8/bin/javac"
else
    JAVAC=javac
fi

if [ -n "${JAVA:-}" ]; then
    :
elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA="$JAVA_HOME/bin/java"
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
