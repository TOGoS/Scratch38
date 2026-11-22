import { assertEquals, assertStrictEquals } from "@std/assert";
import type { Triangle, Vertex } from "./stl.ts";
import {
	checkManifold,
	makeObject3d,
	makeObject3dFromPolygonLoops,
	Object3D,
	Polyhedron3D,
	polyhedronLeaves,
	pruneObject3d,
} from "./object3d.ts";
import {
	faceKey,
	removeSpurs,
	simplifyObject3d,
	simplifyPolyhedrons,
} from "./simplify.ts";

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
	const object = makeObject3d([triangulatedCube()]);
	const simplified = pruneObject3d(simplifyObject3d(object));
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
	const simplified = pruneObject3d(simplifyObject3d(makeObject3d([faces])));
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	assertEquals(leaf.data.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("polyhedrons sharing a face are merged into one", () => {
	const object = makeObject3d([CUBE_FACES, translate(CUBE_FACES, 1)]);
	const simplified = pruneObject3d(simplifyObject3d(object));
	assertEquals("members" in simplified, false);
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	assertEquals(leaf.data.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("simplify keeps the old data as a base, which prune then drops", () => {
	const object = makeObject3d([triangulatedCube()]);
	const [before] = leavesOf(object);
	const [after] = leavesOf(simplifyObject3d(object));
	assertEquals(before.data.polygons.length, 12);
	assertEquals(after.data.polygons.length, 12 + 6);
	assertEquals(
		leavesOf(pruneObject3d(simplifyObject3d(object)))[0].data.polygons
			.length,
		6,
	);
});

Deno.test("an object with nothing to merge is returned unchanged", () => {
	const object = makeObject3d([CUBE_FACES]);
	assertStrictEquals(simplifyObject3d(object), object);
});

Deno.test("leaves from different data are simplified together", () => {
	const object: Object3D = {
		classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
		members: [
			makeObject3d([CUBE_FACES]),
			makeObject3d([translate(CUBE_FACES, 1)]),
		],
	};
	const simplified = pruneObject3d(simplifyObject3d(object));
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 6);
	checkManifold(simplified);
});

Deno.test("faceKey ignores where a loop starts and handles repeated vertexes", () => {
	const a: Vertex = [-1, -1, 0];
	const b: Vertex = [1, 0, 0];
	const c: Vertex = [1, 1, 0];
	const d: Vertex = [0, 1, 0];
	const e: Vertex = [-1, 0, 0];
	assertEquals(
		faceKey([a, b, c, a, d, e]),
		faceKey([a, d, e, a, b, c]),
	);
	assertEquals(
		faceKey([a, b, c, a, d, e], true),
		faceKey([e, d, a, c, b, a]),
	);
});

Deno.test("removeSpurs cuts out a spur that runs out and straight back", () => {
	const triangle: Vertex[] = [[0, 0, 0], [2, 0, 0], [1, 1, 0]];
	const spurred: Vertex[] = [[0, 0, 0], [2, 0, 0], [3, 0, 0], [2, 0, 0], [
		1,
		1,
		0,
	]];
	assertEquals(removeSpurs(spurred), triangle);
});

Deno.test("two polygons sharing a run of three edges merge across the whole run", () => {
	// f is a square; g is a U around it that shares f's left, bottom, and right edges.
	const f: Vertex[] = [[0, 0, 0], [3, 0, 0], [3, 1, 0], [0, 1, 0]];
	const g: Vertex[] = [
		[-1, -1, 0],
		[4, -1, 0],
		[4, 2, 0],
		[3, 2, 0],
		[3, 1, 0],
		[3, 0, 0],
		[0, 0, 0],
		[0, 1, 0],
		[0, 2, 0],
		[-1, 2, 0],
	];
	const [polyhedron] = simplifyPolyhedrons([[f, g]]);
	assertEquals(polyhedron.length, 1);
	assertEquals(polyhedron[0].length, 1);
	assertEquals(polyhedron[0][0].length, 8);
});

Deno.test("two polyhedrons sharing two faces lose both shared faces", () => {
	// A unit cube, and an L-shaped solid built from two cubes that each touch A on one face.
	const cube1 = translate(CUBE_FACES, 1);
	const cube2 = CUBE_FACES.map((loop) =>
		loop.map(([x, y, z]): Vertex => [x, y, z + 1])
	);
	const object = makeObject3d([CUBE_FACES, [...cube1, ...cube2]]);
	const simplified = pruneObject3d(simplifyObject3d(object));
	assertEquals("members" in simplified, false);
	const [leaf] = leavesOf(simplified);
	assertEquals(leaf.data.polygons.length, 8);
	checkManifold(simplified);
});

// IMPLIED WINDING ORDER APPLIES HERE
Deno.test("simplify splits a line shared with itself into outer and hole loops", () => {
	const a: Vertex = [0, 0, 0];
	const b: Vertex = [4, 0, 0];
	const c: Vertex = [4, 4, 0];
	const d: Vertex = [0, 4, 0];
	const e: Vertex = [1, 1, 0];
	const f: Vertex = [1, 3, 0];
	const g: Vertex = [3, 3, 0];
	const h: Vertex = [3, 1, 0];
	// A square annulus cut open along a-e, so a-e is traversed in both directions.
	const cutDonut = [a, b, c, d, a, e, f, g, h, e];
	const [polyhedron] = simplifyPolyhedrons([[cutDonut]]);
	assertEquals(polyhedron.length, 1);
	assertEquals(polyhedron[0].length, 2);
	assertEquals(polyhedron[0][0], [a, b, c, d]);
	assertEquals(polyhedron[0][1], [e, f, g, h]);
});

// IMPLIED WINDING ORDER APPLIES HERE
Deno.test("simplify removes every line a polygon shares with itself", () => {
	// A square plate with a square hole. Each horizontal face is split into two coplanar
	// polygons sharing two cuts (corner to corner), so merging leaves one cut doubled.
	const topUpper: Vertex[] = [
		[4, 4, 1],
		[0, 4, 1],
		[1, 3, 1],
		[3, 3, 1],
	];
	const topLower: Vertex[] = [
		[0, 0, 1],
		[4, 0, 1],
		[4, 4, 1],
		[3, 3, 1],
		[3, 1, 1],
		[1, 1, 1],
		[1, 3, 1],
		[0, 4, 1],
	];
	const below = (loop: Vertex[]): Vertex[] =>
		loop.map(([x, y]): Vertex => [x, y, 0]).reverse();
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
		[topUpper],
		[topLower],
		[below(topUpper)],
		[below(topLower)],
		...walls.map((wall) => [wall]),
	];
	const object = makeObject3dFromPolygonLoops([plate]);
	checkManifold(object);
	const simplified = pruneObject3d(simplifyObject3d(object));
	checkManifold(simplified);
	const [leaf] = leavesOf(simplified);
	assertEquals(
		leaf.data.polygons.filter((polygon) => polygon.length === 8).length,
		2,
	);
	for (const polygon of leaf.data.polygons) {
		const lines = polygon.map((edge) => edge.line);
		assertEquals(new Set(lines).size, lines.length);
	}
});

// Two faces that share two separate runs of edges. Merging them would enclose a hole.
const FACE_F: Vertex[] = [
	[0, 0, 0],
	[1, 0, 0],
	[1, 1, 0],
	[1, 2, 0],
	[1, 3, 0],
	[0, 3, 0],
];
const FACE_G: Vertex[] = [
	[1, 0, 0],
	[3, 0, 0],
	[3, 3, 0],
	[1, 3, 0],
	[1, 2, 0],
	[2, 2, 0],
	[2, 1, 0],
	[1, 1, 0],
];

Deno.test("simplify without single-loop-polygons may leave a polygon with a hole", () => {
	const polygons = simplifyPolyhedrons([[FACE_F, FACE_G]]).flat();
	assertEquals(polygons.length, 1);
	assertEquals(polygons[0].length, 2);
});

Deno.test("single-loop-polygons refuses a merge that would enclose a hole", () => {
	const polygons = simplifyPolyhedrons([[FACE_F, FACE_G]], {
		singleLoopPolygons: true,
	}).flat();
	assertEquals(polygons.length, 2);
	for (const loops of polygons) {
		assertEquals(loops.length, 1);
	}
});

Deno.test("single-loop-polygons still merges faces that share one run of edges", () => {
	const left: Vertex[] = [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]];
	const right: Vertex[] = [[1, 0, 0], [2, 0, 0], [2, 1, 0], [1, 1, 0]];
	const polygons = simplifyPolyhedrons([[left, right]], {
		singleLoopPolygons: true,
	}).flat();
	assertEquals(polygons.length, 1);
	assertEquals(polygons[0].length, 1);
	assertEquals(polygons[0][0].length, 4);
});
