# Scratch38-S0026

Parser for 3DPrintingData.tef and/or 3DPrintingLog.org, in Clojure.

### Background

I have this thing where I want to write a computer program
but then spend decades waffling about it because I can't decide
which programming language to use.

So for this I have chosen Clojure.

### Goals

- Command-line tool to parse data from ProjectNotes2 files, emitting [TOGETLTSV](https://www.nuke24.net/docs/2026/TOGETLTSV202601.html)

### Non-goals

- Any knowledge about where to find those files
- Anything about service orchestration

### Possibilities

The parsers may be exposed as libraries in addition to the command-line entrypoint.

Could be extended to parse other files, if that turns out to be more
convenient than writing separate parsing tools.


## Notes

I already have some Java functions for parsing TEF in [TScript34-P0014](https://github.com/TOGoS/TScript34/blob/subtrees/p0014/master/pom.xml),
using the 'danducer' framework defined by [TScript34-P0010](https://github.com/TOGoS/TScript34/blob/subtrees/p0010/master/pom.xml).

The nice thing about Danducers is they are pure functions,
so even if you don't like the API, it can be wrapped easily enough.
