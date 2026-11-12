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
- Generic TEF dialect: captures document metadata, entries, headers, comments,
  directives, and parse issues without dropping source data, and reports the
  entry types and fields that a domain dialect still needs to map.
- Import command: `togetl import` writes deterministic, self-contained streams.
- Parts and namespaces dialects, vocabulary reporting, SQLite loading, and the
  PHP browser are added in later milestones.

## Usage

```sh
./togetl version

./togetl import --input fixtures/upstream/3DPrintingData.tef \
    --output out/3dprinting-data.togetltsv \
    --report out/3dprinting-data-unmapped.tsv
```

`import` defaults to the `generic` dialect and `warn` tolerance. Local files
are identified by their absolute `file:` URL unless `--source-url` overrides it.
