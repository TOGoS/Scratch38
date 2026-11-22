import type { Triangle, Vertex } from "./stl.ts";

/**
 * A sequence of vertexes tracing part of a polygon boundary. The polygon's interior is on the left
 * of each step, so viewed from outside an outer boundary runs counter-clockwise and a hole boundary
 * runs clockwise. This is the same convention that `PolygonEdge` directions express.
 */
export type Loop = Vertex[];

export interface PolygonEdge {
	readonly line: number;
	readonly direction: "forwards" | "backwards";
}

/**
 * A polygon's boundary as a flat, unordered list of directed edges.
 * Grouping the edges into loops is a separate concern (see `loopsOfEdges`).
 */
export type Polygon = readonly PolygonEdge[];

/** A polygon given as loops of vertexes, at a boundary where loops are the natural input. */
export type PolygonLoops = readonly Loop[];

/** A polyhedron given as a list of polygons, each a list of vertex loops. */
export type PolyhedronLoops = readonly PolygonLoops[];

export interface PolyhedronFace {
	readonly polygon: number;
	readonly side: "front" | "back";
}

export type Polyhedron = readonly PolyhedronFace[];

/** The shared geometry that `Polyhedron3D`s refer to. Treated as immutable once built. */
export interface Polyhedron3DData {
	readonly classRef: "tag:nuke24.net,2026-11:s38s39:Polyhedron3DData-v1";
	readonly vertexes: readonly Vertex[];
	readonly lines: readonly (readonly [number, number])[];
	readonly polygons: readonly Polygon[];
	readonly polyhedrons: readonly Polyhedron[];
}

/** One polyhedron within a `Polyhedron3DData`. */
export interface Polyhedron3D {
	readonly classRef: "tag:nuke24.net,2026-11:s38s39:Polyhedron3D-v1";
	readonly data: Polyhedron3DData;
	readonly polyhedron: number;
}

/** The union of a list of `Object3D`s, whatever their representation. */
export interface UnionObject3D {
	readonly classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1";
	readonly members: readonly Object3D[];
}

export type Object3D = Polyhedron3D | UnionObject3D;

function keyOf(v: Vertex): string {
	return v.join(",");
}

function itemAt<T>(items: readonly T[], index: number, what: string): T {
	const item = items[index];
	if (item === undefined) {
		throw new Error(`${what} ${index} does not exist`);
	}
	return item;
}

function sortedUnique(indexes: number[]): number[] {
	return [...new Set(indexes)].sort((a, b) => a - b);
}

/**
 * Groups directed edges into closed loops, following each edge to the one that starts where it ends.
 * Endpoints are compared with `key`. Throws on branching (an endpoint starting more than one edge)
 * or on a path that cannot close, so malformed boundaries are reported here rather than silently accepted.
 */
export function loopsOfEdges<T>(
	edges: readonly (readonly [T, T])[],
	key: (value: T) => string,
): T[][] {
	const startOf = new Map<string, number>();
	edges.forEach(([from], i) => {
		const fromKey = key(from);
		if (startOf.has(fromKey)) {
			throw new Error(
				`Cannot group edges into loops: vertex ${fromKey} starts more than one edge`,
			);
		}
		startOf.set(fromKey, i);
	});
	const visited = new Set<number>();
	const result: T[][] = [];
	for (let start = 0; start < edges.length; start++) {
		if (visited.has(start)) continue;
		const loop: T[] = [];
		let current = start;
		while (!visited.has(current)) {
			visited.add(current);
			const [from, to] = edges[current];
			loop.push(from);
			const next = startOf.get(key(to));
			if (next === undefined) {
				throw new Error(
					`Cannot group edges into loops: edge from ${key(from)} to ${
						key(to)
					} has no continuation`,
				);
			}
			current = next;
		}
		if (current !== start) {
			throw new Error(
				"Cannot group edges into loops: edges do not form closed loops",
			);
		}
		result.push(loop);
	}
	return result;
}

/**
 * Builds an `Object3D` from polyhedrons, each a list of polygons, each polygon a list of loops.
 * Loops are counter-clockwise when viewed from outside; loop order within a polygon is not significant.
 * Each polygon's edges are stored as one flat, unordered list once vertex and line indexes are assigned.
 * Vertexes and lines with the same coordinates or endpoints are shared.
 * If `base` is given, the new parts are appended to copies of its lists, and existing parts are reused where they match.
 * A single polyhedron becomes a `Polyhedron3D`; otherwise the result is a `UnionObject3D` of them.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function makeObject3dFromPolygonLoops(
	polyhedra: readonly PolyhedronLoops[],
	base?: Polyhedron3DData,
): Object3D {
	const vertexes: Vertex[] = [...(base?.vertexes ?? [])];
	const vertexIndexes = new Map<string, number>();
	vertexes.forEach((v, i) => {
		if (!vertexIndexes.has(keyOf(v))) vertexIndexes.set(keyOf(v), i);
	});
	const vertexIndex = (v: Vertex): number => {
		let index = vertexIndexes.get(keyOf(v));
		if (index === undefined) {
			index = vertexes.length;
			vertexes.push(v);
			vertexIndexes.set(keyOf(v), index);
		}
		return index;
	};
	
	const lines: (readonly [number, number])[] = [...(base?.lines ?? [])];
	const lineIndexes = new Map<string, number>();
	lines.forEach(([a, b], i) => {
		const key = `${Math.min(a, b)},${Math.max(a, b)}`;
		if (!lineIndexes.has(key)) lineIndexes.set(key, i);
	});
	const lineIndex = (from: number, to: number) => {
		const key = `${Math.min(from, to)},${Math.max(from, to)}`;
		let index = lineIndexes.get(key);
		if (index === undefined) {
			index = lines.length;
			lines.push([from, to]);
			lineIndexes.set(key, index);
		}
		const [a] = lines[index];
		return {
			line: index,
			direction: a === from ? "forwards" as const : "backwards" as const,
		};
	};
	
	const polygons: Polygon[] = [...(base?.polygons ?? [])];
	const polyhedrons: Polyhedron[] = [...(base?.polyhedrons ?? [])];
	const newPolyhedrons: number[] = [];
	for (const polygonsOfPolyhedron of polyhedra) {
		const faces: PolyhedronFace[] = [];
		for (const loops of polygonsOfPolyhedron) {
			const edges: PolygonEdge[] = [];
			for (const loop of loops) {
				const indexes = loop.map(vertexIndex);
				indexes.forEach((from, i) =>
					edges.push(
						lineIndex(from, indexes[(i + 1) % indexes.length]),
					)
				);
			}
			polygons.push(edges);
			faces.push({ polygon: polygons.length - 1, side: "front" });
		}
		polyhedrons.push(faces);
		newPolyhedrons.push(polyhedrons.length - 1);
	}
	
	const data: Polyhedron3DData = {
		classRef: "tag:nuke24.net,2026-11:s38s39:Polyhedron3DData-v1",
		vertexes,
		lines,
		polygons,
		polyhedrons,
	};
	const leaves: Polyhedron3D[] = newPolyhedrons.map((polyhedron) => ({
		classRef: "tag:nuke24.net,2026-11:s38s39:Polyhedron3D-v1",
		data,
		polyhedron,
	}));
	return leaves.length === 1 ? leaves[0] : {
		classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
		members: leaves,
	};
}

/** Builds an object whose polygons each have one loop. */
export function makeObject3d(
	polyhedra: readonly (readonly Loop[])[],
	base?: Polyhedron3DData,
): Object3D {
	return makeObject3dFromPolygonLoops(
		polyhedra.map((loops) => loops.map((loop) => [loop])),
		base,
	);
}

/** Every `Polyhedron3D` in an object, in order, however its unions are nested. */
export function polyhedronLeaves(object: Object3D): Polyhedron3D[] {
	return "members" in object
		? object.members.flatMap(polyhedronLeaves)
		: [object];
}

/** Rebuilds an object with each `Polyhedron3D` replaced by `replace(leaf)`, keeping the union structure. */
function mapLeaves(
	object: Object3D,
	replace: (leaf: Polyhedron3D) => Polyhedron3D,
): Object3D {
	return "members" in object
		? {
			classRef: "tag:nuke24.net,2026-11:s38s39:UnionObject3D-v1",
			members: object.members.map((m) => mapLeaves(m, replace)),
		}
		: replace(object);
}

/** The endpoints of one polygon edge, in the edge's direction. */
function endpointsOf(
	data: Polyhedron3DData,
	edge: PolygonEdge,
): readonly [Vertex, Vertex] {
	const [a, b] = itemAt(data.lines, edge.line, "line");
	return edge.direction === "forwards"
		? [data.vertexes[a], data.vertexes[b]]
		: [data.vertexes[b], data.vertexes[a]];
}

/** The loops of one polygon, found by grouping its flat edges into cycles. */
function loopOfPolygon(data: Polyhedron3DData, polygonIndex: number): Loop[] {
	const edges = itemAt(data.polygons, polygonIndex, "polygon").map((edge) =>
		endpointsOf(data, edge)
	);
	return loopsOfEdges(edges, keyOf);
}

/** The loops of each polygon of one polyhedron, in face order. */
// IMPLIED WINDING ORDER APPLIES HERE
export function polygonLoopsOfPolyhedron(leaf: Polyhedron3D): Loop[][] {
	const faces = itemAt(leaf.data.polyhedrons, leaf.polyhedron, "polyhedron");
	return faces.map((face) => {
		const loops = loopOfPolygon(leaf.data, face.polygon);
		return face.side === "front"
			? loops
			: loops.map((loop) => loop.slice().reverse());
	});
}

/** The loops of one polyhedron, counter-clockwise when viewed from outside. */
export function loopOfPolyhedron(leaf: Polyhedron3D): Loop[] {
	return polygonLoopsOfPolyhedron(leaf).flat();
}

/** The loops of every polyhedron in an object, one list per polyhedron. */
export function loopsOfObject3d(object: Object3D): Loop[][] {
	return polyhedronLeaves(object).map(loopOfPolyhedron);
}

/**
 * Lists the triangles of an object, counter-clockwise when viewed from outside.
 * Only triangular polygons are supported.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function trianglesOfObject3d(object: Object3D): Triangle[] {
	return loopsOfObject3d(object).flat().map((loop) => {
		if (loop.length !== 3) {
			throw new Error(
				`A polygon has ${loop.length} vertexes; only triangles can be written as STL`,
			);
		}
		return loop as Triangle;
	});
}

/**
 * Throws unless the object is manifold, a simplified definition: every polygon is closed,
 * and every edge is matched by an equal number of uses in the opposite direction.
 */
export function checkManifold(object: Object3D): void {
	const counts = new Map<string, number>();
	for (const leaf of polyhedronLeaves(object)) {
		const faces = itemAt(
			leaf.data.polyhedrons,
			leaf.polyhedron,
			"polyhedron",
		);
		for (const face of faces) {
			// Walking the edges checks that the polygon is closed and does not branch.
			loopOfPolygon(leaf.data, face.polygon);
			for (
				const edge of itemAt(
					leaf.data.polygons,
					face.polygon,
					"polygon",
				)
			) {
				let [from, to] = endpointsOf(leaf.data, edge);
				if (face.side === "back") [from, to] = [to, from];
				const key = `${keyOf(from)}|${keyOf(to)}`;
				counts.set(key, (counts.get(key) ?? 0) + 1);
			}
		}
	}
	for (const [key, count] of counts) {
		const [from, to] = key.split("|");
		const reverse = counts.get(`${to}|${from}`) ?? 0;
		if (reverse !== count) {
			throw new Error(
				`Object is not manifold: the edge from (${from}) to (${to}) is used ${count} time(s), and its reverse ${reverse} time(s)`,
			);
		}
	}
}

/**
 * Writes only the parts of `data` that the given polyhedrons refer to, renumbered in their original order.
 * Returns the new data, and the new index of each old polyhedron.
 */
function pruneData(
	data: Polyhedron3DData,
	polyhedronIndexes: number[],
): { data: Polyhedron3DData; polyhedron: Map<number, number> } {
	const polyhedrons = polyhedronIndexes.map((i) =>
		itemAt(data.polyhedrons, i, "polyhedron")
	);
	const polygonIndexes = sortedUnique(
		polyhedrons.flatMap((p) => p.map((face) => face.polygon)),
	);
	const polygons = polygonIndexes.map((i) =>
		itemAt(data.polygons, i, "polygon")
	);
	const lineIndexes = sortedUnique(
		polygons.flatMap((polygon) => polygon.map((edge) => edge.line)),
	);
	const lines = lineIndexes.map((i) => itemAt(data.lines, i, "line"));
	const vertexIndexes = sortedUnique(lines.flat());
	
	const renumber = (indexes: number[]) =>
		new Map(indexes.map((old, i) => [old, i]));
	const vertexMap = renumber(vertexIndexes);
	const lineMap = renumber(lineIndexes);
	const polygonMap = renumber(polygonIndexes);
	
	return {
		data: {
			classRef: data.classRef,
			vertexes: vertexIndexes.map((i) => data.vertexes[i]),
			lines: lines.map((
				[a, b],
			): [number, number] => [vertexMap.get(a)!, vertexMap.get(b)!]),
			polygons: polygons.map((polygon) =>
				polygon.map((edge) => ({
					line: lineMap.get(edge.line)!,
					direction: edge.direction,
				}))
			),
			polyhedrons: polyhedrons.map((polyhedron) =>
				polyhedron.map((face) => ({
					polygon: polygonMap.get(face.polygon)!,
					side: face.side,
				}))
			),
		},
		polyhedron: new Map(polyhedronIndexes.map((old, i) => [old, i])),
	};
}

/**
 * Writes a new `Polyhedron3DData` for each data the object refers to, containing only what the object's
 * polyhedrons use. The union structure is kept.
 */
export function pruneObject3d(object: Object3D): Object3D {
	const used = new Map<Polyhedron3DData, Set<number>>();
	for (const leaf of polyhedronLeaves(object)) {
		const set = used.get(leaf.data) ?? new Set<number>();
		set.add(leaf.polyhedron);
		used.set(leaf.data, set);
	}
	const pruned = new Map<Polyhedron3DData, ReturnType<typeof pruneData>>();
	for (const [data, set] of used) {
		pruned.set(data, pruneData(data, sortedUnique([...set])));
	}
	return mapLeaves(object, (leaf) => {
		const result = pruned.get(leaf.data)!;
		return {
			classRef: leaf.classRef,
			data: result.data,
			polyhedron: result.polyhedron.get(leaf.polyhedron)!,
		};
	});
}
