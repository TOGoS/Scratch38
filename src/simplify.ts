import type { Vertex } from "./stl.ts";
import {
	Loop,
	loopOfPolyhedron,
	loopsOfEdges,
	makeObject3dFromPolygonLoops,
	Object3D,
	Polyhedron3D,
	Polyhedron3DData,
	polyhedronLeaves,
	PolyhedronLoops,
} from "./object3d.ts";

/** Tolerance for coplanarity and collinearity checks, which are only approximate. */
const EPSILON = 1e-6;

function keyOf(v: Vertex): string {
	return v.join(",");
}

/** A key for the undirected line between two vertexes. */
function edgeKey(a: Vertex, b: Vertex): string {
	const ka = keyOf(a);
	const kb = keyOf(b);
	return ka < kb ? `${ka}|${kb}` : `${kb}|${ka}`;
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
 * A key for a face, as its multiset of directed edges. Sorting makes the key independent of where
 * a loop starts, and repeated vertexes need no special handling. With `reversed`, each edge is
 * reversed, giving the key of the same face viewed from the other side.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function faceKey(loop: Loop, reversed = false): string {
	return loop
		.map((v, i) => {
			const next = loop[(i + 1) % loop.length];
			return reversed
				? `${keyOf(next)}>${keyOf(v)}`
				: `${keyOf(v)}>${keyOf(next)}`;
		})
		.sort()
		.join("|");
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

/** Whether `v` lies strictly between `p` and `q` on a straight line. */
function isBetweenCollinear(p: Vertex, v: Vertex, q: Vertex): boolean {
	const vp = sub(p, v);
	const vq = sub(q, v);
	const bound = EPSILON * length(vp) * length(vq);
	return length(cross(vp, vq)) <= bound && dot(vp, vq) < 0;
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
/** Whether some edge of a loop is traversed again, in the opposite direction, by the same loop. */
function touchesItself(loop: Loop): boolean {
	const keys = loop.map(keyOf);
	const n = keys.length;
	const edges = new Set(keys.map((k, i) => `${k}|${keys[(i + 1) % n]}`));
	return keys.some((k, i) => edges.has(`${keys[(i + 1) % n]}|${k}`));
}

/**
 * Joins two loops across a run of consecutive edges they share, traversed in opposite directions.
 * Normally the longest run is merged and any other shared runs are left in place, which can leave a
 * polygon with a hole. With `singleLoop`, the merge is refused unless f and g share exactly one run,
 * and the result has no repeated vertex or self-touching line. Gluing two disks along one arc, and nowhere
 * else, gives a disk, so the outline stays a single loop.
 */
export function mergeAcross(
	f: Loop,
	g: Loop,
	singleLoop = false,
): Loop | undefined {
	const fKeys = f.map(keyOf);
	const gKeys = g.map(keyOf);
	const n = f.length;
	const m = g.length;
	if (singleLoop) {
		const shared = f.map((_, i) =>
			gKeys.some((_, j) =>
				gKeys[j] === fKeys[(i + 1) % n] &&
				gKeys[(j + 1) % m] === fKeys[i]
			)
		);
		const sharedCount = shared.filter((s) => s).length;
		const runs = shared.filter((s, i) =>
			s && !shared[(i - 1 + n) % n]
		).length;
		if (runs !== 1 || sharedCount === n) return undefined;
	}
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
	if (singleLoop && length !== sharedCountOf(fKeys, gKeys)) return undefined;
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
	// A repeated vertex is a pinch: the pieces touch at a point as well as along the shared arc, which is not a disk.
	if (
		singleLoop &&
		(touchesItself(merged) ||
			new Set(merged.map(keyOf)).size !== merged.length)
	) {
		return undefined;
	}
	return merged.length >= 3 ? merged : undefined;
}

/** How many of f's edges are traversed backwards by g. */
function sharedCountOf(fKeys: string[], gKeys: string[]): number {
	const n = fKeys.length;
	const m = gKeys.length;
	return fKeys.filter((_, i) =>
		gKeys.some((_, j) =>
			gKeys[j] === fKeys[(i + 1) % n] && gKeys[(j + 1) % m] === fKeys[i]
		)
	).length;
}

/**
 * Splits a loop that uses a line twice in opposite directions into loops that do not share lines.
 * Such a doubled line is a cut between a polygon's outer boundary and a hole; dropping both uses
 * leaves the boundaries as separate loops. The remaining edges are then grouped into loops by
 * `loopsOfEdges`. Returns one loop if there is no such line.
 */
export function splitSharedLines(loop: Loop): Loop[] {
	const edges: [Vertex, Vertex][] = loop.map((
		v,
		i,
	) => [v, loop[(i + 1) % loop.length]]);
	const uses = new Map<string, number[]>();
	edges.forEach((edge, i) => {
		const key = edgeKey(edge[0], edge[1]);
		uses.set(key, [...(uses.get(key) ?? []), i]);
	});
	const removed = new Set<number>();
	for (const positions of uses.values()) {
		const unmatched = [...positions];
		while (unmatched.length >= 2) {
			const i = unmatched.shift()!;
			const partner = unmatched.findIndex((j) =>
				keyOf(edges[i][0]) === keyOf(edges[j][1]) &&
				keyOf(edges[i][1]) === keyOf(edges[j][0])
			);
			if (partner < 0) continue;
			removed.add(i);
			removed.add(unmatched.splice(partner, 1)[0]);
		}
	}
	if (removed.size === 0) return [loop];
	return loopsOfEdges(edges.filter((_, i) => !removed.has(i)), keyOf);
}

/**
 * Joins polyhedrons that share a polygon with opposite orientations, removing that polygon.
 * Returns one list of faces per resulting polyhedron.
 */
function mergePolyhedrons(polyhedrons: Loop[][]): Loop[][] {
	const parent = polyhedrons.map((_, i) => i);
	const find = (i: number): number =>
		parent[i] === i ? i : (parent[i] = find(parent[i]));
		
	const byFace = new Map<string, { p: number; f: number }[]>();
	polyhedrons.forEach((faces, p) =>
		faces.forEach((loop, f) => {
			const key = faceKey(loop);
			byFace.set(key, [...(byFace.get(key) ?? []), { p, f }]);
		})
	);
	
	const matched = new Set<string>();
	polyhedrons.forEach((faces, p) =>
		faces.forEach((loop, f) => {
			const id = `${p}:${f}`;
			if (matched.has(id)) return;
			const partner = (byFace.get(faceKey(loop, true)) ?? [])
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

interface MergeEntry {
	loop: Loop;
	normal: Vertex | undefined;
	alive: boolean;
}

/**
 * Repeatedly joins coplanar loops that share a line. The index of loops using each line is built
 * once and updated as loops merge, so only pairs that share a line are tried. Returns the loops
 * in their original relative order; the first of a merged pair keeps its place.
 */
function mergeCoplanar(loops: Loop[], singleLoop: boolean): Loop[] {
	const entries: MergeEntry[] = loops.map((loop) => ({
		loop,
		normal: undefined,
		alive: true,
	}));
	
	const byLine = new Map<string, Set<number>>();
	const updateLines = (id: number, loop: Loop, add: boolean) => {
		for (let i = 0; i < loop.length; i++) {
			const key = edgeKey(loop[i], loop[(i + 1) % loop.length]);
			const using = byLine.get(key);
			if (add) {
				if (using === undefined) byLine.set(key, new Set([id]));
				else using.add(id);
			} else if (using !== undefined) {
				using.delete(id);
				if (using.size === 0) byLine.delete(key);
			}
		}
	};
	entries.forEach((entry, id) => updateLines(id, entry.loop, true));
	
	const versions = entries.map(() => 0);
	const pending: [number, number][] = [];
	const tried = new Map<string, string>();
	const enqueue = (a: number, b: number) => {
		if (a !== b) pending.push(a < b ? [a, b] : [b, a]);
	};
	// Every pair sharing a line is a possible merge to start with.
	for (const using of byLine.values()) {
		const ids = [...using];
		for (let i = 0; i < ids.length; i++) {
			for (let j = i + 1; j < ids.length; j++) enqueue(ids[i], ids[j]);
		}
	}
	
	const normalOf = (id: number): Vertex | undefined => {
		const entry = entries[id];
		if (entry.normal === undefined) entry.normal = loopNormal(entry.loop);
		return entry.normal;
	};
	const coplanar = (a: number, b: number): boolean => {
		const na = normalOf(a);
		const nb = normalOf(b);
		if (na === undefined || nb === undefined) return false;
		const lengths = length(na) * length(nb);
		return lengths !== 0 && dot(na, nb) / lengths > 1 - EPSILON;
	};
	
	// A merge changes only the survivor's loop, so only its partner pairs need revisiting.
	for (let next = 0; next < pending.length; next++) {
		const [a, b] = pending[next];
		if (!entries[a].alive || !entries[b].alive) continue;
		const stamp = `${versions[a]},${versions[b]}`;
		const key = `${a}:${b}`;
		if (tried.get(key) === stamp) continue;
		tried.set(key, stamp);
		if (!coplanar(a, b)) continue;
		const merged = mergeAcross(
			entries[a].loop,
			entries[b].loop,
			singleLoop,
		);
		if (merged === undefined) continue;
		
		updateLines(a, entries[a].loop, false);
		updateLines(b, entries[b].loop, false);
		entries[a].loop = merged;
		entries[a].normal = undefined;
		entries[b].alive = false;
		updateLines(a, merged, true);
		versions[a]++;
		
		const partners = new Set<number>();
		for (let i = 0; i < merged.length; i++) {
			const using = byLine.get(
				edgeKey(merged[i], merged[(i + 1) % merged.length]),
			);
			if (using === undefined) continue;
			for (const id of using) {
				if (id !== a && entries[id].alive) partners.add(id);
			}
		}
		for (const id of partners) enqueue(a, id);
	}
	
	return entries.filter((entry) => entry.alive).map((entry) => entry.loop);
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
 * each polygon being one or more loops. The caller groups those loops into the flat
 * edges stored in `Polyhedron3DData`.
 */
export interface SimplifyOptions {
	/** Refuse merges that would leave a polygon with a hole. */
	singleLoopPolygons?: boolean;
}

export function simplifyPolyhedrons(
	polyhedra: Loop[][],
	options: SimplifyOptions = {},
): PolyhedronLoops[] {
	const cleaned = polyhedra.map((loops) =>
		loops.map(cleanLoop).filter((loop) => loop.length >= 3)
	);
	const groups: PolyhedronLoops[] = mergePolyhedrons(cleaned).map(
		(loops) =>
			mergeCoplanar(loops, options.singleLoopPolygons ?? false).map(
				splitSharedLines,
			),
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
function sameCounts(before: Loop[][], after: PolyhedronLoops[]): boolean {
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
export function simplifyObject3d(
	object: Object3D,
	options: SimplifyOptions = {},
): Object3D {
	const leaves = polyhedronLeaves(object);
	const before = leaves.map(loopOfPolyhedron);
	const after = simplifyPolyhedrons(before, options);
	if (sameCounts(before, after)) {
		return object;
	}
	return makeObject3dFromPolygonLoops(after, commonData(leaves));
}
