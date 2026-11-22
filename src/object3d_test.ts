import { assertEquals, assertStrictEquals, assertThrows } from "@std/assert";
import type { Triangle } from "./stl.ts";
import {
	checkManifold,
	makeObject3D,
	Object3D,
	Polyhedron3D,
	polyhedronLeaves,
	pruneObject3D,
	trianglesOfObject3D,
} from "./object3d.ts";

// A tetrahedron with outward-facing, counter-clockwise triangles.
// IMPLIED WINDING ORDER APPLIES HERE
const TETRAHEDRON: Triangle[] = [
	[[0, 0, 0], [0, 1, 0], [1, 0, 0]],
	[[0, 0, 0], [1, 0, 0], [0, 0, 1]],
	[[0, 0, 0], [0, 0, 1], [0, 1, 0]],
	[[1, 0, 0], [0, 1, 0], [0, 0, 1]],
];

function leafOf(object: Object3D): Polyhedron3D {
	const [leaf] = polyhedronLeaves(object);
	return leaf;
}

Deno.test("a single polyhedron is a Polyhedron3D", () => {
	const object = makeObject3D([TETRAHEDRON]);
	assertEquals("members" in object, false);
	assertEquals(leafOf(object).data.vertexes.length, 4);
	assertEquals(leafOf(object).data.lines.length, 6);
	assertEquals(leafOf(object).data.polygons.length, 4);
});

Deno.test("several polyhedrons are a UnionObject3D of Polyhedron3Ds sharing one data", () => {
	const object = makeObject3D([TETRAHEDRON, TETRAHEDRON]);
	assertEquals("members" in object, true);
	const leaves = polyhedronLeaves(object);
	assertEquals(leaves.length, 2);
	assertStrictEquals(leaves[0].data, leaves[1].data);
});

Deno.test("a base data is extended without changing it", () => {
	const first = leafOf(makeObject3D([TETRAHEDRON]));
	const baseCount = first.data.polygons.length;
	const second = leafOf(makeObject3D([TETRAHEDRON], first.data));
	assertEquals(first.data.polygons.length, baseCount);
	assertEquals(second.data.polygons.length, 2 * baseCount);
	assertEquals(second.data.vertexes.length, 4);
	assertEquals(second.data.lines.length, 6);
});

Deno.test("polyhedronLeaves flattens nested unions", () => {
	const one = makeObject3D([TETRAHEDRON]);
	const nested = makeObject3D([TETRAHEDRON, TETRAHEDRON]);
	const object: Object3D = {
		classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
		members: [one, nested],
	};
	assertEquals(polyhedronLeaves(object).length, 3);
});

Deno.test("triangles round-trip through the data", () => {
	assertEquals(trianglesOfObject3D(makeObject3D([TETRAHEDRON])), TETRAHEDRON);
});

Deno.test("refuses to write non-triangular polygons", () => {
	const object = makeObject3D([[[[0, 0, 0], [1, 0, 0], [1, 1, 0], [
		0,
		1,
		0,
	]]]]);
	assertThrows(
		() => trianglesOfObject3D(object),
		Error,
		"only triangles",
	);
});

Deno.test("a closed surface is manifold", () => {
	checkManifold(makeObject3D([TETRAHEDRON]));
});

Deno.test("an open surface is not manifold", () => {
	const open = TETRAHEDRON.slice(1);
	assertThrows(
		() => checkManifold(makeObject3D([open])),
		Error,
		"not manifold",
	);
});

// IMPLIED WINDING ORDER APPLIES HERE
Deno.test("a face with the wrong winding is not manifold", () => {
	const flipped: Triangle[] = [[
		TETRAHEDRON[0][0],
		TETRAHEDRON[0][2],
		TETRAHEDRON[0][1],
	], ...TETRAHEDRON.slice(1)];
	assertThrows(
		() => checkManifold(makeObject3D([flipped])),
		Error,
		"not manifold",
	);
});

Deno.test("the same closed surface in two polyhedrons is manifold when each is closed", () => {
	checkManifold(makeObject3D([TETRAHEDRON, TETRAHEDRON]));
});

Deno.test("prune removes unused vertexes and keeps the union structure", () => {
	const object = makeObject3D([TETRAHEDRON, TETRAHEDRON]);
	const pruned = pruneObject3D(object);
	const leaves = polyhedronLeaves(pruned);
	assertEquals("members" in pruned, true);
	assertEquals(leaves.length, 2);
	assertEquals(leaves[0].data.vertexes.length, 4);
	assertEquals(leaves[0].data.polygons.length, 8);
	assertEquals(trianglesOfObject3D(pruned), [...TETRAHEDRON, ...TETRAHEDRON]);
});

Deno.test("prune drops polyhedrons and geometry that nothing refers to", () => {
	const first = leafOf(makeObject3D([TETRAHEDRON]));
	const unused = makeObject3D([TETRAHEDRON], first.data);
	const pruned = pruneObject3D(unused);
	assertEquals(leafOf(pruned).data.polyhedrons.length, 1);
	assertEquals(leafOf(pruned).data.polygons.length, 4);
	assertEquals(trianglesOfObject3D(pruned), TETRAHEDRON);
});
