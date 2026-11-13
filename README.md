# TOGETL (TOGoS's Extract, Transform, Load system)

Tools for converting TEF, Org, and music source documents into TOGETLTSV 202601
statement streams, loading them into SQLite, and browsing the result read-only.

## Architecture

Source documents (TEF, Org, music) are parsed into TOGETLTSV 202601 statement
streams (a line-oriented RDF-like format: `source location subject predicate object`),
which are then loaded into a SQLite database and browsed read-only via a PHP viewer.

## Current status

Implemented:
- TEF reader and generic TEF dialect (source metadata, entries, headers, comments, directives, parse issues)
- Declarative mapping dialects (`parts`, `namespaces`) driven by TSV tables under `mappings/`
- TOGETLTSV 202601 writer and reader, with alias handling and round-trip tests
- SQLite loader (`togetl load`) with source-agnostic schema, deterministic batch/loading IRIs
- Read-only PHP browser in `web/`
- Vocabulary reporting (`togetl vocab`)

Not yet implemented:
- Org importer (an Org parser library with tests exists, but no domain mapping to TOGETLTSV)
- Music importer
- Batch ontology formalization
- TEF parser should be refactored to stream input (similar to Org reader)

## Build

```sh
./scripts/install-deps.sh --distro debian   # or --distro alpine; see SETUP.md
./scripts/get-jdk.sh          # only if no JDK 8 on PATH or in JAVA_HOME
./scripts/get-sqlite-jdbc.sh
./scripts/fetch-fixtures.sh   # optional upstream sample documents
./build.sh
./test/run-tests.sh
```

See [SETUP.md](SETUP.md) for required packages, the Java selection order, and
distro-specific notes.

The build targets Java 8 and does not require Maven.

## Status

- TOGETLTSV 202601 writer and reader, with alias handling and round-trip tests.
- TEF reader: BOM handling, strict UTF-8 validation, source spans, comments,
  directives, tolerance levels, and recovery for orphan continuations.
- Generic TEF dialect: emits source-document metadata, entries, headers, comments,
  directives, and parse issues; reports unmapped entry types and fields.
  Functional fields report conflicts instead of silently choosing a value;
  identical duplicates are deduplicated and multi-valued fields keep all
  values.
- Declarative mapping dialects driven by TSV tables under `mappings/`:
  - `parts` for `3DPrintingData.tef`
  - `namespaces` for `namespaces.tef` and `vocab/*.tef`
- Vocabulary reporting: `togetl vocab` compares terms used by mappings and
  streams against `vocab/*.tef`.
- Import command: `togetl import` writes deterministic, self-contained streams.
- SQLite loader: `togetl load` reads TOGETLTSV streams into a source-agnostic
  schema with document, batch, loading, statement, parse-issue, report, and
  vocabulary tables. Batch and loading IRIs are deterministic, and re-running
  the same load replaces that batch instead of duplicating statements.
- Read-only PHP browser in `web/` with overview, subject, predicate, document,
  vocabulary, issue, and part pages.
- Org and music importers are added in later milestones.

## Usage

```sh
./togetl version

# Parts data with the domain mapping.
./togetl import --dialect parts \
    --input fixtures/upstream/3DPrintingData.tef \
    --output out/3dparts-data.togetltsv \
    --report out/3dparts-data-report.tsv

# Shared namespace vocabulary.
./togetl import --dialect namespaces \
    --input fixtures/upstream/namespaces.tef \
    --output out/namespaces.togetltsv

# Check mapping terms against vocabulary definitions.
./togetl vocab --report out/vocab-report.tsv

# Load the streams into SQLite.
./togetl load --db out/togetl.sqlite3 \
    --input out/3dparts-data.togetltsv \
    --report out/3dparts-data-report.tsv \
    --input out/namespaces.togetltsv

# Browse read-only.
TOGETL_DB=out/togetl.sqlite3 php -S localhost:8080 web/router.php
```

`import` defaults to the `generic` dialect and `warn` tolerance. Local files
are identified by their absolute `file:` URL unless `--source-url` overrides it.

Mapping tables live in `mappings/*.tsv` and are selected by name. Each table has
an `#entry` section mapping entry types to subject templates and RDF types, and a
`#field` section mapping headers to predicates, value parsers, and cardinality.
Rules with `entryType` `*` apply unless a more specific rule exists.

`vocab` exits non-zero when a mapping references an undefined term. Its report
has one row per term with `ok`, `undefined`, `proposed`, `unused`, `duplicate`,
`deprecated`, or `deprecated-in-use` status.
