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
- `./stlmod` uses the shebang `#!/usr/bin/env -S deno run`, so `deno` must be on PATH.
  Without that, it fails with "env: 'deno': No such file or directory".
- Test command: `deno test`. Type check: `deno task check`. Format: `deno fmt`.
  `deno.json` excludes `*.md` from formatting. Before that was in place, a bare `deno fmt`
  rewrapped AGENTS.md (which must stay verbatim) and README.md. Those were restored from git.
  Do not run `deno fmt` without limiting it to code paths.

## Decisions

- AGENTS.md: verbatim copies of the Worktrees, IDs for TODO items, and Commit attribution sections from S0036.
  The other S0036 sections are specific to that project.
- Stack values are `Vector3DObject` (src/vector3d-object.ts). Built from triangles: each shared
  vertex and edge is stored once, and all faces form one polyhedron in the `default` group.
- `load/stl` without a binary/text modifier detects the format from the content.
  A file is binary if its size matches the triangle count in its header, otherwise ASCII.
- `store/stl` without a modifier writes ASCII (README says store takes load's modifiers, so the default had to be chosen).
- Only `stl`, `stl/bin`, and `stl/text` are implemented; `obj` gives an unsupported-format error.
- Only `file:` URIs and plain paths work. `-` is stdin/stdout. Other URI schemes give an error for now.
- Facet normals are ignored on load and recomputed on store from the winding order.
- Store writes only the `default` group. Non-triangular polygons are an error on store.
- Coding style (CONTRIBUTING.md): tabs, LF, blank lines indented. Do not run `deno fmt` over code
  because it strips blank-line indentation. Hand-written code and a one-off awk pass were used instead.
  The awk pass rewrites files and loses the executable bit; re-run `chmod +x stlmod` after editing it.
- Winding: counter-clockwise viewed from outside, same as STL (user's decision, 2026-11-21).
  My earlier note that the user's premise was wrong was confirmed: STL is counter-clockwise,
  OpenSCAD's manual says clockwise. README's Winding Order section records this.
- Operation syntax is parsed by splitting on the first unencoded `:` (default value, literal),
  then on `;` (named params, percent-decoded), then on `/` (name and modifiers).
- Operations declare their accepted named parameters and default parameter, so unknown names are errors.
- All operation arguments are parsed and checked before any runs, so a bad argument late on the
  command line cannot leave partial side effects.
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.

## Open questions (waiting on the user)

- simplify: the bullet list ends with an unfinished "Rewriting" item.
- `3DShape` (simplify) and `3DObject` (load/store) are used for the same type. Which name should be used?
- Behavior when operations leave values on the stack at the end of a run is unspecified.
  Currently leftover values are silently discarded.
