import { assertEquals } from "@std/assert";
import type { Triangle, Vertex } from "./stl.ts";
import {
	checkManifold,
	pruneVector3DObject,
	vector3DObjectFromPolyhedrons,
	vector3DObjectFromTriangles,
} from "./vector3d-object.ts";
import { simplifyVector3DObject } from "./simplify.ts";

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

Deno.test("adjacent coplanar triangles are merged back into quads", () => {
	const object = vector3DObjectFromTriangles(triangulatedCube());
	const simplified = simplifyVector3DObject(object, false);
	assertEquals(simplified.polygons.length, 6);
	assertEquals(simplified.vertexes.length, 8);
	assertEquals(simplified.lines.length, 12);
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
	const object = vector3DObjectFromPolyhedrons([faces]);
	const simplified = simplifyVector3DObject(object, false);
	assertEquals(simplified.polygons.length, 6);
	assertEquals(simplified.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("polyhedrons sharing a face are merged into one", () => {
	const object = vector3DObjectFromPolyhedrons([
		CUBE_FACES,
		translate(CUBE_FACES, 1),
	]);
	const simplified = simplifyVector3DObject(object, false);
	assertEquals(simplified.polyhedrons.length, 1);
	assertEquals(simplified.polygons.length, 6);
	assertEquals(simplified.vertexes.length, 8);
	checkManifold(simplified);
});

Deno.test("keep-unused retains the original parts, which prune removes", () => {
	const object = vector3DObjectFromTriangles(triangulatedCube());
	const kept = simplifyVector3DObject(object, true);
	assertEquals(kept.polygons.length, 12 + 6);
	assertEquals(kept.polyhedrons.length, 2);
	const pruned = pruneVector3DObject(kept, "default");
	assertEquals(pruned.polygons.length, 6);
	checkManifold(pruned);
});

Deno.test("a shape with nothing to merge is unchanged", () => {
	const object = vector3DObjectFromPolyhedrons([CUBE_FACES]);
	const simplified = simplifyVector3DObject(object, false);
	assertEquals(simplified.polygons.length, 6);
	assertEquals(simplified.vertexes.length, 8);
});
