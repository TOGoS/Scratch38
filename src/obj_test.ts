import { assertEquals, assertThrows } from "@std/assert";
import type { Vertex } from "./stl.ts";
import { makeObject3D } from "./object3d.ts";
import { formatObj, parseObj } from "./obj.ts";

const SQUARE = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4\n";

Deno.test("parses a polygon with any number of vertexes", () => {
	assertEquals(parseObj(SQUARE), [[[[0, 0, 0], [1, 0, 0], [1, 1, 0], [
		0,
		1,
		0,
	]]]]);
});

Deno.test("negative vertex references count back from the vertexes read so far", () => {
	assertEquals(parseObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n"), [[[
		[0, 0, 0],
		[1, 0, 0],
		[0, 1, 0],
	]]]);
});

Deno.test("texture and normal references are ignored", () => {
	const text =
		"v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvn 0 0 1\nf 1/1/1 2/1/1 3//1\n";
	assertEquals(parseObj(text), [[[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]]);
});

Deno.test("comments and vertex weights are accepted", () => {
	const text =
		"# a comment\nv 0 0 0 1.0 # origin\nv 1 0 0\nv 0 1 0\nf 1 2 3 # face\n";
	assertEquals(parseObj(text), [[[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]]);
});

Deno.test("each o statement starts a new polyhedron, whatever its name", () => {
	const polyhedra = parseObj(
		"v 0 0 0\nv 1 0 0\nv 0 1 0\no first\nf 1 2 3\no first\nv 0 0 1\nf 1 2 4\n",
	);
	assertEquals(polyhedra.length, 2);
	assertEquals(polyhedra[1], [[[0, 0, 0], [1, 0, 0], [0, 0, 1]]]);
});

Deno.test("group, smoothing, material and library statements are ignored", () => {
	const polyhedra = parseObj(
		`mtllib things.mtl\ng a b\ns 1\nusemtl red\n${SQUARE}`,
	);
	assertEquals(polyhedra.length, 1);
});

Deno.test("unsupported statements are errors, with the line number", () => {
	assertThrows(
		() => parseObj(`${SQUARE}l 1 2\n`),
		Error,
		'line 6: unsupported OBJ statement "l"',
	);
	assertThrows(
		() => parseObj(`${SQUARE}vp 0 0\n`),
		Error,
		'unsupported OBJ statement "vp"',
	);
});

Deno.test("a face needs at least three vertexes", () => {
	assertThrows(
		() => parseObj("v 0 0 0\nv 1 0 0\nf 1 2\n"),
		Error,
		"at least three vertexes",
	);
});

Deno.test("a face may not refer to a missing vertex", () => {
	assertThrows(
		() => parseObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 4\n"),
		Error,
		"vertex 4, which does not exist",
	);
});

Deno.test("a file without faces is an error", () => {
	assertThrows(() => parseObj("v 0 0 0\n"), Error, "has no faces");
});

Deno.test("writing then reading keeps every polyhedron, in order", () => {
	const square: Vertex[][] = [[[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]]];
	const triangle: Vertex[][] = [[[0, 0, 0], [1, 0, 0], [0, 0, 1]]];
	const text = formatObj(makeObject3D([square, triangle]));
	assertEquals(parseObj(text), [square, triangle]);
});
