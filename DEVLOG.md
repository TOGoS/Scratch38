# TOGETL DevLog

A space for contributors to dump notes,
so that we have a place for such things that isn't the README.
Only rarely should you need to refer to anything in here,
e.g. when in-progress work by one agent is to be handed off to another.

I'll start with an example entry to set the tone.

## 2026-11-13 - First devlog entry - TOGoS

I had Reviewer Beam clean up the documentation.
The orignal plan author, Big Pickle, would probably say we removed too much.
But I am the one in charge, and anyway, I found that specifying too much
mostly led to agents thinking that things were set in stone that actually
were not.

I'll be the primary editor of the docs from now on.
Only edit them to make necessary corrections of if I ask you to, thanks.
(But as per the paragraph at the top of this file, I don't actually
expect anyone to read this message!  It's duplicating principles
alredy specified in CONTRIBUTING.md, anyway.)

## 2026-11-13 - Deleting the pickle branch - TOGoS

Deleting subtrees/s0036/20261111-pickle,
which was pointing at 15131fa0a709369f9ba9da4aff34ed0232827efa,
since agents kept thinking it had unmerged work (it did not;
it had been rebased into master).

Noting here on the off chance there's any value in remembering
that alternate history.

## 2026-11-13 - Syntax errors in source documents - Master Haiku

A scan of the source documents with the current parser found the
following problems. The TEF parser will treat them as fatal once the
streaming refactor lands, so they need fixing in the source documents.
Line numbers refer to `fixtures/upstream/`.

3DPrintingData.tef:
- L457: no blank line between the `=part p1422` header block and the
  content `You'll want to print two of these.`
- L449, L456: `openscad-rendering-ref: ttp://...` should be `http://`.

music.txt:
- L49, 55, 59, 72, 80, 87, 90, 94, 101: indented items under `=category`
  lines with no blank line before them.
- L1574: `x-rdf-subject:urn:...` is missing the space after the colon.

3DPrintingLog.org:
- Unclosed drawers (no `:END:`) at L10455 (`:extra_width_at_corners:`),
  L13941 (`:editorial:`), and `:PROPERTIES:` at L11726, 12317, 13865,
  14954, 20368.
- Duplicate `CUSTOM_ID`s at L11024, 11675, 18471, 20711, 20723.

namespaces.tef and vocab/*.tef are clean.
