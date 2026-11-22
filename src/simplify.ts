import type { Vertex } from "./stl.ts";
import {
	Loop,
	loopOfPolyhedron,
	makeObject3dFromPolygonLoops,
	Object3D,
	Polyhedron3D,
	Polyhedron3DData,
	polyhedronLeaves,
	PolyhedronPolygons,
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

/**
 * The same loop regardless of where it starts. Every rotation is compared, so this also holds for loops
 * that visit a vertex more than once, where the smallest vertex does not identify a single start.
 */
export function canonicalLoop(loop: Loop): string {
	const keys = loop.map(keyOf);
	let best: string | undefined;
	for (let start = 0; start < keys.length; start++) {
		const candidate = [...keys.slice(start), ...keys.slice(0, start)].join(
			"|",
		);
		if (best === undefined || candidate < best) best = candidate;
	}
	return best ?? "";
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
 * Removes spurs: places where the loop runs out along a line and straight back along it,
 * so that a vertex's neighbours on both sides are the same.
 * Removing such a spur does not change the shape.
 */
export function removeSpurs(loop: Loop): Loop {
	let result = loop;
	let found = true;
	while (found && result.length >= 4) {
		found = false;
		const n = result.length;
		for (let i = 0; i < n; i++) {
			if (keyOf(result[(i - 1 + n) % n]) === keyOf(result[(i + 1) % n])) {
				const drop = new Set([i, (i + 1) % n]);
				result = result.filter((_, k) => !drop.has(k));
				found = true;
				break;
			}
		}
	}
	return result;
}

/**
 * Joins two loops across the longest run of consecutive edges they share, traversed in opposite directions,
 * dropping that run. Only one run is merged; any other shared run stays, so the result may use a line twice.
 * `splitSharedLines` deals with those afterwards. Spurs left behind are removed.
 * Returns undefined if they share no edge, or the result would have fewer than three vertexes.
 */
function mergeAcross(f: Loop, g: Loop): Loop | undefined {
	const fKeys = f.map(keyOf);
	const gKeys = g.map(keyOf);
	const n = f.length;
	const m = g.length;
	let best: { i: number; s: number; length: number } | undefined;
	for (let i = 0; i < n; i++) {
		for (let s = 0; s < m; s++) {
			// A run of one edge: g[s] = f[i+1] and g[s+1] = f[i]. The run in g then reads f backwards.
			if (
				gKeys[s] !== fKeys[(i + 1) % n] ||
				gKeys[(s + 1) % m] !== fKeys[i]
			) continue;
			let length = 1;
			let start = s;
			while (
				length + 1 < n && length + 1 < m &&
				gKeys[(start - 1 + m) % m] === fKeys[(i + length + 1) % n]
			) {
				start = (start - 1 + m) % m;
				length++;
			}
			if (best === undefined || length > best.length) {
				best = { i, s: start, length };
			}
		}
	}
	if (best === undefined) return undefined;
	
	const { i, s, length } = best;
	// From the end of the run around f to its start, then from the start around g to its end.
	const fPart = Array.from(
		{ length: n - length + 1 },
		(_, t) => f[(i + length + t) % n],
	);
	const gPart = Array.from(
		{ length: m - length + 1 },
		(_, t) => g[(s + length + t) % m],
	);
	const merged = removeSpurs(cleanLoop([...fPart, ...gPart.slice(1, -1)]));
	return merged.length >= 3 ? merged : undefined;
}

/**
 * Splits a loop that uses a line twice in opposite directions into loops that do not share lines.
 * Such a doubled line is a cut between a polygon's outer boundary and a hole; dropping both uses
 * leaves the boundaries as separate loops of one polygon. Returns one loop if there is no such line.
 */
export function splitSharedLines(loop: Loop): Loop[] {
	const n = loop.length;
	if (n < 4) return [loop];
	const keys = loop.map(keyOf);
	const uses = new Map<string, number[]>();
	for (let i = 0; i < n; i++) {
		const a = keys[i];
		const b = keys[(i + 1) % n];
		const key = a < b ? `${a}|${b}` : `${b}|${a}`;
		uses.set(key, [...(uses.get(key) ?? []), i]);
	}
	const removed = new Set<number>();
	for (const positions of uses.values()) {
		const unmatched = [...positions];
		while (unmatched.length >= 2) {
			const i = unmatched.shift()!;
			const partner = unmatched.findIndex((j) =>
				keys[i] === keys[(j + 1) % n] && keys[(i + 1) % n] === keys[j]
			);
			if (partner < 0) continue;
			removed.add(i);
			removed.add(unmatched.splice(partner, 1)[0]);
		}
	}
	if (removed.size === 0) return [loop];
	
	const remaining = keys.map((_, i) => i).filter((i) => !removed.has(i));
	const outgoing = new Map<string, number[]>();
	for (const i of remaining) {
		const from = keys[i];
		outgoing.set(from, [...(outgoing.get(from) ?? []), i]);
	}
	const vertexOf = new Map(keys.map((key, i) => [key, loop[i]]));
	const visited = new Set<number>();
	const result: Loop[] = [];
	for (const start of remaining) {
		if (visited.has(start)) continue;
		const vertices: Loop = [];
		let current = start;
		for (let step = 0; step <= remaining.length; step++) {
			visited.add(current);
			vertices.push(vertexOf.get(keys[current])!);
			const to = keys[(current + 1) % n];
			const candidates = outgoing.get(to) ?? [];
			const ordered = [
				...candidates.filter((i) => i > current),
				...candidates,
			];
			const next = ordered.find((i) => i === start || !visited.has(i));
			if (next === undefined) break;
			current = next;
			if (current === start) break;
		}
		if (current !== start) {
			throw new Error(
				"Could not split a loop that shares lines with itself",
			);
		}
		result.push(vertices);
	}
	return result;
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
 * Mutates the given loops.
 */
function removeCollinearVertexes(loops: Loop[]): void {
	let changed = true;
	while (changed) {
		changed = false;
		const coords = new Map<string, Vertex>();
		const neighbours = new Map<string, Set<string>>();
		const link = (a: string, b: string) => {
			neighbours.set(a, (neighbours.get(a) ?? new Set()).add(b));
			neighbours.set(b, (neighbours.get(b) ?? new Set()).add(a));
		};
		for (const loop of loops) {
			loop.forEach((v, i) => {
				coords.set(keyOf(v), v);
				link(keyOf(v), keyOf(loop[(i + 1) % loop.length]));
			});
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
			const affected = loops.filter((loop) =>
				loop.some((v) => keyOf(v) === key)
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

/**
 * Simplifies polyhedrons given as loops. Returns one list of polygons per polyhedron,
 * each polygon being one or more loops.
 */
export function simplifyPolyhedrons(polyhedra: Loop[][]): PolyhedronPolygons[] {
	const cleaned = polyhedra.map((loops) =>
		loops.map(cleanLoop).filter((loop) => loop.length >= 3)
	);
	const groups: PolyhedronPolygons[] = mergePolyhedrons(cleaned).map(
		(loops) => mergeCoplanar(loops).map(splitSharedLines),
	);
	removeCollinearVertexes(groups.flat(2));
	return groups
		.map((polygons) =>
			polygons
				.map((loops) => loops.filter((loop) => loop.length >= 3))
				.filter((loops) => loops.length > 0)
		)
		.filter((polygons) => polygons.length > 0);
}

/** The shared data, if every leaf refers to the same `Polyhedron3DData`. */
function commonData(leaves: Polyhedron3D[]): Polyhedron3DData | undefined {
	const first = leaves[0]?.data;
	return first !== undefined && leaves.every((leaf) => leaf.data === first)
		? first
		: undefined;
}

/** Whether simplification changed nothing, judged by the number of polyhedrons, loops, and vertexes. */
function sameCounts(before: Loop[][], after: PolyhedronPolygons[]): boolean {
	const beforeCounts = [
		before.length,
		before.flat().length,
		before.flat().flat().length,
	];
	const afterCounts = [
		after.length,
		after.flat(2).length,
		after.flat(3).length,
	];
	return beforeCounts.every((n, i) => n === afterCounts[i]);
}

/**
 * Simplifies an object on a best-effort basis. Its leaves are treated as one set,
 * however they are nested. The new parts are appended to the leaves' shared data if they all have one,
 * so the old data is kept as the base. Use `prune` afterwards to drop unused parts.
 * If nothing changes, the same object is returned.
 */
export function simplifyObject3d(object: Object3D): Object3D {
	const leaves = polyhedronLeaves(object);
	const before = leaves.map(loopOfPolyhedron);
	const after = simplifyPolyhedrons(before);
	if (sameCounts(before, after)) {
		return object;
	}
	return makeObject3dFromPolygonLoops(after, commonData(leaves));
}
