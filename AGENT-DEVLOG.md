# Agent devlog

Notes to self about implementation progress.
Kept out of README.md, which describes the tool and its spec.

## Environment

- 2026-11-21: Deno was not installed. Installed Deno 2.9.8 into the home directory
  (`~/.deno/bin/deno`) with the official `https://deno.land/install.sh`, after reading the script.
  The installer only prompts for shell setup in an interactive terminal,
  so PATH was not modified. Use `~/.deno/bin/deno` or add that directory to PATH.
- Deno agent skills (`dx skills add denoland/skills --skill deno`): skipped.
  `dx` is not shipped with this Deno build, and `deno x` would run unvetted npm code, so I did not use it.
- `./stlmod` uses the shebang `#!/usr/bin/env -S deno run --allow-read --allow-write --allow-net`, so `deno` must be
  on PATH. Without that, it fails with "env: 'deno': No such file or directory".
- Tasks: `deno task test`, `deno task check`, `deno task fmt`.
  Use `deno task fmt`, not plain `deno fmt`: plain `deno fmt` removes blank-line indentation,
  and `deno task fmt` restores it with `tools/fix-blank-lines.ts`.
  Plain `deno fmt` also once rewrapped AGENTS.md (which must stay verbatim) and README.md.
  `deno.json` excludes `*.md` from formatting, so that should not happen again.
- Unit tests exist: `src/*_test.ts` and `tools/*_test.ts`, 94 tests as of the OBJ entry.

## Decisions

### General

- AGENTS.md: verbatim copies of the Worktrees, IDs for TODO items, and Commit attribution sections from S0036.
  The other sections are project-specific. CONTRIBUTING.md holds the copied design and documentation
  principles, plus the code style rules.
- Code style: tabs, LF, blank lines indented to the depth of the line before them.
  `tools/fix-blank-lines.ts` does that after `deno fmt`. It does not handle blank lines
  inside multi-line template strings, and there are none in this codebase.
- Operation syntax is parsed by splitting on the first unencoded `:` (default value, literal),
  then on `;` (named params, percent-decoded), then on `/` (name and modifiers).
  A default value may contain `;`, because the colon split happens first; the named-parameter form of the
  same value must be percent-encoded.
- Operations declare their accepted named parameters and default parameter, so unknown names are errors.
- All operation arguments are parsed and checked before any runs, so a bad argument late on the
  command line cannot leave partial side effects.
- Terminology: an *operator* is the function that static parameters are passed to in order to create an
  *operation*; an operation is what runs against the stack. One operator may produce operations with different
  stack shapes, depending on its parameters. Parameters cannot refer to stack values, so stack effects are
  statically analyzable. There is no `foo:${stack-value}`-style syntax for operator parameters.
- *Type* is what TypeScript can infer from a value in isolation (`classRef`); *role* is what a context expects
  of that value. Meaning comes from type plus role. Prefer one type per type/role combination unless a type is
  genuinely valid in several roles; role-specific TypeScript aliases are optional until used.
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.
- Winding: counter-clockwise viewed from outside, same as STL. OBJ faces are counter-clockwise viewed from the
  front, the same convention, so no reversal is needed. OpenSCAD's manual says clockwise; that premise was wrong.
  README's Winding Order section records this. Code that depends on the convention is marked
  `// IMPLIED WINDING ORDER APPLIES HERE`; find them with `grep -rn "IMPLIED WINDING ORDER" .`.
- `load/stl` without a binary/text modifier detects the format from the content: a file is binary if its size
  matches the triangle count in its header, otherwise ASCII.
- `store/stl` without a modifier writes ASCII.
- Facet normals are ignored on load and recomputed on store from the winding order.
- Resources: `openResource(name)` returns a `Resource` with `read()` and `write()`. Stdin/stdout, plain paths,
  `file:`, `http:`, and `https:` are supported; storing to an HTTP(S) URL is an error. Other URI schemes error.
- `validate` peeks: it checks the object on top of the stack and leaves it there. "Manifold" is the simplified
  definition from the user: every polygon is closed, and every line is used by exactly two polygon edges in
  opposite directions.
- Leftover stack values at the end of a run are silently discarded.
- `tools/fix-blank-lines.ts` is run by `deno task fmt`. Its tests are in `tools/fix-blank-lines_test.ts`.
- `src/cli_test.ts` runs `stlmod` as a subprocess. It needs `--allow-run`, which is in the test task.
- Wavefront `.mod` binary support is out of scope until needed; `obj/bin` remains unsupported.

### Revised architecture

This supersedes the earlier implementation notes after `efb5377`.

- `3DObject` is the prose name of a role, not a TypeScript identifier. Use `ThreeDObject` as the TypeScript
  identifier for now; it may be a union of concrete object types.
- `Vector3DObjectData` holds the shared vertex, line, polygon, and polyhedron lists.
  `Vector3DPolyhedronGroup` references a `Vector3DObjectData` plus a polyhedron-group index.
  `Union3DObject` contains a list of `ThreeDObject`s; it represents the union of otherwise-distinct objects,
  which may share a `Vector3DObjectData`.
  The `Dictionary<String,Object>` role is `Map<string, ThreeDObject>`.
- The special `default` sub-object is removed. It existed only to make a collection look like a single object,
  which caused the earlier ambiguity.
- A single 3D object and a collection of named entries are different stack types. `load/obj` pushes a single
  object; `load/obj/collection` pushes a `Map<string, ThreeDObject>`.
- Unnamed OBJ groups are reachable by loading the whole file as one 3D object. In a collection,
  `load/obj/collection;default-name-prefix=anonymous-` names them `anonymous-1`, `anonymous-2`, and so on.
  Without the prefix, they are excluded from the `Map<string, ThreeDObject>` role. Supporting unnamed entries
  in a collection would need a different collection type; it is not planned now.
  The OBJ loader may keep unnamed groups in a parallel list internally, then union all entries for the
  single-object case. That list is not a public stack type.
- Extracting a named entry is explicit: `extract:<name>` has shape `(collection<K,T> -- named-object:T)` and
  throws if the name is absent.
- `prune` on a single object has no names and needs no mappings. `prune/collection` keeps and renames entries.
  The `/collection` modifier is required until the parser can know the role of the top stack value.
- A mapping is a semicolon-separated list of entries of the form `(<new name> "=") <old name>`; a bare old name
  keeps its name. Duplicate new names are an error, since there is no correct way to combine them.
  Example: `prune/collection:fred=ted;main;cube=jon`.
- `rename-entries` is "prune without pruning", for collections only. It uses the same mapping grammar and keeps
  unmapped entries. Spell it `rename-entries:<mapping>` or
  `rename-entries;mapping=<the same mapping string, percent-encoded>`, e.g.
  `rename-entries:fred=ted;cube=jon`.
- `simplify` never merges polyhedron groups; polyhedrons within one group may be merged. It works in coordinate
  space with outward-facing loops, so equal coordinates are shared. Passes: merge polyhedrons sharing an
  oppositely oriented polygon; merge coplanar loops sharing an edge; remove vertexes with exactly two collinear
  neighbours; rebuild. `simplify/keep-unused` appends the result after the original parts; plain `simplify`
  prunes them. Deduplicate only obvious results: cache a simplified polyhedron, reuse one index for identical
  results, and keep the original when simplification changes nothing.
- `obj/bin` remains unsupported; free-form curves/surfaces, `l`, and `p` in OBJ are errors.

### Punted to future

- Role-aware parsing, which would let the `/collection` modifier on `prune` become optional.
- Store/writer behavior for collections.
- Whether `simplify` on a collection simplifies the whole collection or one extracted object.
- A better naming system for identifiers that start with a digit (see To-do).

### Known divergences in current code

- `Vector3DObject` still combines data, polyhedron groups, and names with a `default` group; split it into
  `Vector3DObjectData` and `Vector3DPolyhedronGroup`.
- `prune;main-object-name=...` still exists; replace it with `prune` and `prune/collection` mappings.
- `store`, `validate`, `simplify`, and `trianglesFromVector3DObject` still use the `default` group; make them
  role-specific.
- OBJ load/store still produce and consume one combined object with `default`; add `load/obj/collection`,
  `extract`, `default-name-prefix`, `Union3DObject` for the single-object union, and a matching writer choice.
- `simplify` currently merges across all polyhedrons of `default`; make it group-scoped.
- `rename-entries` is not implemented.
- README and tests still describe the old architecture; update them during the refactor.

## To-do

Keep this section at the end of the devlog until we have a more formal task list.

- Figure out a better system for munging names that start with numbers, such as `Object3D` instead of
  `ThreeDObject`, then apply it consistently and all at once.
- Refactor the model into `Vector3DObjectData`, `Vector3DPolyhedronGroup`, and `Union3DObject`, with
  `Map<string, ThreeDObject>` for collections.
- Implement the revised operators: `load/obj/collection`, `extract`, `prune/collection`, `rename-entries`.
- Decide the punted items above before merging this branch to master.
- Update README and tests to match the revised architecture.
