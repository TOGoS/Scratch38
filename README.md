# Some File Formats

## Namespacing: `http://ns.nuke24.net/X-2024/` + whatever

- Avoids polluting the root nuke24.net namespace with experimental stuff
- Don't overthink it
- Can always declare 'nicer' names later, with the old ones as aliases
  - Advice for parsers: Does adding or removing "X-2024/" from an
    identifier make it something you know about?
    Emit a warning and assume that's the meaning.

## Formats

- [S38.1Blocks](./S38.1Blocks.org) - Binary, often-4kB-aligned blocks format
- [HashFormats](./HashFormats.md) - Line-based formats that start with "#format "
