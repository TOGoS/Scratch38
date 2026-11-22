# TOGETL (TOGoS's Extract, Transform, Load system)

Tools for converting TEF, Org, and music source documents into TOGETLTSV 202601
statement streams, loading them into SQLite, and browsing the result read-only.

## Architecture

Source documents (TEF, Org, music) are parsed into TOGETLTSV 202601 statement
streams (a line-oriented RDF-like format: `source location subject predicate object`),
which are then loaded into a SQLite database and browsed read-only via a PHP viewer.

## Current status

Implemented:
- TEF reader (streams its input; syntax errors are fatal) and generic TEF dialect (source metadata, entries, headers, comments, directives)
- Declarative mapping dialects (`parts`, `namespaces`) driven by TSV tables under `mappings/`
- TOGETLTSV 202601 writer and reader, with alias handling and round-trip tests
- SQLite loader (`togetl load`) with source-agnostic schema, deterministic batch/loading IRIs
- Read-only PHP browser in `web/`
- Vocabulary reporting (`togetl vocab`)

Not yet implemented:
- Org importer (an Org parser library with tests exists, but no domain mapping to TOGETLTSV)
- Music importer
- Batch ontology formalization

## Build

```sh
./scripts/install-deps.sh --distro debian   # or --distro alpine; see SETUP.md
./scripts/get-jdk.sh          # only if no JDK 8 on PATH or in JAVA_HOME
make
```

`make` builds `togetl`, runs the tests, fetches the SQLite JDBC jar if it is
missing, and produces `out/thedb.sqlite3`.
`make run-web-server` then serves the database read-only.
See the [Makefile](Makefile) for the individual targets.

See [SETUP.md](SETUP.md) for required packages, the Java selection order, and
distro-specific notes.

The build targets Java 8 and does not require Maven.

## Usage

`make` runs the whole pipeline from the demo documents in `fixtures/demo/`.
The outputs are named after the source they are derived from, so
`out/3DPrintingData.togetltsv` comes from `fixtures/demo/3DPrintingData.tef`.
Aggregates get their own names.

The commands behind the Makefile rules are:

```sh
./togetl version

# Parts data with the domain mapping.
./togetl import --dialect=parts \
    --derive-source-uri=bitprint \
    --input=fixtures/demo/3DPrintingData.tef \
    --output=out/3DPrintingData.togetltsv \
    --report=out/3DPrintingData.report.tsv

# Shared namespace vocabulary.
./togetl import --dialect=namespaces \
    --derive-source-uri=bitprint \
    --input=fixtures/demo/namespaces.tef \
    --output=out/namespaces.togetltsv

# Check mapping terms against vocabulary definitions.
./togetl vocab --input=out/3DPrintingData.togetltsv \
    --input=out/namespaces.togetltsv \
    --report=out/vocab-report.tsv

# Load the streams into SQLite.
./togetl load --db=out/thedb.sqlite3 \
    --input=out/3DPrintingData.togetltsv \
    --report=out/3DPrintingData.report.tsv \
    --input=out/namespaces.togetltsv

# Browse read-only.
TOGETL_DB=out/thedb.sqlite3 php -S localhost:8080 web/router.php
```

`import` defaults to the `generic` dialect. Every import needs a source URI,
given as exactly one of:

- `--derive-source-uri=bitprint`: the source URI is the bitprint of the content.
  The input is digested before it is parsed.
- `--source-uri=<uri>`: the source URI is given. If it is a `urn:bitprint:` URN,
  the importer checks it against the content and fails on a mismatch. Any other
  URI is accepted with a warning.

Mapping tables live in `mappings/*.tsv` and are selected by name. Each table has
an `#entry` section mapping entry types to subject templates and RDF types, and a
`#field` section mapping headers to predicates, value parsers, and cardinality.
Rules with `entryType` `*` apply unless a more specific rule exists.

`vocab` exits non-zero when a mapping references an undefined term. Its report
has one row per term with `ok`, `undefined`, `proposed`, `unused`, `duplicate`,
`deprecated`, or `deprecated-in-use` status.
