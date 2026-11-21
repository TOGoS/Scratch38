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
- `prune` keeps only what the main object references, renumbers each index array in its original order,
  and makes the main object the `default`. Its default name is `default`.
  The main object becomes `default` (user's decision). Keeping several named objects is a future extension.
- `validate` peeks: it checks the object on top of the stack and leaves it there (user's decision).
  "Manifold" is the simplified definition the user gave: every polygon of the `default` object is closed,
  and every line is used by exactly two polygon edges going in different directions.
  `checkManifold` implements it, and README says it is provisional.
- The name for the type is `3DObject` everywhere (user's decision). The README's simplify shape is updated to match.
- Leftover stack values at the end of a run are silently discarded (user's decision).
- `tools/fix-blank-lines.ts` is run by `deno task fmt`. Its tests are in `tools/fix-blank-lines_test.ts`.
- `src/cli_test.ts` runs `stlmod` as a subprocess. It needs `--allow-run`, which is in the test task.

- `simplify` (src/simplify.ts) works in coordinate space: each polygon is an outward-facing loop of coordinates,
  so vertexes with equal coordinates are merged automatically. Passes, in order:
  1. Merge polyhedrons that share a polygon with opposite orientation, dropping that polygon.
  2. Within each merged group, merge coplanar loops sharing an edge (skipping merges that would repeat a vertex).
  3. Remove vertexes with exactly two neighbours on a straight line (tolerance 1e-6).
  4. Rebuild the object. `simplify/keep-unused` appends the result after the original parts, which are kept;
     plain `simplify` then prunes to `default`.
  Known limitation: simplify drops object names (merged polyhedrons have no single name).
- HTTP and HTTPS URIs are loaded with `fetch` (`resource.ts`). Storing to them is an error.
  The shebang and the test task have `--allow-net`, and tests use a local `Deno.serve` on 127.0.0.1.
- OBJ (`src/obj.ts`): winding was checked against the Wavefront spec (https://paulbourke.net/dataformats/obj/).
  Faces are counter-clockwise when viewed from the front, the same as this project, so no reversal is needed
  (marked `IMPLIED WINDING ORDER APPLIES HERE`).
  Each `o` becomes a named polyhedron, which gets its own named group. `default` holds all of them.
  The builder accepts names, and the OBJ writer emits `o` before every polyhedron (bare when unnamed), so names
  round-trip. `prune;main-object-name=X` keeps the named polyhedron, but it becomes `default` and loses its name.
  `obj/bin` is rejected (I took OBJ to be text only). Free-form curves/surfaces, `l`, and `p` are errors.
- `validate` and `checkManifold`: a simplified definition from the user. Every polygon is closed, and every line is
  used by exactly two polygon edges in opposite directions.

## Open questions (waiting on the user)

- `obj/bin`: the README listed it. Keep it as unsupported, or implement the Wavefront binary `.mod` format?
- Simplify drops object names. Should it keep names where the merged polyhedrons still correspond to one?

