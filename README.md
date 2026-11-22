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

## Operators and Operations

In the context of the the stack-based language, these terms are used loosey to mean:
- **operation** - a concrete operation to be applied to the stack
- **operator** - the constructor of an operation.

e.g. the `load` operator is instantiated with a `resource-name` parameter
to construct the operation named `load;resource-name=foo.stl`.

In cases where no parameters are passed, e.g. `crash` and `dup`,
the name can refer either to the constructor (operator) and the instance (operation).

## Operation 'Shape'

`(foo:String -- bar:Object3D baz:String)` would mean that an operation
- Takes one value from the stack, which must be a String
- Puts two values onto the stack: a Object3D and a String, pushed in that order

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
The supported schemes are `file:`, `http:`, and `https:`.
`http:` and `https:` resources can only be loaded, not stored.
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

## Internal Model

Objects on the stack each represent one `Object3D`
(until/unless we add support for other types of data on the stack,
like numbers, literal strings, etc).
Type names put "how it is represented" before "what it represents",
so `UnionObject3D` is the union-based representation of an `Object3D`.

`Object3D` is a role: anything on the stack that represents one 3D object.
The types that play it are listed by the TypeScript type `Object3D`:

```
type Object3D = Polyhedron3D | UnionObject3D;
```

- `Polyhedron3D`: a reference to one polyhedron within a `Polyhedron3DData`.
- `UnionObject3D`: a union of a list of `Object3D`s, whatever their representation.
  A union of `Polyhedron3D`s is the way to represent several polyhedrons together.
  (Whether a given operation is possible for every member, given its representation, is a separate question.)
  Its member list is therefore an `Object3D[]`.

`Polyhedron3DData` holds the geometry that these refer to:
- A list of vertexes, each of which is (x, y, z), where x, y, and z are numbers
  (probably `number` in TypeScript, but let's parameterize it as `D` in case
  we want to support rational numbers later or something).
- A list of lines between vertexes, each of which is a pair of vertex indices.
- A list of polygons, each of which is a flat, unordered list of (line index, direction) edges.
  Together the edges form one or more closed boundaries; grouping them into loops is left to
  algorithms that need that order. The polygon's interior is on the left of each directed edge.
  Direction is 'forwards' or 'backwards', since each line segment is re-used
  for two (or some multiple of two) polygons.
- A list of polyhedrons, each of which is a list of (polygon index, side),
  where side is 'front' or 'back'.

It is allowed for there to be duplication,
since that allows for transformations that change
one object but not other identical ones.
e.g. two polyhedrons may share a point at (0,0),
but then we may have an operation that rewrites
the polyhedrons to move their centers away from (0,0).
Having two instances of the (0,0) vertex...might help
certain [very naive, now that I think of it] implementations reorganize things?

That said, we should usually try to keep the vertexes and lines unique.

Objects on the stack, and the `Polyhedron3DData` they refer to, are effectively immutable.
Operations that change geometry make a new `Polyhedron3DData`, copying the lists of the old one
and appending to them, so unchanged parts are referenced rather than rewritten.
The new data may still contain parts that nothing refers to.
`prune` writes a new `Polyhedron3DData` containing only the parts that the object refers to.

## Winding Order

A polygon's interior is on the left of each of its directed edges when viewed from outside the
solid, following the right-hand rule. So an outer boundary runs counter-clockwise and a hole
boundary runs clockwise. The same rule applies to the vertex sequence of a `Loop`.

This matches STL, whose facet vertexes are counter-clockwise when viewed from outside
and whose facet normals point outward by the right-hand rule.
We chose it purely because STL is the first format we support.
Other formats do not all agree:
- OpenSCAD's `polyhedron` expects points clockwise when viewed from outside (per its manual),
  so importing or exporting OpenSCAD polyhedrons requires reversing each face's vertex order.
- OBJ also uses counter-clockwise order when viewed from the front, so it needs no reversal.

## Lazy Object Representations

Internally, this might lazily represent objects that have been transformed.
e.g. there could be a `PrunedObject3D` type that simply wraps
another `Object3D`.  The runtime would not need to calculate
the pruning until specific data (like what's the actual coordinate of vertex #0) is actually needed.

## Operators

Note: 'modify' is used as shorthand to describe a transformation between
an input and output object.  Objects are actually immutable.

### `dup`

Duplicate the top value on the stack.

Shape: `(a -- a a)`

### `load`

Modifiers:
- `stl` - STL as the format. `load` detects binary or text from the content; `store` writes text.
- `stl/bin` - binary STL
- `stl/text` - text STL
- `obj` - OBJ as the format (text)
- `obj/text` - same as `obj`
- `obj/bin` is not supported, since OBJ is a text format here.

Parameters:
- `resource-name` (default) - name of resource to load

Shape: `(-- Object3D)`

OBJ details:
- Each `o` statement starts a new polyhedron, and faces before the first `o` form one too. Object names are not kept.
  The result is a `Polyhedron3D` if there is one polyhedron, otherwise a `UnionObject3D` of `Polyhedron3D`s.
- Faces may have any number of vertexes. Negative vertex references are relative to the vertexes read so far.
- Texture and normal references, and `g`, `s`, `mtllib`, and `usemtl` statements, are ignored.
- Other statements, such as `l`, `p`, and free-form curves and surfaces,
  are errors (unless we extend our model to be able to represent them).
- Faces are counter-clockwise when viewed from the front, as in this project.

### `store`

Same modifiers and parameters as load, but stores the result instead of loading it.
Removes the object from the stack.
STL writes every polyhedron of the object as one solid, and only accepts triangles.
OBJ writes each polyhedron as an unnamed object (`o` line), since the object has no names,
and writes each polygon's single boundary as one `f` line.
OBJ faces cannot express holes, so storing a polygon with holes fails.
Turning such a polygon into hole-free faces (or a different OBJ representation) is future work.

Shape: `(Object3D --)`

### `validate`

Shape: `(a:Object3D -- a:Object3D)`

Ensures that the object is 'manifold' (or at least not obviously problematic), and fails otherwise.
The object is left on the stack unchanged.

This is a simplified definition, to be refined later.
A polyhedron-based object is manifold if the edges of every polygon
in it form closed boundaries (no branching or dangling ends),
and every line is used by two polygon edges,
which go in different directions (or some multiple of these pairs).
Polygons with holes are accepted.
`validate` accepts any `Object3D` whose leaves are all polyhedrons (a `Polyhedron3D`, a union of them, a union of unions, and so on).
The leaves are checked together, as one object.

### `simplify`

Shape: `(input:Object3D -- output:Object3D)`

Simplifies a shape, without changing its shape, on a best-effort basis:
- Merging polyhedrons that share a polygon, which is on opposite sides of each, into one polyhedron without that polygon
- Replacing adjacent coplanar polygons with a single polygon, across one run of consecutive shared lines
- Consolidating collinear lines that meet at a vertex with no other lines
- Merging vertexes with identical coordinates, and lines between the same two vertexes

Two polygons that share several separate runs of lines are merged across one run only.
If the merged outline then uses a line twice in opposite directions, `simplify` drops both uses
and splits the outline into the outer loop and hole loops of one polygon.
Spurs left by a merge (where the outline runs out along a line and straight back) are removed.
A merge is skipped if it would leave fewer than three vertexes.
Coplanarity and collinearity are checked with a small tolerance.

`simplify` accepts any `Object3D` whose leaves are all polyhedrons (a `Polyhedron3D`, a union of them, a union of unions, and so on).
Its leaves are treated as one set: nested unions do not separate them, so polyhedrons from anywhere in the object may be merged.

You may want to call `prune` after `simplify`.

#### `prune`

Shape: `(input:Object3D -- output:Object3D)`

Writes a new `Polyhedron3DData` containing only the vertexes, lines, polygons, and polyhedrons that the object refers to,
rewriting indexes accordingly. Indexes keep their relative order.
`prune` likewise works on the whole object, however its unions are nested.

e.g. a shape with 10 vertexes, the first two of which are unused by its polyhedrons
would be transformed to one with 8 vertexes, and all the vertex indexes 2 lower than
the corresponding indexes of the input.

### `crash`

Always fails, for testing error handling.

Parameters:
- `message` (default) - text written to standard error. Default: `Crash operation invoked`.

Shape: `(--)`

The program writes the message to standard error and exits with status 1.
