# Some File Formats

## Namespacing: `tag:nuke24.net,` + year + `:` + whatever

- Previously `http://ns.nulke24.net/X-` + year + `/` + whatever,
  but that's exactly what `tag:` is for.
- Canonical UUIDs for formats can be generated from the original
  URI.  These can be generated using `uuidgen --sha1 --namespace '@url' --name $url`.
  e.g. the UUID for `http://ns.nuke24.net/X-2024/Formats/FooFormat`
  would be `3506c796-9788-5f81-8695-ef2f6513e7c9`.

## Formats

- [S38.1Blocks](./S38.1Blocks.org) - Binary, often-4kB-aligned blocks format
- [HashFormats](./HashFormats.md) - Line-based formats that start with "#format "
- [MQTTishV1](./MQTTishV1.md) - A line-based format for representing (some) MQTT message streams
