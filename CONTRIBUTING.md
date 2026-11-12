# Contributing

This project is intentionally lightweight.
The main conventions are here so that generated agents and humans follow the same rules.

## Java style

- Use tabs for indentation in Java source files.
- Use spaces only for alignment after tabs if needed.
- Preserve indentation on blank lines inside blocks; a blank line should still carry the block's indent.
- The repository config is in [.editorconfig](./.editorconfig).
- Run `./scripts/format-java-indent.sh` on Java files before committing formatting changes.

## Project notes and agent handoff

- Design decisions belong in [PLAN.md](./PLAN.md).
- Change requests and execution backlog belong in [TODO.tef](./TODO.tef).
- Keep the backlog concise and acceptance-oriented.

## Tooling

- The Java source tree is built with `./build.sh`.
- Use the project-local formatter for indentation-only cleanups rather than adding a heavy external dependency unless the project grows beyond its current size.
