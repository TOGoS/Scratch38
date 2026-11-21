import type { Vertex } from "./stl.ts";
import {
	defaultPolyhedronNames,
	loopsFromVector3DObject,
	Vector3DObject,
} from "./vector3d-object.ts";

/** An OBJ object: its name, if any, and its faces as loops of vertex coordinates. */
export interface ObjObject {
	name?: string;
	loops: Vertex[][];
}

/** Statements that are accepted and have no effect here. */
const IGNORED_STATEMENTS = new Set(["vt", "vn", "g", "s", "mtllib", "usemtl"]);

interface PendingObject {
	name?: string;
	/** Faces as 0-based vertex indexes, checked once the whole file has been read. */
	faces: number[][];
}

/**
 * Parses Wavefront OBJ text (see https://paulbourke.net/dataformats/obj/).
 * Supports vertexes (`v`), faces (`f`, with any number of vertexes), and object names (`o`).
 * Texture and normal references are ignored. Other statements, such as free-form curves,
 * lines, and points, are errors.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function parseObj(text: string): ObjObject[] {
	const vertexes: Vertex[] = [];
	const pending: PendingObject[] = [{ faces: [] }];
	let current = pending[0];
	
	text.split("\n").forEach((rawLine, lineIndex) => {
		const line = rawLine.replace(/#.*$/, "").trim();
		if (line === "") return;
		const where = `line ${lineIndex + 1}`;
		const [keyword, ...args] = line.split(/\s+/);
		switch (keyword) {
			case "v": {
				if (args.length < 3 || args.length > 4) {
					throw new Error(
						`${where}: vertex needs three coordinates, and an optional weight`,
					);
				}
				const coords = args.slice(0, 3).map(Number);
				if (!coords.every(Number.isFinite)) {
					throw new Error(
						`${where}: vertex coordinates must be numbers`,
					);
				}
				vertexes.push(coords as Vertex);
				break;
			}
			case "f": {
				if (args.length < 3) {
					throw new Error(
						`${where}: face needs at least three vertexes`,
					);
				}
				current.faces.push(args.map((token) => {
					const n = Number(token.split("/")[0]);
					if (!Number.isInteger(n) || n === 0) {
						throw new Error(
							`${where}: bad vertex reference "${token}"`,
						);
					}
					// Negative references count back from the vertexes read so far.
					return n < 0 ? vertexes.length + n : n - 1;
				}));
				break;
			}
			case "o": {
				// A bare `o` starts a new unnamed object, and `o name` reopens or starts a named one.
				const name = args.join(" ");
				if (name === "") {
					current = { faces: [] };
					pending.push(current);
				} else {
					current = pending.find((p) => p.name === name) ??
						{ name, faces: [] };
					if (!pending.includes(current)) pending.push(current);
				}
				break;
			}
			default:
				if (!IGNORED_STATEMENTS.has(keyword)) {
					throw new Error(
						`${where}: unsupported OBJ statement "${keyword}"`,
					);
				}
		}
	});
	
	const objects = pending.filter((p) => p.faces.length > 0).map((p) => ({
		name: p.name,
		loops: p.faces.map((refs) =>
			refs.map((i) => {
				const v = vertexes[i];
				if (v === undefined) {
					throw new Error(
						`Face refers to vertex ${i + 1}, which does not exist`,
					);
				}
				return v;
			})
		),
	}));
	if (objects.length === 0) {
		throw new Error("OBJ file has no faces");
	}
	return objects;
}

/**
 * Writes a Vector3DObject's `default` object as OBJ text. Each polyhedron gets an `o`
 * line, named if it has a name, so that names survive a round trip.
 */
export function formatObj(object: Vector3DObject): string {
	const polyhedrons = loopsFromVector3DObject(object);
	const names = defaultPolyhedronNames(object);
	
	const vertexes: Vertex[] = [];
	const indexes = new Map<string, number>();
	const indexOf = (v: Vertex): number => {
		const key = v.join(",");
		let index = indexes.get(key);
		if (index === undefined) {
			index = vertexes.length;
			vertexes.push(v);
			indexes.set(key, index);
		}
		return index;
	};
	
	const body: string[] = [];
	polyhedrons.forEach((loops, p) => {
		body.push(names[p] === undefined ? "o" : `o ${names[p]}`);
		for (const loop of loops) {
			// IMPLIED WINDING ORDER APPLIES HERE
			body.push(`f ${loop.map((v) => indexOf(v) + 1).join(" ")}`);
		}
	});
	
	const header = ["# written by stlmod"];
	const vertexLines = vertexes.map((v) => `v ${v.join(" ")}`);
	return [...header, ...vertexLines, ...body].join("\n") + "\n";
}
