import type { Vertex } from "./stl.ts";
import {
	Loop,
	loopOfPolyhedron,
	makeObject3D,
	Object3D,
	Polyhedron3D,
	Polyhedron3DData,
	polyhedronLeaves,
} from "./object3d.ts";

/** Tolerance for coplanarity and collinearity checks, which are only approximate. */
const EPSILON = 1e-6;

function keyOf(v: Vertex): string {
	return v.join(",");
}

function sub(a: Vertex, b: Vertex): Vertex {
	return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}

function cross(a: Vertex, b: Vertex): Vertex {
	return [
		a[1] * b[2] - a[2] * b[1],
		a[2] * b[0] - a[0] * b[2],
		a[0] * b[1] - a[1] * b[0],
	];
}

function dot(a: Vertex, b: Vertex): number {
	return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

function length(a: Vertex): number {
	return Math.hypot(a[0], a[1], a[2]);
}

/** Removes repeated consecutive vertexes, including across the wrap-around. */
function cleanLoop(loop: Loop): Loop {
	const result: Loop = [];
	for (const v of loop) {
		if (
			result.length === 0 || keyOf(result[result.length - 1]) !== keyOf(v)
		) {
			result.push(v);
		}
	}
	while (
		result.length > 1 &&
		keyOf(result[0]) === keyOf(result[result.length - 1])
	) {
		result.pop();
	}
	return result;
}

function hasRepeatedVertex(loop: Loop): boolean {
	return new Set(loop.map(keyOf)).size !== loop.length;
}

/** The same loop regardless of where it starts. */
function canonicalLoop(loop: Loop): string {
	const keys = loop.map(keyOf);
	let start = 0;
	for (let i = 1; i < keys.length; i++) {
		if (keys[i] < keys[start]) start = i;
	}
	return [...keys.slice(start), ...keys.slice(0, start)].join("|");
}

function rotate(loop: Loop, start: number): Loop {
	return [...loop.slice(start), ...loop.slice(0, start)];
}

/** Area-weighted normal of a loop, pointing out of the front of the polygon. */
// IMPLIED WINDING ORDER APPLIES HERE
function loopNormal(loop: Loop): Vertex {
	const normal: Vertex = [0, 0, 0];
	for (let i = 0; i < loop.length; i++) {
		const c = cross(loop[i], loop[(i + 1) % loop.length]);
		normal[0] += c[0];
		normal[1] += c[1];
		normal[2] += c[2];
	}
	return normal;
}

function areCoplanar(a: Loop, b: Loop): boolean {
	const na = loopNormal(a);
	const nb = loopNormal(b);
	const lengths = length(na) * length(nb);
	if (lengths === 0) return false;
	return dot(na, nb) / lengths > 1 - EPSILON;
}

/** Whether `v` lies strictly between `p` and `q` on a straight line. */
function isBetweenCollinear(p: Vertex, v: Vertex, q: Vertex): boolean {
	const vp = sub(p, v);
	const vq = sub(q, v);
	const bound = EPSILON * length(vp) * length(vq);
	return length(cross(vp, vq)) <= bound && dot(vp, vq) < 0;
}

function reverseLoop(loop: Loop): Loop {
	return loop.slice().reverse();
}

/**
 * Joins two loops that share an edge traversed in opposite directions,
 * dropping that edge. Returns undefined if they do not share one, or the result would not be simple.
 */
function mergeAcross(f: Loop, g: Loop): Loop | undefined {
	const fKeys = f.map(keyOf);
	const gKeys = g.map(keyOf);
	for (let i = 0; i < f.length; i++) {
		const a = fKeys[i];
		const b = fKeys[(i + 1) % f.length];
		for (let j = 0; j < g.length; j++) {
			if (gKeys[j] !== b || gKeys[(j + 1) % g.length] !== a) continue;
			// fRot runs from b to a, gRot from a to b, so drop the shared edge at both ends.
			const fRot = rotate(f, (i + 1) % f.length);
			const gRot = rotate(g, (j + 1) % g.length);
			const merged = cleanLoop([...fRot, ...gRot.slice(1, -1)]);
			if (merged.length >= 3 && !hasRepeatedVertex(merged)) {
				return merged;
			}
		}
	}
	return undefined;
}

/**
 * Joins polyhedrons that share a polygon with opposite orientations, removing that polygon.
 * Returns one list of faces per resulting polyhedron.
 */
function mergePolyhedrons(polyhedrons: Loop[][]): Loop[][] {
	const parent = polyhedrons.map((_, i) => i);
	const find = (i: number): number =>
		parent[i] === i ? i : (parent[i] = find(parent[i]));
		
	const byCanonical = new Map<string, { p: number; f: number }[]>();
	polyhedrons.forEach((faces, p) =>
		faces.forEach((loop, f) => {
			const key = canonicalLoop(loop);
			byCanonical.set(key, [...(byCanonical.get(key) ?? []), { p, f }]);
		})
	);
	
	const matched = new Set<string>();
	polyhedrons.forEach((faces, p) =>
		faces.forEach((loop, f) => {
			const id = `${p}:${f}`;
			if (matched.has(id)) return;
			const partner =
				(byCanonical.get(canonicalLoop(reverseLoop(loop))) ?? [])
					.find((o) => o.p !== p && !matched.has(`${o.p}:${o.f}`));
			if (partner === undefined) return;
			matched.add(id);
			matched.add(`${partner.p}:${partner.f}`);
			parent[find(partner.p)] = find(p);
		})
	);
	
	const groups = new Map<number, Loop[]>();
	polyhedrons.forEach((faces, p) =>
		faces.forEach((loop, f) => {
			if (matched.has(`${p}:${f}`)) return;
			const root = find(p);
			groups.set(root, [...(groups.get(root) ?? []), loop]);
		})
	);
	return [...groups.values()];
}

/** Repeatedly joins coplanar loops that share an edge. Mutates and returns `loops`. */
function mergeCoplanar(loops: Loop[]): Loop[] {
	let changed = true;
	while (changed) {
		changed = false;
		outer: for (let i = 0; i < loops.length; i++) {
			for (let j = 0; j < loops.length; j++) {
				if (i === j || !areCoplanar(loops[i], loops[j])) continue;
				const merged = mergeAcross(loops[i], loops[j]);
				if (merged !== undefined) {
					loops[i] = merged;
					loops.splice(j, 1);
					changed = true;
					break outer;
				}
			}
		}
	}
	return loops;
}

/**
 * Removes vertexes that have exactly two neighbours, lying on a straight line between them.
 * Such a vertex is shared only by the two lines through it, so removing it changes no shape.
 * Mutates the loops in `groups`.
 */
function removeCollinearVertexes(groups: Loop[][]): void {
	let changed = true;
	while (changed) {
		changed = false;
		const coords = new Map<string, Vertex>();
		const neighbours = new Map<string, Set<string>>();
		const link = (a: string, b: string) => {
			neighbours.set(a, (neighbours.get(a) ?? new Set()).add(b));
			neighbours.set(b, (neighbours.get(b) ?? new Set()).add(a));
		};
		for (const loops of groups) {
			for (const loop of loops) {
				loop.forEach((v, i) => {
					coords.set(keyOf(v), v);
					link(keyOf(v), keyOf(loop[(i + 1) % loop.length]));
				});
			}
		}
		
		for (const [key, set] of neighbours) {
			if (set.size !== 2) continue;
			const [p, q] = [...set];
			if (
				!isBetweenCollinear(
					coords.get(p)!,
					coords.get(key)!,
					coords.get(q)!,
				)
			) {
				continue;
			}
			const affected = groups.flatMap((loops) =>
				loops.filter((loop) => loop.some((v) => keyOf(v) === key))
			);
			if (affected.some((loop) => loop.length - 1 < 3)) continue;
			for (const loop of affected) {
				loop.splice(loop.findIndex((v) => keyOf(v) === key), 1);
			}
			changed = true;
			break;
		}
	}
}

/** Simplifies polyhedrons given as loops. Returns one list of loops per polyhedron. */
export function simplifyPolyhedrons(polyhedrons: Loop[][]): Loop[][] {
	const cleaned = polyhedrons.map((loops) =>
		loops.map(cleanLoop).filter((loop) => loop.length >= 3)
	);
	const groups = mergePolyhedrons(cleaned).map(mergeCoplanar);
	removeCollinearVertexes(groups);
	return groups.filter((loops) => loops.length > 0);
}

/** The shared data, if every leaf refers to the same `Polyhedron3DData`. */
function commonData(leaves: Polyhedron3D[]): Polyhedron3DData | undefined {
	const first = leaves[0]?.data;
	return first !== undefined && leaves.every((leaf) => leaf.data === first)
		? first
		: undefined;
}

/** Whether simplification changed nothing, judged by the number of polyhedrons, loops, and vertexes. */
function sameCounts(before: Loop[][], after: Loop[][]): boolean {
	const count = (polyhedra: Loop[][]) => [
		polyhedra.length,
		polyhedra.flat().length,
		polyhedra.flat().flat().length,
	];
	return count(before).every((n, i) => n === count(after)[i]);
}

/**
 * Simplifies an object on a best-effort basis. Its leaves are treated as one set,
 * however they are nested. The new parts are appended to the leaves' shared data if they all have one,
 * so the old data is kept as the base. Use `prune` afterwards to drop unused parts.
 * If nothing changes, the same object is returned.
 */
export function simplifyObject3D(object: Object3D): Object3D {
	const leaves = polyhedronLeaves(object);
	const before = leaves.map(loopOfPolyhedron);
	const after = simplifyPolyhedrons(before);
	if (sameCounts(before, after)) {
		return object;
	}
	return makeObject3D(after, commonData(leaves));
}
