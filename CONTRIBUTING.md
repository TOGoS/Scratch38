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

Do not document implementation details in README or archived plan documents.

The Java code itself is the canonical source of truth about the Java code.
Design decisions and the change-request backlog now live in the issue tracker / PR
discussion; there is no live PLAN.md or TODO.tef anymore (see `.archived-docs/`).

## Design principles

Aim for simplicity in API, then simplicity in implementation. Specifically,
recovering from badly-formed input is a non-goal. Known typos should be errors.
Other unrecognized terms should be warnings. Erroneous syntax (malformed TEF
headers, unclosed tags in Org/JSON/XML, etc.) are definitely errors. Continue
parsing only when doing so does not complicate the parser implementation.

**Do** make an effort to provide clear and specific error messages so that problems
in the source material can be easily identified and corrected (one reason we go
out of our way to track line/column numbers through the entire process) or new
rules added to the parser.

## Tooling

- The Java source tree is built with `./build.sh`.
- Use the project-local formatter for indentation-only cleanups rather than adding a heavy external dependency unless the project grows beyond its current size.
