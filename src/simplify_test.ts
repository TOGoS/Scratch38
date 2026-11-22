import { assertEquals, assertStrictEquals } from "@std/assert";
import type { Triangle, Vertex } from "./stl.ts";
import {
	checkManifold,
	makeObject3D,
	Object3D,
	Polyhedron3D,
	polyhedronLeaves,
	pruneObject3D,
} from "./object3d.ts";
import { simplifyObject3D } from "./simplify.ts";

// The six faces of the unit cube, each with its vertexes counter-clockwise viewed from outside.
// IMPLIED WINDING ORDER APPLIES HERE
const CUBE_FACES: Vertex[][] = [
	[[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]],
	[[0, 0, 0], [0, 1, 0], [1, 1, 0], [1, 0, 0]],
	[[1, 0, 0], [1, 1, 0], [1, 1, 1], [1, 0, 1]],
	[[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]],
	[[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]],
	[[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]],
];

function translate(loops: Vertex[][], dx: number): Vertex[][] {
	return loops.map((loop) => loop.map(([x, y, z]): Vertex => [x + dx, y, z]));
}

/** Each quad split into two triangles along its first diagonal. */
function triangulatedCube(): Triangle[] {
	return CUBE_FACES.flatMap(([v0, v1, v2, v3]): Triangle[] => [
		[v0, v1, v2],
		[v0, v2, v3],
	]);
}

function leavesOf(object: Object3D): Polyhedron3D[] {
	return polyhedronLeaves(object);
}

Deno.test("adjacent coplanar triangles are merged back into quads", () => {
	const object = makeObject3D([triangulatedCube()]);
	const simplified = pruneObject3D(simplifyObject3D(object));
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	assertEquals(leaf.data.vertexes.length, 8);
	assertEquals(leaf.data.lines.length, 12);
	checkManifold(simplified);
});

Deno.test("a vertex on a straight edge with two neighbours is removed", () => {
	const midpoint: Vertex = [0.5, 0, 1];
	const faces = CUBE_FACES.map((loop, i) => {
		if (i === 0) {
			// Top: the front edge now passes through the midpoint.
			return [loop[0], midpoint, loop[1], loop[2], loop[3]];
		}
		if (i === 4) {
			// Front: the same edge, traversed the other way.
			return [loop[0], loop[1], loop[2], midpoint, loop[3]];
		}
		return loop;
	});
	const simplified = pruneObject3D(simplifyObject3D(makeObject3D([faces])));
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	assertEquals(leaf.data.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("polyhedrons sharing a face are merged into one", () => {
	const object = makeObject3D([CUBE_FACES, translate(CUBE_FACES, 1)]);
	const simplified = pruneObject3D(simplifyObject3D(object));
	assertEquals("members" in simplified, false);
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	assertEquals(leaf.data.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("simplify keeps the old data as a base, which prune then drops", () => {
	const object = makeObject3D([triangulatedCube()]);
	const [before] = leavesOf(object);
	const [after] = leavesOf(simplifyObject3D(object));
	assertEquals(before.data.polygons.length, 12);
	assertEquals(after.data.polygons.length, 12 + 6);
	assertEquals(
		leavesOf(pruneObject3D(simplifyObject3D(object)))[0].data.polygons
			.length,
		6,
	);
});

Deno.test("an object with nothing to merge is returned unchanged", () => {
	const object = makeObject3D([CUBE_FACES]);
	assertStrictEquals(simplifyObject3D(object), object);
});

Deno.test("leaves from different data are simplified together", () => {
	const object: Object3D = {
		classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
		members: [
			makeObject3D([CUBE_FACES]),
			makeObject3D([translate(CUBE_FACES, 1)]),
		],
	};
	const simplified = pruneObject3D(simplifyObject3D(object));
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	checkManifold(simplified);
});
