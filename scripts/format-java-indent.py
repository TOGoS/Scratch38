#!/usr/bin/env -S uv run --script
"""Normalize leading indentation in Java source files.

Indentation is rewritten as tabs in multiples of four columns, with any
remaining columns as spaces. Blank lines are re-indented to match the
surrounding code. Files that need no changes are left untouched.
"""

from pathlib import Path
import argparse
import re
import sys

TAB_WIDTH = 4


def indent_width(prefix: str) -> int:
    return len(prefix.expandtabs(TAB_WIDTH))


def indent_for_width(width: int) -> str:
    return ("\t" * (width // TAB_WIDTH)) + (" " * (width % TAB_WIDTH))


def normalize_leading_indent(prefix: str) -> str:
    return indent_for_width(indent_width(prefix))


def leading_indent(line: str) -> str:
    return re.match(r"^[ \t]*", line).group(0)


def split_newline(line: str) -> tuple[str, str]:
    if line.endswith("\r\n"):
        return line[:-2], "\r\n"
    if line.endswith("\n"):
        return line[:-1], "\n"
    return line, ""


def blank_line_indent(lines: list[str], index: int) -> int:
    prev_width = 0
    for j in range(index - 1, -1, -1):
        candidate = lines[j].rstrip("\r\n")
        if candidate.strip() != "":
            prev_width = indent_width(leading_indent(candidate))
            break

    next_width = 0
    for j in range(index + 1, len(lines)):
        candidate = lines[j].rstrip("\r\n")
        if candidate.strip() != "":
            next_width = indent_width(leading_indent(candidate))
            break

    return max(prev_width, next_width)


def normalize_text(text: str) -> str:
    lines = text.splitlines(True)
    for i, line in enumerate(lines):
        body, newline = split_newline(line)
        if body.strip() == "":
            lines[i] = indent_for_width(blank_line_indent(lines, i)) + newline
        else:
            prefix = leading_indent(body)
            suffix = body[len(prefix):]
            lines[i] = normalize_leading_indent(prefix) + suffix + newline
    return "".join(lines)


def find_java_files(roots: list[Path]) -> list[Path]:
    java_files: list[Path] = []
    for root in roots:
        if not root.exists():
            raise SystemExit(f"Path does not exist: {root}")
        if root.is_file():
            if root.suffix == ".java":
                java_files.append(root)
            continue
        java_files.extend(sorted(root.rglob("*.java")))
    return java_files


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Normalize leading indentation in Java source files.",
    )
    parser.add_argument(
        "roots",
        nargs="*",
        metavar="PATH",
        help="Java file or directory to format",
    )
    args = parser.parse_args(argv)

    roots = [Path(p) for p in args.roots]
    if not roots:
        print("warning: no targets specified; nothing to format", file=sys.stderr)

    for path in find_java_files(roots):
        text = path.read_text(encoding="utf-8")
        new_text = normalize_text(text)
        if new_text != text:
            path.write_text(new_text, encoding="utf-8")

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
