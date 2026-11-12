# TOGETL ETL

Tools for converting TEF, Org, and music source documents into TOGETLTSV 202601
statement streams, loading them into SQLite, and browsing the result read-only.

See [PLAN.md](PLAN.md) for the full design.

## Build

```sh
./scripts/get-jdk.sh          # only if no JDK 8 on PATH
./scripts/get-sqlite-jdbc.sh
./scripts/fetch-fixtures.sh   # optional upstream sample documents
./build.sh
./test/run-tests.sh
```

`build.sh` prefers `.toolchain/jdk8` when present, otherwise `javac` on `PATH`.
The build targets Java 8 and does not require Maven.

## Status

- TOGETLTSV 202601 writer and reader, with alias handling and round-trip tests.
- TEF reader: BOM handling, strict UTF-8 validation, source spans, comments,
  directives, tolerance levels, and recovery for orphan continuations and
  malformed header lines.
- Import commands, vocabulary reporting, SQLite loading, and the PHP browser are
  added in later milestones.

## Usage

```sh
./togetl version
```

Import and database commands are added in later milestones.
