#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

python3 - "$@" <<'PY'
from pathlib import Path
import re
import sys

roots = [Path('src')]
if sys.argv[1:]:
    roots = [Path(p) for p in sys.argv[1:]]


def indent_width(prefix: str) -> int:
    return len(prefix.expandtabs(4))


def indent_for_width(width: int) -> str:
    return ("\t" * (width // 4)) + (" " * (width % 4))


def normalize_leading_indent(prefix: str) -> str:
    width = indent_width(prefix)
    return indent_for_width(width)


java_files = []
for root in roots:
    if not root.exists():
        raise SystemExit(f"Path does not exist: {root}")
    if root.is_file():
        if root.suffix == '.java':
            java_files.append(root)
        continue
    java_files.extend(sorted(root.rglob('*.java')))

for path in java_files:
    text = path.read_text(encoding='utf-8')
    lines = text.splitlines(True)
    for i, line in enumerate(lines):
        newline = ''
        body = line
        if body.endswith('\r\n'):
            body = body[:-2]
            newline = '\r\n'
        elif body.endswith('\n'):
            body = body[:-1]
            newline = '\n'
        if body.strip() == '':
            prev_prefix = ''
            prev_width = 0
            for j in range(i - 1, -1, -1):
                candidate = lines[j].rstrip('\r\n')
                if candidate.strip() != '':
                    prev_prefix = re.match(r'^[ \t]*', candidate).group(0)
                    prev_width = indent_width(prev_prefix)
                    break
            next_prefix = ''
            next_width = 0
            for j in range(i + 1, len(lines)):
                candidate = lines[j].rstrip('\r\n')
                if candidate.strip() != '':
                    next_prefix = re.match(r'^[ \t]*', candidate).group(0)
                    next_width = indent_width(next_prefix)
                    break
            target_width = max(prev_width, next_width)
            lines[i] = indent_for_width(target_width) + newline
        else:
            prefix = re.match(r'^[ \t]*', body).group(0)
            suffix = body[len(prefix):]
            lines[i] = normalize_leading_indent(prefix) + suffix + newline

    new_text = ''.join(lines)
    if new_text != text:
        path.write_text(new_text, encoding='utf-8')
PY
