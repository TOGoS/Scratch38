# STL Generation And Modification Tool

## Command-Line Options

Each argument is one operation to apply.
Loading and saving are just more operations.
It's 'concatenative', see.

```
./stlmod load/stl:<URI, filename, or "-" for stdin> some-op:params some-other-op:more-params store/stl/bin:<output filename or "-" for stdout> 
```

Exit status is 0 if every operation succeeds.
If an operation fails, its error message is written to standard error and the exit status is 1.

## Grammar used in examples

The following grammars are specified using the following pseudocode syntax:
- `<foo>` - named substring
- `"xyz"` - literal string
- `(foo bar baz)` - group
- `<expr>?` - expr appears zero or one times
- `<expr>+` - expr appears one or more times
- `<expr>*` - expr appears any number of times

`<foo> <delimiter>`, e.g. `<foo> "/"`, implies that the substring named `foo`
is either self-delimiting or does not contain the delimiter.

## Operation 'Shape'

`(foo:String -- bar:3DObject baz:String)` would mean that an operation
- Takes one value from the stack, which must be a String
- Puts two values onto the stack: a 3DObject and a String, pushed in that order

On both the left and the right sides of the `--`, the top of the stack is on the right.

So the result of this example would be that `foo` has been removed from the stack,
and `bar` and `baz` have been added.

Operations can be thought of as functions of stack to stack,
with possible side-effects.

## Conventional Operation Names

Operation names can be any string of text, but for now let's have them all
follow the format:

```
<op name> ("/" <modifier>) (";" <percent-encoded parameter name> ("=" <percent-encoded parameter value>)?)* (":" <value of default parameter>)?
```

The `":" <xyz>` form at the end is equivalent to specifying `<default parameter name> "=" <xyz, but percent-encoded>`
in the named parameter values section.  This way any literal string can be supplied for that
default parameter (whatever the operator calls it) without having to percent-encode it.

## Resource Names

Some operators work on a named resource.
If the name starts with a URI scheme and a colon, it will be treated as a URI.
If it is `-`, that stands for standard input or output (depending on whether the operation is to read or write to it).
Empty string should be considered an error.
Otherwise, it's a file path.

Therefore, ensure a file path is interpreted as a file path, even when it could otherwise
be interpreted as a URI or `-`,
either ensure it is prefixed with `//` (UNC) `/` (absolute within the current filesystem)
`./` (relative to the current directory) or `../` (relative to a parent directory),
or transform it into a `file:` URI.

## Internal Object Representation

TypeScript types representing data should generally be of the form:

```
type SomeDataType {
	classRef: "tag:nuke24.net,2026-11:s38s39:SomeDataType-v1",
	field: Whatever,
	anotherField: WhateverElse
};
```

and

```
type SomeAdHocUnionType = SomeDataType | SomeOtherDataType;
```

This allows for a JSON-based serialization;
the namespaces provide some hints towards an RDF representation
of the data structures, should we need such a thing.

## Internal Model of Vector3DObject

A vector-based 3DObject (we'll leave the possibility for other representations),
or `Vector3DObject`, consists of:
- A list of vertexes, each of which is (x, y, z), where x, y, and z are numbers
  (probably `number` in TypeScript, but let's parameterize it as `D` in case
  we want to support rational numbers later or something).
- A list of lines between vertexes, each of which is a pair of vertex indices
- A list of polygons, each of which is made of a series of (line index, direction)
  such that the vertexes are visited in counter-clockwise order when viewed
  from 'the front' of the polygon.
  Direction is 'forwards' or 'backwards', since each line segment is re-used
  for two polygons.
- A list of polyhedrons, each of which is a list of (polygon index, side),
  where side is 'front' or 'back'.
- A list of polyhedron groups, each of which is a list of polyhedron indexes
- A dictionary of name -> polyhedron group index, indicating the top-level objects;
  the one called `default` is the one implicitly represented by the dataset as a whole.

It is allowed for there to be duplication,
since that allows for transformations that change
one object but not other identical ones.
e.g. two polyhedrons may share a point at (0,0),
but then we may have an operation that rewrites
the polyhedrons to move their centers away from (0,0).
Having two instances of the (0,0) vertex...might help
keep the rewritten versions.

That said, we should usually try to keep the vertexes and lines unique.

`Vector3DObject` instances should generally be considered immutable
once they are constructed, and it is allowed for `Vector3DObject`s
to contain components that are not used by any named top-level object.
Operations that transform one `Vector3DObject` to another may therefore
simply copy or reference (if using tree-based list structures) the first's data.

## Winding Order

Polygon vertexes are listed counter-clockwise when viewed from the front of the polygon,
i.e. from outside the solid, following the right-hand rule.

This matches STL, whose facet vertexes are counter-clockwise when viewed from outside
and whose facet normals point outward by the right-hand rule.
We chose it purely because STL is the first format we support.
Other formats do not all agree:
- OpenSCAD's `polyhedron` expects points clockwise when viewed from outside (per its manual),
  so importing or exporting OpenSCAD polyhedrons requires reversing each face's vertex order.
- OBJ's winding order has not been checked yet; check before implementing OBJ.

## Lazy Object Representations

Internally, this might lazily represent objects that have been transformed.
e.g. there could be a `PrunedVector3DObject` type that simply wraps
another `Vector3DObject`.  The runtime would not need to calculate
the pruning until specific data (like what's the actual coordinate of vertex #0) is actually needed.

## Operators

Note: 'modify' is used to mean the transformation between
an input and output object.  Objects are actually immutable.

### `load`

Modifiers:
- `stl` - STL as the format. `load` detects binary or text from the content; `store` writes text.
- `stl/bin` - binary STL
- `stl/text` - text STL
- `obj`
- `obj/bin`
- `obj/text`

Parameters:
- `resource-name` (default) - name of resource to load

Shape: `(-- 3DObject)`

### `store`

Same modifiers and parameters as load, but stores the result instead of loading it.
Removes the object from the stack.

Shape: `(3DObject --)`

### `validate`

Shape: `(3DObject --)`

Ensure that the object is manifold.

### `simplify`

Shape: `(input:3DShape -- output:3DShape)`

Simplifies a shape by:
- Replacing adjacent coplanar polygons with a single polygon,
- Consolidating collinear lines that meet at a vertex with no other lines
- Discarding lines and vertexes that are unused after the simplification

Equivalent to `simplify/keep-unused` followed by `prune`.

#### `simplify/keep-unused`

Same as `simplify`, but retains unused sub-objects (vertexes, lines, polygons, polyhedrons, groups).

#### `prune`

Optional parameters:
- `main-object-name` - name of main object to keep; default is `default`.

Shape: `(input:3DObject -- output:3DObject)`

Removes all sub-objects not referenced by the top level polyhedron group,
rewriting indexes accordingly. Indexes keep their relative order.
The output's `default` object is the main object; other top-level objects are removed.

e.g. a shape with 10 vertexes, the first two of which are unused by the main polyhedron group
would be transformed to one with 8 vertexes, and all the vertex indexes 2 lower than
the corresponding indexes of the input.

### `crash`

Always fails, for testing error handling.

Parameters:
- `message` (default) - text written to standard error. Default: `Crash operation invoked`.

Shape: `(--)`

The program writes the message to standard error and exits with status 1.
