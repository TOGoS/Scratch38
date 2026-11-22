# Agent devlog

Notes to self about implementation progress.
Kept out of README.md, which describes the tool and its spec.

## Environment

- 2026-11-21: Deno was not installed. Installed Deno 2.9.8 into the home directory
  (`~/.deno/bin/deno`) with the official `https://deno.land/install.sh`, after reading the script.
  The installer only prompts for shell setup when run in an interactive terminal,
  so PATH was not modified. Use `~/.deno/bin/deno` or add that directory to PATH.
- Deno agent skills (`dx skills add denoland/skills --skill deno`): skipped.
  `dx` is not shipped with this Deno build, and `deno x` would run unvetted npm code, so I did not use it.
- `./stlmod` uses the shebang `#!/usr/bin/env -S deno run --allow-read --allow-write`, so `deno` must be on PATH.
  Without that, it fails with "env: 'deno': No such file or directory".
- Tasks: `deno task test`, `deno task check`, `deno task fmt`.
  Use `deno task fmt`, not plain `deno fmt`: plain `deno fmt` removes blank-line indentation,
  and `deno task fmt` restores it with `tools/fix-blank-lines.ts`.
  Plain `deno fmt` also once rewrapped AGENTS.md (which must stay verbatim) and README.md.
  `deno.json` excludes `*.md` from formatting, so that should not happen again.
- Unit tests exist: `src/*_test.ts` and `tools/*_test.ts`, 94 tests as of the OBJ entry.

## Decisions

- AGENTS.md: verbatim copies of the Worktrees, IDs for TODO items, and Commit attribution sections from S0036.
  The other sections are project-specific. CONTRIBUTING.md holds the copied design and documentation
  principles, plus the code style rules.
- Code style: tabs, LF, blank lines indented to the depth of the line before them.
  `tools/fix-blank-lines.ts` does that after `deno fmt`. It does not handle blank lines
  inside multi-line template strings, and there are none in this codebase.
- Operation syntax is parsed by splitting on the first unencoded `:` (default value, literal),
  then on `;` (named params, percent-decoded), then on `/` (name and modifiers).
- Operations declare their accepted named parameters and default parameter, so unknown names are errors.
- All operation arguments are parsed and checked before any runs, so a bad argument late on the
  command line cannot leave partial side effects.
- Terminology: an *operator* is the function that static parameters are passed to in order to create an
  *operation*; an operation is what runs against the stack. One operator may produce operations with different
  stack shapes, depending on its parameters. Parameters cannot refer to stack values, so stack effects are
  statically analyzable. There is no `foo:${stack-value}`-style syntax for operator parameters.
- *Type* is what TypeScript can infer from a value in isolation (`classRef`); *role* is what a context expects
  of that value. Meaning comes from type plus role. Give each role its own type, sharing structure where useful;
  do not reuse one `Vector3DObject` type as both a single object and a collection just because role-checking
  would allow it.
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.
- Stack values are currently `Vector3DObject` (src/vector3d-object.ts). Built from triangles: each shared
  vertex and edge is stored once, and all faces form one polyhedron in the `default` group. The role/type and
  single-object/collection decisions below supersede this; it will be split into separate types.
- Winding: counter-clockwise viewed from outside, same as STL (user's decision).
  The premise for the earlier clockwise choice was wrong: STL is counter-clockwise,
  and OpenSCAD's manual says clockwise. README's Winding Order section records this.
  Code that depends on the convention is marked with `// IMPLIED WINDING ORDER APPLIES HERE`.
  Find them with `grep -rn "IMPLIED WINDING ORDER" .`.
- `load/stl` without a binary/text modifier detects the format from the content.
  A file is binary if its size matches the triangle count in its header, otherwise ASCII.
- `store/stl` without a modifier writes ASCII (README says store takes load's modifiers, so the default had to be chosen).
- Only `stl`, `stl/bin`, and `stl/text` are implemented; `obj` gives an unsupported-format error.
- Resources: `openResource(name)` returns a `Resource` with `read()` and `write()`.
  Stdin/stdout and `file:`/plain-path resources are implemented. Other URI schemes give an error.
  To add http/https, add a case to `parseResourceName` and `openResource`. Operations don't change.
- Facet normals are ignored on load and recomputed on store from the winding order.
- Store writes only the `default` group. Non-triangular polygons are an error on store.
- `prune` keeps only the groups named by its `mapping` parameter, plus what they reference, and renumbers
  each index array in its original order. See "Agreed decisions after the first implementation round" below.
- `validate` peeks: it checks the object on top of the stack and leaves it there (user's decision).
  "Manifold" is the simplified definition the user gave: every polygon of the `default` object is closed,
  and every line is used by exactly two polygon edges going in different directions.
  `checkManifold` implements it, and README says it is provisional.
- The name for the type is `3DObject` everywhere (user's decision). The README's simplify shape is updated to match.
- Leftover stack values at the end of a run are silently discarded (user's decision).
- `tools/fix-blank-lines.ts` is run by `deno task fmt`. Its tests are in `tools/fix-blank-lines_test.ts`.
- `src/cli_test.ts` runs `stlmod` as a subprocess. It needs `--allow-run`, which is in the test task.

### Agreed decisions after the first implementation round

- Polyhedron groups (top-level sub-objects) are never merged by `simplify`. Polyhedrons within the same
  group may be merged. This preserves object names, since each named `.obj` object is one polyhedron group.
- `simplify` deduplicates only when the result is obvious: cache the simplified form of a polyhedron,
  reuse the existing polyhedron index when the same simplified shape appears in several places, and keep
  the original polyhedron if simplification produces an identical result. Do no extra computation to find
  less obvious duplicates.
- `prune/collection` has a default parameter named `mapping`. Its value is a comma-separated list of entries
  of the form `(<new name> "=") <old name>`. With no `=`, the entry keeps its name; with `=`, it is renamed.
  Example: `prune/collection:fred=ted,main,cube=jon` keeps `main`, renames `ted` to `fred`, renames `jon`
  to `cube`, and removes all entries not mentioned, along with anything they alone reference.
- A separate `rename-entries` operation is agreed in principle: "prune without pruning", for collections only.
  It should use the same mapping syntax but keep unmapped entries. Its parameter details still need to be settled.
- Wavefront `.mod` binary support stays out of scope until needed; `obj/bin` remains unsupported.

### Second revision: single objects vs collections (agreed)

- A single 3D object and a collection of named entries are different stack types. `load/obj/collection` pushes
  a collection; other OBJ loads push a single 3D object.
- Extracting a named entry is explicit: `extract:<name>` has shape `(collection<K,T> -- named-object:T)` and
  throws if the name is absent.
- `prune` on a single object has no names and needs no mappings. `prune/collection` uses the name mappings
  discussed above. The `/collection` modifier is required until the parser can know the role of the top stack
  value.
- The special `default` sub-object is removed. It existed only to make a collection look like a single object,
  which is the source of this ambiguity.
- Multiple mapping entries that produce the same new name are an error; there is no correct way to combine them.
- `rename-entries` remains "prune without pruning", but only makes sense for collections.

#### Decisions that need revision

- The `default` group in `Vector3DObject`, and the rule that store/validate/simplify operate on `default`, must be
  replaced by explicit single-object or collection operations.
- The `prune` mapping decision above now applies only to the collection form. `default=jon` is no longer a special
  case; `default` is just an ordinary name when used in a collection.
- The plan that `simplify` preserves names by preserving polyhedron groups needs revisiting: a single object has no
  names, while a collection needs an explicit choice between simplifying the whole collection and simplifying one
  extracted object.
- The OBJ decisions need revising: `load/obj` must say which stack type it pushes, unnamed `o` groups and the old
  `default` need a defined representation in the collection form, and the writer needs a matching single/collection
  choice.
- The open questions about synthesizing `default` are resolved by removing `default`; duplicate new names are an
  error. `load/obj/collection`, `extract:<name>`, and required `prune/collection` are settled. The rename operator
  is `rename-entries`; its parameter details remain open.

- `simplify` (src/simplify.ts) works in coordinate space: each polygon is an outward-facing loop of coordinates,
  so vertexes with equal coordinates are merged automatically. Passes, in order:
  1. Within each polyhedron group, merge polyhedrons that share a polygon with opposite orientation, dropping that polygon.
  2. Within each merged group, merge coplanar loops sharing an edge (skipping merges that would repeat a vertex).
  3. Remove vertexes with exactly two neighbours on a straight line (tolerance 1e-6).
  4. Rebuild the object. `simplify/keep-unused` appends the result after the original parts, which are kept;
     plain `simplify` then prunes to `default`.
  Object names are preserved by keeping polyhedron groups intact (see the agreed decisions above).
- HTTP and HTTPS URIs are loaded with `fetch` (`resource.ts`). Storing to them is an error.
  The shebang and the test task have `--allow-net`, and tests use a local `Deno.serve` on 127.0.0.1.
- OBJ (`src/obj.ts`): winding was checked against the Wavefront spec (https://paulbourke.net/dataformats/obj/).
  Faces are counter-clockwise when viewed from the front, the same as this project, so no reversal is needed
  (marked `IMPLIED WINDING ORDER APPLIES HERE`).
  Each `o` becomes a named polyhedron group; `default` holds all of them.
  The builder accepts names, and the OBJ writer emits `o` before every polyhedron (bare when unnamed), so names
  round-trip. `prune` now renames and keeps groups through its mapping parameter; see the agreed decisions above.
  `obj/bin` is rejected (OBJ is text only here). Free-form curves/surfaces, `l`, and `p` are errors.
- `validate` and `checkManifold`: a simplified definition from the user. Every polygon is closed, and every line is
  used by exactly two polygon edges in opposite directions.

## Open questions (waiting on the user)

- How should an unnamed OBJ `o` group be represented in a collection: a synthetic name, a positional reference,
  or an error?
- What are the stack type names for a single 3D object, a collection, and a named entry?
- What parameter name and shape should `rename-entries` use?

