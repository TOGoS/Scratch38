import { assertEquals, assertThrows } from "@std/assert";
import { Triangle } from "./stl.ts";
import {
	pruneVector3DObject,
	trianglesFromVector3DObject,
	Vector3DObject,
	vector3DObjectFromTriangles,
} from "./vector3d-object.ts";

// A tetrahedron with outward-facing, counter-clockwise triangles.
// IMPLIED WINDING ORDER APPLIES HERE
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
			uses.set(edge.line, [
				...(uses.get(edge.line) ?? []),
				edge.direction,
			]);
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

// IMPLIED WINDING ORDER APPLIES HERE
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

Deno.test("prune removes unused vertexes and renumbers the rest", () => {
	const object = vector3DObjectFromTriangles(TETRAHEDRON);
	object.vertexes.unshift([9, 9, 9]);
	object.lines = object.lines.map((
		[a, b],
	): [number, number] => [a + 1, b + 1]);
	const pruned = pruneVector3DObject(object, "default");
	assertEquals(pruned.vertexes.length, 4);
	assertEquals(trianglesFromVector3DObject(pruned), TETRAHEDRON);
});

Deno.test("prune removes objects other than the main one", () => {
	const object = vector3DObjectFromTriangles(TETRAHEDRON);
	object.polygons.push(object.polygons[0]);
	object.polyhedrons.push([{ polygon: 4, side: "front" }]);
	object.polyhedronGroups.push([1]);
	object.namedGroups.other = 1;
	const pruned = pruneVector3DObject(object, "default");
	assertEquals(Object.keys(pruned.namedGroups), ["default"]);
	assertEquals(pruned.polygons.length, 4);
	assertEquals(pruned.polyhedrons.length, 1);
	assertEquals(trianglesFromVector3DObject(pruned), TETRAHEDRON);
});

Deno.test("prune makes the named object the default", () => {
	const object = vector3DObjectFromTriangles(TETRAHEDRON);
	object.namedGroups = { main: 0 };
	const pruned = pruneVector3DObject(object, "main");
	assertEquals(pruned.namedGroups, { default: 0 });
	assertEquals(trianglesFromVector3DObject(pruned), TETRAHEDRON);
});

Deno.test("prune rejects an unknown object name", () => {
	const object = vector3DObjectFromTriangles(TETRAHEDRON);
	assertThrows(
		() => pruneVector3DObject(object, "missing"),
		Error,
		"no top-level object named missing",
	);
});
