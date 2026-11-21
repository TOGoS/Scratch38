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
 * Builds a Vector3DObject from triangles, whose vertexes are counter-clockwise
 * when viewed from outside. Shared vertexes and edges are stored once.
 * All triangles become one polyhedron, which is the `default` object.
 */
export function vector3DObjectFromTriangles(
	triangles: Triangle[],
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
	const polygons: Polygon[] = triangles.map((triangle) => {
		const indexes = triangle.map(vertexIndex);
		return [0, 1, 2].map((i) =>
			lineIndex(indexes[i], indexes[(i + 1) % 3])
		);
	});
	
	return {
		classRef: CLASS_REF,
		vertexes,
		lines,
		polygons,
		polyhedrons: [
			polygons.map((_, polygon) => ({ polygon, side: "front" as const })),
		],
		polyhedronGroups: [[0]],
		namedGroups: { default: 0 },
	};
}

/**
 * Lists the triangles of the `default` object, with vertexes counter-clockwise when viewed from outside.
 * Faces on the `back` side are reversed.
 * Only triangular polygons are supported.
 */
export function trianglesFromVector3DObject(
	object: Vector3DObject,
): Triangle[] {
	const groupIndex = object.namedGroups.default;
	if (groupIndex === undefined) {
		throw new Error("Object has no default group");
	}
	const group = object.polyhedronGroups[groupIndex];
	if (group === undefined) {
		throw new Error(`Default group index ${groupIndex} does not exist`);
	}
	
	const triangles: Triangle[] = [];
	for (const polyhedronIndex of group) {
		const polyhedron = object.polyhedrons[polyhedronIndex];
		if (polyhedron === undefined) {
			throw new Error(`Polyhedron ${polyhedronIndex} does not exist`);
		}
		for (const { polygon: polygonIndex, side } of polyhedron) {
			const polygon = object.polygons[polygonIndex];
			if (polygon === undefined) {
				throw new Error(`Polygon ${polygonIndex} does not exist`);
			}
			if (polygon.length !== 3) {
				throw new Error(
					`Polygon ${polygonIndex} has ${polygon.length} edges; only triangles can be written as STL`,
				);
			}
			const starts = polygon.map((edge) => {
				const [a, b] = object.lines[edge.line];
				return edge.direction === "forwards" ? a : b;
			});
			const ends = polygon.map((edge) => {
				const [a, b] = object.lines[edge.line];
				return edge.direction === "forwards" ? b : a;
			});
			for (let i = 0; i < 3; i++) {
				if (ends[i] !== starts[(i + 1) % 3]) {
					throw new Error(
						`Polygon ${polygonIndex} edges do not form a closed loop`,
					);
				}
			}
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
	}
	return triangles;
}
