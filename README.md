# Scratch38-S0026

Parse for 3DPrintingData.tef and/or 3DPrintingLog.org, in Clojure.

I have this thing where I want to write a computer program
but then spend decades waffling about it because I can't decide
which programming language to use.

So for this I have chosen Clojure.

Goals:
- Command-line tool to parse data from ProjectNotes2 files, emitting [TOGETLTSV](https://www.nuke24.net/docs/2026/TOGETLTSV202601.html)

Non-goals:
- Any knowledge about where to find those files
- Anything about service orchestration

The parsers may be exposed as libraries in addition to the command-line entrypoint.

Could be extended to parse other files, if that turns out to be more
convenient than writing separate parsing tools.
