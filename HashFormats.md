# Some formats

## TODO

- [ ] Add to https://www.nuke24.net/docs/ns/namespaces.tef
  - Also add ContentCouch, TScript34 while at it
- [ ] Figure out what CRC32 means

## Naming

Q: Hedge by
- shoving identifiers into a date-based namespace,
- putting crap at the end?
  - year
  - versin number
- putting an "X-" in there somewhere?

Q: What have I done before?
- Aside from 'Datatypes', things are namespaced by project.
- Projects sometimes have numbers at the end, but not year,
  e.g. "Synthgen2100", "Game21", "TScript34"
- Could make another numbered project, 'NS49' or whatever,
  or use "Scratch38" or "ContentCouch"

Ultimately the names don't matter that much.
Anything that helps me feel less anxiety about choosing one is probably good.
Year prefixes are a change, but do that in a pretty straightforward way.

"I can always pick a nicer-looking name and treat the old one
as a synonym for it".

- http://ns.nuke24.net/X-2024/Formats/HashFormat
- http://ns.nuke24.net/X-2024/Formats/JSONLFileManifest
- http://ns.nuke24.net/Formats/TSVFileManifestV1 (formerly http://ns.nuke24.net/X-2024/Formats/TSVFileManifest)
- http://ns.nuke24.net/X-2024/Formats/JSONLRDF
- http://ns.nuke24.net/X-2024/Formats/JSONLResolutionLog

## HashFormat meta-format

First (or so; some formats might allow secondary formats to be specified)
line is "#format" + whitespace + format identifier,
where 'format identifier' is probably some URI.

A `#format` line indicates the format of a file without
implying any specific meaning, unlike [TOGoSTextBlocks](https://www.nuke24.net/docs/2012/TOGoSBinaryBlocks.html),
which explicitly provides a lexical &rarr; value mapping.

HashFormat has its own URI, so can be nested.

### General line-based format

In general, HashFormat-based formats are line-based
and assume that comment lines can be ignored,
which allows us to jam comments in there.

Comment lines are lines consisting only of `#`,
of `#` followed by whitespace, and `#!` followed by anything.
i.e. lines starting with `#` that are obviously not
parsing directives, such as `#format`, `#TBB`, `#EXTM3U`, `#lang`, etc.

### Example

```
#format http://ns.nuke24.net/X-2024/Formats/HashFormat
# ^ Indicates that the remainder of the file is itself a
#   HashFormat-formatted file, which means it should have
#   another #format line, following any comment lines.
#format http://ns.nuke24.net/X-2024/Formats/SomeExampleFormat
abc123
foo bar baz
```

### Reserved Extensions

#### Any non-whitespace following the format identifier.

Not sure if there should be a standard or at least convention for what follows,
though thinking SchemaSchema-style `: modifier1 : modifier2` seems reasonable.


## Metadata

For use in either of the file manifest formats.

These are chosen to match the conventional xmlns namespace names as used by CCOuch's XML/RDF format.

- dc:modified :: http://purl.org/dc/terms/modified
  - modification time as unix timestamp (seconds)
- bz:fileLength :: http://bitzi.com/xmlns/2002/01/bz-core#fileLength
  - length of content, in bytes
- ccouch:fileNodeId :: http://ns.nuke24.net/ContentCouch/fileNodeId
  - filesystem-specific identifier of the node at which the file is stored
    (inode number on most unix filesystems, a long 'file key' string on NTFS)

## JSONLFileManifest

```
#format http://ns.nuke24.net/X-2024/Formats/JSONLFileManifest
#format http://ns.nuke24.net/X-2024/Formats/JSONLRDF
# Secondary format.  Might be useful for some generic parser.
# 
# The primary, FileManifest/Formats/JSONLv1 format is 'essentially' JSON-lines.
# Blank and '#'-prefixed lines can be ignored.
# 
# Attributes:
# - path: path of file relative to some implicit root directory
#   (or explicit, if you look at the hidden-in-a-#-directive group)
# - [bz:]fileLength: length of content, in bytes
# - [dc:]modified: modification time, in seconds, an integer
#   - if a string, ISO-8601, but lets stick to ints for now
# - calculatedUrns: list of calculated URNs
#   e.g. "urn:bitprint:SQ5HALIG6NCZTLXB7DNI56PXFFQDDVUZ.276TET7NAXG7FVCDQWOENOX4VABJSZ4GBV7QATQ", "data:,Hello,%20world!"]
# - crc32Hex: hex-encoded CRC32 (which CRC32?)
# 
# The secondary format is...maybe not something I need to flesh out at the moment,
# but it's on my mind, so let's say that at a minimum
# there is a #begin-group (group metadata as JSON) directive,
# and a corresponding #end-group one.
# - `#long-names` indicates fully qualified names for attributes and references.
#   - Some special postfix, uhm, let's say " ref", indicates that values
#     are actually references.
# - `#item-prototype {...default values...} indicates values that can be assumed
#   for objects that don't explicitly indicate values for those attributes.
#begin-group {"created": 1710893973, "createdBySoftwareName": "FooManifester-v1.2.3", "rootPath": "//SOME-DEVICE/..." }
# Some directives that are 
#long-names {"classRef": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type ref", "path": ....}
#item-prototype {"classRef": "http://ns.nuke24.net/X-2024/FileManifest/File"}
{"path": "foo/bar.txt", "calculatedUrns": ["urn:bitprint:SQ5HALIG6NCZTLXB7DNI56PXFFQDDVUZ.276TET7NAXG7FVCDQWOENOX4VABJSZ4GBV7QATQ", "data:,Hello%20world!"], "modified": 1710958501}
#end-group
```


## http://ns.nuke24.net/Formats/TSVFileManifestV1

(Changed from `http://ns.nuke24.net/X-2024/Formats/TSVFileManifest` on 2024-05-18)

Officially documented at http://www.nuke24.net/docs/2024/TSVFileManifestV1.html .

```
#format http://ns.nuke24.net/Formats/TSVFileManifestV1

foo/bar/helloworld.txt	urn:bitprint:SQ5HALIG6NCZTLXB7DNI56PXFFQDDVUZ.276TET7NAXG7FVCDQWOENOX4VABJSZ4GBV7QATQ	data:,Hello,%20world!

# Same, but without giving a name:
urn:bitprint:SQ5HALIG6NCZTLXB7DNI56PXFFQDDVUZ.276TET7NAXG7FVCDQWOENOX4VABJSZ4GBV7QATQ	data:,Hello,%20world!

# Provide metadata about some blob;
# by convention, bz:fileLength = http://bitzi.com/xmlns/2002/01/bz-core#fileLength.
# Conventional prefixes TBD, but 'whatever ContentCouch uses
# for xmlns prefixes' is a reasonable guess.
urn:bitprint:SQ5HALIG6NCZTLXB7DNI56PXFFQDDVUZ.276TET7NAXG7FVCDQWOENOX4VABJSZ4GBV7QATQ	: bz:fileLength @ 13
```

Tokens are tab separated, hence 'TSV' in the name.

Each token is either a URI (if it starts with a valid URI scheme+":"),
metadata (if it starts with ":"), or, if the first token, a relative path.

Blank and comment lines (defined by the HashFormat format) can be ignored.

Tokens beginning with ':' indicate metadata using SchemaSchema syntax, i.e. `: property name @ property value : another @ ...`,
where property values may be abstract identifiers or JSON-style string literals.

Compatible with M3U if only one column!

## http://ns.nuke24.net/X-2024/Formats/JSONLResolutionLog

```
#format http://ns.nuke24.net/X-2024/Formats/JSONLResolutionLog
```

Inputs and outputs can be expressed as URIs or as TOGVM expressions.

A resolution represents a step towards a more concrete form of
the resulting value.  e.g. a first step might be to convert
the application of an abstract function, such as
`pixel dimensions of encoded image( <image file URI> )` (pseudocode)
to the application of a specific implementation, e.g.
`pixel dimensions according to magick-7.1.0-31( <image file URI> )`.

Attributes:
- canonical - true if this resolution is, in theory, well-defined and 100% reproducible.
  - Resolution using a specific version of software applied to a well-defined input
    is canonical, assuming that software's behavior is repeatable.
  - Resolutions that could under different circumstances give different results
    are *not* canonical
  - e.g. `rand() = 0.3713` is *not* canonical, but `10 * 5 = 50` is.
  - `file:foo/bar/baz.txt = data:,Hello%20there!` is not canonical, because
    at a different point in time or on a different computer, the file
    could have different content, or not exist at all.
- inputUri - URI to be resolved, probably an `active` URI or somilar
- inputExpression - TOGVM-like expression to be resolved
- outputUri - URI representing result
- outputExpression - Expression representing result
- resolved - timestamp that resolution was done
- resolvedBy - name of software/instance 
- context - any information about the context that might have affected
  the resolution, such as host OS, name, processor architecture, etc 


## See also

- https://www.rfc-editor.org/rfc/rfc4180 - CSV RFC
- https://jsonlines.org/
- https://frictionlessdata.io/ - ???

My as-yet-undocumented variation on TSV, which has a
"#COLUMNS:foo	bar	baz" header.
That could be its own HashFormat.
