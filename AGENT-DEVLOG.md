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
  The tests describe the architecture before 2026-11-21; see Known divergences.

## Decisions (2026-11-21)

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
  of that value. Meaning comes from type plus role. `role:Foo` and `type:Foo` are related but separate concepts.
  Role applies to the place, type to the actual data structure. (In a language with ADTs, a role would be an ADT
  whose variants are the types that can fill it.) Prefer one type per type/role combination unless a type is
  genuinely valid in several roles.
- Naming: "3D" goes right after the word it qualifies, e.g. `Object3D`, `Polyhedron3DData`, and `UnionObject3D`. Type names put "how it is represented" before "what it represents".
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.
- Winding: a polygon's interior is on the left of each directed edge, so outer boundaries run counter-clockwise
  and hole boundaries clockwise viewed from outside. This matches STL and OBJ, so neither needs reversal.
  OpenSCAD's `polyhedron` defines faces clockwise viewed from outside, the opposite convention, so importing or
  exporting OpenSCAD polyhedrons requires reversing each face's vertex order.
  README's Winding Order section records this. Code that depends on the convention is marked
  `// IMPLIED WINDING ORDER APPLIES HERE`; find them with `grep -rn "IMPLIED WINDING ORDER" .`.
- `load/stl` without a binary/text modifier detects the format from the content: a file is binary if its size
  matches the triangle count in its header, otherwise ASCII.
- `store/stl` without a modifier writes ASCII.
- Facet normals are ignored on load and recomputed on store from the winding order.
- Resources: `openResource(name)` returns a `Resource` with `read()` and `write()`. Stdin/stdout, plain paths,
  `file:`, `http:`, and `https:` are supported; storing to an HTTP(S) URL is an error. Other URI schemes error.
- `validate` peeks: it checks the object on top of the stack and leaves it there.
  "Manifold" is the simplified definition from the user: each polygon's edges form closed boundaries (no branching
  or dangling ends), and every line is used by exactly two polygon edges in opposite directions.
- Leftover stack values at the end of a run are silently discarded.
- `tools/fix-blank-lines.ts` is run by `deno task fmt`. Its tests are in `tools/fix-blank-lines_test.ts`.
- `src/cli_test.ts` runs `stlmod` as a subprocess. It needs `--allow-run`, which is in the test task.
- Wavefront `.mod` binary support is out of scope until needed; `obj/bin` remains unsupported.

### Architecture

- `Object3D` is the role of anything on the stack that represents one 3D object.
  In TypeScript the role is written `type Object3D = Polyhedron3D | UnionObject3D;` (user's decision).
  The alias lists the types that currently play the role, and it is what makes `UnionObject3D`'s member list describable (`Object3D[]`).
  Adding a new implementation means editing the alias, as with any closed union.
  - `Polyhedron3D`: a reference to one polyhedron within a `Polyhedron3DData`.
  - `UnionObject3D`: a union of a list of `Object3D`s, whatever their representation.
    Several polyhedrons together are a `UnionObject3D` of `Polyhedron3D`s.
    It is abstract: whether the interpreter can handle a given member is a separate question.
- `Polyhedron3DData` holds the geometry that these refer to: vertexes, lines, polygons, and polyhedrons.
  It has no polyhedron groups and no names. A polyhedron group is a `UnionObject3D` of `Polyhedron3D`s.
- `Polyhedron3DData` and the objects that refer to it are effectively immutable. An operation that changes geometry
  makes a new `Polyhedron3DData` by copying the old lists and appending to the copies. The old data is the base, so
  unchanged parts are referenced rather than rewritten. Structural sharing (e.g. a persistent tree list) is deferred.
  - Idea: A list type that is simply a reference to a shared, append-only array, and a length,
    may be a suitable 'effectively immutable even while the backing list is shared' data structure!
- `simplify` and `validate` accept any polyhedron-based `Object3D`: one whose leaves are all `Polyhedron3D`s,
  however they are nested in `UnionObject3D`s (user's decision). The leaves are treated as one set, so nested unions
  do not separate them. Polyhedrons from anywhere in the object may be merged.
  Passes: merge polyhedrons sharing an oppositely oriented polygon; merge coplanar loops sharing an edge; split loops
  that use a line twice into outer and hole loops; remove vertexes with exactly two collinear neighbours; rebuild.
  The output's new data includes the whole old data as its base. `simplify` does not prune; `prune` is a separate
  operation.
- `prune` writes a new `Polyhedron3DData` containing only what the object refers to, unless the data is already pruned.
  `prune` works on the whole object, however its unions are nested.
- `validate` checks the leaves together, as one set, however they are nested.
- OBJ: `load/obj` produces one `UnionObject3D` of `Polyhedron3D`s, one per `o` section, plus one for
  faces before the first `o`. Object names are not kept, since names only matter once there are dictionaries.
- OBJ: `store/obj` writes each polyhedron as an unnamed object (a bare `o` line).
  STL: `store/stl` writes every polyhedron of the object as one solid.
- Storing a collection is not part of this work (see Punted).
- `obj/bin` remains unsupported; free-form curves/surfaces, `l`, and `p` in OBJ are errors.
- Coplanar merging: `mergeAcross` takes the longest run of consecutive shared lines (reverse-traversed) and drops only that run.
  Other shared runs stay. `splitSharedLines` then removes each line used twice in opposite directions and splits the
  outline into the outer and hole loops of one polygon. Spurs (a vertex whose two neighbours are the same) are removed
  by `removeSpurs`. The old rejection of repeated vertexes is gone.
- Face matching across polyhedrons uses `faceKey`, a sorted multiset of directed edges. Sorting makes it independent
  of where a loop starts and handles repeated vertexes; the earlier `canonicalLoop` missed equal faces whose loop
  repeated a vertex.
- Deterministic order: polyhedron faces are matched in input order (first unmatched partner), and coplanar merges
  scan loops by index. A polyhedron sharing several faces with another loses all of those shared faces.
- Deduplicate only obvious results: keep the original when a simplification changes nothing, and reuse one index
  for identical results.

### Punted to future

- Collections: `Dictionary<string, Object3D>` (represented by `Map<string, Object3D>`), named OBJ objects, `extract`,
  `prune/collection`, `rename-entries`, `default-name-prefix`, and mapping grammar. Thought experiment only, not to be
  implemented yet. Notes so far:
  - Loading OBJ into a collection (`load/obj/collection`) would need names; a collection entry would be a
    `UnionObject3D` of that name's polyhedrons, sharing one `Polyhedron3DData`.
  - Unnamed groups cannot go into a collection without a prefix; with a prefix (`anonymous-1`, ...) they can.
    Any mapping or generated name that collides with an existing name is an error.
  - Writing a collection to a format with one object is an error. Writing a single object to OBJ is a list of
    unnamed objects, so a collection could be written to OBJ with names only if each entry is one polyhedron.
  - Duplicate `o name` in a file would be an error for a collection.
- Role-aware parsing, which would let `/collection` become optional where the role is obvious.
- Whether `simplify` on a collection simplifies the whole collection or one extracted object.
- A better naming system for identifiers that start with a digit (see To-do).

### Implementation (2026-11-21)

Implemented in `src/object3d.ts`, which replaces `vector3d-object.ts`:
- `Polyhedron3DData`, `Polyhedron3D`, `UnionObject3D`, and `type Object3D = Polyhedron3D | UnionObject3D`,
  with `readonly` fields so the compiler enforces immutability.
- `makeObject3dFromPolygonLoops(polyhedra, base?)`: one builder. Each polygon is given as loops of vertexes;
  these are flattened into one unordered list of directed edges in the stored data. Without a base it creates fresh
  data. With a base it copies the base's lists, reuses base vertexes and lines whose coordinates or endpoints match,
  and appends. A single polyhedron gives a `Polyhedron3D`; more than one gives a `UnionObject3D` of them.
  `makeObject3d` is the convenience form where each polygon has one loop.
- `pruneObject3d`: writes a new data per source data, containing only what the leaves use, renumbered in order.
  The union structure is kept.
- `checkManifold`: works on coordinates, so leaves from different data are checked together. Each directed edge must
  be used as many times as its reverse. That gives "two uses in opposite directions, or some multiple of these pairs".
- `simplify` (`src/simplify.ts`): `simplifyObject3d` runs the merge and split passes on the leaves' loops, then builds
  polygons from the resulting loop groups. If the leaves share one data, the new parts are appended to it (the base).
  Otherwise they go into fresh data. If nothing changes (same counts of polyhedrons, loops, and vertexes), it returns
  the input object. It does not prune.
- `prune` is a separate operation. `simplify` is not followed by `prune` automatically.
- `dup` (`(a -- a a)`) is new.
- OBJ: each `o` starts a new polyhedron, and object names are dropped. The writer emits a bare `o` before each
  polyhedron. The model has no names, so this is a lossy but consistent round trip.
- STL: `store/stl` writes all leaves as one solid, and fails on non-triangular polygons.

The old `default`, `namedGroups`, `polyhedronGroups`, `prune;main-object-name`, and `simplify/keep-unused` are gone.

## Open questions (waiting on the user)

- Resolved: `Polyhedron3DUnion` is dropped. A polyhedron group is a `UnionObject3D` of `Polyhedron3D`s (user's decision).
- Resolved: nested unions are flattened for `simplify` and `validate`; the leaves form one set (user's decision).
  Partly settled in code: leaves from different data are simplified together, and the output goes into fresh data
  (no base). Confirm this.
- `obj/bin`: the README listed it. Keep it as unsupported, or implement the Wavefront binary `.mod` format?

## 2026-11-22 - Polygons with holes - Master Pickle

A polygon's boundary is now a flat, unordered list of directed edges, not a list of loops.
Grouping edges into loops is a separate concern (`loopsOfEdges`), used by OBJ/STL output and
by coplanar merging. A polygon may have several boundaries (an outer boundary and holes);
its interior is always on the left of each directed edge, so holes run clockwise when viewed
from outside.

`simplify` removes every line a polygon shares with itself. After a coplanar merge,
a line used twice in opposite directions is a cut between an outer boundary and a hole.
`splitSharedLines` drops both uses and groups the remaining edges into loops with `loopsOfEdges`.

Splitting a polygon back into single-loop polygons for export is still not implemented.
`store/obj` and `store/stl` both fail on a polygon with holes; STL also rejects other non-triangular polygons.

## 2026-11-22 - Lower-camel-case names ending in 3D - Master Pickle

Renamed lower-camel-case identifiers ending in `3D` to end in `3d`:
`makeObject3d`, `simplifyObject3d`, `pruneObject3d`, `trianglesOfObject3d`,
and `loopsOfObject3d`. Upper-camel-case type names such as `Object3D` are unchanged.

- `fix-polygons` (`src/fixPolygons.ts`, `src/triangulate.ts`): faces are projected onto the axis their vector area
  points along, holes are bridged to the outer loop (nearest visible vertex), and the result is ear-clipped.
  Triangles keep the face's outward orientation. `convex` merges triangles with `mergeAcross` (exported from
  `simplify.ts`) while the result stays convex.
  `single-loop`: faces with holes are triangulated, then merged back by `mergePieces` using the single-loop
  merge rule. Faces with a single loop are left alone, even when concave.
- Single-loop merge rule (`mergeAcross(f, g, true)`, also used by `simplify/single-loop-polygons`): merge only when
  f and g share exactly one run of edges, and the result has no repeated vertex or self-touching line. Gluing two
  disks along one arc gives a disk. Sharing two runs, or touching at an extra point, does not (it encloses a hole
  or pinches). Plain `simplify` keeps the old behaviour and may leave holes, which is allowed.
  `convex` merging also uses this rule.
  Known limits: faces made of several separate regions, and holes whose bridge crosses an existing edge, are errors.
  Tests in `src/fixPolygons.test.ts` check vector area, total area, manifoldness, and the target constraint.
- Operators and operations, split into modules:
  - `src/operators.ts`: the registry and token parsing (`instantiateOperation`, `runOperations`, `argumentsOf`).
  - `src/operator/<name>.ts`: each operator's parameters and validation. Default-exports an object literal
    (`satisfies Operator`), not a per-operator type; operators are just functions from arguments to operations.
    It passes the validated values to its operation's constructor.
  - `src/operator/Operator.ts`: the `Operator` type and `OperatorArguments` (modifiers and named parameter values,
    with the default value already filled in).
  - `src/operation/<Name>.ts`: each operation as a class with an async `apply(stack)` that returns a new stack.
  - `src/operation/Operation.ts`: the `Operation` type.
  Parameters describe what an operator accepts; arguments are the actual values.
  The file layout follows the naming convention in CONTRIBUTING.md.

## To-do

Keep this section at the end of the devlog until we have a more formal task list.

- STL can only represent triangles, and OBJ faces cannot express holes.
  - Both `store/stl` and `store/obj` throw when a polygon cannot be written; STL rejects non-triangular polygons.
  - To make them work we'll have to split polygons, including polygons with holes, back up into triangles
    (or use another OBJ representation, such as free-form objects).
- Consider a shared, append-only list type so appending to a base does not need to copy the backing array.
- Collections are deferred; see Punted.
- Figure out a better system for names that start with digits (for example `Object3D` versus `ThreeDObject`).
