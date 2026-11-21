# AGENTS.md

The Worktrees, IDs for TODO items, and Commit attribution sections are copied verbatim from `../Scratch38-S0036/AGENTS.md`.
The other sections were written for this project.

## Coding style

See [CONTRIBUTING.md](./CONTRIBUTING.md) for design principles, documentation rules, and code style.
The main rules for code you write:

- Indent with one tab per level. Blank lines inside indented code are indented to the same depth.
- Use LF line endings, UTF-8, and a final newline.
- Format with `deno task fmt`, not plain `deno fmt`. Plain `deno fmt` removes indentation from blank lines.

## Validation

Run these after code changes:

```sh
deno test
deno task check
```

## Worktrees

When doing work in a branch, create a worktree in `./.worktrees/` + short branch name.

## IDs for TODO items

When adding new items to a project task queue (e.g. TODO.org),
remove the first line from `.available-ticket-ids.txt` and use that as the ID.
This file is not versioned, and different IDs are listed in different checkouts.
This guarantees each task gets a unique ID,
races between checkouts notwithstanding (but this should not happen
given that you will normally only write new tasks in response to a
conversation with the architect).

## Commit attribution

When an agent makes a commit entirely on its own, set author identity per commit command
(e.g. using `git -c user.name=... -c user.email=... commit ...`),
rather than changing repository or global git config.

The identity encodes the agent and the tool that ran it:

- Name: the agent's display name, e.g. `Master Haiku`.
- Email: `togos00+<agent>.<harness>@gmail.com`, where `<agent>` is the display name in lowercase
  with spaces replaced by dots, and `<harness>` is `opencode`, `copilot`, or the tool's name in lowercase.
  The `togos00+` prefix records that the owner is responsible for the commit.

For example, `Master Haiku <togos00+master.haiku.opencode@gmail.com>`. The same agent running
under CoPilot uses `togos00+master.haiku.copilot@gmail.com`.
