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
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.
- Stack values are `Vector3DObject` (src/vector3d-object.ts). Built from triangles: each shared
  vertex and edge is stored once, and all faces form one polyhedron in the `default` group.
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
- `prune` has a default parameter named `mapping`. Its value is a comma-separated list of entries of the
  form `(<new name> "=") <old name>`. With no `=`, the group keeps its name; with `=`, it is renamed.
  Example: `prune:fred=ted,main,default=jon` keeps `main` as `main`, renames `ted` to `fred`, renames `jon`
  to `default`, and removes all groups not mentioned, along with anything they alone reference.
- A separate `rename-sub-objects` operation is agreed in principle: "prune without pruning". It should use
  the same mapping syntax but keep unmapped groups. Its exact name and parameter details still need to be
  settled before implementation.
- Wavefront `.mod` binary support stays out of scope until needed; `obj/bin` remains unsupported.

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

- If a `prune` mapping does not map anything to `default`, should the result have no `default` group,
  or should one be synthesized from the kept groups? Downstream operations currently assume `default`.
- What should happen when several mapping entries produce the same new name, and what is the final
  name and parameter shape for `rename-sub-objects`?

