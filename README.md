# TOGETL (TOGoS's Extract, Transform, Load system)

Tools for converting TEF, Org, and music source documents into TOGETLTSV 202601
statement streams, loading them into SQLite, and browsing the result read-only.

See [PLAN.md](PLAN.md) for the full design.

See [TODO.tef](TODO.tef) for a queue of additional change requests.

## Build

```sh
./scripts/get-jdk.sh          # only if no JDK 8 on PATH
./scripts/get-sqlite-jdbc.sh
./scripts/fetch-fixtures.sh   # optional upstream sample documents
./build.sh
./test/run-tests.sh
```

`build.sh` and `test/run-tests.sh` use this Java selection order:
1. `$JAVAC`/`$JAVA` (environment variables), if set.
2. `$JAVA_HOME/bin/javac` and `$JAVA_HOME/bin/java`, if `$JAVA_HOME` is set.
3. `.toolchain/jdk8`, if present.
4. `javac`/`java` on `$PATH`.

The build targets Java 8 and does not require Maven.

## Status

- TOGETLTSV 202601 writer and reader, with alias handling and round-trip tests.
- TEF reader: BOM handling, strict UTF-8 validation, source spans, comments,
  directives, tolerance levels, and recovery for orphan continuations and
  malformed header lines.
- Generic TEF dialect: captures document metadata, entries, headers, comments,
  directives, and parse issues without dropping source data, and reports the
  entry types and fields that a domain dialect still needs to map.
- Declarative mapping dialects driven by TSV tables under `mappings/`:
  - `parts` for `3DPrintingData.tef` — parts, categories, sets, standards,
    narration, aliases, links, STL/CSG files, and part references.
  - `namespaces` for `namespaces.tef` and `vocab/*.tef` — namespaces, classes,
    properties, datatypes, formats, and project bases.
  - Unmapped entries and fields are reported with source locations; mapped
    dialect output stays at the domain/provenance layer rather than emitting TEF
    structural statements on mapped subjects.
  - Functional fields report conflicts instead of silently choosing a value;
    identical duplicates are deduplicated and multi-valued fields keep all
    values.
- Vocabulary reporting: `togetl vocab` compares terms used by mappings and
  streams against `vocab/*.tef` and reports undefined, proposed, unused, and
  duplicate terms.
- Import command: `togetl import` writes deterministic, self-contained streams.
- SQLite loading and the PHP browser are added in later milestones.

## Usage

```sh
./togetl version

# Parts data with the domain mapping.
./togetl import --dialect parts \
    --input fixtures/upstream/3DPrintingData.tef \
    --output out/3dprinting-data.togetltsv \
    --report out/3dprinting-data-report.tsv

# Shared namespace vocabulary.
./togetl import --dialect namespaces \
    --input fixtures/upstream/namespaces.tef \
    --output out/namespaces.togetltsv

# Check mapping terms against vocabulary definitions.
./togetl vocab --report out/vocab-report.tsv
```

`import` defaults to the `generic` dialect and `warn` tolerance. Local files
are identified by their absolute `file:` URL unless `--source-url` overrides it.

Mapping tables live in `mappings/*.tsv` and are selected by name (for example
`--dialect parts` loads `mappings/parts.tsv`). Each table has an `#entry`
section mapping entry types to subject templates and RDF types, and a `#field`
section mapping headers to predicates, value parsers, and cardinality. Rules
with `entryType` `*` apply unless a more specific rule exists.

`vocab` exits non-zero when a mapping references an undefined term. Its report
has one row per term with `ok`, `undefined`, `proposed`, `unused`, `duplicate`,
`deprecated`, or `deprecated-in-use` status.
