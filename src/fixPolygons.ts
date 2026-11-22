import {
	Loop,
	makeObject3dFromPolygonLoops,
	Object3D,
	PolygonLoops,
	polygonLoopsOfPolyhedron,
	polyhedronLeaves,
} from "./object3d.ts";
import { mergeAcross } from "./simplify.ts";
import { triangulateLoops, vectorAreaOfLoops } from "./triangulate.ts";

export type FixTarget = "single-loop" | "convex" | "triangles";

const TARGETS: readonly FixTarget[] = ["single-loop", "convex", "triangles"];

/** Whether a single loop turns the same way at every corner, as seen from its own normal. */
function isConvexLoop(loop: Loop): boolean {
	const normal = vectorAreaOfLoops([loop]);
	return loop.every((a, i) => {
		const b = loop[(i + 1) % loop.length];
		const c = loop[(i + 2) % loop.length];
		const e1 = [b[0] - a[0], b[1] - a[1], b[2] - a[2]];
		const e2 = [c[0] - b[0], c[1] - b[1], c[2] - b[2]];
		const turn = [
			e1[1] * e2[2] - e1[2] * e2[1],
			e1[2] * e2[0] - e1[0] * e2[2],
			e1[0] * e2[1] - e1[1] * e2[0],
		];
		return turn[0] * normal[0] + turn[1] * normal[1] +
				turn[2] * normal[2] >= -1e-9;
	});
}

/**
 * Greedily joins neighbouring pieces, refusing joins that would leave a hole. With `convex`, a join is
 * also refused unless the result is convex.
 */
function mergePieces(pieces: Loop[], convex: boolean): Loop[] {
	const result = pieces.slice();
	let changed = true;
	while (changed) {
		changed = false;
		outer: for (let i = 0; i < result.length; i++) {
			for (let j = 0; j < result.length; j++) {
				if (i === j) continue;
				const merged = mergeAcross(result[i], result[j], true);
				if (merged !== undefined && (!convex || isConvexLoop(merged))) {
					result[i] = merged;
					result.splice(j, 1);
					changed = true;
					break outer;
				}
			}
		}
	}
	return result;
}

/**
 * The polygons that replace one face (given as its loops, outer boundary first) for a fix target.
 * Each returned polygon is a list of loops; these are all single loops.
 */
function fixFace(loops: Loop[], target: FixTarget): PolygonLoops[] {
	const single = loops.length === 1;
	if (target === "single-loop") {
		return single
			? [loops]
			: mergePieces(triangulateLoops(loops), false).map((
				piece,
			) => [piece]);
	}
	if (target === "triangles") {
		return single && loops[0].length === 3
			? [loops]
			: triangulateLoops(loops).map((t) => [t]);
	}
	if (single && isConvexLoop(loops[0])) {
		return [loops];
	}
	return mergePieces(triangulateLoops(loops), true).map((piece) => [piece]);
}

/**
 * Rewrites every face of an object so that each polygon satisfies a target:
 * - `single-loop`: faces with holes are triangulated, then the triangles are merged back wherever no hole would form.
 * - `convex`: every polygon is convex, using triangulation followed by convex merging.
 * - `triangles`: every polygon is a triangle.
 * The surface is unchanged: its vector area and total area are preserved, and it stays manifold.
 */
export function fixPolygons(object: Object3D, target: FixTarget): Object3D {
	if (!TARGETS.includes(target)) {
		throw new Error(`unknown polygon fix ${target}`);
	}
	return makeObject3dFromPolygonLoops(
		polyhedronLeaves(object).map((leaf) =>
			polygonLoopsOfPolyhedron(leaf).flatMap((loops) =>
				fixFace(loops, target)
			)
		),
	);
}
