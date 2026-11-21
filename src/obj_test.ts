import { assertEquals, assertThrows } from "@std/assert";
import type { Vertex } from "./stl.ts";
import { formatObj, parseObj } from "./obj.ts";
import { vector3DObjectFromPolyhedrons } from "./vector3d-object.ts";

const SQUARE = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nf 1 2 3 4\n";

Deno.test("parses a polygon with any number of vertexes", () => {
	const [object] = parseObj(SQUARE);
	assertEquals(object.name, undefined);
	assertEquals(object.loops, [[[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]]]);
});

Deno.test("negative vertex references count back from the vertexes read so far", () => {
	const [object] = parseObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1\n");
	assertEquals(object.loops, [[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]);
});

Deno.test("texture and normal references are ignored", () => {
	const text =
		"v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvn 0 0 1\nf 1/1/1 2/1/1 3//1\n";
	const [object] = parseObj(text);
	assertEquals(object.loops, [[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]);
});

Deno.test("comments and vertex weights are accepted", () => {
	const [object] = parseObj(
		"# a comment\nv 0 0 0 1.0 # origin\nv 1 0 0\nv 0 1 0\nf 1 2 3 # face\n",
	);
	assertEquals(object.loops, [[[0, 0, 0], [1, 0, 0], [0, 1, 0]]]);
});

Deno.test("each o statement starts a named object", () => {
	const objects = parseObj(
		"v 0 0 0\nv 1 0 0\nv 0 1 0\no first\nf 1 2 3\no second\nv 0 0 1\nf 1 2 4\n",
	);
	assertEquals(objects.map((o) => o.name), ["first", "second"]);
	assertEquals(objects[1].loops, [[[0, 0, 0], [1, 0, 0], [0, 0, 1]]]);
});

Deno.test("group, smoothing, material and library statements are ignored", () => {
	const [object] = parseObj(
		`mtllib things.mtl\ng a b\ns 1\nusemtl red\n${SQUARE}`,
	);
	assertEquals(object.loops.length, 1);
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

Deno.test("writing then reading keeps names, including an unnamed object", () => {
	const square: Vertex[][] = [[[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]]];
	const triangle: Vertex[][] = [[[0, 0, 0], [1, 0, 0], [0, 0, 1]]];
	const object = vector3DObjectFromPolyhedrons(
		[square, triangle],
		[undefined, "tri"],
	);
	const objects = parseObj(formatObj(object));
	assertEquals(objects.map((o) => o.name), [undefined, "tri"]);
	assertEquals(objects[0].loops, [[[0, 0, 0], [1, 0, 0], [1, 1, 0], [
		0,
		1,
		0,
	]]]);
	assertEquals(objects[1].loops, [[[0, 0, 0], [1, 0, 0], [0, 0, 1]]]);
});
