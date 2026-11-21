import { assertEquals, assertThrows } from "@std/assert";
import { Triangle } from "./stl.ts";
import {
  trianglesFromVector3DObject,
  Vector3DObject,
  vector3DObjectFromTriangles,
} from "./vector3d-object.ts";

// A tetrahedron with outward-facing, counter-clockwise triangles.
const TETRAHEDRON: Triangle[] = [
  [
    [0, 0, 0],
    [0, 1, 0],
    [1, 0, 0],
  ],
  [
    [0, 0, 0],
    [1, 0, 0],
    [0, 0, 1],
  ],
  [
    [0, 0, 0],
    [0, 0, 1],
    [0, 1, 0],
  ],
  [
    [1, 0, 0],
    [0, 1, 0],
    [0, 0, 1],
  ],
];

Deno.test("shares vertexes and edges between faces", () => {
  const object = vector3DObjectFromTriangles(TETRAHEDRON);
  assertEquals(object.vertexes.length, 4);
  assertEquals(object.lines.length, 6);
  assertEquals(object.polygons.length, 4);
});

Deno.test("each shared edge is used once in each direction", () => {
  const object = vector3DObjectFromTriangles(TETRAHEDRON);
  const uses = new Map<number, string[]>();
  for (const polygon of object.polygons) {
    for (const edge of polygon) {
      uses.set(edge.line, [...(uses.get(edge.line) ?? []), edge.direction]);
    }
  }
  for (const directions of uses.values()) {
    assertEquals(directions.sort(), ["backwards", "forwards"]);
  }
});

Deno.test("round-trips triangles through Vector3DObject", () => {
  const object = vector3DObjectFromTriangles(TETRAHEDRON);
  assertEquals(trianglesFromVector3DObject(object), TETRAHEDRON);
});

Deno.test("back-side faces are written reversed", () => {
  const object: Vector3DObject = vector3DObjectFromTriangles(TETRAHEDRON);
  object.polyhedrons[0][0] = { polygon: 0, side: "back" };
  const triangles = trianglesFromVector3DObject(object);
  assertEquals(triangles[0], [
    TETRAHEDRON[0][0],
    TETRAHEDRON[0][2],
    TETRAHEDRON[0][1],
  ]);
});

Deno.test("refuses to write non-triangular polygons", () => {
  const object = vector3DObjectFromTriangles(TETRAHEDRON);
  object.polygons[0] = object.polygons[0].slice(0, 2);
  assertThrows(
    () => trianglesFromVector3DObject(object),
    Error,
    "only triangles",
  );
});

Deno.test("refuses objects without a default group", () => {
  const object = vector3DObjectFromTriangles(TETRAHEDRON);
  delete object.namedGroups.default;
  assertThrows(
    () => trianglesFromVector3DObject(object),
    Error,
    "no default group",
  );
});
