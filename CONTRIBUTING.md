# Contributing

This project is intentionally lightweight.
The main conventions are here so that generated agents and humans follow the same rules.

## Java style

- Use tabs for indentation in Java source files.
- Use spaces only for alignment after tabs if needed.
- Preserve indentation on blank lines inside blocks; a blank line should still carry the block's indent.
- The repository config is in [.editorconfig](./.editorconfig).
- Run `./scripts/format-java-indent.py` on Java files before committing formatting changes.

## Project notes and agent handoff

- Keep changes consistent with the architecture described in README.md.
- [AGENTS.md](./AGENTS.md) provides a reasonable primer for Clankers who
  are asked to contribute.

A good prompt to get your sessions primed:

> I would like you to internalize the information in AGENTS.md and perhaps do some extra reading on the side.
> I will then fork this conversation for new tasks in order to have the core information cached.

In VS Code, you can then `/fork My new pre-primed session` to have that info cached
and save some processing time.

## Documentation

Avoid redundant documentation -- it becomes confusing when things change.

Do not document implementation details in documents such as PLAN / README.

The Java code itself is the canonical source of truth about the Java code.
PLAN should be reserved for aspirations ('let's stream data instead of loading files into strings')
and not bother itself with what the current implementation happens to be
except when changing it is relevant to, like, a milestone or something.

## Tooling

- The Java source tree is built with `./build.sh`.
- Use the project-local formatter for indentation-only cleanups rather than adding a heavy external dependency unless the project grows beyond its current size.
