# Contributing

This project is intentionally lightweight.
The main conventions are here so that generated agents and humans follow the same rules.

The design principles and documentation sections are copied from `../Scratch38-S0036/CONTRIBUTING.md`.
The only change is that the devlog is named `AGENT-DEVLOG.md` here.

## Design principles

Aim for simplicity in API, then simplicity in implementation. Specifically,
recovering from badly-formed input is a non-goal. Known typos should be errors.

**Do** make an effort to provide clear and specific error messages
so that problems in the source material can be easily identified and corrected.

## Documentation

Avoid redundant documentation -- it becomes confusing when things change.

Do not document implementation details in README or archived plan documents.

When in doubt, avoid cluttering README.
Notes about the current implementation, changes you have made, or changes you are planing
may go into AGENT-DEVLOG.md under a header of the form "## <date> - <title> - <your name>"

## Code style

- TypeScript source files containing unit test code should be named like `<modulename>.test.ts`,
  e.g. unit tests for `src/foomod.ts` would go in `src/foomod.test.ts`.
- Files are UTF-8 with LF line endings and a final newline (see [.editorconfig](./.editorconfig)).
- Indent with one tab per level of nesting. Do not indent with spaces.
- Blank lines inside indented code are indented too, to the same depth as the line before them.
  Do not leave a blank line completely empty inside a block.
- Format with `deno task fmt`. It runs `deno fmt`, which removes indentation from blank lines,
  and then `tools/fix-blank-lines.ts`, which restores it using the indentation of the line before.
  Do not run `deno fmt` on its own.
- Upper camel case (e.g. for types): Upper-case first letter of each word, then squish words together.
- Lower camel case (e.g. fields): Lower-case all words, then upper-case first letter of all but the first, then squish words together.
- Following this convention, "XML HTTP request" becomes `XMLHTTPRequest` (upper), or `xmlHttpRequest` (lower).

## File naming

- A TypeScript file whose default export is a type is named after that type, in PascalCase,
  e.g. `src/operation/Dup.ts` with `export default class Dup`.
- A file whose default export is not a type (an object, function, or value), or that has no default export,
  is named in lower camel case, e.g. `src/operator/dup.ts` whose default export is an object literal,
  or `src/operation/stack.ts` exporting helpers.

## Project notes and agent handoff

- Keep changes consistent with the architecture described in README.md
  and design / documentation principles indicated above.
- [AGENTS.md](./AGENTS.md) provides a reasonable primer for agents
  who are asked to contribute.
