# TOGETL ETL — project plan

Status: MVP implementation starting. This document records the intended shape of the
system so that generated code has something to be consistent with.

## 1. Purpose

Convert heterogeneous source documents into **TOGETLTSV 202601** statement streams,
load those streams into a **SQLite** database, and browse the result read-only with
**PHP**.

The primary motivation is the 3D-printing data in `ProjectNotes2`:

- `2023/3DPrinting/3DPrintingData.tef` — structured parts data
- `2023/3DPrinting/3DPrintingLog.org` — prose, timestamps, checklists, and named
  relational tables

The same pipeline also ingests `music.txt` and the shared namespace vocabulary, and is
expected to grow more source formats over time.

## 2. Inputs

| Input | Format | Importer | Role |
|---|---|---|---|
| `https://gitlab.com/TOGoS/ProjectNotes2/-/raw/master/2023/3DPrinting/3DPrintingData.tef` | TEF v0.3 | TEF (parts dialect) | parts, categories, files, links |
| `https://gitlab.com/TOGoS/ProjectNotes2/-/raw/master/2023/3DPrinting/3DPrintingLog.org` | Org | Org | log entries, tables, print history |
| `https://www.nuke24.net/docs/ns/namespaces.tef` | TEF v0.3 | TEF (namespaces dialect) | vocabulary |
| `https://www.nuke24.net/music/music.txt` | TEF-like | Music | recordings, songs, albums, files |
| `vocab/*.tef` | TEF v0.3 | TEF (namespaces dialect) | project vocabulary |

Source files are **read-only**. Writing back to sources is out of scope for the MVP.

## 3. Pipeline

```
source document
    -> format reader (TEF / Org / music)
    -> parsed entries (with source spans)
    -> dialect mapping (entry type + header key -> subject/predicate/object)
    -> Statement stream
    -> TOGETLTSV writer (one file or many)
    -> SQLite loader
    -> PHP read-only browser
```

Every importer emits the same `Statement` model. No importer writes SQL directly, and
the SQLite loader knows nothing about TEF or Org.

## 4. TOGETLTSV output

The format is TOGETLTSV 202601. The first line of every stream is:

```
#format urn:uuid:b783bac7-58e9-4340-93ef-7973914732d5
```

Following lines are:

```
source location subject predicate object
```

with any amount of space/tab whitespace between columns.

`source` identifies the 'document import' that emitted the statement.
An 'document import' references exactly one source document,
and can also be a member of a batch (batch ontology is not yet formalized).

- Blank lines and lines beginning with whitespace + `#` + whitespace are comments.
- Lines beginning with `#` + a non-whitespace word are directives. Only `#format` and
  `#alias` are defined.
- Repeating the `#format` line clears all aliases. This is what makes concatenation
  safe: every emitted file is self-contained and can follow any other file.
- `location` is `l<line>c<col>` (zero-based), `L<line>C<col>` (one-based), or `b<offset>`
  (byte offset), optionally a span written `start...end`. `-` means unknown.
- Objects are IRIs, blank nodes (`_:name`), or literals.
- Literals are written as `data:` URIs containing percent-encoded UTF-8. A datatype may
  be appended after a pipe: `data:,123|http://www.w3.org/2001/XMLSchema#integer`.
- A `|` chain is the general TS34-like "encoded value" convention.

Our writer:

- emits a fixed, small alias block (`rdf:`, `rdfs:`, `xsd:`, `togetl:`, `tef:`, `cc:`,
  `dc:`, `dcterms:`) at the top of each file;
- writes all statements in source order;
- derives blank-node names deterministically from document IRI + entry ordinal + field
  ordinal, so the same input always produces the same names;
- writes one stream per input document by default. `--output-dir` may split by document;
  concatenating the results remains valid.

## 5. Identifiers

Instances and vocabulary terms are both **tag URIs** under the `nuke24.net` authority and the fixed date `2026-11`, but they live in different namespaces:

```
tag:nuke24.net,2026-11:togetl:Document
tag:nuke24.net,2026-11:togetl:Part
tag:nuke24.net,2026-11:togetl:tef:Entry
tag:nuke24.net,2026-11:3dparts:part:p1310
tag:nuke24.net,2026-11:3dparts:rendering:<hash>
tag:nuke24.net,2026-11:3dparts:stl-file:<hash>
tag:nuke24.net,2026-11:togetl:document:<sha256-16>
```

Rules:

- The date is fixed. Changing it would silently re-identify every instance.
- `tag:nuke24.net,2026-11:togetl:` is reserved for TOGETL vocabulary and infrastructure terms.
- `tag:nuke24.net,2026-11:<domain>:` is reserved for domain data and derived resources, such as the `3dparts` namespace for STL files and renderings.
- Key segments are percent-encoded outside `A-Za-z0-9-._~:`.
- Documents are identified by a hash of their source URL, not by the URL itself, so a
  moved document keeps its identity. `togetl:sourceUrl` records the URL.
- TEF syntax terms live under `tag:nuke24.net,2026-11:togetl:tef:`.

Identifier interpretation is deliberately provisional. Some source IDs are shorthand for
larger identifiers: `WSITEM-1234` is expected to become `urn:oid:<base>.1234`, `p`-prefixed
STL IDs belong to another OID space, and some records will be hashed into `urn:uuid:`
values. The mapping layer isolates ID construction so these decisions can change without
touching importers.

## 6. Provenance

Every statement carries a source document and a location. The database additionally
records the batch that produced it, via the document import's membership in that batch.

| Concept | Meaning | Identity |
|---|---|---|
| Document | a source file | `tag:...,togetl:document:<sha256-16 of URL>` |
| Document import | one importer execution against one document* | `tag:...,togetl:document-import:<hash>` |
| Batch | one `togetl load` operation over one or more documents | `tag:...,togetl:batch:<hash>` |
| Statement | one triple at one location | row in `statement` |

*Current implementation intentionally mints document-import IDs deterministically
from `(document IRI, parser/importer version)`. If multiple executions are
indistinguishable under that model, they collapse to one identity by design.
Whether the determinism is really true and whether to represent per-run executions
separately is a decision yet to be made.

A document import records the batch it belongs to with `general:isMemberOf`, so a batch
need not have its own per-document loading identity. Batch IDs are deterministic hashes
of source content, importer version, and options. Re-importing the same document replaces
that batch's statements rather than duplicating them.

Default mode records document, document import, batch membership, and location.
`--simple-provenance` records only the document.

## 7. TEF parsing

### 7.1 Vendored parser

The TEF parser from TScript34 (MIT) is vendored into the repository and patched. The
published `tscript34-p0014` artifact is not used directly because the patches below need
to change its chunk model.

Vendored files (package names adjusted):

```
net.nuke24.togetl.tef.TEFParser
net.nuke24.togetl.tef.LLChunks
net.nuke24.togetl.tef.LLChunkMerger
net.nuke24.togetl.tef.util.ArrayUtil
net.nuke24.togetl.tef.util.DucerUtil
net.nuke24.togetl.ducer.Function
net.nuke24.togetl.ducer.DucerChunk
net.nuke24.togetl.ducer.DucerState2
```

Original copyright and license headers are preserved; a `NOTICE` records the origin.

### 7.2 Patches

1. Add source spans to every chunk: line, column, byte start/end, end line/column.
2. Emit `Comment` chunks instead of discarding `#`-prefixed header lines.
3. Emit `Directive` chunks for `#format`, `#alias`, and unknown directives.
4. Strip a UTF-8 BOM at the start of the stream and record it.
5. Decode UTF-8 with `CodingErrorAction.REPORT`; malformed input becomes a parse issue
   rather than a silent U+FFFD.
6. Recover orphan continuation lines as content under `warn`/`lenient`; fail under
   `strict`.
7. Verify spec edge cases: `=` with an empty type, `==` escaping at beginning of line,
   content ending at `\n=`, trailing LF at EOF being content, lone CR as content, CRLF
   line endings.

### 7.3 Entry model

`TefReader` merges chunks into entries:

```java
final class TEFEntry {
    String type;              // "part"
    String value;             // "p1310"
    List<TEFHeader> headers;  // ordered, duplicates preserved
    String content;           // may be empty
    SourceSpan span;
    List<TEFComment> comments;
    List<TEFDirective> directives;
}
```

`tef:content` is the entry's free-form note or description,
and in mapped dialects should be translated into an appropriate
domain predicate (currently `rdfs:comment`) on the mapped subject.

The parser keeps the full raw entry form available.
The `generic` dialect emits that structure directly, while mapped dialects
emit domain/provenance statements and report unmapped constructs by location.

### 7.4 Tolerance

`--tolerance strict|warn|lenient`, default `warn`.

- `strict` — any syntax problem aborts the import.
- `warn` — recover where the spec allows, emit `togetl:parseIssue` statements.
- `lenient` — same as warn, but suppresses issue statements for known cosmetic problems.

## 8. Dialects and mappings

An importer combines a **reader** (TEF, Org, music) with a **dialect** that maps parsed
entries to statements.

MVP selects the dialect explicitly:

```
--dialect parts
--dialect namespaces
--dialect generic
```

Automatic dialect detection (manifest, `#format`, filename, key signature) is a
post-MVP idea. The `Dialect` interface is designed so a registry can be added later
without changing importers.

Mapping tables are TSV files under `mappings/`:

```
#entry  part  tag:nuke24.net,2026-11:togetl:part:${value}
entryType       subjectTemplate                 type
part            tag:nuke24.net,2026-11:togetl:part:${value}  togetl:Part

#field  entryType  header       predicate          valueParser  cardinality
field    part       name         togetl:name        plain        1
field    part       stl-file     togetl:stlFile     fileRef      *
field    part       tags         togetl:tag         plain        *
```

- `subjectTemplate` supports `${value}` and `${header:name}` substitutions.
- `valueParser` is one of `plain`, `iri`, `fileRef`, `number`, `timestamp`, `bool`,
  `csv`, `x-rdf-subject`.
- `cardinality` is `1`, `?`, or `*`.
- Every predicate referenced must be defined in `vocab/`.

Entries and headers with no mapping produce report rows with source locations,
so nothing disappears silently from import diagnostics.

### 8.1 Parts dialect

Source: `3DPrintingData.tef`.

- `=part <id>` -> `tag:...,togetl:part:<id>`, type `togetl:Part`.
- `=part-category`, `=part-set`, `=category`, `=standard`, `=narration` map to their own
  classes.
- Repeated headers such as `tags`, `keywords`, `member-of`, `see-also`, `photo-ref`,
  `stl-file`, `openscad-rendering-ref`, `openscad-design-ref`, `mirror` are multi-valued
  and produce one triple each.
- Functional headers (`name`, `description`, `obsoleted-by` where appropriate) produce a
  conflict report when repeated with different values.
- Known typos (`=p1rt`, `sytl-file`, `stl-ref`, `oopenscad-rendering-ref`, `ttp://`) are
  mapped with `togetl:asWritten` and reported. They are not silently corrected.
- Duplicate `=part p1310` entries are merged under the same subject; conflicting fields
  are reported.

### 8.2 Namespaces dialect

Source: `namespaces.tef` and `vocab/*.tef`.

- `=namespace` -> namespace/ontology record
- `=type` -> `rdfs:Class`
- `=attribute` -> `rdf:Property`
- `=datatype` -> `rdfs:Datatype`
- `=format` -> `togetl:Format`
- `=projbase` -> project base record

This dialect is what builds the vocabulary registry, so the vocabulary is expressed in
the same format the system imports.

### 8.3 Org importer

Two layers:

1. **Document structure** — headings (including TODO keyword, priority cookie, and
   tags), outline path, `CUSTOM_ID`, planning entries, property drawers, timestamps,
   checkboxes, links, `#+` directives, and table rows/cells. Headings become
   `togetl:orgHeading` resources; a heading with a `CUSTOM_ID` gets a stable tag URI.
2. **Named tables** — `#+NAME:` tables become typed rows. Column names come from the
   header row; mappings under `mappings/org-3dparts-log.tsv` assign predicates and
   value parsers. Foreign keys are resolved through the same mapping layer
   (`print_1.GCode` -> `slicing_*`, `slicing_*.Part STL Number` -> `part_*`,
   `print_1.Print ID` suffix -> `printer_1`, `item_0.Item #` -> `WSITEM-*`).

TEF parts and Org table rows that refer to the same part key resolve to the same
subject, so provenance and conflicts are visible across sources.

### 8.4 Music importer

Source: `music.txt`. It is TEF-shaped but not marked with `#format`.

- `=recording`, `=song`, `=album`, `=version`, `=category`, `=album-snapshot`,
  `=version-log`, `=quote`, `=narrative`.
- `file:` values are tab- or space-separated `filename<TAB>urn:...`; both forms are
  parsed. When the value includes `filename + urn:bitprint:...`, import maps it to a
  canonical URL-shaped file resource (`http://wherever-files.nuke24.net/uri-res/raw/<urn>/<filename>`)
  so part/file predicates point directly at a regular resource IRI.
- `dir:` values may use `x-rdf-subject:` pseudo-URIs.
- Indented content under `=category` is an orphan continuation and must warn, not fail.

### 8.5 Repeated headers and conflicts

Repeated headers are handled case by case, driven by the mapping's cardinality:

- `*` — multiple triples, all kept (tags, keywords, member-of, see-also, ...).
- `1` — at most one value; a second different value is a conflict. Both statements are
  emitted and a `togetl:conflict` report row is written. Identical duplicates are
  deduplicated.
- `?` — zero or one; same as `1` but absence is fine.

No silent last-write-wins.

## 9. Vocabulary

Vocabulary sources:

- `vocab/togetl.tef` — terms minted for this project
- `vocab/domain.tef` — non-`togetl` domain/shared terms (`general`, `3dparts`, `wsitem`)
- `vocab/tef-dialect.tef` — TEF entry/header terms
- `namespaces.tef` — shared namespaces
- ContentCouch and Dublin Core terms used by mappings
- TOGVM source-location terms

The `vocab` command produces a report:

```
term, status, usedBy, definedBy, note
```

with sections for:

- used but undefined
- defined but unused
- defined more than once
- deprecated terms still in use
- proposed terms (used in mappings, not yet defined)

A build check fails if a mapping references an undefined predicate.

## 10. Database

SQLite, loaded by Java. The schema is source-agnostic:

```sql
CREATE TABLE document (
    iri TEXT PRIMARY KEY,
    source_url TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    byte_length INTEGER NOT NULL,
    detected_format TEXT,
    parser_version TEXT
);

CREATE TABLE batch (
    iri TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    importer_version TEXT NOT NULL,
    options_json TEXT NOT NULL
);

CREATE TABLE loading (
    iri TEXT PRIMARY KEY,
    batch_iri TEXT NOT NULL REFERENCES batch(iri),
    document_iri TEXT NOT NULL REFERENCES document(iri)
);

CREATE TABLE statement (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    line INTEGER,
    column INTEGER,
    end_line INTEGER,
    end_column INTEGER,
    subject TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT NOT NULL,
    object_kind TEXT NOT NULL,   -- iri | blank | literal
    datatype TEXT,
    batch_iri TEXT,
    loading_iri TEXT
);

CREATE INDEX statement_subject ON statement(subject);
CREATE INDEX statement_predicate ON statement(predicate);
CREATE INDEX statement_object ON statement(object);
CREATE INDEX statement_subject_predicate ON statement(subject, predicate);

CREATE TABLE parse_issue (
    id INTEGER PRIMARY KEY,
    document_iri TEXT NOT NULL,
    line INTEGER,
    column INTEGER,
    severity TEXT NOT NULL,
    code TEXT NOT NULL,
    message TEXT NOT NULL,
    raw TEXT
);

CREATE TABLE vocab_term (
    iri TEXT PRIMARY KEY,
    kind TEXT,
    label TEXT,
    comment TEXT,
    status TEXT,
    defined_in TEXT
);
```

Views for browsing (`part`, `part_file`, `part_alias`, `conflict`) are added per domain.

The loader reads TOGETLTSV, so it can also load streams produced by other tools.

## 11. PHP browser

Read-only PDO SQLite. Open with `PRAGMA query_only = ON`.

Pages:

- `/` — counts, sources, recent batches, issue summary
- `/subject?iri=...` — all statements, grouped by predicate, with provenance
- `/predicate?iri=...` — subjects using a predicate
- `/document?iri=...` — metadata, loadings, issues
- `/vocab` — vocabulary report
- `/issues` — parse issues and conflicts
- `/part/<id>` — domain view over the generic tables

No authentication, no editing, no writes.

## 12. Repository layout

```
.
├── PLAN.md
├── README.md
├── build.sh
├── .gitignore
├── lib/                      # pinned jars (sqlite-jdbc), checksums
├── scripts/
│   ├── get-jdk.sh
│   ├── get-sqlite-jdbc.sh
│   └── fetch-fixtures.sh
├── src/main/java/net/nuke24/togetl/
│   ├── cli/
│   ├── tef/
│   ├── org/
│   ├── importers/
│   ├── rdf/
│   ├── togetltsv/
│   ├── vocab/
│   └── db/
├── src/test/java/net/nuke24/togetl/
├── vocab/
├── mappings/
├── fixtures/
│   ├── small/
│   ├── expected/
│   └── upstream/             # fetched, gitignored
├── test/
│   └── run-tests.sh
└── web/
```

## 13. Build and toolchain

- Java 8 language level and API. No `var`, records, `List.of`, text blocks, or
  `java.net.http`.
- Plain `javac`, no Maven.
- `build.sh` uses `--release 8` when available and falls back to `-source 1.8 -target 1.8`
  on a real JDK 8.
- `scripts/get-jdk.sh` downloads a pinned Temurin 8 release into `.toolchain/jdk8` and
  verifies its SHA-256. Pinned version: `8u512-b09`.
- `scripts/get-sqlite-jdbc.sh` downloads `sqlite-jdbc-3.46.1.3.jar` into `lib/` and
  verifies its checksum.
- Tests use a small built-in harness (`TestMain`) rather than JUnit, to keep the
  dependency set to one jar.

Commands:

```sh
./scripts/get-jdk.sh
./scripts/get-sqlite-jdbc.sh
./build.sh
./test/run-tests.sh

./togetl import --dialect parts --input fixtures/upstream/3DPrintingData.tef \
    --output out/3dparts-data.togetltsv
./togetl import --dialect namespaces --input vocab/togetl.tef \
    --output out/togetl-vocab.togetltsv
./togetl vocab --input out/togetl-vocab.togetltsv --report out/vocab-report.tsv
./togetl db --tsv out/*.togetltsv --db out/togetl.sqlite
```

## 14. Milestones

| # | Deliverable | Acceptance |
|---|---|---|
| M0 | Skeleton: `Iri`, `Literal`, `Statement`, `Location`, `TsvWriter`, `AliasTable`, build/test scripts | round-trip and alias tests pass; concatenated streams re-import to the same triple set |
| M1 | Vendored TEF lexer + `TEFReader`: spans, comments, directives, BOM, strict UTF-8, tolerance | original parser tests plus new edge-case tests pass; bad input yields parse issues, never a crash |
| M2 | Generic TEF dialect + TSV writer + unmapped reporting | `3DPrintingData.tef` imports; no silent drops |
| M3 | Parts dialect, namespaces dialect, vocabulary registry, vocab report | counts match the source; every mapping predicate is defined |
| M4 | SQLite schema/loader + PHP browser, parts only | reproducible DB; subject/predicate/document/vocab/issues pages work |
| M5 | Org structure: headings, outline path, `CUSTOM_ID`, timestamps, checkboxes, links | `CUSTOM_ID`s resolve; stable heading tree |
| M6 | Org named tables, column mappings, foreign keys, TEF↔Org merge | all 33 tables load; `print_1` -> `slicing_*` -> `part_*` joins resolve |
| M7 | `music.txt` importer | 301 recordings; both `file:` forms parsed; orphan content warns |
| M8 | Hardening | offline test suite green; checksums pinned; no un-pinned dependencies |

MVP emits semantic triples only. The optional `--syntax compact|full` layers are
deferred; the reader keeps the raw entry model so they can be added later without
re-parsing.

## 15. Conventions

- Java package names: all lowercase (`net.nuke24.togetl.tef`).
- Class names: `UpperCamelCase`; acronyms stay uppercase (`TEFParser`, `IRI`, `TSVWriter`).
- Methods, fields, variables: `lowerCamelCase`; acronyms are title-cased after the first
  word (`tefParser`, `parseTef`, `myXmlHttpRequest`).
- Constants: `UPPER_SNAKE_CASE`.
- UTF-8 everywhere, LF line endings in generated text.
- Deterministic output: same input + same options -> byte-identical stream.
- No network access during tests.

## 16. Deferred ideas

- Automatic TEF dialect detection (manifest, `#format`, filename, key signature).
- `--syntax compact|full` archival layers.
- Incremental re-import and batch invalidation.
- Writing updates back to source documents.
- Editing in the web UI.
- Vocabulary versioning and term deprecation workflow.
- Additional source formats beyond TEF, Org, and music.

## 17. Notes and open questions

- The exact OID bases for `WSITEM-*` and `p*` IDs are not yet fixed; current mappings use
  tag URIs and can be changed centrally.
- Org table column names are noisy and will need a per-table mapping review.
- Conflict policy for functional headers is per-property; the first pass will treat
  `name`, `description`, and `obsoleted-by` as functional and everything else as
  multi-valued.
- The PHP browser requires the PDO SQLite extension at deployment time. It is not needed
  to build or test the Java side.
