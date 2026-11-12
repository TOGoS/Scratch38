Big Pickle did a pretty good job generating the plan and implementing the parser.
Some things I'd like it to change (and perhaps update notes in PLAN.md accordingly):

- [ ] Java naming conventions: It was confused by my wording.  The convention
  of `XMLHTTPRequest myXmlHttpRequest` is actually quite simple:
  - For UpperCamelCase, upper-case the first letter of each word.
  - For lowerCamelCase, lower-case everything, *then* upper-case the first letter
    of all but the first word.
  - And then mash the words together.
- [ ] Replace "http://ns.nuke24.net/TOGETL/" with "tag:nuke24.net,2026-11:togetl:"
  - I meant for the tag: namespace to be used for newly-made-up terms, not necessarily
    for identifiers for parsed data!  It might be clearer if we used a separate
    namespace for data, e.g. "tag:nuke24.net,2026-11:xyz:" (to drive home the point
    that these will mostly be temporary until we get the actual OID namespaces in order)
- [ ] Don't include "Uri" or "Ref" in term names, since, when translated to RDF,
  whether the value is a literal or a URI reference is part of the value.
  e.g. "doc-ref: http://foo" should probably be mapped to "documentedBy <http://foo>" (in turtle-ese).
  i.e. the resource referenced is not itself a URI!
- [ ] The 'tef:content' of an entry is not the 'content' of the object being described.
  It's usually notes about it.  A 'dc:description', perhaps.
- [ ] As long as I'm nit-picking, tabs for indentation, please.  At least in Java code.
  One per indent level.
  Use spaces for alignment after the tabs, if needed.
