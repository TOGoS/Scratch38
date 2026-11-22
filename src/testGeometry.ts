// Shared test geometry: polyhedra built from explicit polygons.
import type { Vertex } from "./stl.ts";
import type { Loop } from "./object3d.ts";

// --- Test geometry -------------------------------------------------------

/**
 * A prism over a counter-clockwise 2D outline, from z=0 to z=height.
 * Returns the polygons of its polyhedron: each polygon is a list of loops.
 */
export function prism(xy: [number, number][], height: number): Loop[][] {
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
export function lPrism(): Loop[][] {
	return prism([[0, 0], [2, 0], [2, 1], [1, 1], [1, 2], [0, 2]], 1);
}

/** A box with a square pocket in its top face. The top face is one polygon with a hole. */
export function pocketBox(): Loop[][] {
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

export function boxPrism(): Loop[][] {
	return prism([[0, 0], [2, 0], [2, 1], [0, 1]], 1);
}
