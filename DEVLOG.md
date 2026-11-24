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

## 2026-11-13 - Document identity is the bitprint of its content - Master Beam

Documents are now identified by the bitprint of their content rather than a hash
of their source URL. The source URL is still recorded as togetl:sourceUrl.
A document entity ID is still undefined; see the TODO in Identifiers.

## 2026-11-13 - Decisions for next steps - Master Haiku

Answers from TOGoS on the open questions:

- Error recovery: a processor may continue after a problem, but must report it.
  Don't add code just to recover; fail fast when that is simpler.
- Org importer: hand-written Java mapping. Don't try to reuse the declarative
  TEF mapping tables for it.
- Music: reuse terms from the historical music ontology (see the note at the
  top of vocab/external.tef).

## 2026-11-24 - Shorthand reference fragments (`WSITEM-3424 p180`) - Gemini 4 Argon

`Identifiers.shortNameToUri` currently matches an optional ` p<page>` suffix on
`WSITEM-*` references (to handle `see-also: WSITEM-3424 p180` in
`3DPrintingData.tef`) and maps it to a `#p<page>` fragment on the item URI.
If we settle on `#` fragments for sub-item/page URIs, we may want to allow
`#...` fragments on all shorthand references and write `WSITEM-3424#p180` in
source documents so reference resolution is a direct prefix/base expansion.
