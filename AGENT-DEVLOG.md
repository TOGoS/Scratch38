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
- Stack values are placeholders (`unknown`) until the 3DObject type exists.
- Operation syntax is parsed by splitting on the first unencoded `:` (default value, literal),
  then on `;` (named params, percent-decoded), then on `/` (name and modifiers).
- Operations declare their accepted named parameters and default parameter, so unknown names are errors.
- All operation arguments are parsed and checked before any runs, so a bad argument late on the
  command line cannot leave partial side effects.
- `crash` throws. The top level catches the error, writes it to stderr, and exits with status 1.
  It prints only the message, not a stack trace, to match the other errors.

## Open questions (waiting on the user)

- Face winding: README says clockwise when viewed from the front. Wikipedia's STL article says
  right-hand rule, counter-clockwise when viewed from outside. OpenSCAD's manual says clockwise
  when viewed from outside. Which convention does the internal model use?
- simplify: the bullet list ends with an unfinished "Rewriting" item.
- `3DShape` (simplify) and `3DObject` (load/store) are used for the same type. Which name should be used?
- Behavior when operations leave values on the stack at the end of a run is unspecified.
