import type { Vertex } from "./stl.ts";
import {
	Loop,
	Object3D,
	polygonLoopsOfPolyhedron,
	polyhedronLeaves,
} from "./object3d.ts";

/** Statements that are accepted and have no effect here. */
const IGNORED_STATEMENTS = new Set(["vt", "vn", "g", "s", "mtllib", "usemtl"]);

/**
 * Parses Wavefront OBJ text (see https://paulbourke.net/dataformats/obj/) into polyhedrons.
 * Each `o` statement starts a new polyhedron, and faces before the first `o` form one too.
 * Object names are not kept. Supports vertexes (`v`), faces (`f`, with any number of vertexes),
 * and object names (`o`). Texture and normal references are ignored. Other statements, such as
 * free-form curves, lines, and points, are errors.
 */
// IMPLIED WINDING ORDER APPLIES HERE
export function parseObj(text: string): Loop[][] {
	const vertexes: Vertex[] = [];
	const sections: number[][][] = [[]];
	
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
				sections[sections.length - 1].push(args.map((token) => {
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
			case "o":
				sections.push([]);
				break;
			default:
				if (!IGNORED_STATEMENTS.has(keyword)) {
					throw new Error(
						`${where}: unsupported OBJ statement "${keyword}"`,
					);
				}
		}
	});
	
	const polyhedra = sections
		.filter((section) => section.length > 0)
		.map((section) =>
			section.map((indexes) =>
				indexes.map((i) => {
					const v = vertexes[i];
					if (v === undefined) {
						throw new Error(
							`Face refers to vertex ${
								i + 1
							}, which does not exist`,
						);
					}
					return v;
				})
			)
		);
	if (polyhedra.length === 0) {
		throw new Error("OBJ file has no faces");
	}
	return polyhedra;
}

/**
 * Writes an object as OBJ text. Each polyhedron gets a bare `o` line, since objects have no names.
 * OBJ faces cannot express holes, so a polygon with more than one boundary is an error.
 */
export function formatObj(object: Object3D): string {
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
	for (const leaf of polyhedronLeaves(object)) {
		body.push("o");
		for (const loops of polygonLoopsOfPolyhedron(leaf)) {
			if (loops.length === 0) {
				throw new Error("OBJ cannot write a polygon with no boundary");
			}
			if (loops.length > 1) {
				throw new Error("OBJ cannot write a polygon with holes");
			}
			// IMPLIED WINDING ORDER APPLIES HERE
			body.push(`f ${loops[0].map((v) => indexOf(v) + 1).join(" ")}`);
		}
	}
	
	const header = ["# written by stlmod"];
	const vertexLines = vertexes.map((v) => `v ${v.join(" ")}`);
	return [...header, ...vertexLines, ...body].join("\n") + "\n";
}
