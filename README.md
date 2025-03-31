# Some File Formats

## Namespacing: `http://ns.nuke24.net/X-2024/` + whatever

- Avoids polluting the root nuke24.net namespace with experimental stuff
- Don't overthink it
- Can always declare 'nicer' names later, with the old ones as aliases
  - Advice for parsers: Does adding or removing "X-2024/" from an
    identifier make it something you know about?
    Emit a warning and assume that's the meaning.
- Canonical UUIDs for formats can be generated from the original
  URI.  These can be generated using `uuidgen --sha1 --namespace '@url' --name $url`.
  e.g. the UUID for `http://ns.nuke24.net/X-2024/Formats/FooFormat`
  would be `3506c796-9788-5f81-8695-ef2f6513e7c9`.

## Formats

- [S38.1Blocks](./S38.1Blocks.org) - Binary, often-4kB-aligned blocks format
- [HashFormats](./HashFormats.md) - Line-based formats that start with "#format "
- [MQTTishV1](./MQTTishV1.md) - A line-based format for representing (some) MQTT message streams
