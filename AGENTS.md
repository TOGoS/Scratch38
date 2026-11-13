# AGENTS.md

Quick startup context for coding agents working in this repository.

## What this project is

TOGETL converts source documents (mostly TEF today) into TOGETLTSV statement streams, then loads/browses them.

Primary references:
- [README.md](./README.md) for usage/build
- [CONTRIBUTING.md](./CONTRIBUTING.md) for conventions

## Local terms (project-specific)

- **TEF**: vaguely MIME-like source document format parsed into entries (`=type value`), headers (`key: value`), content, comments, directives.
- **TOGETLTSV**: line-oriented RDF-like statement stream:
  `source location subject predicate object`
- **mapped dialect**: domain/provenance output via TSV mapping tables in [mappings/](./mappings/), not raw TEF structure.
- **generic dialect**: emits TEF structural data (entry/header/etc.) and unmapped diagnostics.

## Vocabulary and namespaces

Canonical vocab files:
- [vocab/togetl.tef](./vocab/togetl.tef): `togetl:*` terms (project/infrastructure)
- [vocab/domain.tef](./vocab/domain.tef): non-`togetl` terms (`general:*`, `3dparts:*`, `wsitem:*`)
- [vocab/tef-dialect.tef](./vocab/tef-dialect.tef): TEF structural terms

Tag-URI base convention:
- `tag:nuke24.net,2026-11:togetl:` for project/infrastructure terms
- `tag:nuke24.net,2026-11:general:`, `tag:nuke24.net,2026-11:3dparts:`, etc. for domain/shared terms

The entire `tag:nuke24.net,2026-11:*` vocabulary namespace is, for now, project-local and not yet externally standardized.
No outside consumers are known to depend on it yet, so vocabulary renames are acceptable as long as this note remains.

## Important modeling decisions (do not regress)

- Predicate names should describe conceptual relationships, not TEF representation details
  (e.g. "-ref" suffixes should not be carried over, since RDF explicitly indicates by-value or by-reference as part of the value, rather than in the predicate name).
- TEF source header names may still include legacy spellings (typo variants),
  but those map to canonical RDF predicates, and should emit warnings or errors so that the source documents can eventually be normalized.
  
Some specific cases:
- `doc-ref` maps to `general:isDocumentedBy`.
- Mapped domain subjects should **not** get automatic `togetl:document` links just because they came from a TEF file;
  that is part of the 'provenance metadata' that is represented at a lower level (first two columns) in the TOGETLTSV stream.
- For `fileRef` values with `filename + urn:bitprint:*`, importer canonicalizes to:
  `http://wherever-files.nuke24.net/uri-res/raw/<urn>/<filename>`,
  avoiding having to emit a blank node with separate `general:filename` / `rdf:type togetl:File` properties.

## Mapping system constraints

- Mapping tables are TSV under [mappings/](./mappings/), with `#entry` and `#field` sections.
- `valueParser`s currently support 1-header parsing, but can emit multiple statements.
- Cardinality rules:
  - `*`: keep all values
  - `1` / `?`: report conflicts on differing duplicates; do not silently last-write-win

## Validation workflow

Default verification after code changes:

```sh
./test/run-tests.sh
```

This runs project build + tests; use it unless the change is documentation-only.

The Visual Studio Code 'devcontainer' runs on alpine and should have
`JAVA_HOME` set to `/usr/lib/jvm/java-1.8-openjdk`, as specified by `devcontainer.json`.
Sometimes VS Code agents find that it is not set correctly,
in which case it can be worked-around with an `export JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk` before the command.

## Documentation and design principles

See CONTRIBUTING.md for documentation conventions and design principles.

## Commit attribution

When an agent makes a commit entirely on its own, set author identity per commit command
(e.g. using `git -c user.name=... -c user.email=... commit ...`),
rather than changing repository or global git config.

The identity encodes the agent and the tool that ran it:

- Name: the agent's display name, e.g. `Master Haiku`.
- Email: `togos00+<agent>.<harness>@gmail.com`, where `<agent>` is the display name in lowercase
  with spaces replaced by dots, and `<harness>` is `opencode`, `copilot`, or the tool's name in lowercase.
  The `togos00+` prefix records that the owner is responsible for the commit.

For example, `Master Haiku <togos00+master.hiaku.opencode@gmail.com>`. The same agent running
under CoPilot uses `togos00+master.hiaku.copilot@gmail.com`.
