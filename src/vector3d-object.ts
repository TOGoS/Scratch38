import type { Triangle, Vertex } from "./stl.ts";

/**
 * Vector-based 3D object. See "Internal Model of Vector3DObject" in README.md.
 */
export interface Vector3DObject {
	classRef: "tag:nuke24.net,2026-11:s38s39:Vector3DObject-v1";
	vertexes: Vertex[];
	/** Each line is a pair of vertex indexes. */
	lines: [number, number][];
	polygons: Polygon[];
	polyhedrons: Polyhedron[];
	/** Each group is a list of polyhedron indexes. */
	polyhedronGroups: number[][];
	/** Top-level names to polyhedron group indexes. `default` is the object as a whole. */
	namedGroups: Record<string, number>;
}

export interface PolygonEdge {
	line: number;
	direction: "forwards" | "backwards";
}

/** Edges in counter-clockwise order, viewed from the polygon's front. */
export type Polygon = PolygonEdge[];

export interface PolyhedronFace {
	polygon: number;
	side: "front" | "back";
}

export type Polyhedron = PolyhedronFace[];

const CLASS_REF = "tag:nuke24.net,2026-11:s38s39:Vector3DObject-v1" as const;

/**
 * Builds a Vector3DObject from polyhedrons, each a list of polygon loops whose vertexes
 * are counter-clockwise when viewed from outside. Shared vertexes and edges are stored once.
 * All polyhedrons go in one group, which is the `default` object.
 */
export function vector3DObjectFromPolyhedrons(
	polyhedrons: Vertex[][][],
): Vector3DObject {
	const vertexes: Vertex[] = [];
	const vertexIndexes = new Map<string, number>();
	const vertexIndex = (v: Vertex): number => {
		const key = v.join(",");
		let index = vertexIndexes.get(key);
		if (index === undefined) {
			index = vertexes.length;
			vertexes.push(v);
			vertexIndexes.set(key, index);
		}
		return index;
	};
	
	const lines: [number, number][] = [];
	const lineIndexes = new Map<string, number>();
	const lineIndex = (
		from: number,
		to: number,
	): { line: number; direction: PolygonEdge["direction"] } => {
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
			direction: a === from ? "forwards" : "backwards",
		};
	};
	
	// IMPLIED WINDING ORDER APPLIES HERE
	const polygons: Polygon[] = polyhedrons.flat().map((loop) => {
		const indexes = loop.map(vertexIndex);
		return indexes.map((from, i) =>
			lineIndex(from, indexes[(i + 1) % indexes.length])
		);
	});
	
	let polygonCount = 0;
	const polyhedronFaces: Polyhedron[] = polyhedrons.map((loops) =>
		loops.map(() => ({ polygon: polygonCount++, side: "front" as const }))
	);
	
	return {
		classRef: CLASS_REF,
		vertexes,
		lines,
		polygons,
		polyhedrons: polyhedronFaces,
		polyhedronGroups: [polyhedronFaces.map((_, i) => i)],
		namedGroups: { default: 0 },
	};
}

/**
 * Builds a Vector3DObject from triangles, whose vertexes are counter-clockwise
 * when viewed from outside. All triangles become one polyhedron, which is the `default` object.
 */
export function vector3DObjectFromTriangles(
	triangles: Triangle[],
): Vector3DObject {
	return vector3DObjectFromPolyhedrons([triangles]);
}

/** The polyhedrons of the `default` object, in the order its group lists them. */
export function defaultPolyhedrons(object: Vector3DObject): Polyhedron[] {
	const groupIndex = object.namedGroups.default;
	if (groupIndex === undefined) {
		throw new Error("Object has no default group");
	}
	const group = itemAt(
		object.polyhedronGroups,
		groupIndex,
		"polyhedron group",
	);
	return group.map((polyhedronIndex) =>
		itemAt(object.polyhedrons, polyhedronIndex, "polyhedron")
	);
}

/** The faces of the `default` object, in the order they are listed in its polyhedrons. */
function defaultFaces(object: Vector3DObject): PolyhedronFace[] {
	return defaultPolyhedrons(object).flat();
}

/**
 * Returns the vertex indexes at the start of each edge of a polygon, in order.
 * Throws unless every edge ends where the next one starts, so that the polygon is closed.
 */
export function polygonVertexIndexes(
	object: Vector3DObject,
	polygonIndex: number,
): number[] {
	const polygon = itemAt(object.polygons, polygonIndex, "polygon");
	const starts = polygon.map((edge) => {
		const [a, b] = itemAt(object.lines, edge.line, "line");
		return edge.direction === "forwards" ? a : b;
	});
	const ends = polygon.map((edge) => {
		const [a, b] = itemAt(object.lines, edge.line, "line");
		return edge.direction === "forwards" ? b : a;
	});
	for (let i = 0; i < polygon.length; i++) {
		if (ends[i] !== starts[(i + 1) % polygon.length]) {
			throw new Error(
				`Polygon ${polygonIndex} edges do not form a closed loop`,
			);
		}
	}
	return starts;
}

/**
 * Lists the triangles of the `default` object, with vertexes counter-clockwise when viewed from outside.
 * Faces on the `back` side are reversed.
 * Only triangular polygons are supported.
 */
export function trianglesFromVector3DObject(
	object: Vector3DObject,
): Triangle[] {
	const triangles: Triangle[] = [];
	for (const { polygon: polygonIndex, side } of defaultFaces(object)) {
		const polygon = itemAt(object.polygons, polygonIndex, "polygon");
		if (polygon.length !== 3) {
			throw new Error(
				`Polygon ${polygonIndex} has ${polygon.length} edges; only triangles can be written as STL`,
			);
		}
		const starts = polygonVertexIndexes(object, polygonIndex);
		// IMPLIED WINDING ORDER APPLIES HERE
		const vertexes = starts.map((index) =>
			object.vertexes[index]
		) as Triangle;
		// IMPLIED WINDING ORDER APPLIES HERE
		triangles.push(
			side === "front"
				? vertexes
				: [vertexes[0], vertexes[2], vertexes[1]],
		);
	}
	return triangles;
}

/**
 * Throws unless the `default` object is manifold, a simplified definition:
 * every polygon is closed, and every line is used by exactly two polygon edges,
 * which go in different directions.
 */
export function checkManifold(object: Vector3DObject): void {
	const uses = new Map<number, { forwards: number; backwards: number }>();
	for (const { polygon: polygonIndex } of defaultFaces(object)) {
		polygonVertexIndexes(object, polygonIndex);
		for (const edge of itemAt(object.polygons, polygonIndex, "polygon")) {
			const count = uses.get(edge.line) ?? { forwards: 0, backwards: 0 };
			if (edge.direction === "forwards") {
				count.forwards++;
			} else {
				count.backwards++;
			}
			uses.set(edge.line, count);
		}
	}
	for (const [line, count] of uses) {
		if (count.forwards !== 1 || count.backwards !== 1) {
			const [a, b] = itemAt(object.lines, line, "line");
			throw new Error(
				`Object is not manifold: line ${line} (vertexes ${a} and ${b}) is used ${count.forwards} time(s) forwards and ${count.backwards} time(s) backwards; each should be used once`,
			);
		}
	}
}

function sortedUnique(indexes: number[]): number[] {
	return [...new Set(indexes)].sort((a, b) => a - b);
}

function itemAt<T>(items: T[], index: number, what: string): T {
	const item = items[index];
	if (item === undefined) {
		throw new Error(`${what} ${index} does not exist`);
	}
	return item;
}

/**
 * Removes everything not referenced by the named top-level object, and renumbers
 * the rest, keeping their relative order. The result's `default` is that object.
 */
export function pruneVector3DObject(
	object: Vector3DObject,
	mainName: string,
): Vector3DObject {
	if (!Object.hasOwn(object.namedGroups, mainName)) {
		throw new Error(`Object has no top-level object named ${mainName}`);
	}
	const groupIndex = object.namedGroups[mainName];
	
	const polyhedronIndexes = sortedUnique(
		itemAt(object.polyhedronGroups, groupIndex, "polyhedron group"),
	);
	const polyhedrons = polyhedronIndexes.map((i) =>
		itemAt(object.polyhedrons, i, "polyhedron")
	);
	const polygonIndexes = sortedUnique(
		polyhedrons.flatMap((p) => p.map((face) => face.polygon)),
	);
	const polygons = polygonIndexes.map((i) =>
		itemAt(object.polygons, i, "polygon")
	);
	const lineIndexes = sortedUnique(
		polygons.flatMap((p) => p.map((edge) => edge.line)),
	);
	const lines = lineIndexes.map((i) => itemAt(object.lines, i, "line"));
	const vertexIndexes = sortedUnique(lines.flat());
	
	const newIndexes = (indexes: number[]) =>
		new Map(indexes.map((old, i) => [old, i]));
	const vertexMap = newIndexes(vertexIndexes);
	const lineMap = newIndexes(lineIndexes);
	const polygonMap = newIndexes(polygonIndexes);
	
	return {
		classRef: CLASS_REF,
		vertexes: vertexIndexes.map((i) => object.vertexes[i]),
		lines: lines.map(([a, b]): [number, number] => [
			vertexMap.get(a)!,
			vertexMap.get(b)!,
		]),
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
		polyhedronGroups: [polyhedrons.map((_, i) => i)],
		namedGroups: { default: 0 },
	};
}
