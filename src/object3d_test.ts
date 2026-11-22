import { assertEquals, assertStrictEquals, assertThrows } from "@std/assert";
import type { Triangle, Vertex } from "./stl.ts";
import {
	checkManifold,
	makeObject3d,
	makeObject3dFromPolygonLoops,
	Object3D,
	Polyhedron3D,
	polyhedronLeaves,
	pruneObject3d,
	trianglesOfObject3d,
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
	const object = makeObject3d([TETRAHEDRON]);
	assertEquals("members" in object, false);
	assertEquals(leafOf(object).data.vertexes.length, 4);
	assertEquals(leafOf(object).data.lines.length, 6);
	assertEquals(leafOf(object).data.polygons.length, 4);
});

Deno.test("several polyhedrons are a UnionObject3D of Polyhedron3Ds sharing one data", () => {
	const object = makeObject3d([TETRAHEDRON, TETRAHEDRON]);
	assertEquals("members" in object, true);
	const leaves = polyhedronLeaves(object);
	assertEquals(leaves.length, 2);
	assertStrictEquals(leaves[0].data, leaves[1].data);
});

Deno.test("a base data is extended without changing it", () => {
	const first = leafOf(makeObject3d([TETRAHEDRON]));
	const baseCount = first.data.polygons.length;
	const second = leafOf(makeObject3d([TETRAHEDRON], first.data));
	assertEquals(first.data.polygons.length, baseCount);
	assertEquals(second.data.polygons.length, 2 * baseCount);
	assertEquals(second.data.vertexes.length, 4);
	assertEquals(second.data.lines.length, 6);
});

Deno.test("polyhedronLeaves flattens nested unions", () => {
	const one = makeObject3d([TETRAHEDRON]);
	const nested = makeObject3d([TETRAHEDRON, TETRAHEDRON]);
	const object: Object3D = {
		classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
		members: [one, nested],
	};
	assertEquals(polyhedronLeaves(object).length, 3);
});

Deno.test("triangles round-trip through the data", () => {
	assertEquals(trianglesOfObject3d(makeObject3d([TETRAHEDRON])), TETRAHEDRON);
});

Deno.test("refuses to write non-triangular polygons", () => {
	const object = makeObject3d([[[[0, 0, 0], [1, 0, 0], [1, 1, 0], [
		0,
		1,
		0,
	]]]]);
	assertThrows(
		() => trianglesOfObject3d(object),
		Error,
		"only triangles",
	);
});

Deno.test("a closed surface is manifold", () => {
	checkManifold(makeObject3d([TETRAHEDRON]));
});

Deno.test("an open surface is not manifold", () => {
	const open = TETRAHEDRON.slice(1);
	assertThrows(
		() => checkManifold(makeObject3d([open])),
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
		() => checkManifold(makeObject3d([flipped])),
		Error,
		"not manifold",
	);
});

Deno.test("the same closed surface in two polyhedrons is manifold when each is closed", () => {
	checkManifold(makeObject3d([TETRAHEDRON, TETRAHEDRON]));
});

// IMPLIED WINDING ORDER APPLIES HERE
Deno.test("validate accepts a donut-shaped polygon", () => {
	// A square plate with a square hole. The top and bottom are single polygons with two loops each.
	const topOuter: Vertex[] = [[0, 0, 1], [4, 0, 1], [4, 4, 1], [0, 4, 1]];
	const topHole: Vertex[] = [[1, 1, 1], [1, 3, 1], [3, 3, 1], [3, 1, 1]];
	const bottomOuter: Vertex[] = [[0, 0, 0], [0, 4, 0], [4, 4, 0], [4, 0, 0]];
	const bottomHole: Vertex[] = [[1, 1, 0], [3, 1, 0], [3, 3, 0], [1, 3, 0]];
	const walls: Vertex[][] = [
		[[0, 0, 0], [0, 0, 1], [0, 4, 1], [0, 4, 0]],
		[[0, 4, 0], [0, 4, 1], [4, 4, 1], [4, 4, 0]],
		[[4, 4, 0], [4, 4, 1], [4, 0, 1], [4, 0, 0]],
		[[4, 0, 0], [4, 0, 1], [0, 0, 1], [0, 0, 0]],
		[[1, 1, 0], [1, 1, 1], [3, 1, 1], [3, 1, 0]],
		[[3, 1, 0], [3, 1, 1], [3, 3, 1], [3, 3, 0]],
		[[3, 3, 0], [3, 3, 1], [1, 3, 1], [1, 3, 0]],
		[[1, 3, 0], [1, 3, 1], [1, 1, 1], [1, 1, 0]],
	];
	const plate = [
		[topOuter, topHole],
		[bottomOuter, bottomHole],
		...walls.map((wall) => [wall]),
	];
	const object = makeObject3dFromPolygonLoops([plate]);
	assertEquals(polyhedronLeaves(object)[0].data.polygons[0].length, 2);
	checkManifold(object);
});

Deno.test("prune removes unused vertexes and keeps the union structure", () => {
	const object = makeObject3d([TETRAHEDRON, TETRAHEDRON]);
	const pruned = pruneObject3d(object);
	const leaves = polyhedronLeaves(pruned);
	assertEquals("members" in pruned, true);
	assertEquals(leaves.length, 2);
	assertEquals(leaves[0].data.vertexes.length, 4);
	assertEquals(leaves[0].data.polygons.length, 8);
	assertEquals(trianglesOfObject3d(pruned), [...TETRAHEDRON, ...TETRAHEDRON]);
});

Deno.test("prune drops polyhedrons and geometry that nothing refers to", () => {
	const first = leafOf(makeObject3d([TETRAHEDRON]));
	const unused = makeObject3d([TETRAHEDRON], first.data);
	const pruned = pruneObject3d(unused);
	assertEquals(leafOf(pruned).data.polyhedrons.length, 1);
	assertEquals(leafOf(pruned).data.polygons.length, 4);
	assertEquals(trianglesOfObject3d(pruned), TETRAHEDRON);
});
