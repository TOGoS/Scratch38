I asked Big Pickle to do a review using the following prompt,
which was suggested by ChatGPT after I vented to it about
things the coding agents tended to miss.

-----

## Codebase Consistency and Refactoring Audit

Review this codebase for opportunities to improve its internal consistency, clarity, and design.

Do not limit the review to obvious duplication or superficial cleanup. Look for problems that become apparent when comparing related code across modules.

### Areas to investigate

Pay particular attention to:

- **Inconsistent application of abstractions:** An abstraction, helper, pattern, or convention is used in some places but bypassed in others without a clear reason.
- **Naming inconsistencies:** Equivalent concepts have different names, or the same name is used for different concepts.
- **Pointless asymmetries:** Related functions, types, modules, or workflows differ in ways that appear accidental rather than intentional.
- **Redundant concepts:** Multiple types, helpers, flags, or code paths represent essentially the same thing.
- **Unnecessary complexity:** Indirection, configuration, branching, or abstractions that do not provide meaningful value.
- **Inconsistent boundaries:** Responsibilities are divided differently across otherwise similar components.
- **Missed simplifications:** Places where an existing pattern could replace bespoke code, or where a simpler design would be clearer.

### How to conduct the review

1. Explore the relevant codebase structure and identify its established conventions.
2. Compare related code rather than reviewing each file in isolation.
3. Look for patterns that are only partially or inconsistently applied.
4. Distinguish intentional differences from accidental inconsistencies. Do not force symmetry where the requirements genuinely differ.
5. Consider whether a proposed cleanup improves the design as a whole, rather than merely moving code around.

### First, report findings only

Do not modify files yet.

For each finding, provide:

- A concise description of the problem.
- Specific file and line references.
- The related code or pattern that provides a useful comparison.
- Why the inconsistency appears unnecessary or harmful.
- A proposed correction.
- Any risks or uncertainties.

Prioritize findings by their likely impact on maintainability and clarity, not by how easy they are to fix.

Avoid speculative style preferences, trivial formatting issues, and changes that would make the code less consistent with its existing conventions.

### After the audit

Summarize the most valuable opportunities and ask which ones I want implemented.

When implementing approved changes:

- Keep the scope focused.
- Preserve existing behavior unless a behavior change is explicitly approved.
- Apply the chosen abstraction or convention consistently across relevant code.
- Update tests and run the appropriate checks.
- Report what changed, what was verified, and what remains unresolved.

-----

## TOGETL Java audit — consistency and maintainability findings

Read-only audit; no files were modified. Findings are ordered by expected maintainability impact. Line numbers refer to the current `bitprint` worktree.

---

### 1. Import-report rows have two models, a bespoke parser, and the wrong exception type

**Problem**
The same report row is represented twice:

- `etl.ImportReport.Row` — `src/main/java/net/nuke24/togetl/etl/ImportReport.java:85-118`, holds a `Location`.
- `db.ConflictRow` — `src/main/java/net/nuke24/togetl/db/ConflictRow.java:6-49`, holds a stringified location and a misleading name.

Writing and reading are implemented independently: `ImportReport.writeTsv` (`ImportReport.java:65-79`) writes five columns, while `LoadCommand.readReport` (`LoadCommand.java:195-228`) hand-parses them. The reader:

- skips any row whose first column is literally `"kind"` (`LoadCommand.java:210`) instead of recognizing a header structurally;
- accepts rows with only four columns and silently substitutes an empty location (`LoadCommand.java:206,225`);
- throws `TOGETLTSVParseException` (`LoadCommand.java:207,217`) even though the input is an importer report, not TOGETLTSV.

The loader then consumes the duplicate model: `Loader.load(..., List<ConflictRow>, ...)` (`Loader.java:49-52`), `insertReportRows` (`Loader.java:520-558`).

**Why harmful**
Any report-schema change requires coordinated edits in four places, and writer/parser can drift without a test noticing. A legitimate data row whose kind is `kind` is silently dropped. Report parse failures are reported to users as TSV format errors, pointing at the wrong subsystem.

The report “kind” vocabulary is also unmanaged: `unmapped-entry-type`, `unmapped-field`, `unmapped-directive`, `conflict` are literals in `ImportReport`, `as-written-entry`/`as-written-field` in `MappingTEFDialect.java:104,124`, and `invalid-iri`/`empty-value`/`unparsed-value` in `ValueParsers.java:115,124,171,232,247,262`. By contrast `VocabularyReport` defines status constants (`VocabularyReport.java:21-27`). The columns are also overloaded: for directives, `entryType` is empty and `field` holds the directive name (`ImportReport.java:31-33`).

**Proposed correction**
Make one row type and one serializer/deserializer pair own this format, e.g. a neutral `ReportRow` (or `ImportReport.Row` with a public factory) plus `ImportReport.readTsv(Reader, name)`. Have `Loader` accept that type, and delete `ConflictRow`. Introduce constants/enum for report kinds and either generalize the column names or document the per-kind meaning. Use a dedicated `ReportParseException` carrying file and line.

**Risks/uncertainties**
This crosses the `etl`/`db` boundary; place the shared type where dependency direction stays clean. The on-disk report schema and DB table are effectively public; keep the five-column layout unless a migration is approved.

---

### 2. Namespace constants are defined three times

**Problem**
The same namespace strings are independently declared in:

- `Vocab.java:13-22`
- `Identifiers.java:14-16` (`TAG_PREFIX`, `DATA_TAG_PREFIX`, but no `general:`)
- `AliasTable.withDefaults()` — `AliasTable.java:19-28`

**Comparison**
`Vocab.TOGETL_NS` is `"tag:nuke24.net,2026-11:togetl:"`; `Identifiers.TAG_PREFIX` is built from `TAG_DATE`; `AliasTable` repeats the literal. `Vocab.TEF_NS` and `AliasTable`’s `tef:` are also separate literals.

**Why harmful**
Adding or renaming a namespace requires three edits, and the three lists already differ. `Identifiers` is the identifier-minting authority but cannot name the `general:` namespace used by vocabulary terms. Divergence would silently produce IRIs that do not compress/expand consistently.

**Proposed correction**
Create one leaf-level namespace catalog (e.g. `net.nuke24.togetl.namespace.Namespaces`) with the base URIs, then have `Vocab`, `Identifiers`, and `AliasTable.withDefaults()` reference it. Build aliases from that catalog rather than duplicating strings.

**Risks/uncertainties**
Package placement matters: `rdf`, `vocab`, `ident`, and `togetltsv` all need it, so a dependency-free package is safest.

---

### 3. `Vocab` constant names claim the `togetl:` namespace for `general:` and `3dparts:` terms

**Problem**
Nearly every term constant is prefixed `TOGETL_`, regardless of namespace. Examples:

- `TOGETL_SHA256 = general("sha256")` — `Vocab.java:56`
- `TOGETL_FILENAME = general("filename")` — `Vocab.java:91`
- `TOGETL_PART = data("Part")`, `TOGETL_STL_FILE`, `TOGETL_CATEGORY`, `TOGETL_STANDARD` — `Vocab.java:39-47,103-111`
- `TOGETL_NAME`, `TOGETL_TITLE`, `TOGETL_ALIAS`, etc. — `Vocab.java:94-117`

**Why harmful**
The prefix is the primary signal of which namespace a term belongs to, and it is wrong for a large fraction of constants. It hides the domain grouping that `vocab/domain.tef` establishes and makes namespace-related mistakes harder to spot. It also amplifies the duplication in finding 2.

**Proposed correction**
Rename by namespace (`GENERAL_SHA256`, `GENERAL_FILENAME`, `PARTS3D_PART`, …) or drop the prefix and use `Vocab.general("sha256")` at call sites. The project-local vocabulary policy in `AGENTS.md` explicitly permits renames.

**Risks/uncertainties**
Mechanical but broad diff across Java and tests. Keep `TEF_*` names, which are already namespace-correct.

---

### 4. Document hash/key is recomputed in four places; `documentKey` is a no-op wrapper

**Problem**
`Identifiers.documentKey` (`Identifiers.java:98-104`) simply validates and returns its input; its only caller is `documentHash` (`Identifiers.java:106-110`). The hash is then recomputed independently in:

- `DialectSupport.DocumentSession` constructor — `DialectSupport.java:48`
- `DialectSupport.emitGenericEntry` — `DialectSupport.java:155`, once per entry
- `MappingTEFDialect.Session` constructor — `MappingTEFDialect.java:73`
- `ValueParserContext` constructor — `ValueParserContext.java:45`, once per header

**Why harmful**
The “document hash” is derived identity, but no single object owns it. The generic dialect hashes the same document once per entry, and the mapping dialect once per header via `ValueParserContext`. Any change to the hash scheme must be made in four places, with a real risk of one path using a different derivation.

**Proposed correction**
Compute it once in `SourceDocument` (e.g. `getDocumentHash()` backed by `Identifiers.shortSha256(documentIri)`), and pass that value through `DocumentSession`/`ValueParserContext`. Remove `documentKey` unless it is intended to grow a real transformation.

**Risks/uncertainties**
Behavior is unchanged only if the hash input remains exactly the document IRI. Add a test asserting stable blank-node labels across both dialects.

---

### 5. Comment/directive emission is duplicated, and entry directives are emitted twice

**Problem**
`DialectSupport` contains two near-identical pairs:

- `emitDocumentComment` (`DialectSupport.java:112-125`) vs `emitGenericComment` (`DialectSupport.java:208-223`) — same three statements, different blank-node label.
- `emitDocumentDirective` (`DialectSupport.java:127-146`) vs `emitGenericDirective` (`DialectSupport.java:225-242`) — same five statements.

More importantly, `TEFReader.Assembler` treats comments and directives differently (`TEFReader.java:270-285`): a comment becomes document-level only when outside an entry, but every directive is sent to `sink.documentDirective`, and is also attached to the current entry. Consequently:

- `DocumentSession.documentDirective` emits and reports the directive at document level (`DialectSupport.java:62-64,143-145`);
- the generic dialect emits the same directive again inside the entry (`DialectSupport.java:182-184`);
- entry comments are emitted only once.

**Why harmful**
Consumers cannot infer whether “document directive” means “outside any entry” or “anywhere in the document.” The generic dialect produces two differently-labeled blank nodes for one directive, and the mapping dialect reports entry-level directives as unmapped document directives even though it does not emit entry directives at all.

**Proposed correction**
Decide one ownership rule and encode it in `TEFReader`:

- document comments/directives only when `current == null`, and entry ones only on the entry; or
- both document-level and entry-level for both kinds.

Then factor shared statement emission into helpers parameterized by subject/link predicate/blank-node prefix.

**Risks/uncertainties**
This changes emitted statements and reports; it needs an explicit semantic decision and test updates. Verify whether any downstream consumer already relies on the duplicate document-level directive.

---

### 6. CLI option parsing and error handling are triplicated and already inconsistent

**Problem**
`ImportCommand`, `LoadCommand`, and `VocabCommand` each independently implement:

- the `--key=value` split loop (`ImportCommand.java:79-104`, `LoadCommand.java:77-104`, `VocabCommand.java:71-93`);
- an identical `next(args, index, option)` (`ImportCommand.java:124-129`, `LoadCommand.java:113-118`, `VocabCommand.java:95-100`);
- a private `UsageException` class (`ImportCommand.java:311-317`, `LoadCommand.java:245-251`, `VocabCommand.java:203-209`);
- a help pre-scan and a per-command catch chain (`ImportCommand.java:51-77`, `LoadCommand.java:44-75`, `VocabCommand.java:41-69`);
- optional-output opening (`ImportCommand.java:239-250,252-264`, `VocabCommand.java:131-148`).

They are already asymmetric: `VocabCommand` does not accept `-i` for `--input`, `LoadCommand` has no `--help` listed in usage beyond the pre-scan, and only `ImportCommand` uses `"-"` as an implicit default output.

**Why harmful**
Every parsing or messaging fix must be replicated three times, and small differences become user-visible inconsistencies.

**Proposed correction**
Add a small shared `Args`/`CommandLine` helper for `key=value` splitting and value consumption, plus a shared command runner for help and exception formatting. Keep each command’s `usage` text and validation local.

**Risks/uncertainties**
Avoid creating a framework. The helper should only remove mechanical duplication; command-specific semantics should remain obvious.

---

### 7. Directory resolution and default-file enumeration are duplicated

**Problem**
`MappingTable.defaultFiles` + `resolveDirectory` (`MappingTable.java:93-110,234-247`) and `VocabularyLoader.defaultVocabFiles` + `resolveVocabDirectory` (`VocabularyLoader.java:64-80,82-95`) are structurally identical, differing only in subdirectory (`mappings`/`vocab`) and suffix (`*.tsv`/`*.tef`).

**Why harmful**
Two implementations of the `TOGETL_ROOT`-then-cwd policy can diverge. Both also duplicate the directory-stream boilerplate.

**Proposed correction**
Extract a shared helper, e.g. `ProjectFiles.defaultFiles(String subdirectory, String glob)`, and have both loaders use it. Consider also sharing the `TOGETL_ROOT`/cwd fallback with `MappingTable.resolve`.

**Risks/uncertainties**
Low. Keep sort order and empty-list-on-missing behavior identical.

---

### 8. “First header” lookup exists three times, with different trimming and naming

**Problem**

- `TEFEntry.getFirstHeader(String)` — `TEFEntry.java:65-72`, returns the header, no trimming.
- `VocabularyLoader.firstValue` — `VocabularyLoader.java:107-114`, returns a trimmed value.
- `EntryRule.firstHeaderValue` — `EntryRule.java:93-100`, returns a trimmed value.

Naming is also asymmetric: `TEFEntry.getFirstHeader` vs `TEFDocument.getDirective` (`TEFDocument.java:60-67`), even though both are “first item with this name.”

Related: `VocabularyLoader.loadInto` silently accepts `short-descripiton` (`VocabularyLoader.java:56-58`), with no diagnostic, despite the `AGENTS.md` rule that legacy/typo header spellings should warn or error so sources can be normalized.

**Why harmful**
Three subtly different lookup semantics make it easy to introduce a value with stray whitespace or a null mismatch. The typo alias is invisible, so the typo can persist indefinitely.

**Proposed correction**
Use `TEFEntry.getFirstHeader(name)` plus explicit `.getValue().trim()` at call sites, or add `getFirstHeaderValue` with documented trimming. Rename `TEFDocument.getDirective` to `getFirstDirective` for symmetry. Make the typo alias emit a warning (or centralize legacy header aliases with diagnostics).

**Risks/uncertainties**
Changing trimming could alter vocabulary labels/comments. Warning emission needs a sink or a returned diagnostic rather than a silent branch.

---

### 9. Whitespace validation and whitespace-index helpers are copied across packages

**Problem**
The same character loop is implemented in:

- `Statement.java:41-47`
- `IRI.java:23-27`
- `BlankNode.java:25-29`
- `AliasTable.java:97-103`
- `EntryRule.java:102-109`
- `ValueParsers.java:84-91`
- `OrgParser.firstWhitespace` — `OrgParser.java:299-306`
- `ValueParsers.firstWhitespace` — `ValueParsers.java:303-310`

**Why harmful**
Eight copies of a trivial rule, with different error messages and different null-check placement. A change to what counts as whitespace (or adding a tab-specific rule) would require finding them all.

**Proposed correction**
Add `util.Strings` with `containsWhitespace`, `firstWhitespace`, and `requireNoWhitespace(value, description)`, and use it consistently. Keep each caller’s exception type and message wording local where it matters.

**Risks/uncertainties**
Adds a dependency from `rdf` to `util`; both are leaf packages, so this is acceptable.

---

### 10. Bitprint formatting is duplicated, and one copy hardcodes the prefix

**Problem**
`BitprintDigest.format` (`BitprintDigest.java:11-17`) splits the 44-byte hash into 20/24 and Base32-encodes it. `BitprintScheme.hashToUri` (`BitprintScheme.java:14-20`) repeats the identical split and encoding, then prepends the prefix. `BitprintDigest.of` (`BitprintDigest.java:19-23`) hardcodes `"urn:bitprint:"` instead of using `BitprintScheme.BITPRINTURNPREFIX`.

**Why harmful**
Two copies of the bitprint layout, and a prefix literal that can drift from the scheme constant. A change to digest layout or prefix would require finding both.

**Proposed correction**
Make `BitprintDigest.format` the single body formatter; have `BitprintScheme.hashToUri` return `BITPRINTURNPREFIX + BitprintDigest.format(hash)`; have `BitprintDigest.of` use `BitprintScheme.instance.hashToUri(digest.digest())` or at least the constant.

**Risks/uncertainties**
Low; verify no callers depend on `format` including/excluding the prefix.

---

### 11. `ValueParserContext` has a redundant overload and recomputes shared values

**Problem**

- `emitRaw(IRI, IRI, Node)` (`ValueParserContext.java:99-101`) and `emitRaw(Node, IRI, Node)` (`ValueParserContext.java:104-106`) have identical bodies; the first is unnecessary because `IRI` implements `Node`.
- `getLocation()` recomputes `DialectSupport.locationOf(header.getSpan())` on every call (`ValueParserContext.java:89-91`), including from `emitRaw` and `report`.
- `documentHash` is recomputed in the constructor (`ValueParserContext.java:45`), even though the session already computed it (finding 4).
- Datatype IRIs are built ad hoc in `ValueParsers` (`ValueParsers.java:230,243,245`) while integer/boolean/string have constants in `Vocab`.

**Why harmful**
Unnecessary API surface invites accidental use of the wrong overload; repeated location/hash derivation adds avoidable work per header; ad-hoc XSD construction is inconsistent with the rest of the vocabulary constants.

**Proposed correction**
Keep only `emitRaw(Node, IRI, Node)`, cache `location` in the constructor, accept `documentHash` as a constructor argument, and add `Vocab.XSD_DECIMAL`/`XSD_DATE`/`XSD_DATE_TIME` (or a `Vocab.xsd(...)` helper).

**Risks/uncertainties**
Low. Existing `IRI` callers still compile against the `Node` overload.

---

### 12. `MappingTable` has an asymmetric column scheme, and hides behavior in the note column

**Problem**
The table format is documented as:

```
#entry
entryType  subjectTemplate  type  note
#field
field  entryType  header  predicate  valueParser  cardinality  note
```

(`MappingTable.java:22-27`)

`#entry` has no leading tag column, while `#field` requires a leading `field` column whose only purpose is to be ignored; the parser special-cases the header at `MappingTable.java:178`. Header detection is also inconsistent: `parseEntry` checks one cell (`:155`), `parseField` checks two (`:178`).

Separately, `parseAsWritten` (`MappingTable.java:211-216`) treats any note beginning `asWritten=` as a control directive. A documentation column silently changes output.

**Why harmful**
A redundant constant column is easy to omit, and the differing header checks make the format harder to learn and validate. Reusing the note column as configuration means ordinary documentation text can accidentally alter behavior.

**Proposed correction**
Pick one uniform layout: either drop the leading `field` column, or add a leading `entry` column to `#entry`. Use one header-detection rule. Move `asWritten=` into an explicit column or a section directive, or at minimum require/document an exact prefix and validate unknown `key=` notes.

**Risks/uncertainties**
Changing the TSV layout requires updating all files under `mappings/` and any external tables. Because the vocabulary is project-local this is probably acceptable, but confirm before changing the format.

---

### 13. The `"-"` source sentinel is repeated, and `Location` uses the same string for a different concept

**Problem**
The “no source / stdin / stdout” sentinel `"-"` appears in:

- `Statement.java:19`
- `TOGETLTSVReader.java:90`
- `TOGETLTSVWriter.java:58`
- `Loader.java:230,261`
- CLI code: `ImportCommand.java:42,133,165,240`, `VocabCommand.java:131`

`Location` separately uses `"-"` for “no location” (`Location.java:85,177`), which is a different concept.

**Why harmful**
No named constant defines the source sentinel, so it is easy to mistype or to conflate the two meanings when reading code. The overloading of the same textual marker makes generic handling tempting and error-prone.

**Proposed correction**
Define a single constant, e.g. `Statement.NO_SOURCE = "-"` (or a small `Source`/`Columns` helper), and use it in the TSV reader/writer and loader. Keep `Location.NONE` distinct and name it accordingly.

**Risks/uncertainties**
Low; no serialized format change.

---

### 14. `TOGETLTSVReader` treats `data:` asymmetrically between subjects and objects

**Problem**
`parseObject` decodes `data:` URIs into literals (`TOGETLTSVReader.java:108-125`), but `parseResource` (`TOGETLTSVReader.java:101-106`) accepts any non-blank token as an `IRI`, including `data:`. `IRI.of` only rejects whitespace, so a `data:` subject is silently accepted as an IRI resource. The writer cannot produce a literal subject (`TOGETLTSVWriter.formatResource` at `:61-69` throws), so this is not a round-trip hazard in that direction—but the reader accepts input the format arguably should reject.

**Why harmful**
The reader’s validity boundary is unclear: `data:` means “literal” in object position and “IRI” in subject position. That inconsistency will surprise implementers of other readers/writers and makes malformed streams harder to diagnose.

**Proposed correction**
Either reject `data:` in `parseResource` with a `TOGETLTSVParseException`, or explicitly document that `data:` is permitted as an IRI subject. Add a round-trip/validation test either way.

**Risks/uncertainties**
Rejecting it is a behavior change for hand-written or third-party streams; choose based on intended format semantics.

---

### 15. Dead or unused API surface

**Problem**
Confirmed unused outside their declarations:

- `MappingTable.getUsedIris` — `MappingTable.java:58-72`
- `ValueParsers.isKnown` — `ValueParsers.java:54-56`
- `LoadedStream.getName` — `LoadedStream.java:32-34` (the loader uses only hash and statements)

Also unused in Java source (some may be intentional future vocabulary): `RDFS_LABEL`, `XSD_STRING`, `TOGETL_CATEGORY`, `TOGETL_CONTENT_TYPE`, `TOGETL_KEYWORD`, `TOGETL_MEMBER`, `TOGETL_MIRROR`, `TOGETL_NARRATION`, `TOGETL_OBSOLETED_BY`, `TOGETL_PARSE_ISSUE`, `TOGETL_PART_CATEGORY`, `TOGETL_PART_SET`, `TOGETL_SEE_ALSO`, `TOGETL_SOURCE`, `TOGETL_STANDARD`, `TOGETL_SUBFORMAT_OF`, `TOGETL_UNMAPPED_ENTRY`, `TOGETL_UNMAPPED_FIELD` (`Vocab.java`).

**Why harmful**
Unused methods imply capabilities that do not exist and make it harder to tell intentional extension points from leftovers. Large unused vocabulary constant blocks dilute the signal of which terms the Java code actually emits.

**Proposed correction**
Remove methods with no callers, or wire them into validation (`isKnown` could validate mapping tables; `getUsedIris` could feed `VocabularyReport` instead of re-walking rules). For vocabulary constants, either move future terms to a clearly marked “future/proposed” section or generate constants from the vocabulary files.

**Risks/uncertainties**
Confirm nothing external links against this Java API. The repository appears project-local, but `Vocab` constants are convenient for downstream scripts.

---

### 16. `Iri` vs `Uri` spelling is mixed for the same concept

**Problem**
The project’s documentation consistently says “source URI” (`AGENTS.md`), but Java APIs mix spellings:

- `SourceDocument.getSourceIri`, `getDocumentIri` — `SourceDocument.java:32-47`
- `TEFDocument.getSourceIri` — `TEFDocument.java:36`
- `SourceProvenance.getBitprintUri` — `SourceProvenance.java:37`
- `Identifiers.documentIri` — `Identifiers.java:52`
- `ImportCommand.sourceUri` — `ImportCommand.java:43`

**Why harmful**
Mostly a readability issue, but it makes searches and naming choices inconsistent and obscures that these all refer to the same URI concept.

**Proposed correction**
Standardize on one spelling for URI-valued accessors (likely `Uri`, matching the domain language) or document that `Iri` is the RDF-node concept and `Uri` the source identifier. A mechanical rename is feasible.

**Risks/uncertainties**
Broad but low-risk rename; avoid changing serialized values.

---

## Most valuable opportunities

If only a few changes are made, these give the best maintainability return:

1. **Unify the import-report model and TSV I/O** (finding 1) — removes a duplicated model, a bespoke parser, a wrong exception type, and unmanaged report kinds.
2. **Create one namespace catalog** (finding 2) — eliminates three sources of truth before more namespaces are added.
3. **Cache document hash on `SourceDocument`** (finding 4) — removes repeated hashing and four copies of the derivation.
4. **Resolve directive/comment ownership and deduplicate emitters** (finding 5) — fixes a real output asymmetry, not just duplication.
5. **Introduce shared CLI and file-discovery helpers** (findings 6–7) — removes the largest amount of mechanical duplication in the command layer.
6. **Rename `Vocab` constants by namespace** (finding 3) — improves clarity across nearly every call site.

Findings 8–16 are smaller, mostly local cleanups; they are still worth doing but can follow the structural fixes.

---

**Which would you like me to implement?** Reply with finding numbers or groups (for example “1, 2, 4, 5” or “all of 1–10”). I recommend starting with **1, 2, 4, 5, and 6**; finding 5 in particular needs your decision on whether directives inside an entry should also be emitted at document level, since fixing it changes generated output.
