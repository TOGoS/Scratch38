# Contributing

This project is intentionally lightweight.
The main conventions are here so that generated agents and humans follow the same rules.

The design principles and documentation sections are copied from `../Scratch38-S0036/CONTRIBUTING.md`.
The only change is that the devlog is named `AGENT-DEVLOG.md` here.

## Design principles

Aim for simplicity in API, then simplicity in implementation. Specifically,
recovering from badly-formed input is a non-goal. Known typos should be errors.
Other unrecognized terms should be warnings. Erroneous syntax (malformed TEF
headers, unclosed tags in Org/JSON/XML, etc.) are definitely errors. Continue
parsing only when doing so does not complicate the parser implementation.

**Do** make an effort to provide clear and specific error messages
so that problems in the source material can be easily identified and corrected
(this is one reason we go out of our way to track line/column numbers through the entire process)
or parser rules can be adjusted to accomodate intentional variations.

## Documentation

Avoid redundant documentation -- it becomes confusing when things change.

Do not document implementation details in README or archived plan documents.

When in doubt, avoid cluttering README.
Notes about the current implementation, changes you have made, or changes you are planing
may go into AGENT-DEVLOG.md under a header of the form "## <date> - <title> - <your name>"

## Code style

- Files are UTF-8 with LF line endings and a final newline (see [.editorconfig](./.editorconfig)).
- Indent with one tab per level of nesting. Do not indent with spaces.
- Blank lines inside indented code are indented too, to the same depth as the line before them.
  Do not leave a blank line completely empty inside a block.
- Do not run `deno fmt` over the project. It removes indentation from blank lines,
  so it undoes the rule above. Format by hand.

## Project notes and agent handoff

- Keep changes consistent with the architecture described in README.md
  and design / documentation principles indicated above.
- [AGENTS.md](./AGENTS.md) provides a reasonable primer for agents
  who are asked to contribute.
