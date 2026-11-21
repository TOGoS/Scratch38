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
- Unit tests exist: `src/*_test.ts` and `tools/*_test.ts`, 59 tests as of this entry.

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
  Choosing the main object as the new `default` is my reading of the README, and is worth confirming.

## Open questions (waiting on the user)

- `3DShape` (simplify) and `3DObject` (load/store/prune) are used for the same type. Which name should be used?
- Behavior when operations leave values on the stack at the end of a run is unspecified.
  Currently leftover values are silently discarded.
- `validate` is specified as `(3DObject --)`, which consumes the object. Is that intended?
  That would make `load ... validate store` impossible.
- `prune`: should the result's `default` be the main object (as implemented), or should the main object keep its name?
- OBJ winding order has not been checked. Check it before implementing OBJ.
