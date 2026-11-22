import { assertAlmostEquals, assertEquals, assertThrows } from "@std/assert";
import type { Vertex } from "./stl.ts";
import {
	checkManifold,
	Loop,
	makeObject3dFromPolygonLoops,
	Object3D,
	polygonLoopsOfPolyhedron,
	polyhedronLeaves,
	trianglesOfObject3d,
} from "./object3d.ts";
import { fixPolygons } from "./fixPolygons.ts";

// --- Test geometry -------------------------------------------------------

/**
 * A prism over a counter-clockwise 2D outline, from z=0 to z=height.
 * Returns the polygons of its polyhedron: each polygon is a list of loops.
 */
function prism(xy: [number, number][], height: number): Loop[][] {
	const bottom = xy.map(([x, y]): Vertex => [x, y, 0]);
	const top = xy.map(([x, y]): Vertex => [x, y, height]);
	const polygons: Loop[][] = [[top], [bottom.slice().reverse()]];
	xy.forEach((_, i) => {
		const j = (i + 1) % xy.length;
		polygons.push([[bottom[i], bottom[j], top[j], top[i]]]);
	});
	return polygons;
}

/** An L-shaped prism. Its top and bottom faces are concave. */
function lPrism(): Loop[][] {
	return prism([[0, 0], [2, 0], [2, 1], [1, 1], [1, 2], [0, 2]], 1);
}

/** A box with a square pocket in its top face. The top face is one polygon with a hole. */
function pocketBox(): Loop[][] {
	const outer = [[0, 0], [2, 0], [2, 2], [0, 2]];
	const hole = [[0.5, 0.5], [0.5, 1.5], [1.5, 1.5], [1.5, 0.5]]; // clockwise: the hole is outside the face
	const at = (xy: number[][], z: number): Vertex[] =>
		xy.map(([x, y]): Vertex => [x, y, z]);
	const outerBottom = at(outer, 0);
	const outerTop = at(outer, 1);
	const holeTop = at(hole, 1);
	const holeLow = at(hole, 0.5);
	const polygons: Loop[][] = [
		[outerTop, holeTop], // top face with a pocket
		[outerBottom.slice().reverse()], // bottom
		[holeLow.slice().reverse()], // pocket floor
	];
	outer.forEach((_, i) => {
		const j = (i + 1) % outer.length;
		polygons.push([[
			outerBottom[i],
			outerBottom[j],
			outerTop[j],
			outerTop[i],
		]]);
	});
	hole.forEach((_, i) => {
		const j = (i + 1) % hole.length;
		polygons.push([[holeLow[i], holeLow[j], holeTop[j], holeTop[i]]]);
	});
	return polygons;
}

function boxPrism(): Loop[][] {
	return prism([[0, 0], [2, 0], [2, 1], [0, 1]], 1);
}

// --- Measurements --------------------------------------------------------

/** The loops of each face of an object, in order. */
function facesOf(object: Object3D): Loop[][] {
	return polyhedronLeaves(object).flatMap((leaf) =>
		polygonLoopsOfPolyhedron(leaf)
	);
}

/** Half the sum of cross products around each loop of a face: the face's vector area. Hole loops count with their own orientation. */
function vectorAreaOf(loops: Loop[]): Vertex {
	const sum: Vertex = [0, 0, 0];
	for (const loop of loops) {
		loop.forEach((a, i) => {
			const b = loop[(i + 1) % loop.length];
			sum[0] += a[1] * b[2] - a[2] * b[1];
			sum[1] += a[2] * b[0] - a[0] * b[2];
			sum[2] += a[0] * b[1] - a[1] * b[0];
		});
	}
	return [sum[0] / 2, sum[1] / 2, sum[2] / 2];
}

function totalVectorArea(object: Object3D): Vertex {
	const total: Vertex = [0, 0, 0];
	for (const face of facesOf(object)) {
		const v = vectorAreaOf(face);
		total[0] += v[0];
		total[1] += v[1];
		total[2] += v[2];
	}
	return total;
}

function totalArea(object: Object3D): number {
	return facesOf(object).reduce((sum, face) => {
		const [x, y, z] = vectorAreaOf(face);
		return sum + Math.hypot(x, y, z);
	}, 0);
}

/** Whether a single loop is convex, given its own normal. */
function isConvex(loop: Loop): boolean {
	const normal = vectorAreaOf([loop]);
	return loop.every((a, i) => {
		const b = loop[(i + 1) % loop.length];
		const c = loop[(i + 2) % loop.length];
		const e1 = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
		const e2 = [c[0] - b[0], c[1] - b[1], c[2] - b[2]];
		const cross = [
			e1[1] * e2[2] - e1[2] * e2[1],
			e1[2] * e2[0] - e1[0] * e2[2],
			e1[0] * e2[1] - e1[1] * e2[0],
		];
		return cross[0] * normal[0] + cross[1] * normal[1] +
				cross[2] * normal[2] >= -1e-9;
	});
}

function assertSameSurface(before: Object3D, after: Object3D): void {
	const a = totalVectorArea(before);
	const b = totalVectorArea(after);
	for (let i = 0; i < 3; i++) assertAlmostEquals(a[i], b[i], 1e-9);
	assertAlmostEquals(totalArea(before), totalArea(after), 1e-9);
	checkManifold(after);
}

// --- fixPolygons/single-loop ---------------------------------------------

Deno.test("single-loop: a face with a hole is split into faces with one loop each", () => {
	const input = makeObject3dFromPolygonLoops([pocketBox()]);
	assertEquals(facesOf(input).some((loops) => loops.length > 1), true);
	const output = fixPolygons(input, "single-loop");
	for (const loops of facesOf(output)) {
		assertEquals(loops.length, 1);
	}
	assertSameSurface(input, output);
});

Deno.test("single-loop: faces that already have one loop are left as they are", () => {
	const input = makeObject3dFromPolygonLoops([lPrism()]);
	const output = fixPolygons(input, "single-loop");
	assertEquals(facesOf(output).length, facesOf(input).length);
	assertSameSurface(input, output);
});

// --- fixPolygons/convex --------------------------------------------------

Deno.test("convex: every output face is convex and has one loop", () => {
	const input = makeObject3dFromPolygonLoops([lPrism()]);
	const output = fixPolygons(input, "convex");
	for (const loops of facesOf(output)) {
		assertEquals(loops.length, 1);
		assertEquals(isConvex(loops[0]), true);
	}
	assertSameSurface(input, output);
});

Deno.test("convex: faces that are already convex are left as they are", () => {
	const input = makeObject3dFromPolygonLoops([boxPrism()]);
	const output = fixPolygons(input, "convex");
	assertEquals(facesOf(output).length, facesOf(input).length);
	assertSameSurface(input, output);
});

Deno.test("convex: fixing twice changes nothing more", () => {
	const once = fixPolygons(
		makeObject3dFromPolygonLoops([lPrism()]),
		"convex",
	);
	const twice = fixPolygons(once, "convex");
	assertEquals(facesOf(twice).length, facesOf(once).length);
});

// --- fixPolygons/triangles -----------------------------------------------

Deno.test("triangles: every output face is a triangle", () => {
	const input = makeObject3dFromPolygonLoops([lPrism()]);
	const output = fixPolygons(input, "triangles");
	for (const loops of facesOf(output)) {
		assertEquals(loops.length, 1);
		assertEquals(loops[0].length, 3);
	}
	assertSameSurface(input, output);
});

Deno.test("triangles: a face with a hole becomes triangles", () => {
	const input = makeObject3dFromPolygonLoops([pocketBox()]);
	const output = fixPolygons(input, "triangles");
	for (const loops of facesOf(output)) {
		assertEquals(loops.length, 1);
		assertEquals(loops[0].length, 3);
	}
	assertSameSurface(input, output);
});

Deno.test("triangles: the result can be written as STL", () => {
	const output = fixPolygons(
		makeObject3dFromPolygonLoops([pocketBox()]),
		"triangles",
	);
	assertEquals(trianglesOfObject3d(output).length, facesOf(output).length);
});

Deno.test("triangles: fixing twice changes nothing more", () => {
	const once = fixPolygons(
		makeObject3dFromPolygonLoops([pocketBox()]),
		"triangles",
	);
	const twice = fixPolygons(once, "triangles");
	assertEquals(facesOf(twice).length, facesOf(once).length);
});

// --- Errors --------------------------------------------------------------

Deno.test("fixPolygons rejects an unknown target", () => {
	assertThrows(
		() =>
			fixPolygons(
				makeObject3dFromPolygonLoops([boxPrism()]),
				"quads" as "convex",
			),
		Error,
		"unknown polygon fix",
	);
});
